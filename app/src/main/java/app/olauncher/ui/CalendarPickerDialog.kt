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
import app.olauncher.helper.CalendarSyncHelper
import app.olauncher.helper.getColorFromAttr

object CalendarPickerDialog {

    fun show(
        context: Context,
        calendars: List<CalendarSyncHelper.DeviceCalendar>,
        preferredId: Long = -1L,
        onPick: (CalendarSyncHelper.DeviceCalendar) -> Unit,
        onCancel: () -> Unit = {},
    ) {
        if (calendars.isEmpty()) {
            onCancel()
            return
        }
        // Single writable calendar — no need to ask.
        if (calendars.size == 1) {
            onPick(calendars.first())
            return
        }

        val dialog = Dialog(context)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_choose_calendar, null)
        dialog.setContentView(view)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        view.setBackgroundColor(context.getColorFromAttr(R.attr.dialogShadeColor))
        dialog.setOnCancelListener { onCancel() }

        val list = view.findViewById<LinearLayout>(R.id.calendarList)
        val sorted = calendars.sortedWith(
            compareByDescending<CalendarSyncHelper.DeviceCalendar> { it.id == preferredId }
                .thenByDescending { it.isPrimary }
                .thenByDescending { it.accountType == "com.google" }
                .thenBy { it.displayName.lowercase() }
        )

        sorted.forEach { calendar ->
            val row = LayoutInflater.from(context)
                .inflate(R.layout.item_calendar_choice, list, false) as TextView
            row.text = calendar.label()
            row.alpha = if (calendar.id == preferredId || (preferredId <= 0 && calendar.isPrimary)) {
                1f
            } else {
                0.75f
            }
            row.setOnClickListener {
                onPick(calendar)
                dialog.dismiss()
            }
            list.addView(row)
        }

        view.findViewById<TextView>(R.id.cancelButton).setOnClickListener {
            dialog.dismiss()
            onCancel()
        }
        dialog.show()
    }
}
