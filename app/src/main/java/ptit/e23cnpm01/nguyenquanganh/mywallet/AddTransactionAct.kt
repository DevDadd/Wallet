package ptit.e23cnpm01.nguyenquanganh.mywallet

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class AddTransactionAct : AppCompatActivity() {
    private val selectedDateTime = Calendar.getInstance()
    private lateinit var dbHelper: DBHelper
    private lateinit var categorySpinner: Spinner
    private lateinit var transactionTypeSwitch: Switch
    private lateinit var moneyAmount: EditText
    private lateinit var noteInput: EditText
    private lateinit var dateInput: EditText
    private lateinit var btnAdd: Button
    private lateinit var btnCancel: Button
    private var displayedCategories: List<WalletCategory> = emptyList()
    lateinit var btnAddCategory: TextView

    private val addCategoryLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) finish()
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.add_transaction)

        dbHelper = DBHelper(this)
        categorySpinner = findViewById(R.id.spinnerCategory)
        transactionTypeSwitch = findViewById(R.id.switchTransactionType)
        moneyAmount = findViewById(R.id.edtAmount)
        noteInput = findViewById(R.id.edtNote)
        dateInput = findViewById(R.id.edtTransactionDate)
        btnAdd = findViewById(R.id.btnSaveTransaction)
        btnCancel = findViewById(R.id.btnCancelTransaction)
        intent.getStringExtra(EXTRA_INITIAL_DATE)?.let(::setSelectedDate)
            ?: setSelectedDate(SimpleDateFormat("yyyy-MM-dd", Locale.US).format(selectedDateTime.time))

        dateInput.setOnClickListener {
            showDatePicker()
        }

        btnAdd.setOnClickListener { saveTransaction() }
        btnCancel.setOnClickListener { finish() }
        btnAddCategory = findViewById(R.id.btnAddCategory)
        btnAddCategory.setOnClickListener {
            addCategoryLauncher.launch(Intent(this, AddCategoryAct::class.java))
        }

        transactionTypeSwitch.setOnCheckedChangeListener { _, _ -> loadCategories() }
        loadCategories()
    }

    private fun showDatePicker() {
        DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                selectedDateTime.set(Calendar.YEAR, year)
                selectedDateTime.set(Calendar.MONTH, month)
                selectedDateTime.set(Calendar.DAY_OF_MONTH, dayOfMonth)

                dateInput.setText(
                    SimpleDateFormat("dd/MM/yyyy", Locale("vi", "VN"))
                        .format(selectedDateTime.time)
                )
            },
            selectedDateTime.get(Calendar.YEAR),
            selectedDateTime.get(Calendar.MONTH),
            selectedDateTime.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun setSelectedDate(databaseDate: String) {
        val parsedDate = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            isLenient = false
        }.parse(databaseDate) ?: return
        selectedDateTime.time = parsedDate
        dateInput.setText(
            SimpleDateFormat("dd/MM/yyyy", Locale("vi", "VN")).format(parsedDate)
        )
    }

    private fun loadCategories() {
        val typeId = if (transactionTypeSwitch.isChecked) {
            DBHelper.TYPE_INCOME
        } else {
            DBHelper.TYPE_EXPENSE
        }
        displayedCategories = dbHelper.getCategoriesByType(typeId)
        val categoryNames = listOf(getString(R.string.spinner_choose)) + displayedCategories.map { it.name }

        categorySpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            categoryNames
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
    }

    private fun saveTransaction() {
        val categoryPosition = categorySpinner.selectedItemPosition
        if (categoryPosition <= 0) {
            showMessage(R.string.msg_choose_category)
            return
        }

        val amount = moneyAmount.text.toString().trim().replace(',', '.').toDoubleOrNull()
        if (amount == null || amount <= 0) {
            showMessage(R.string.msg_amount_positive)
            return
        }

        val databaseDate = try {
            SimpleDateFormat("dd/MM/yyyy", Locale("vi", "VN")).apply {
                isLenient = false
            }.parse(dateInput.text.toString().trim())?.let {
                SimpleDateFormat("yyyy-MM-dd", Locale.US).format(it)
            }
        } catch (_: Exception) {
            null
        }
        if (databaseDate == null) {
            showMessage(R.string.msg_invalid_date)
            return
        }

        val category = displayedCategories[categoryPosition - 1]
        dbHelper.addTransaction(
            amount = amount,
            note = noteInput.text.toString().trim().ifBlank { null },
            date = databaseDate,
            idCategory = category.id
        )

        setResult(
            RESULT_OK,
            Intent()
                .putExtra(EXTRA_TRANSACTION_DATE, databaseDate)
                .putExtra(EXTRA_CATEGORY_ID, category.id)
        )
        finish()
    }

    private fun showMessage(@StringRes message: Int) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }


    override fun onDestroy() {
        dbHelper.close()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_TRANSACTION_DATE = "transaction_date"
        const val EXTRA_CATEGORY_ID = "category_id"
        const val EXTRA_INITIAL_DATE = "initial_date"
    }
}
