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
import android.view.inputmethod.EditorInfo
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import app.olauncher.R
import app.olauncher.data.BulletType
import app.olauncher.data.JournalEntry
import app.olauncher.data.JournalStore
import app.olauncher.helper.CalendarSyncHelper
import app.olauncher.helper.getColorFromAttr

object AddBulletDialog {

    data class Result(
        val text: String,
        val type: BulletType,
        val priority: Boolean,
        val tags: List<String>,
        val calendarId: Long?,
    )

    fun show(
        context: Context,
        existing: JournalEntry? = null,
        existingTags: List<String> = emptyList(),
        calendars: List<CalendarSyncHelper.DeviceCalendar> = emptyList(),
        preferredCalendarId: Long = -1L,
        preselectedTags: List<String> = emptyList(),
        onSave: (Result) -> Unit,
        onDelete: (() -> Unit)? = null,
    ) {
        val dialog = Dialog(context)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_add_bullet, null)
        dialog.setContentView(view)

        val sheetHeight = (context.resources.displayMetrics.heightPixels * 0.9f).toInt()
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, sheetHeight)
            setGravity(Gravity.BOTTOM)
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            // Dim the journal behind the sheet.
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            attributes = attributes.apply { dimAmount = 0.45f }
        }

        val title = view.findViewById<TextView>(R.id.dialogTitle)
        val input = view.findViewById<EditText>(R.id.entryInput)
        val typeTask = view.findViewById<TextView>(R.id.typeTask)
        val typeEvent = view.findViewById<TextView>(R.id.typeEvent)
        val typeNote = view.findViewById<TextView>(R.id.typeNote)
        val tagsSection = view.findViewById<View>(R.id.tagsSection)
        val tagList = view.findViewById<LinearLayout>(R.id.tagChipGroup)
        val newTagInput = view.findViewById<EditText>(R.id.newTagInput)
        val addTagButton = view.findViewById<TextView>(R.id.addTagButton)
        val calendarSection = view.findViewById<View>(R.id.calendarSection)
        val calendarSpinner = view.findViewById<Spinner>(R.id.calendarSpinner)
        val priorityRow = view.findViewById<LinearLayout>(R.id.priorityRow)
        val priorityIcon = view.findViewById<ImageView>(R.id.priorityIcon)
        val priorityToggle = view.findViewById<TextView>(R.id.priorityToggle)
        val delete = view.findViewById<TextView>(R.id.deleteButton)
        val cancel = view.findViewById<TextView>(R.id.cancelButton)
        val save = view.findViewById<TextView>(R.id.saveButton)

        var selectedType = existing?.type ?: BulletType.TASK
        var priority = existing?.priority ?: false
        val selectedAlpha = 1f
        val dimAlpha = 0.45f
        val editing = existing != null

        val selectedTags = linkedSetOf<String>()
        val knownTags = linkedMapOf<String, String>() // normalized -> display
        fun rememberTag(raw: String) {
            val key = JournalStore.normalizeTag(raw) ?: return
            if (!knownTags.containsKey(key)) knownTags[key] = raw.trim()
        }
        existingTags.forEach { rememberTag(it) }
        existing?.tags?.forEach {
            rememberTag(it)
            selectedTags.add(JournalStore.normalizeTag(it)!!)
        }
        if (existing == null) {
            preselectedTags.forEach {
                rememberTag(it)
                JournalStore.normalizeTag(it)?.let { key -> selectedTags.add(key) }
            }
        }

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

        fun rebuildTagRows() {
            tagList.removeAllViews()
            if (knownTags.isEmpty()) {
                val empty = TextView(context).apply {
                    setText(R.string.tags_empty_hint)
                    setTextAppearance(context, R.style.TextSmallLight)
                    setPadding(0, 8, 0, 8)
                }
                tagList.addView(empty)
                return
            }
            knownTags.forEach { (key, label) ->
                val row = TextView(context).apply {
                    setTextAppearance(context, R.style.TextMedium)
                    typeface = android.graphics.Typeface.create("serif", android.graphics.Typeface.NORMAL)
                    text = "#$label"
                    setPadding(0, dp(context, 10), 0, dp(context, 10))
                    alpha = if (key in selectedTags) selectedAlpha else dimAlpha
                    setOnClickListener {
                        if (key in selectedTags) selectedTags.remove(key) else selectedTags.add(key)
                        alpha = if (key in selectedTags) selectedAlpha else dimAlpha
                    }
                }
                tagList.addView(row)
            }
        }

        fun addNewTagFromInput() {
            val raw = newTagInput.text?.toString().orEmpty()
            val key = JournalStore.normalizeTag(raw) ?: return
            rememberTag(raw)
            selectedTags.add(key)
            newTagInput.text = null
            rebuildTagRows()
        }

        rebuildTagRows()
        addTagButton.setOnClickListener { addNewTagFromInput() }
        newTagInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                addNewTagFromInput()
                true
            } else {
                false
            }
        }

        val calendarLabels = calendars.map { it.label() }
        if (calendars.isNotEmpty()) {
            calendarSpinner.adapter = ArrayAdapter(
                context,
                android.R.layout.simple_spinner_dropdown_item,
                calendarLabels,
            )
            val preferId = existing?.calendarId?.takeIf { it > 0L } ?: preferredCalendarId
            val preferIndex = calendars.indexOfFirst { it.id == preferId }
                .takeIf { it >= 0 }
                ?: calendars.indexOfFirst { it.isPrimary }.takeIf { it >= 0 }
                ?: 0
            calendarSpinner.setSelection(preferIndex)
        }

        fun refreshType() {
            typeTask.alpha = if (selectedType == BulletType.TASK) selectedAlpha else dimAlpha
            typeEvent.alpha = if (selectedType == BulletType.EVENT) selectedAlpha else dimAlpha
            typeNote.alpha = if (selectedType == BulletType.NOTE) selectedAlpha else dimAlpha
            tagsSection.visibility =
                if (selectedType == BulletType.TASK) View.VISIBLE else View.GONE
            calendarSection.visibility =
                if (selectedType == BulletType.EVENT) View.VISIBLE else View.GONE
        }

        fun refreshPriority() {
            priorityToggle.setText(if (priority) R.string.priority_on else R.string.priority_off)
            priorityToggle.alpha = if (priority) selectedAlpha else dimAlpha
            priorityIcon.setImageResource(
                if (priority) R.drawable.ic_star else R.drawable.ic_star_outline
            )
            priorityIcon.imageAlpha = if (priority) 255 else 140
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
        val togglePriority = View.OnClickListener {
            priority = !priority
            refreshPriority()
        }
        priorityRow.setOnClickListener(togglePriority)
        priorityToggle.setOnClickListener(togglePriority)
        priorityIcon.setOnClickListener(togglePriority)
        cancel.setOnClickListener { dialog.dismiss() }
        save.setOnClickListener {
            val text = input.text?.toString().orEmpty().trim()
            if (text.isEmpty()) {
                input.error = context.getString(R.string.entry_hint)
                return@setOnClickListener
            }
            val tags = if (selectedType == BulletType.TASK) {
                selectedTags.mapNotNull { knownTags[it] }
            } else {
                emptyList()
            }
            val calendarId = if (selectedType == BulletType.EVENT && calendars.isNotEmpty()) {
                calendars.getOrNull(calendarSpinner.selectedItemPosition)?.id
            } else {
                null
            }
            onSave(Result(text, selectedType, priority, tags, calendarId))
            dialog.dismiss()
        }

        view.setBackgroundColor(context.getColorFromAttr(R.attr.dialogShadeColor))
        view.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )
        priorityIcon.setColorFilter(context.getColorFromAttr(R.attr.primaryColor))

        dialog.show()
        input.requestFocus()
    }

    private fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()
}
