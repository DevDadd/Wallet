package ptit.e23cnpm01.nguyenquanganh.mywallet

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.Switch
import android.widget.Toast
import android.content.Intent
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class EditCategoryAct : AppCompatActivity() {
    private lateinit var catspinner : Spinner
    private lateinit var dbHelper: DBHelper
    private lateinit var catName: EditText
    private lateinit var catNote: EditText
    private lateinit var catLogoSpinner: Spinner
    private lateinit var typeSwitch: Switch
    private lateinit var btnUpdate: Button
    private lateinit var btnDelete: Button
    private lateinit var btnCancel: Button
    private var originalCategory: WalletCategory? = null
    private var categoryId = -1L

    private var typeId = DBHelper.TYPE_EXPENSE
    private var listParentCategory: List<WalletCategory> = emptyList()

    private val addCategoryLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != RESULT_OK) return@registerForActivityResult
        val data = result.data ?: return@registerForActivityResult
        val newCategoryId = data.getLongExtra(AddCategoryAct.EXTRA_CATEGORY_ID, -1L)
        val newTypeId = data.getIntExtra(AddCategoryAct.EXTRA_TYPE_ID, typeId)
        if (newCategoryId < 0) return@registerForActivityResult
        typeSwitch.isChecked = newTypeId == DBHelper.TYPE_INCOME
        loadCategory(newCategoryId)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.edit_category)
        categoryId = intent.getLongExtra(EXTRA_CATEGORY_ID, -1L)
        catspinner = findViewById(R.id.spinnerCategory)
        catName = findViewById(R.id.edtCategoryName)
        catNote = findViewById(R.id.edtNote)
        catLogoSpinner = findViewById(R.id.spinnerCategoryLogo)
        catLogoSpinner.adapter = LogoSpinnerAdapter(this)
        dbHelper = DBHelper(this)
        typeSwitch = findViewById(R.id.switchTransactionType)
        btnUpdate = findViewById(R.id.btnSaveTransaction)
        btnDelete = findViewById(R.id.btnDelete)
        btnCancel = findViewById(R.id.btnCancelTransaction)

        val category = dbHelper.getCategoryById(categoryId)
        if (category == null) {
            Toast.makeText(this, R.string.msg_category_not_found, Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        originalCategory = category
        fillCategory(category)

        typeSwitch.setOnCheckedChangeListener { _, _ -> loadCategory() }

        btnUpdate.setOnClickListener { saveCategory()}
        btnCancel.setOnClickListener { finish() }
        btnDelete.setOnClickListener { delCategory(categoryId)}
        findViewById<TextView>(R.id.btnAddCategory).setOnClickListener {
            addCategoryLauncher.launch(
                Intent(this, AddCategoryAct::class.java)
                    .putExtra(AddCategoryAct.EXTRA_INITIAL_TYPE_ID, typeId)
            )
        }
    }

    fun loadCategory(selectedCategoryID: Long? = null){
        typeId = if (typeSwitch.isChecked) DBHelper.TYPE_INCOME else DBHelper.TYPE_EXPENSE
        catspinner.isEnabled = true

        val childrenByParent = dbHelper.getCategoriesByType(typeId).groupBy { it.parentId }
        val parentOptions = mutableListOf<Pair<WalletCategory, Int>>()
        fun addOption(category: WalletCategory, level: Int) {
            if (category.id == categoryId) return
            parentOptions.add(category to level)
            childrenByParent[category.id].orEmpty().forEach { addOption(it, level + 1) }
        }
        childrenByParent[null].orEmpty().forEach { addOption(it, 0) }

        listParentCategory = parentOptions.map { it.first }
        catspinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            listOf(getString(R.string.spinner_choose)) +
                parentOptions.map { (category, level) -> "    ".repeat(level) + category.name }
        ).apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        val targetId = selectedCategoryID ?: originalCategory?.takeIf { it.typeId == typeId }?.parentId
        val selectedIndex = listParentCategory.indexOfFirst { it.id == targetId }
        catspinner.setSelection(if (selectedIndex >= 0) selectedIndex + 1 else 0)
    }

    fun fillCategory(category: WalletCategory) {
        typeSwitch.isChecked = category.typeId == DBHelper.TYPE_INCOME
        loadCategory(category.parentId)
        catName.setText(category.name)
        catNote.setText(category.note.orEmpty())

        val logoIndex = (0 until catLogoSpinner.adapter.count)
            .firstOrNull { catLogoSpinner.adapter.getItem(it) == category.iconName } ?: 0
        catLogoSpinner.setSelection(logoIndex)

        typeSwitch.isEnabled = true
    }

    private fun saveCategory() {
        val name = catName.text.toString().trim()
        
        if (name.isBlank()) {
            Toast.makeText(this, R.string.msg_enter_category_name, Toast.LENGTH_SHORT).show()
            return
        }
        val position = catspinner.selectedItemPosition
        val parentId = if (position > 0) listParentCategory[position - 1].id else null

        val isNameExisted = dbHelper.getCategoriesByType(typeId).any {
            it.id != categoryId && it.parentId == parentId && it.name.equals(name, ignoreCase = true)
        }
        if (isNameExisted) {
            Toast.makeText(this, R.string.msg_category_name_existed, Toast.LENGTH_SHORT).show()
            return
        }

        val iconName = catLogoSpinner.selectedItem.toString()

        val updatedRows = dbHelper.updateCategory(
            categoryId = categoryId,
            name = name,
            iconName = iconName,
            note = catNote.text.toString().trim().ifBlank { null },
            parentId = parentId,
            typeId = typeId
        )
        if (updatedRows == 0) {
            Toast.makeText(this, R.string.msg_category_not_found, Toast.LENGTH_SHORT).show()
            return
        }

        setResult(RESULT_OK)
        finish()
    }

    fun delCategory(categoryId: Long){
        AlertDialog.Builder(this)
            .setTitle(R.string.dialog_delete_category_title)
            .setMessage(getString(R.string.dialog_delete_category, originalCategory?.name.orEmpty()))
            .setPositiveButton(R.string.action_delete) { _, _ ->
                dbHelper.deleteCategory(categoryId)
                setResult(RESULT_OK)
                finish()
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    override fun onDestroy() {
        dbHelper.close()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_CATEGORY_ID = "category_id"
    }
}
