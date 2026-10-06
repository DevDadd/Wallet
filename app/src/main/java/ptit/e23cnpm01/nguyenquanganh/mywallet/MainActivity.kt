package ptit.e23cnpm01.nguyenquanganh.mywallet

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.home_page)

        showTodaySummary()
    }

    private fun showTodaySummary() {
        val databaseDate = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val displayDate = SimpleDateFormat("dd/MM/yyyy", Locale("vi", "VN")).format(Date())

        val dbHelper = DBHelper(this)
        try {
            val totalIncome = dbHelper.getTotalIncomeByDate(databaseDate)
            val totalExpense = dbHelper.getTotalExpenseByDate(databaseDate)

            findViewById<android.widget.TextView>(R.id.title).text = getString(R.string.home_title_date, displayDate)
            findViewById<android.widget.TextView>(R.id.totalIncome).text =
                getString(R.string.main_total_income, AmountFormatter.format(totalIncome))
            findViewById<android.widget.TextView>(R.id.totalOutcome).text =
                getString(R.string.main_total_expense, AmountFormatter.format(totalExpense))
        } finally {
            dbHelper.close()
        }
    }
}
