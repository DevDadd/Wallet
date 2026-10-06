package ptit.e23cnpm01.nguyenquanganh.mywallet

import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs

object AmountFormatter {
    private val locale = Locale("vi", "VN")
    private val integerFormat = NumberFormat.getIntegerInstance(locale)
    private val compactFormat = DecimalFormat("0.##", DecimalFormatSymbols(locale)).apply {
        roundingMode = RoundingMode.HALF_UP
    }

    fun format(amount: Double): String {
        val absoluteAmount = abs(amount)
        val (value, suffix) = when {
            absoluteAmount >= 1_000_000_000 -> amount / 1_000_000_000 to "B"
            absoluteAmount >= 1_000_000 -> amount / 1_000_000 to "M"
            absoluteAmount >= 1_000 -> amount / 1_000 to "k"
            else -> return "${integerFormat.format(amount)} đ"
        }
        return compactFormat.format(value) + suffix
    }
    fun formatOrBlank(amount: Double): String = if (amount == 0.0) "" else format(amount)
}
