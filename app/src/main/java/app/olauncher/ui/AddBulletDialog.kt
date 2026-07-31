package app.olauncher.ui

import android.app.DatePickerDialog
import android.app.Dialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.DialogInterface
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
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import app.olauncher.R
import app.olauncher.data.BulletType
import app.olauncher.data.JournalEntry
import app.olauncher.data.JournalLog
import app.olauncher.data.JournalStore
import app.olauncher.data.Prefs
import app.olauncher.helper.CalendarSyncHelper
import app.olauncher.helper.getColorFromAttr
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

object AddBulletDialog {

    data class Result(
        val text: String,
        val type: BulletType,
        val priority: Boolean,
        val tags: List<String>,
        val calendarId: Long?,
        /** yyyy-MM-dd when scheduled; null = Unscheduled. */
        val scheduledDateKey: String?,
        /** Minutes from midnight when timed; null = all-day / none. */
        val timeMinutes: Int?,
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

        // Full-screen host so we can pad the sheet above nav / IME without moving the
        // window itself (window y-offset caused a ghost sheet + flicker).
        val host = FrameLayout(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
        }
        val sheet = LayoutInflater.from(context).inflate(R.layout.dialog_add_bullet, host, false)
        val sheetLp = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM,
        )
        host.addView(sheet, sheetLp)
        dialog.setContentView(host)

        val sheetScroll = sheet.findViewById<ScrollView>(R.id.sheetScroll)
        val screenHeight = context.resources.displayMetrics.heightPixels
        val closedBottomMargin = dp(context, 28) // clearance above gesture / home buttons
        var bottomInset = closedBottomMargin

