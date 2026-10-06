package ptit.e23cnpm01.nguyenquanganh.mywallet

import android.content.Intent
import android.os.Bundle
import android.view.GestureDetector
import android.view.MotionEvent
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs

class MonthCalendarAct : AppCompatActivity() {

    private lateinit var dbHelper: DBHelper
    private lateinit var titleView: TextView
    private lateinit var monthGrid: LinearLayout
    private lateinit var gestureDetector: GestureDetector

    private val databaseDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private var selectedDate: String = ""

    private val displayedMonth: Calendar = Calendar.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        setContentView(R.layout.month_calender)

        dbHelper = DBHelper(this)
        titleView = findViewById(R.id.tvMonthTitle)
        monthGrid = findViewById(R.id.monthGrid)

        selectedDate = intent.getStringExtra(EXTRA_INITIAL_DATE) ?: databaseDateFormat.format(Date())
        displayedMonth.time = runCatching { databaseDateFormat.parse(selectedDate) }.getOrNull() ?: Date()
        displayedMonth.set(Calendar.DAY_OF_MONTH, 1)

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }

        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                val start = e1 ?: return false
                val dx = e2.x - start.x
                if (abs(dx) < SWIPE_MIN_DISTANCE || abs(dx) < abs(e2.y - start.y)) return false
                changeMonth(if (dx < 0) 1 else -1)
                return true
            }
        })

        renderMonth()
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (gestureDetector.onTouchEvent(event)) {
            super.dispatchTouchEvent(MotionEvent.obtain(event).apply { action = MotionEvent.ACTION_CANCEL })
            return true
        }
        return super.dispatchTouchEvent(event)
    }

    private fun changeMonth(offset: Int) {
        displayedMonth.add(Calendar.MONTH, offset)
        renderMonth()
        monthGrid.translationX = offset * monthGrid.width.toFloat()
        monthGrid.animate().translationX(0f).setDuration(200).start()
    }

    private fun renderMonth() {
        val month = displayedMonth.get(Calendar.MONTH)
        titleView.text = getString(R.string.month_title, month + 1, displayedMonth.get(Calendar.YEAR))

        val firstCell = (displayedMonth.clone() as Calendar).apply {
            add(Calendar.DAY_OF_MONTH, -((get(Calendar.DAY_OF_WEEK) + 5) % 7))
        }
        val lastDay = (displayedMonth.clone() as Calendar).apply {
            set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
        }
        val weekCount = (firstCell.daysUntil(lastDay) / 7) + 1
        val lastCell = (firstCell.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, weekCount * 7 - 1) }

        val totals = dbHelper.getDailyTotalsBetween(
            databaseDateFormat.format(firstCell.time),
            databaseDateFormat.format(lastCell.time)
        )

        monthGrid.removeAllViews()
        val day = firstCell
        repeat(weekCount) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 0, 0, resources.getDimensionPixelSize(R.dimen.month_row_spacing))
            }
            repeat(7) {
                val date = databaseDateFormat.format(day.time)
                val inMonth = day.get(Calendar.MONTH) == month
                val total = totals[date]

                row.addView(
                    layoutInflater.inflate(R.layout.item_home_week_day, row, false).apply {
                        if (date == selectedDate) setBackgroundResource(R.drawable.bg_week_day_selected)
                        if (!inMonth) alpha = OUT_OF_MONTH_ALPHA
                        setOnClickListener {
                            setResult(RESULT_OK, Intent().putExtra(EXTRA_SELECTED_DATE, date))
                            finish()
                        }
                        findViewById<TextView>(R.id.tvWeekDayName).text = day.get(Calendar.DAY_OF_MONTH).toString()
                        findViewById<TextView>(R.id.tvWeekDayIncome).text = AmountFormatter.formatOrBlank(total?.income ?: 0.0)
                        findViewById<TextView>(R.id.tvWeekDayExpense).text = AmountFormatter.formatOrBlank(total?.expense ?: 0.0)
                    }
                )
                day.add(Calendar.DAY_OF_MONTH, 1)
            }
            monthGrid.addView(row)
        }
    }

    private fun Calendar.daysUntil(other: Calendar): Int =
        ((other.timeInMillis - timeInMillis + DAY_MILLIS / 2) / DAY_MILLIS).toInt()

    override fun onDestroy() {
        dbHelper.close()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_INITIAL_DATE = "extra_initial_date"
        const val EXTRA_SELECTED_DATE = "extra_selected_date"

        private const val SWIPE_MIN_DISTANCE = 120
        private const val OUT_OF_MONTH_ALPHA = 0.35f
        private const val DAY_MILLIS = 24L * 60 * 60 * 1000
    }
}
