package app.olauncher.ui

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.Window
import android.widget.LinearLayout
import android.widget.TextView
import app.olauncher.R
import app.olauncher.data.IndexDestination
import app.olauncher.helper.getColorFromAttr

object IndexDialog {

    fun show(
        context: Context,
        tagsWithTasks: List<String>,
        onSelect: (IndexDestination) -> Unit,
    ) {
        val dialog = Dialog(context)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_index, null)
        dialog.setContentView(view)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        view.setBackgroundColor(context.getColorFromAttr(R.attr.dialogShadeColor))

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
            context.getString(R.string.index_unscheduled_row),
            IndexDestination.Unscheduled,
        )
        rowNumber = 2
        tagsWithTasks.forEach { tag ->
            addRow(
                context.getString(R.string.index_tag_row, rowNumber, tag),
                IndexDestination.Tag(tag),
            )
            rowNumber++
        }

        dialog.show()
    }

    private fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()
}
