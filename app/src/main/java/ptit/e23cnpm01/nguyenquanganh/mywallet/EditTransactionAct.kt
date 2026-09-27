package ptit.e23cnpm01.nguyenquanganh.mywallet

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.Switch
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class EditTransactionAct : AppCompatActivity() {
    private val selectedDateTime = Calendar.getInstance()
    private lateinit var dbHelper: DBHelper
    private lateinit var categorySpinner: Spinner
    private lateinit var typeSwitch: Switch
    private lateinit var amountInput: EditText
    private lateinit var noteInput: EditText
    private lateinit var dateInput: EditText
    private var categories: List<WalletCategory> = emptyList()
    private var transactionId = -1L
    private var originalTransaction: WalletTransaction? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.edit_transaction)

        transactionId = intent.getLongExtra(EXTRA_TRANSACTION_ID, -1L)
        dbHelper = DBHelper(this)
        categorySpinner = findViewById(R.id.spinnerEditCategory)
        typeSwitch = findViewById(R.id.switchEditTransactionType)
        amountInput = findViewById(R.id.edtEditAmount)
        noteInput = findViewById(R.id.edtEditNote)
        dateInput = findViewById(R.id.edtEditTransactionDate)

        val transaction = dbHelper.getTransactionById(transactionId)
        if (transaction == null) {
            showMessage(R.string.msg_transaction_not_found)
            finish()
            return
        }
        originalTransaction = transaction
        fillTransaction(transaction)

        typeSwitch.setOnCheckedChangeListener { _, _ ->
            loadCategories()
        }
        dateInput.setOnClickListener { showDatePicker() }
        findViewById<Button>(R.id.btnUpdateTransaction).setOnClickListener { updateTransaction() }
        findViewById<Button>(R.id.btnDeleteTransaction).setOnClickListener { confirmDelete() }
        findViewById<Button>(R.id.btnCancelEditTransaction).setOnClickListener { finish() }
    }

    private fun fillTransaction(transaction: WalletTransaction) {
        typeSwitch.isChecked = transaction.typeId == DBHelper.TYPE_INCOME
        loadCategories(transaction.categoryId)
        amountInput.setText(if (transaction.amount % 1.0 == 0.0) transaction.amount.toLong().toString() else transaction.amount.toString())
        noteInput.setText(transaction.note.orEmpty())
        setSelectedDate(transaction.date)
    }

    private fun loadCategories(selectedCategoryId: Long? = null) {
        val typeId = if (typeSwitch.isChecked) DBHelper.TYPE_INCOME else DBHelper.TYPE_EXPENSE
        categories = dbHelper.getCategoriesByType(typeId)
        categorySpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            listOf(getString(R.string.spinner_choose)) + categories.map { it.name }
        ).apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        val targetId = selectedCategoryId ?: originalTransaction?.takeIf { it.typeId == typeId }?.categoryId
        val selectedIndex = categories.indexOfFirst { it.id == targetId }
        categorySpinner.setSelection(if (selectedIndex >= 0) selectedIndex + 1 else 0)
    }

    private fun showDatePicker() {
        DatePickerDialog(this, { _, year, month, day ->
            selectedDateTime.set(year, month, day)
            dateInput.setText(SimpleDateFormat("dd/MM/yyyy", Locale("vi", "VN")).format(selectedDateTime.time))
        }, selectedDateTime.get(Calendar.YEAR), selectedDateTime.get(Calendar.MONTH), selectedDateTime.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun setSelectedDate(databaseDate: String) {
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }
            .parse(databaseDate) ?: return
        selectedDateTime.time = date
        dateInput.setText(SimpleDateFormat("dd/MM/yyyy", Locale("vi", "VN")).format(date))
    }

    private fun updateTransaction() {
        val categoryPosition = categorySpinner.selectedItemPosition
        val amount = amountInput.text.toString().trim().replace(',', '.').toDoubleOrNull()
        val databaseDate = parseDatabaseDate()
        when {
            categoryPosition <= 0 -> showMessage(R.string.msg_choose_category)
            amount == null || amount <= 0 -> showMessage(R.string.msg_amount_positive)
            databaseDate == null -> showMessage(R.string.msg_invalid_date)
            else -> {
                val category = categories[categoryPosition - 1]
                dbHelper.updateTransaction(
                    transactionId, amount, noteInput.text.toString().trim().ifBlank { null }, databaseDate, category.id
                )
                returnToHome(databaseDate, category.id)
            }
        }
    }

    private fun confirmDelete() {
        AlertDialog.Builder(this)
            .setMessage(R.string.dialog_delete_transaction)
            .setPositiveButton(R.string.action_delete) { _, _ ->
                val date = originalTransaction?.date ?: return@setPositiveButton
                dbHelper.deleteTransaction(transactionId)
                returnToHome(date, null)
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun parseDatabaseDate(): String? = try {
        SimpleDateFormat("dd/MM/yyyy", Locale("vi", "VN")).apply { isLenient = false }
            .parse(dateInput.text.toString().trim())?.let {
                SimpleDateFormat("yyyy-MM-dd", Locale.US).format(it)
            }
    } catch (_: Exception) { null }

    private fun returnToHome(date: String, categoryId: Long?) {
        setResult(RESULT_OK, Intent().putExtra(AddTransactionAct.EXTRA_TRANSACTION_DATE, date).apply {
            if (categoryId != null) putExtra(AddTransactionAct.EXTRA_CATEGORY_ID, categoryId)
        })
        finish()
    }

    private fun showMessage(@StringRes message: Int) =
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    override fun onDestroy() {
        dbHelper.close()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_TRANSACTION_ID = "transaction_id"
    }
}
