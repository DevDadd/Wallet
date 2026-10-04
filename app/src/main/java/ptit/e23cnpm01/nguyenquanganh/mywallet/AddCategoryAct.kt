package ptit.e23cnpm01.nguyenquanganh.mywallet

import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity

class AddCategoryAct : AppCompatActivity() {
    private lateinit var dbHelper: DBHelper
    private lateinit var typeSwitch: Switch
    private lateinit var parentSpinner: Spinner
    private lateinit var nameInput: EditText
    private lateinit var noteInput: EditText
    private lateinit var logoSpinner: Spinner
    private var availableParents: List<WalletCategory> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.add_category)
        dbHelper = DBHelper(this)
        typeSwitch = findViewById(R.id.switchTransactionType)
        parentSpinner = findViewById(R.id.spinnerCategory)
        nameInput = findViewById(R.id.edtCategoryName)
        noteInput = findViewById(R.id.edtNote)
        logoSpinner = findViewById(R.id.spinnerCategoryLogo)
        logoSpinner.adapter = LogoSpinnerAdapter(this)

        typeSwitch.isChecked = intent.getIntExtra(EXTRA_INITIAL_TYPE_ID, DBHelper.TYPE_EXPENSE) == DBHelper.TYPE_INCOME

        typeSwitch.setOnCheckedChangeListener { _, _ -> loadParents() }
        findViewById<Button>(R.id.btnSaveTransaction).setOnClickListener { saveCategory() }
        findViewById<Button>(R.id.btnCancelTransaction).setOnClickListener { finish() }
        loadParents()
    }

    private fun loadParents() {
        val typeId = if (typeSwitch.isChecked) DBHelper.TYPE_INCOME else DBHelper.TYPE_EXPENSE
        availableParents = dbHelper.getCategoriesByType(typeId)
        parentSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            listOf(getString(R.string.spinner_no_parent)) + availableParents.map { it.name }
        ).apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        findViewById<TextView>(R.id.tvAddTransactionTitle).setText(
            if (typeSwitch.isChecked) R.string.title_add_income_category else R.string.title_add_expense_category
        )
        findViewById<TextView>(R.id.tvCategoryName).setText(
            if (typeSwitch.isChecked) R.string.label_income_category_name else R.string.label_expense_category_name
        )
    }

    private fun saveCategory() {
        val name = nameInput.text.toString().trim()
        if (name.isBlank()) {
            Toast.makeText(this, R.string.msg_enter_category_name, Toast.LENGTH_SHORT).show()
            return
        }
        val typeId = if (typeSwitch.isChecked) DBHelper.TYPE_INCOME else DBHelper.TYPE_EXPENSE
        val position = parentSpinner.selectedItemPosition
        val parentId = if (position > 0) availableParents[position - 1].id else null

        val isNameExisted = dbHelper.getCategoriesByType(typeId).any {
            it.parentId == parentId && it.name.equals(name, ignoreCase = true)
        }
        if (isNameExisted) {
            Toast.makeText(this, R.string.msg_category_name_existed, Toast.LENGTH_SHORT).show()
            return
        }
        val iconName = logoSpinner.selectedItem.toString()
        val categoryId = dbHelper.addCategory(
            name = name,
            iconName = iconName,
            note = noteInput.text.toString().trim().ifBlank { null },
            parentId = parentId,
            typeId = typeId
        )
        setResult(RESULT_OK, Intent()
            .putExtra(EXTRA_CATEGORY_ID, categoryId)
            .putExtra(EXTRA_TYPE_ID, typeId))
        finish()
    }

    override fun onDestroy() {
        dbHelper.close()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_CATEGORY_ID = "category_id"
        const val EXTRA_TYPE_ID = "type_id"
        const val EXTRA_INITIAL_TYPE_ID = "initial_type_id"
    }
}
