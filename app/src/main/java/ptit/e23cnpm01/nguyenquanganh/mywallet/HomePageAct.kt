package ptit.e23cnpm01.nguyenquanganh.mywallet

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import java.text.Collator
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HomePageAct : AppCompatActivity() {

    lateinit var totalIncome: TextView
    lateinit var totalOutcome: TextView
    lateinit var dbHelper: DBHelper
    private lateinit var titleView: TextView
    private lateinit var listContainer: LinearLayout

    private val expandedCategoryIds = mutableSetOf<Long>()
    private var useFrequencyOrder = false
    private var displayedDatabaseDate: String? = null
    private var selectedCategoryId: Long? = null

    private var categories: List<WalletCategory> = emptyList()
    private var amountsByCategory: Map<Long, Double> = emptyMap()
    private var usageCountsByCategory: Map<Long, Int> = emptyMap()
    private var transactionsByCategory: Map<Long, List<WalletTransaction>> = emptyMap()
    private val categoryLogoCache = mutableMapOf<String, Bitmap>()

    private val vietnamLocale = Locale("vi", "VN")
    private val databaseDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val displayDateFormat = SimpleDateFormat("dd/MM/yyyy", vietnamLocale)
    private val collator = Collator.getInstance(vietnamLocale)
    private val byName = Comparator<WalletCategory> { first, second ->
        collator.compare(first.name, second.name)
    }

    private val addTransactionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != RESULT_OK) return@registerForActivityResult
        val data = result.data ?: return@registerForActivityResult
        val newCategoryId = data.getLongExtra(AddTransactionAct.EXTRA_NEW_CATEGORY_ID, -1L)
        if (newCategoryId > 0) {
            expandedCategoryIds.addAll(dbHelper.getParentCategoryIds(newCategoryId))
            showTodaySummary()
            return@registerForActivityResult
        }
        val savedDate = data.getStringExtra(AddTransactionAct.EXTRA_TRANSACTION_DATE)
            ?: return@registerForActivityResult
        showDate(savedDate)
        selectCategory(data.getLongExtra(AddTransactionAct.EXTRA_CATEGORY_ID, -1L))
    }

    private val editTransactionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != RESULT_OK) return@registerForActivityResult
        val data = result.data
        data?.getStringExtra(AddTransactionAct.EXTRA_TRANSACTION_DATE)?.let(::showDate)
        selectedCategoryId = null
        selectCategory(data?.getLongExtra(AddTransactionAct.EXTRA_CATEGORY_ID, -1L) ?: -1L)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        setContentView(R.layout.home_page)

        dbHelper = DBHelper(this)
        useFrequencyOrder = dbHelper.getHasOpenedCategoryList()
        // Mỗi lần mở ứng dụng bắt đầu từ ngày hiện tại, không khôi phục ngày xem cũ.
        displayedDatabaseDate = databaseDateFormat.format(Date())

        titleView = findViewById(R.id.title)
        totalIncome = findViewById(R.id.totalIncome)
        totalOutcome = findViewById(R.id.totalOutcome)
        listContainer = findViewById(R.id.transactionList)

        findViewById<TextView>(R.id.addTransactionButton).setOnClickListener {
            addTransactionLauncher.launch(
                Intent(this, AddTransactionAct::class.java)
                    .putExtra(AddTransactionAct.EXTRA_INITIAL_DATE, displayedDatabaseDate)
            )
        }
    }

    override fun onResume() {
        super.onResume()

        showTodaySummary()
        if (!useFrequencyOrder) dbHelper.setHasOpenedCategoryList(true)
    }

    private fun showDate(date: String) {
        displayedDatabaseDate = date
        dbHelper.setDisplayedDate(date)
    }

    private fun selectCategory(categoryId: Long) {
        if (categoryId <= 0) return
        selectedCategoryId = categoryId
        expandedCategoryIds.addAll(dbHelper.getParentCategoryIds(categoryId))
    }

    private fun showTodaySummary() {
        val today = displayedDatabaseDate ?: databaseDateFormat.format(Date())
        displayedDatabaseDate = today
        val displayedDate = runCatching { databaseDateFormat.parse(today) }.getOrNull()
            ?.let { displayDateFormat.format(it) }
            ?: today

        titleView.text = getString(R.string.home_title_date, displayedDate)
        totalIncome.text = getString(R.string.home_total_income, formatAmount(dbHelper.getTotalIncomeByDate(today)))
        totalOutcome.text = getString(R.string.home_total_expense, formatAmount(dbHelper.getTotalExpenseByDate(today)))

        categories = dbHelper.getCategoriesByType(DBHelper.TYPE_INCOME) +
            dbHelper.getCategoriesByType(DBHelper.TYPE_EXPENSE)
        amountsByCategory = dbHelper.getCategoryAmountsIncludingChildrenByDate(today)
        usageCountsByCategory = dbHelper.getCategoryUsageCounts()
        transactionsByCategory = dbHelper.getTransactionsByDate(today).groupBy { it.categoryId }

        renderCategoryList()
    }

    private fun renderCategoryList() {
        listContainer.removeAllViews()

        if (categories.isEmpty()) {
            layoutInflater.inflate(R.layout.item_home_no_category, listContainer, true)
            return
        }

        val childrenByParent = categories.groupBy { it.parentId }
        val displayedCategoryIds = mutableSetOf<Long>()
        val usageByCategory = mutableMapOf<Long, Int>()

        fun categoryUsage(category: WalletCategory): Int = usageByCategory.getOrPut(category.id) {
            (usageCountsByCategory[category.id] ?: 0) +
                childrenByParent[category.id].orEmpty().sumOf { categoryUsage(it) }
        }

        fun addCategory(category: WalletCategory, level: Int) {
            if (!displayedCategoryIds.add(category.id)) return

            val children = childrenByParent[category.id].orEmpty()
            val categoryColor = getColor(
                if (category.typeId == DBHelper.TYPE_INCOME) R.color.income else R.color.expense
            )

            listContainer.addView(createCategoryRow(category, level, categoryColor, hasChildren = children.isNotEmpty()))

            if (selectedCategoryId == category.id) {
                val categoryTransactions = transactionsByCategory[category.id].orEmpty()
                if (categoryTransactions.isEmpty()) {
                    listContainer.addView(
                        layoutInflater.inflate(R.layout.item_home_empty, listContainer, false)
                            .apply { indent(level + 1) }
                    )
                } else {
                    categoryTransactions.forEach { transaction ->
                        listContainer.addView(createTransactionRow(transaction, level + 1, categoryColor))
                    }
                }
            }
            if (category.id in expandedCategoryIds) {
                children.sortedWith(byName).forEach { child -> addCategory(child, level + 1) }
            }
        }

        val parentCategories = childrenByParent[null].orEmpty().sortedWith(byName)
        val sortedParents = if (useFrequencyOrder) {
            parentCategories.sortedWith(
                compareByDescending<WalletCategory> { categoryUsage(it) }.then(byName)
            )
        } else {
            parentCategories
        }
        sortedParents.forEach { category -> addCategory(category, 0) }
    }

    private fun createCategoryRow(
        category: WalletCategory,
        level: Int,
        color: Int,
        hasChildren: Boolean
    ): View = layoutInflater.inflate(R.layout.item_home_category, listContainer, false).apply {
        indent(level)
        setOnClickListener {
            if (hasChildren) {
                if (expandedCategoryIds.remove(category.id)) {
                    selectedCategoryId = null
                } else {
                    expandedCategoryIds.add(category.id)
                    selectedCategoryId = category.id
                }
            } else {
                selectedCategoryId = if (selectedCategoryId == category.id) null else category.id
            }
            renderCategoryList()
        }

        findViewById<ImageView>(R.id.imgCategoryIcon).apply {
            contentDescription = getString(R.string.home_category_icon_desc, category.name)
            val logo = categoryLogoCache[category.iconName] ?: runCatching {
                assets.open(category.iconName).use { BitmapFactory.decodeStream(it) }
            }.getOrNull()
            if (logo != null) {
                categoryLogoCache[category.iconName] = logo
                setImageBitmap(logo)
            }
        }
        findViewById<TextView>(R.id.tvCategoryName).apply {
            text = category.name
            setTextColor(color)
        }
        findViewById<TextView>(R.id.tvCategoryAmount).apply {
            text = formatAmount(amountsByCategory[category.id] ?: 0.0)
            setTextColor(color)
        }
        findViewById<ImageButton>(R.id.btnEditCategory).apply {
            contentDescription = getString(R.string.home_category_edit_desc, category.name)
            setOnClickListener {
                startActivity(
                    Intent(this@HomePageAct, EditCategoryAct::class.java)
                        .putExtra(EditCategoryAct.EXTRA_CATEGORY_ID, category.id)
                )
            }
        }
    }

    private fun createTransactionRow(
        transaction: WalletTransaction,
        level: Int,
        color: Int
    ): View = layoutInflater.inflate(R.layout.item_home_transaction, listContainer, false).apply {
        val label = transaction.note ?: transaction.categoryName
        indent(level)
        contentDescription = getString(R.string.home_transaction_edit_desc, label)
        setOnClickListener {
            editTransactionLauncher.launch(
                Intent(this@HomePageAct, EditTransactionAct::class.java)
                    .putExtra(EditTransactionAct.EXTRA_TRANSACTION_ID, transaction.id)
            )
        }

        findViewById<TextView>(R.id.tvTransactionLabel).apply {
            text = getString(R.string.home_transaction_label, label)
            setTextColor(color)
        }
        findViewById<TextView>(R.id.tvTransactionAmount).apply {
            text = formatAmount(transaction.amount)
            setTextColor(color)
        }
    }

    private fun View.indent(level: Int) {
        val step = resources.getDimensionPixelSize(R.dimen.category_indent)
        setPaddingRelative(paddingStart + level * step, paddingTop, paddingEnd, paddingBottom)
    }

    private fun formatAmount(amount: Double): String = AmountFormatter.format(amount)

    override fun onDestroy() {
        dbHelper.close()
        super.onDestroy()
    }
}
