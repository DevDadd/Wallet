    package ptit.e23cnpm01.nguyenquanganh.mywallet

import android.content.Intent
import android.os.Bundle
import android.graphics.Color
import android.view.Gravity
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.text.Collator
import java.util.Date
import java.util.Locale

class HomePageAct : AppCompatActivity() {

    lateinit var totalIncome: TextView
    lateinit var totalOutcome: TextView
    lateinit var dbHelper: DBHelper
    private val expandedCategoryIds = mutableSetOf<Long>()
    private var useFrequencyOrder = false
    private var displayedDatabaseDate: String? = null
    private var selectedCategoryId: Long? = null

    private val addTransactionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != RESULT_OK) return@registerForActivityResult

        val data = result.data
        val savedDate = data?.getStringExtra(AddTransactionAct.EXTRA_TRANSACTION_DATE) ?: return@registerForActivityResult
        displayedDatabaseDate = savedDate
        getSharedPreferences(PREFERENCES_NAME, MODE_PRIVATE).edit()
            .putString(KEY_DISPLAYED_DATE, savedDate)
            .apply()

        val categoryId = data.getLongExtra(AddTransactionAct.EXTRA_CATEGORY_ID, -1L)
        if (categoryId > 0) {
            selectedCategoryId = categoryId
            expandedCategoryIds.addAll(dbHelper.getParentCategoryIds(categoryId))
        }
        showTodaySummary()
    }

    private val editTransactionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != RESULT_OK) return@registerForActivityResult
        val data = result.data
        val changedDate = data?.getStringExtra(AddTransactionAct.EXTRA_TRANSACTION_DATE)
        if (changedDate != null) {
            displayedDatabaseDate = changedDate
            getSharedPreferences(PREFERENCES_NAME, MODE_PRIVATE).edit()
                .putString(KEY_DISPLAYED_DATE, changedDate)
                .apply()
        }
        selectedCategoryId = data?.getLongExtra(AddTransactionAct.EXTRA_CATEGORY_ID, -1L)
            ?.takeIf { it > 0 }
        selectedCategoryId?.let { expandedCategoryIds.addAll(dbHelper.getParentCategoryIds(it)) }
        showTodaySummary()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        setContentView(R.layout.home_page)

        dbHelper = DBHelper(this)
        val preferences = getSharedPreferences(PREFERENCES_NAME, MODE_PRIVATE)
        useFrequencyOrder = preferences
            .getBoolean(KEY_HAS_OPENED_CATEGORY_LIST, false)
        displayedDatabaseDate = preferences.getString(KEY_DISPLAYED_DATE, null)

        totalIncome = findViewById(R.id.totalIncome)
        totalOutcome = findViewById(R.id.totalOutcome)

        findViewById<TextView>(R.id.addTransactionButton).setOnClickListener {
            addTransactionLauncher.launch(
                Intent(this, AddTransactionAct::class.java)
                    .putExtra(AddTransactionAct.EXTRA_INITIAL_DATE, displayedDatabaseDate)
            )
        }

        ViewCompat.setOnApplyWindowInsetsListener(
            findViewById(R.id.homepage)
        ) { v, insets ->

            val systemBars =
                insets.getInsets(WindowInsetsCompat.Type.systemBars())

            v.setPadding(
                systemBars.left,
                systemBars.top,
                systemBars.right,
                systemBars.bottom
            )

            insets
        }

        // Nạp dữ liệu ngay ở lần đầu mở trang chủ.
        showTodaySummary()
    }

    override fun onResume() {
        super.onResume()

        showTodaySummary()
        getSharedPreferences(PREFERENCES_NAME, MODE_PRIVATE).edit()
            .putBoolean(KEY_HAS_OPENED_CATEGORY_LIST, true)
            .apply()
    }

    private fun showTodaySummary() {
        val today = displayedDatabaseDate
            ?: SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        displayedDatabaseDate = today
        val displayedDate = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            .parse(today)
            ?.let { SimpleDateFormat("dd/MM/yyyy", Locale("vi", "VN")).format(it) }
            ?: today

        val total = dbHelper.getTotalIncomeByDate(today)
        val outcome = dbHelper.getTotalExpenseByDate(today)

        findViewById<TextView>(R.id.title).text = "Ngày $displayedDate"
        totalIncome.text = "Tổng thu của ngày\n${formatAmount(total)}"
        totalOutcome.text = "Tổng chi của ngày\n${formatAmount(outcome)}"

        showCategoryLists(
            incomes = dbHelper.getCategoriesByType(DBHelper.TYPE_INCOME),
            expenses = dbHelper.getCategoriesByType(DBHelper.TYPE_EXPENSE),
            amountsByCategory = dbHelper.getCategoryAmountsIncludingChildrenByDate(today),
            usageCountsByCategory = dbHelper.getCategoryUsageCounts(),
            transactions = dbHelper.getTransactionsByDate(today)
        )
    }

    private fun showCategoryLists(
        incomes: List<WalletCategory>,
        expenses: List<WalletCategory>,
        amountsByCategory: Map<Long, Double>,
        usageCountsByCategory: Map<Long, Int>,
        transactions: List<WalletTransaction>
    ) {
        val listContainer = findViewById<LinearLayout>(R.id.transactionList)
        listContainer.removeAllViews()

        if (incomes.isEmpty() && expenses.isEmpty()) {
            listContainer.addView(TextView(this).apply {
                text = "Chưa có danh mục"
                textSize = 16f
                gravity = Gravity.CENTER
                setTextColor(Color.GRAY)
                setPadding(0, dp(24), 0, dp(24))
            })
            return
        }
        addCategoryItems(
            categories = incomes + expenses,
            amountsByCategory = amountsByCategory,
            usageCountsByCategory = usageCountsByCategory,
            transactions = transactions,
            listContainer = listContainer
        )
    }

    private fun addCategoryItems(
        categories: List<WalletCategory>,
        amountsByCategory: Map<Long, Double>,
        usageCountsByCategory: Map<Long, Int>,
        transactions: List<WalletTransaction>,
        listContainer: LinearLayout
    ) {
        val childrenByParent = categories.groupBy { it.parentId }
        val displayedCategoryIds = mutableSetOf<Long>()
        val usageByCategory = mutableMapOf<Long, Int>()
        val collator = Collator.getInstance(Locale("vi", "VN"))

        fun alphabetically(items: List<WalletCategory>): List<WalletCategory> =
            items.sortedWith { first, second -> collator.compare(first.name, second.name) }

        fun categoryTotal(category: WalletCategory): Double = amountsByCategory[category.id] ?: 0.0

        fun categoryUsage(category: WalletCategory): Int = usageByCategory.getOrPut(category.id) {
            var usage = usageCountsByCategory[category.id] ?: 0
            childrenByParent[category.id].orEmpty().forEach { child ->
                usage += categoryUsage(child)
            }
            usage
        }

        fun addTransactionRow(transaction: WalletTransaction, level: Int, color: Int) {
            val row = LinearLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(4) }
                gravity = Gravity.CENTER_VERTICAL
                orientation = LinearLayout.HORIZONTAL
                setPadding(dp(20 + level * 28), dp(4), dp(16), dp(4))
                isClickable = true
                isFocusable = true
                contentDescription = "Sửa giao dịch ${transaction.note ?: transaction.categoryName}"
                setOnClickListener {
                    editTransactionLauncher.launch(
                        Intent(this@HomePageAct, EditTransactionAct::class.java)
                            .putExtra(EditTransactionAct.EXTRA_TRANSACTION_ID, transaction.id)
                    )
                }
            }
            row.addView(TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                text = "• ${transaction.note ?: transaction.categoryName}"
                textSize = 15f
                setTextColor(color)
            })
            row.addView(TextView(this).apply {
                text = formatAmount(transaction.amount)
                textSize = 15f
                setTextColor(color)
            })
            listContainer.addView(row)
        }

        fun addCategory(category: WalletCategory, level: Int) {
            if (!displayedCategoryIds.add(category.id)) return

            val row = LinearLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(8) }
                gravity = Gravity.CENTER_VERTICAL
                orientation = LinearLayout.HORIZONTAL
                setPadding(dp(16 + level * 28), dp(6), dp(16), dp(6))
            }

            val children = childrenByParent[category.id].orEmpty()
            val categoryColor = if (category.typeId == DBHelper.TYPE_INCOME) {
                Color.rgb(0, 190, 0)
            } else {
                Color.RED
            }

            row.addView(ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(dp(44), dp(44))
                setImageResource(R.mipmap.ic_launcher)
                contentDescription = "Biểu tượng ${category.name}"
            })

            row.addView(TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    .apply { marginStart = dp(16) }
                text = category.name
                textSize = 18f
                setTextColor(categoryColor)
            })

            if (category.parentId == null) {
                row.addView(ImageButton(this).apply {
                    layoutParams = LinearLayout.LayoutParams(dp(40), dp(40))
                    setImageResource(android.R.drawable.ic_menu_edit)
                    setBackgroundColor(Color.TRANSPARENT)
                    contentDescription = "Sửa mục ${category.name}"
                    setOnClickListener{
                        val intent = Intent(this@HomePageAct, EditCategoryAct::class.java)
                        startActivity(intent)
                    }
                })
            }

            row.addView(TextView(this).apply {
                text = if (children.isEmpty()) {
                    formatAmount(categoryTotal(category))
                } else {
                    "${formatAmount(categoryTotal(category))}"
                }
                textSize = 18f
                gravity = Gravity.END
                setTextColor(categoryColor)
            })

            if (children.isNotEmpty()) {
                row.isClickable = true
                row.isFocusable = true
                row.setOnClickListener {
                    if (category.id in expandedCategoryIds) {
                        expandedCategoryIds.remove(category.id)
                        // Đóng hẳn mục cha: không giữ lại danh sách giao dịch
                        // trực tiếp của nó sau khi cây mục con đã được thu gọn.
                        selectedCategoryId = null
                    } else {
                        expandedCategoryIds.add(category.id)
                        selectedCategoryId = category.id
                    }
                    showCategoryLists(
                        dbHelper.getCategoriesByType(DBHelper.TYPE_INCOME),
                        dbHelper.getCategoriesByType(DBHelper.TYPE_EXPENSE),
                        amountsByCategory, usageCountsByCategory, transactions
                    )
                }
            } else {
                row.isClickable = true
                row.isFocusable = true
                row.setOnClickListener {
                    // Bấm lại đúng mục con đang chọn sẽ đóng danh sách giao dịch.
                    selectedCategoryId = if (selectedCategoryId == category.id) null else category.id
                    showCategoryLists(
                        dbHelper.getCategoriesByType(DBHelper.TYPE_INCOME),
                        dbHelper.getCategoriesByType(DBHelper.TYPE_EXPENSE),
                        amountsByCategory, usageCountsByCategory, transactions
                    )
                }
            }

            listContainer.addView(row)
            if (selectedCategoryId == category.id) {
                // Chỉ hiện khoản được gán trực tiếp cho mục đang chọn. Các khoản
                // của Ăn sáng/Ăn trưa/Ăn tối được thể hiện ở chính mục con, tránh
                // người dùng nhìn thấy chúng hai lần trong phần Ăn uống.
                val categoryTransactions = transactions.filter { it.categoryId == category.id }
                if (categoryTransactions.isEmpty()) {
                    listContainer.addView(TextView(this).apply {
                        text = "Chưa có giao dịch"
                        textSize = 15f
                        setTextColor(Color.GRAY)
                        setPadding(dp(20 + (level + 1) * 28), dp(4), dp(16), dp(4))
                    })
                } else {
                    categoryTransactions.forEach { transaction ->
                        addTransactionRow(transaction, level + 1, categoryColor)
                    }
                }
            }
            if (category.id in expandedCategoryIds) {
                alphabetically(children).forEach { child ->
                    addCategory(child, level + 1)
                }
            }
        }

        val parentCategories = alphabetically(childrenByParent[null].orEmpty())
        val sortedParents = if (useFrequencyOrder) {
            parentCategories.sortedWith(Comparator { first, second ->
                val frequencyComparison = categoryUsage(second).compareTo(categoryUsage(first))
                if (frequencyComparison != 0) frequencyComparison
                else collator.compare(first.name, second.name)
            })
        } else {
            parentCategories
        }
        sortedParents.forEach { category -> addCategory(category, 0) }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun formatAmount(amount: Double): String {
        val absoluteAmount = kotlin.math.abs(amount)
        val (value, suffix) = when {
            absoluteAmount >= 1_000_000_000 -> amount / 1_000_000_000 to "B"
            absoluteAmount >= 1_000_000 -> amount / 1_000_000 to "M"
            absoluteAmount >= 1_000 -> amount / 1_000 to "k"
            else -> return "${NumberFormat.getIntegerInstance(Locale("vi", "VN")).format(amount)} đ"
        }

        return DecimalFormat(
            "0",
            DecimalFormatSymbols(Locale("vi", "VN"))
        ).apply {
            roundingMode = RoundingMode.HALF_UP
        }.format(value) + suffix
    }

    override fun onDestroy() {
        dbHelper.close()
        super.onDestroy()
    }

    companion object {
        private const val PREFERENCES_NAME = "home_page_preferences"
        private const val KEY_HAS_OPENED_CATEGORY_LIST = "has_opened_category_list"
        private const val KEY_DISPLAYED_DATE = "displayed_date"
    }
}
