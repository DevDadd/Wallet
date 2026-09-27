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
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

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

        logoSpinner.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_item,
            listOf("Mặc định", "food.png", "shopping.png", "salary.png", "bonus.png")
        ).apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

        typeSwitch.setOnCheckedChangeListener { _, _ -> loadParents() }
        findViewById<Button>(R.id.btnSaveTransaction).setOnClickListener { saveCategory() }
        findViewById<Button>(R.id.btnCancelTransaction).setOnClickListener { finish() }
        loadParents()
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.addCategory)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    private fun loadParents() {
        val typeId = if (typeSwitch.isChecked) DBHelper.TYPE_INCOME else DBHelper.TYPE_EXPENSE
        availableParents = dbHelper.getCategoriesByType(typeId)
        parentSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            listOf("-- Không chọn (tạo mục cha) --") + availableParents.map { it.name }
        ).apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        findViewById<TextView>(R.id.tvAddTransactionTitle).text =
            if (typeSwitch.isChecked) "Thêm mục thu" else "Thêm mục chi"
        findViewById<TextView>(R.id.tvCategoryName).text =
            if (typeSwitch.isChecked) "Tên mục thu:" else "Tên mục chi:"
    }

    private fun saveCategory() {
        val name = nameInput.text.toString().trim()
        if (name.isBlank()) {
            Toast.makeText(this, "Hãy nhập tên mục", Toast.LENGTH_SHORT).show()
            return
        }
        val typeId = if (typeSwitch.isChecked) DBHelper.TYPE_INCOME else DBHelper.TYPE_EXPENSE
        val position = parentSpinner.selectedItemPosition
        val parentId = if (position > 0) availableParents[position - 1].id else null
        val iconName = if (logoSpinner.selectedItemPosition == 0) "default.png"
            else logoSpinner.selectedItem.toString()
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
        val intent = Intent(this, HomePageAct::class.java)
        startActivity(intent)
    }

    override fun onDestroy() {
        dbHelper.close()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_CATEGORY_ID = "category_id"
        const val EXTRA_TYPE_ID = "type_id"
    }
}
