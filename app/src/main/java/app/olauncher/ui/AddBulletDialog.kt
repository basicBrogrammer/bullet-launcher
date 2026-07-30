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
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
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

        val sheetScroll = view.findViewById<android.widget.ScrollView>(R.id.sheetScroll)
        val screenHeight = context.resources.displayMetrics.heightPixels
        // Soft cap when the keyboard is closed; shrinks further when IME is open.
        var imeBottom = 0
        fun maxSheetHeight(): Int {
            val aboveIme = (screenHeight - imeBottom).coerceAtLeast(dp(context, 200))
            return minOf((screenHeight * 0.9f).toInt(), aboveIme)
        }

        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setGravity(Gravity.BOTTOM)
            // Wallpaper / translucent launcher windows ignore ADJUST_RESIZE, so the
            // keyboard would cover this bottom sheet. We lift it via IME insets instead.
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            attributes = attributes.apply { dimAmount = 0.55f }
            WindowCompat.setDecorFitsSystemWindows(this, false)
        }

        fun clampSheetHeight() {
            val window = dialog.window ?: return
            val widthSpec = View.MeasureSpec.makeMeasureSpec(
                context.resources.displayMetrics.widthPixels,
                View.MeasureSpec.EXACTLY,
            )
            val maxHeight = maxSheetHeight()
            // Let ScrollView wrap its children for an honest content height.
            sheetScroll.layoutParams = sheetScroll.layoutParams.apply {
                height = ViewGroup.LayoutParams.WRAP_CONTENT
            }
            view.layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
            view.measure(
                widthSpec,
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            )
            val contentHeight = view.measuredHeight
            val windowHeight = if (contentHeight <= maxHeight) {
                sheetScroll.layoutParams = sheetScroll.layoutParams.apply {
                    height = ViewGroup.LayoutParams.WRAP_CONTENT
                }
                view.layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
                contentHeight
            } else {
                // Too tall for the space above the keyboard: scroll the body.
                val footerHeight = contentHeight - sheetScroll.measuredHeight
                val scrollHeight = (maxHeight - footerHeight).coerceAtLeast(dp(context, 120))
                sheetScroll.layoutParams = sheetScroll.layoutParams.apply {
                    height = scrollHeight
                }
                view.layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    maxHeight,
                )
                maxHeight
            }
            // Gravity.BOTTOM: y is the offset from the bottom edge — sit above the IME.
            window.attributes = window.attributes.apply {
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                width = ViewGroup.LayoutParams.MATCH_PARENT
                height = windowHeight
                y = imeBottom
            }
            view.requestLayout()
        }

        fun onImeInsets(insets: WindowInsetsCompat): WindowInsetsCompat {
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            if (ime != imeBottom) {
                imeBottom = ime
                clampSheetHeight()
            }
            return insets
        }

        ViewCompat.setOnApplyWindowInsetsListener(view) { _, insets -> onImeInsets(insets) }

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
            clampSheetHeight()
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
            if (dialog.isShowing) clampSheetHeight()
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

        // Opaque paper/grey surface (same as drawer) so wallpaper doesn't show through.
        view.setBackgroundColor(context.getColorFromAttr(R.attr.drawerBackgroundColor))
        priorityIcon.setColorFilter(context.getColorFromAttr(R.attr.primaryColor))

        dialog.setOnShowListener {
            val decor = dialog.window?.decorView
            if (decor != null) {
                // Decor receives IME insets more reliably than the content view alone.
                ViewCompat.setOnApplyWindowInsetsListener(decor) { _, insets -> onImeInsets(insets) }
                ViewCompat.requestApplyInsets(decor)
            }
            clampSheetHeight()
            ViewCompat.requestApplyInsets(view)
        }
        dialog.show()
        clampSheetHeight()
        // Focus after show so typing can begin; IME insets lift the sheet above the keyboard.
        input.post {
            input.requestFocus()
            dialog.window?.decorView?.let { ViewCompat.requestApplyInsets(it) }
        }
    }

    private fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()
}