        fun maxSheetHeight(): Int {
            val available = (screenHeight - bottomInset).coerceAtLeast(dp(context, 200))
            return minOf((screenHeight * 0.9f).toInt(), available)
        }

        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setGravity(Gravity.FILL)
            // Wallpaper launcher windows ignore ADJUST_RESIZE; lift via insets padding.
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            attributes = attributes.apply {
                dimAmount = 0.55f
                width = ViewGroup.LayoutParams.MATCH_PARENT
                height = ViewGroup.LayoutParams.MATCH_PARENT
                y = 0
            }
            WindowCompat.setDecorFitsSystemWindows(this, false)
        }

        fun clampSheetHeight() {
            val widthSpec = View.MeasureSpec.makeMeasureSpec(
                context.resources.displayMetrics.widthPixels,
                View.MeasureSpec.EXACTLY,
            )
            val maxHeight = maxSheetHeight()
            sheetScroll.layoutParams = sheetScroll.layoutParams.apply {
                height = ViewGroup.LayoutParams.WRAP_CONTENT
            }
            sheet.layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM,
            ).also { it.bottomMargin = bottomInset }

            sheet.measure(
                widthSpec,
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            )
            val contentHeight = sheet.measuredHeight
            if (contentHeight > maxHeight) {
                val footerHeight = contentHeight - sheetScroll.measuredHeight
                val scrollHeight = (maxHeight - footerHeight).coerceAtLeast(dp(context, 120))
                sheetScroll.layoutParams = sheetScroll.layoutParams.apply {
                    height = scrollHeight
                }
                sheet.layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    maxHeight,
                    Gravity.BOTTOM,
                ).also { it.bottomMargin = bottomInset }
            }
            sheet.requestLayout()
        }

        fun applyBottomInset(ime: Int, nav: Int) {
            // Keyboard open: sit on the IME. Keyboard closed: clear nav / home gesture bar.
            val next = if (ime > 0) {
                ime
            } else {
                maxOf(nav, closedBottomMargin)
            }
            if (next == bottomInset) return
            bottomInset = next
            sheet.updateLayoutParams<FrameLayout.LayoutParams> {
                bottomMargin = bottomInset
                gravity = Gravity.BOTTOM
            }
            clampSheetHeight()
        }

        ViewCompat.setOnApplyWindowInsetsListener(host) { _, insets ->
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            val nav = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            applyBottomInset(ime, nav)
            insets
        }

        val title = sheet.findViewById<TextView>(R.id.dialogTitle)
        val input = sheet.findViewById<EditText>(R.id.entryInput)
        val scheduleValue = sheet.findViewById<TextView>(R.id.scheduleValue)
        val scheduleClear = sheet.findViewById<TextView>(R.id.scheduleClear)
        val typeTask = sheet.findViewById<TextView>(R.id.typeTask)
        val typeEvent = sheet.findViewById<TextView>(R.id.typeEvent)
        val typeNote = sheet.findViewById<TextView>(R.id.typeNote)
        val tagsSection = sheet.findViewById<View>(R.id.tagsSection)
        val tagList = sheet.findViewById<LinearLayout>(R.id.tagChipGroup)
        val newTagInput = sheet.findViewById<EditText>(R.id.newTagInput)
        val addTagButton = sheet.findViewById<TextView>(R.id.addTagButton)
        val calendarSection = sheet.findViewById<View>(R.id.calendarSection)
        val calendarSpinner = sheet.findViewById<Spinner>(R.id.calendarSpinner)
        val priorityRow = sheet.findViewById<LinearLayout>(R.id.priorityRow)
        val priorityIcon = sheet.findViewById<ImageView>(R.id.priorityIcon)
        val priorityToggle = sheet.findViewById<TextView>(R.id.priorityToggle)
        val delete = sheet.findViewById<TextView>(R.id.deleteButton)
        val cancel = sheet.findViewById<TextView>(R.id.cancelButton)
        val save = sheet.findViewById<TextView>(R.id.saveButton)

        var selectedType = existing?.type ?: BulletType.TASK
        var priority = existing?.priority ?: false
        val selectedAlpha = 1f
        val dimAlpha = 0.45f
        val editing = existing != null
        val dayFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val displayFormat = SimpleDateFormat("EEE, d MMM", Locale.getDefault())
        val militaryTime = Prefs(context).journalMilitaryTime
        val timeDisplayPattern = if (militaryTime) "HH:mm" else "h:mm a"
        val timeDisplayFormat = SimpleDateFormat(timeDisplayPattern, Locale.getDefault())

        // null schedule = Unscheduled (Index). Picker still opens on "today".
        var scheduledDate: Calendar? = existing?.let { entry ->
            when (entry.log) {
                JournalLog.UNSCHEDULED -> null
                JournalLog.FUTURE -> {
                    // Month key → first of that month for the picker.
                    runCatching {
                        Calendar.getInstance().apply {
                            time = SimpleDateFormat("yyyy-MM", Locale.US).parse(entry.dateKey)!!
                            set(Calendar.DAY_OF_MONTH, 1)
                        }
                    }.getOrNull()
                }
                else -> runCatching {
                    Calendar.getInstance().apply { time = dayFormat.parse(entry.dateKey)!! }
                }.getOrNull()
            }
        }
        var scheduledTimeMinutes: Int? = existing?.timeMinutes

        fun refreshScheduleLabel() {
            val date = scheduledDate
            if (date == null) {
                scheduleValue.setText(R.string.schedule_unscheduled)
                scheduleClear.isVisible = false
            } else {
                val datePart = displayFormat.format(date.time)
                val timePart = scheduledTimeMinutes?.let { minutes ->
                    val cal = Calendar.getInstance().apply {
                        set(Calendar.HOUR_OF_DAY, minutes / 60)
                        set(Calendar.MINUTE, minutes % 60)
                    }
                    timeDisplayFormat.format(cal.time)
                }
                scheduleValue.text = if (timePart != null) "$datePart · $timePart" else datePart
                scheduleClear.isVisible = true
            }
        }

        fun openTimePicker() {
            val initial = scheduledTimeMinutes
            val now = Calendar.getInstance()
            val hour = initial?.div(60) ?: now.get(Calendar.HOUR_OF_DAY)
            val minute = initial?.rem(60) ?: now.get(Calendar.MINUTE)
            fun applyAllDay() {
                scheduledTimeMinutes = null
                refreshScheduleLabel()
                clampSheetHeight()
            }
            TimePickerDialog(
                context,
                { _, h, m ->
                    scheduledTimeMinutes = h * 60 + m
                    refreshScheduleLabel()
                    clampSheetHeight()
                },
                hour,
                minute,
                militaryTime,
            ).apply {
                // Replace Cancel so choosing no time is an explicit "all day".
                setButton(
                    DialogInterface.BUTTON_NEGATIVE,
                    context.getString(R.string.schedule_all_day),
                ) { dialog, _ ->
                    applyAllDay()
                    dialog.dismiss()
                }
                setOnCancelListener { applyAllDay() }
            }.show()
        }

        fun openDateTimePicker() {
            val initial = scheduledDate ?: Calendar.getInstance()
            DatePickerDialog(
                context,
                { _, year, month, day ->
                    scheduledDate = Calendar.getInstance().apply {
                        set(year, month, day, 0, 0, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    refreshScheduleLabel()
                    // Date then time — canceling time keeps an all-day schedule.
                    openTimePicker()
                },
                initial.get(Calendar.YEAR),
                initial.get(Calendar.MONTH),
                initial.get(Calendar.DAY_OF_MONTH),
            ).show()
        }

        scheduleValue.setOnClickListener { openDateTimePicker() }
        scheduleClear.setOnClickListener {
            scheduledDate = null
            scheduledTimeMinutes = null
            refreshScheduleLabel()
            clampSheetHeight()
        }
        refreshScheduleLabel()

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
            val dateKey = scheduledDate?.let { dayFormat.format(it.time) }
            onSave(
                Result(
                    text = text,
                    type = selectedType,
                    priority = priority,
                    tags = tags,
                    calendarId = calendarId,
                    scheduledDateKey = dateKey,
                    timeMinutes = scheduledTimeMinutes,
                )
            )
            dialog.dismiss()
        }

        // Opaque paper/grey surface (same as drawer) so wallpaper doesn't show through.
        sheet.setBackgroundColor(context.getColorFromAttr(R.attr.drawerBackgroundColor))
        priorityIcon.setColorFilter(context.getColorFromAttr(R.attr.primaryColor))

        // Tapping the dimmed area outside the sheet dismisses.
        host.setOnClickListener { dialog.dismiss() }
        sheet.setOnClickListener { /* keep taps on the sheet from dismissing */ }

        dialog.setOnShowListener {
            ViewCompat.requestApplyInsets(host)
            clampSheetHeight()
        }
        dialog.show()
        clampSheetHeight()
        input.post {
            input.requestFocus()
            ViewCompat.requestApplyInsets(host)
        }
    }

    private fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()
}
