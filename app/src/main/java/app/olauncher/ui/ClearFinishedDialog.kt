package app.olauncher.ui

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.TextView
import app.olauncher.R

/**
 * Confirmation shown after a home-screen shake, before completed tasks are deleted.
 */
object ClearFinishedDialog {

    fun show(context: Context, finishedCount: Int, onConfirm: () -> Unit): Dialog {
        val dialog = Dialog(context)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_clear_finished, null)
        dialog.setContentView(view)

        val message = view.findViewById<TextView>(R.id.clearFinishedMessage)
        val confirm = view.findViewById<TextView>(R.id.confirmButton)
        val cancel = view.findViewById<TextView>(R.id.cancelButton)

        if (finishedCount <= 0) {
            message.setText(R.string.clear_finished_none)
            confirm.visibility = View.GONE
            cancel.setText(R.string.done)
        } else {
            message.text = context.resources.getQuantityString(
                R.plurals.clear_finished_message,
                finishedCount,
                finishedCount,
            )
            confirm.setOnClickListener {
                dialog.dismiss()
                onConfirm()
            }
        }
        cancel.setOnClickListener { dialog.dismiss() }

        val width = (context.resources.displayMetrics.widthPixels * 0.86f).toInt().coerceAtLeast(1)
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setGravity(Gravity.CENTER)
            setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
            attributes = attributes.apply { dimAmount = 0.45f }
        }
        dialog.show()
        dialog.window?.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
        return dialog
    }
}
