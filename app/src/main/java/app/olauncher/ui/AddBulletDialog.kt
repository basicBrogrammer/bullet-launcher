package app.olauncher.ui

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.Window
import android.widget.EditText
import android.widget.TextView
import app.olauncher.R
import app.olauncher.data.BulletType
import app.olauncher.data.JournalEntry
import app.olauncher.helper.getColorFromAttr

object AddBulletDialog {

    fun show(
        context: Context,
        existing: JournalEntry? = null,
        onSave: (text: String, type: BulletType, priority: Boolean) -> Unit,
        onDelete: (() -> Unit)? = null,
    ) {
        val dialog = Dialog(context)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_add_bullet, null)
        dialog.setContentView(view)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setBackgroundDrawableResource(R.drawable.rounded_rectangle_dark)

        val title = view.findViewById<TextView>(R.id.dialogTitle)
        val input = view.findViewById<EditText>(R.id.entryInput)
        val typeTask = view.findViewById<TextView>(R.id.typeTask)
        val typeEvent = view.findViewById<TextView>(R.id.typeEvent)
        val typeNote = view.findViewById<TextView>(R.id.typeNote)
        val calendarSyncHint = view.findViewById<TextView>(R.id.calendarSyncHint)
        val priorityToggle = view.findViewById<TextView>(R.id.priorityToggle)
        val delete = view.findViewById<TextView>(R.id.deleteButton)
        val cancel = view.findViewById<TextView>(R.id.cancelButton)
        val save = view.findViewById<TextView>(R.id.saveButton)

        var selectedType = existing?.type ?: BulletType.TASK
        var priority = existing?.priority ?: false
        val selectedAlpha = 1f
        val dimAlpha = 0.45f
        val editing = existing != null

        title.setText(if (editing) R.string.edit_entry else R.string.add_entry)
        if (existing != null) {
            input.setText(existing.text)
            input.setSelection(existing.text.length)
        }
        if (editing && onDelete != null) {
            delete.visibility = View.VISIBLE
            delete.setOnClickListener {
                onDelete()
                dialog.dismiss()
            }
        } else {
            delete.visibility = View.GONE
        }

        fun refreshType() {
            typeTask.alpha = if (selectedType == BulletType.TASK) selectedAlpha else dimAlpha
            typeEvent.alpha = if (selectedType == BulletType.EVENT) selectedAlpha else dimAlpha
            typeNote.alpha = if (selectedType == BulletType.NOTE) selectedAlpha else dimAlpha
            calendarSyncHint.visibility =
                if (selectedType == BulletType.EVENT) View.VISIBLE else View.GONE
        }

        fun refreshPriority() {
            priorityToggle.setText(if (priority) R.string.priority_on else R.string.priority_off)
            priorityToggle.alpha = if (priority) selectedAlpha else dimAlpha
        }

        refreshType()
        refreshPriority()

        typeTask.setOnClickListener {
            selectedType = BulletType.TASK
            refreshType()
        }
        typeEvent.setOnClickListener {
            selectedType = BulletType.EVENT
            refreshType()
        }
        typeNote.setOnClickListener {
            selectedType = BulletType.NOTE
            refreshType()
        }
        priorityToggle.setOnClickListener {
            priority = !priority
            refreshPriority()
        }
        cancel.setOnClickListener { dialog.dismiss() }
        save.setOnClickListener {
            val text = input.text?.toString().orEmpty().trim()
            if (text.isEmpty()) {
                input.error = context.getString(R.string.entry_hint)
                return@setOnClickListener
            }
            onSave(text, selectedType, priority)
            dialog.dismiss()
        }

        // Tint dialog to match theme via a semi-opaque panel
        view.setBackgroundColor(context.getColorFromAttr(R.attr.dialogShadeColor))
        dialog.show()
        input.requestFocus()
    }
}
