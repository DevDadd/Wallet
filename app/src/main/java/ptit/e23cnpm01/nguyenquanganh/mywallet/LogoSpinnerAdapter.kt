package ptit.e23cnpm01.nguyenquanganh.mywallet

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

class LogoSpinnerAdapter(
    private val context: Context,
    private val logos: List<String> = listOf("logo1.png", "logo2.png")
) : BaseAdapter() {
    override fun getCount(): Int = logos.size
    override fun getItem(position: Int): String = logos[position]
    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View =
        createRow(position, convertView)

    override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View =
        createRow(position, convertView)

    private fun createRow(position: Int, convertView: View?): View {
        val row = (convertView as? LinearLayout) ?: LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val padding = (8 * context.resources.displayMetrics.density).toInt()
            setPadding(padding, padding / 2, padding, padding / 2)
            addView(ImageView(context), LinearLayout.LayoutParams(
                (36 * context.resources.displayMetrics.density).toInt(),
                (36 * context.resources.displayMetrics.density).toInt()
            ))
            addView(TextView(context).apply {
                textSize = 16f
                setPadding(padding, 0, 0, 0)
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        val image = row.getChildAt(0) as ImageView
        val label = row.getChildAt(1) as TextView
        val logoName = getItem(position)
        val bitmap = context.assets.open(logoName).use { BitmapFactory.decodeStream(it) }
        image.setImageDrawable(BitmapDrawable(context.resources, bitmap))
        image.contentDescription = logoName
        label.text = logoName
        return row
    }
}
