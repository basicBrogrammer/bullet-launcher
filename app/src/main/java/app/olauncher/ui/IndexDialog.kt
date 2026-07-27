package app.olauncher.ui

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.Window
import android.widget.TextView
import app.olauncher.R
import app.olauncher.data.JournalPages
import app.olauncher.helper.getColorFromAttr

object IndexDialog {

    fun show(context: Context, onSelectPage: (Int) -> Unit) {
        val dialog = Dialog(context)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_index, null)
        dialog.setContentView(view)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        view.setBackgroundColor(context.getColorFromAttr(R.attr.dialogShadeColor))

        view.findViewById<TextView>(R.id.indexDaily).apply {
            setText(R.string.index_daily_row)
            setOnClickListener {
                onSelectPage(JournalPages.DAILY)
                dialog.dismiss()
            }
        }
        view.findViewById<TextView>(R.id.indexMonthly).apply {
            setText(R.string.index_monthly_row)
            setOnClickListener {
                onSelectPage(JournalPages.MONTHLY)
                dialog.dismiss()
            }
        }
        view.findViewById<TextView>(R.id.indexFuture).apply {
            setText(R.string.index_future_row)
            setOnClickListener {
                onSelectPage(JournalPages.FUTURE)
                dialog.dismiss()
            }
        }
        dialog.show()
    }
}
