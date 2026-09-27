package ptit.e23cnpm01.nguyenquanganh.mywallet

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.Switch
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
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

    // Không đọc typeSwitch ở đây: khi khởi tạo thuộc tính, view chưa được findViewById.
    private var typeId = DBHelper.TYPE_EXPENSE
    private var listParentCategory: List<WalletCategory> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.edit_category)
        categoryId = intent.getLongExtra(EXTRA_CATEGORY_ID, -1L)
        catspinner = findViewById(R.id.spinnerCategory)
        catName = findViewById(R.id.edtCategoryName)
        catNote = findViewById(R.id.edtNote)
        catLogoSpinner = findViewById(R.id.spinnerCategoryLogo)
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
    }

    fun loadCategory(selectedCategoryID: Long? = null){
        typeId = if (typeSwitch.isChecked) DBHelper.TYPE_INCOME else DBHelper.TYPE_EXPENSE
        val editingCategory = originalCategory
        if (editingCategory != null && editingCategory.parentId == null) {
            listParentCategory = emptyList()
            catspinner.adapter = ArrayAdapter(
                this,
                android.R.layout.simple_spinner_item,
                listOf(editingCategory.name)
            ).apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
            catspinner.isEnabled = false
            return
        }
        catspinner.isEnabled = true

        listParentCategory = dbHelper.getParentCategories(typeId).filter { it.id != categoryId }
        catspinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            listOf(getString(R.string.spinner_choose)) + listParentCategory.map { it.name }
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

        val logoIndex = resources.getStringArray(R.array.category_logos).indexOf(category.iconName)
        catLogoSpinner.setSelection(if (logoIndex > 0) logoIndex else 0)

        val hasChildren = dbHelper.getCategoriesByType(category.typeId).any { it.parentId == category.id }
        typeSwitch.isEnabled = !hasChildren
    }

    private fun saveCategory() {
        val name = catName.text.toString().trim()
        
        if (name.isBlank()) {
            Toast.makeText(this, R.string.msg_enter_category_name, Toast.LENGTH_SHORT).show()
            return
        }
        val parentId = if (originalCategory?.parentId == null) {
            null
        } else {
            val position = catspinner.selectedItemPosition
            if (position > 0) listParentCategory[position - 1].id else null
        }

        val isNameExisted = dbHelper.getCategoriesByType(typeId).any {
            it.id != categoryId && it.parentId == parentId && it.name.equals(name, ignoreCase = true)
        }
        if (isNameExisted) {
            Toast.makeText(this, R.string.msg_category_name_existed, Toast.LENGTH_SHORT).show()
            return
        }

        val iconName = if (catLogoSpinner.selectedItemPosition == 0) DEFAULT_ICON
            else catLogoSpinner.selectedItem.toString()

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
        private const val DEFAULT_ICON = "default.png"
    }
}
