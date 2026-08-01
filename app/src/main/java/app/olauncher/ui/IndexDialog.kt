package app.olauncher.ui

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.Window
import android.widget.LinearLayout
import android.widget.TextView
import app.olauncher.R
import app.olauncher.data.IndexDestination
import app.olauncher.helper.getColorFromAttr

object IndexDialog {

    /** Inset from each screen edge so the modal keeps ~20% total padding. */
    private const val SCREEN_INSET_FRACTION = 0.1f

    fun show(
        context: Context,
        tagsWithTasks: List<String>,
        onSelect: (IndexDestination) -> Unit,
    ) {
        val dialog = Dialog(context)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_index, null)
        dialog.setContentView(view)
        view.setBackgroundColor(context.getColorFromAttr(R.attr.dialogShadeColor))

        val metrics = context.resources.displayMetrics
        val width = (metrics.widthPixels * (1f - SCREEN_INSET_FRACTION * 2f)).toInt()
        val height = (metrics.heightPixels * (1f - SCREEN_INSET_FRACTION * 2f)).toInt()
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout(width, height)
            setGravity(Gravity.CENTER)
        }

        val list = view.findViewById<LinearLayout>(R.id.indexList)
        var rowNumber = 1

        fun addRow(label: String, destination: IndexDestination) {
            val row = TextView(context).apply {
                setTextAppearance(context, R.style.TextMedium)
                typeface = android.graphics.Typeface.create("serif", android.graphics.Typeface.NORMAL)
                text = label
                setPadding(0, dp(context, 12), 0, dp(context, 12))
                setOnClickListener {
                    onSelect(destination)
                    dialog.dismiss()
                }
            }
            list.addView(row)
        }

        addRow(
            context.getString(R.string.index_overdue_row),
            IndexDestination.Overdue,
        )
        rowNumber = 2
        addRow(
            context.getString(R.string.index_unscheduled_row),
            IndexDestination.Unscheduled,
        )
        rowNumber = 3
        tagsWithTasks.forEach { tag ->
            addRow(
                context.getString(R.string.index_tag_row, rowNumber, tag),
                IndexDestination.Tag(tag),
            )
            rowNumber++
        }

        dialog.show()
        // Re-assert size after show — some themes reset wrap_content on first layout.
        dialog.window?.setLayout(width, height)
        view.layoutParams = ViewGroup.LayoutParams(width, height)
    }

    private fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()
}
