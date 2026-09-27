package ptit.e23cnpm01.nguyenquanganh.mywallet

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.home_page)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.homepage)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        showTodaySummary()
    }

    private fun showTodaySummary() {
        // tblTransaction.date trong database có dạng yyyy-MM-dd.
        val databaseDate = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val displayDate = SimpleDateFormat("dd/MM/yyyy", Locale("vi", "VN")).format(Date())
        val currencyFormat = NumberFormat.getNumberInstance(Locale("vi", "VN"))

        val dbHelper = DBHelper(this)
        try {
            val totalIncome = dbHelper.getTotalIncomeByDate(databaseDate)
            val totalExpense = dbHelper.getTotalExpenseByDate(databaseDate)

            findViewById<android.widget.TextView>(R.id.title).text = "Ngày $displayDate"
            findViewById<android.widget.TextView>(R.id.totalIncome).text =
                "Tổng thu\n${currencyFormat.format(totalIncome)} đ"
            findViewById<android.widget.TextView>(R.id.totalOutcome).text =
                "Tổng chi\n${currencyFormat.format(totalExpense)} đ"
        } finally {
            dbHelper.close()
        }
    }
}
