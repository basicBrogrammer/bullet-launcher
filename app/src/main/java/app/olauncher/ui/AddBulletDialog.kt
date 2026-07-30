package app.olauncher.ui

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
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
        val dialog = BottomSheetDialog(context)
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_add_bullet, null)
        dialog.setContentView(view)

        val title = view.findViewById<TextView>(R.id.dialogTitle)
        val input = view.findViewById<EditText>(R.id.entryInput)
        val typeTask = view.findViewById<TextView>(R.id.typeTask)
        val typeEvent = view.findViewById<TextView>(R.id.typeEvent)
        val typeNote = view.findViewById<TextView>(R.id.typeNote)
        val tagsSection = view.findViewById<View>(R.id.tagsSection)
        val tagChipGroup = view.findViewById<ChipGroup>(R.id.tagChipGroup)
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

        fun rebuildTagChips() {
            tagChipGroup.removeAllViews()
            if (knownTags.isEmpty()) {
                val empty = TextView(context).apply {
                    setText(R.string.tags_empty_hint)
                    setTextAppearance(context, R.style.TextSmallLight)
                    setPadding(0, 8, 0, 8)
                }
                tagChipGroup.addView(empty)
                return
            }
            knownTags.forEach { (key, label) ->
                val chip = Chip(context).apply {
                    text = label
                    isCheckable = true
                    isChecked = key in selectedTags
                    isClickable = true
                    setOnCheckedChangeListener { _, checked ->
                        if (checked) selectedTags.add(key) else selectedTags.remove(key)
                    }
                }
                tagChipGroup.addView(chip)
            }
        }

        fun addNewTagFromInput() {
            val raw = newTagInput.text?.toString().orEmpty()
            val key = JournalStore.normalizeTag(raw) ?: return
            rememberTag(raw)
            selectedTags.add(key)
            newTagInput.text = null
            rebuildTagChips()
        }

        rebuildTagChips()
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
        // Tint star icons with primary text color.
        val textColor = context.getColorFromAttr(R.attr.primaryColor)
        priorityIcon.setColorFilter(textColor)

        dialog.setOnShowListener {
            val sheet = dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
                ?: return@setOnShowListener
            val height = (context.resources.displayMetrics.heightPixels * 0.9f).toInt()
            sheet.layoutParams = sheet.layoutParams.apply { this.height = height }
            sheet.requestLayout()
            val behavior = BottomSheetBehavior.from(sheet)
            behavior.peekHeight = height
            behavior.skipCollapsed = true
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
            // Fill the 90% sheet so the tag section can use leftover space.
            view.layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
        }
        dialog.show()
        input.requestFocus()
    }
}
