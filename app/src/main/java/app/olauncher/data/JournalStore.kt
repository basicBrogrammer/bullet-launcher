package app.olauncher.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Lightweight SharedPreferences-backed store for bullet journal entries.
 */
class JournalStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val dayFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val monthFormat = SimpleDateFormat("yyyy-MM", Locale.US)
    private val displayDayFormat = SimpleDateFormat("EEE, d MMM", Locale.getDefault())
    private val displayMonthFormat = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
    private val displayMonthShortFormat = SimpleDateFormat("MMM yyyy", Locale.getDefault())

    fun todayKey(): String = dayFormat.format(Date())

    fun currentMonthKey(): String = monthFormat.format(Date())

    fun formatDayHeader(dateKey: String = todayKey()): String {
        return try {
            displayDayFormat.format(dayFormat.parse(dateKey)!!)
        } catch (_: Exception) {
            dateKey
        }
    }

    fun formatMonthHeader(monthKey: String = currentMonthKey()): String {
        return try {
            displayMonthFormat.format(monthFormat.parse(monthKey)!!)
        } catch (_: Exception) {
            monthKey
        }
    }

    fun formatMonthShort(monthKey: String): String {
        return try {
            displayMonthShortFormat.format(monthFormat.parse(monthKey)!!)
        } catch (_: Exception) {
            monthKey
        }
    }

    fun dayOfMonth(dateKey: String): Int {
        return try {
            val cal = Calendar.getInstance()
            cal.time = dayFormat.parse(dateKey)!!
            cal.get(Calendar.DAY_OF_MONTH)
        } catch (_: Exception) {
            0
        }
    }

    fun futureMonthKeys(count: Int = 6): List<String> {
        val cal = Calendar.getInstance()
        return (0 until count).map {
            val key = monthFormat.format(cal.time)
            cal.add(Calendar.MONTH, 1)
            key
        }
    }

    fun daysInMonth(monthKey: String = currentMonthKey()): List<String> {
        return try {
            val cal = Calendar.getInstance()
            cal.time = monthFormat.parse(monthKey)!!
            cal.set(Calendar.DAY_OF_MONTH, 1)
            val days = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
            (1..days).map { day ->
                String.format(Locale.US, "%s-%02d", monthKey, day)
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun getAll(): List<JournalEntry> {
        val raw = prefs.getString(KEY_ENTRIES, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    parseEntry(array.getJSONObject(i))?.let { add(it) }
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun getForDay(dateKey: String): List<JournalEntry> =
        getAll()
            .filter { it.log == JournalLog.DAILY && it.dateKey == dateKey }
            .sortedWith(compareByDescending<JournalEntry> { it.priority }.thenBy { it.createdAt })

    fun getForMonth(monthKey: String): List<JournalEntry> =
        getAll()
            .filter {
                (it.log == JournalLog.MONTHLY || it.log == JournalLog.DAILY) &&
                    it.dateKey.startsWith(monthKey)
            }
            .sortedWith(
                compareBy<JournalEntry> { it.dateKey }
                    .thenByDescending { it.priority }
                    .thenBy { it.createdAt }
            )

    fun getForFutureMonth(monthKey: String): List<JournalEntry> =
        getAll()
            .filter { it.log == JournalLog.FUTURE && it.dateKey == monthKey }
            .sortedWith(compareByDescending<JournalEntry> { it.priority }.thenBy { it.createdAt })

    fun getFutureEntries(): List<JournalEntry> =
        getAll()
            .filter { it.log == JournalLog.FUTURE }
            .sortedWith(
                compareBy<JournalEntry> { it.dateKey }
                    .thenByDescending { it.priority }
                    .thenBy { it.createdAt }
            )

    fun getUnscheduled(): List<JournalEntry> =
        getAll()
            .filter { it.log == JournalLog.UNSCHEDULED }
            .sortedWith(compareByDescending<JournalEntry> { it.priority }.thenBy { it.createdAt })

    fun getForTag(tag: String): List<JournalEntry> {
        val needle = normalizeTag(tag) ?: return emptyList()
        return getAll()
            .filter { entry ->
                entry.type == BulletType.TASK &&
                    entry.tags.any { normalizeTag(it) == needle }
            }
            .sortedWith(compareByDescending<JournalEntry> { it.priority }.thenBy { it.createdAt })
    }

    /** Tags that currently have at least one task — empty tags are omitted from the Index. */
    fun getTagsWithTasks(): List<String> {
        val counts = linkedMapOf<String, Pair<String, Int>>()
        getAll().filter { it.type == BulletType.TASK }.forEach { entry ->
            entry.tags.forEach { raw ->
                val key = normalizeTag(raw) ?: return@forEach
                val existing = counts[key]
                if (existing == null) {
                    counts[key] = raw.trim() to 1
                } else {
                    counts[key] = existing.first to (existing.second + 1)
                }
            }
        }
        return counts.values
            .filter { it.second > 0 }
            .map { it.first }
            .sortedBy { it.lowercase(Locale.getDefault()) }
    }

    fun getAllTags(): List<String> = getTagsWithTasks()

    fun add(
        text: String,
        type: BulletType,
        log: JournalLog,
        dateKey: String,
        priority: Boolean = false,
        calendarEventId: Long? = null,
        calendarId: Long? = null,
        fromCalendar: Boolean = false,
        tags: List<String> = emptyList(),
    ): JournalEntry {
        val entry = JournalEntry(
            id = UUID.randomUUID().toString(),
            text = text.trim(),
            type = type,
            log = log,
            dateKey = dateKey,
            priority = priority,
            calendarEventId = calendarEventId,
            calendarId = calendarId,
            fromCalendar = fromCalendar,
            tags = if (type == BulletType.TASK) sanitizeTags(tags) else emptyList(),
        )
        val updated = getAll().toMutableList().apply { add(entry) }
        saveAll(updated)
        return entry
    }

    fun toggleCompleted(id: String): JournalEntry? {
        val all = getAll().toMutableList()
        val index = all.indexOfFirst { it.id == id }
        if (index < 0) return null
        val current = all[index]
        if (current.type != BulletType.TASK) return current
        val updated = current.copy(completed = !current.completed)
        all[index] = updated
        saveAll(all)
        return updated
    }

    fun update(
        id: String,
        text: String,
        type: BulletType,
        priority: Boolean,
        tags: List<String> = emptyList(),
    ): JournalEntry? {
        val all = getAll().toMutableList()
        val index = all.indexOfFirst { it.id == id }
        if (index < 0) return null
        val current = all[index]
        val updated = current.copy(
            text = text.trim(),
            type = type,
            priority = priority,
            // Completing only applies to tasks; clear when changing away.
            completed = if (type == BulletType.TASK) current.completed else false,
            tags = if (type == BulletType.TASK) sanitizeTags(tags) else emptyList(),
        )
        all[index] = updated
        saveAll(all)
        return updated
    }

    fun delete(id: String) {
        saveAll(getAll().filterNot { it.id == id })
    }

    fun setCalendarLink(
        id: String,
        calendarEventId: Long?,
        calendarId: Long?,
        fromCalendar: Boolean = false,
    ): JournalEntry? {
        val all = getAll().toMutableList()
        val index = all.indexOfFirst { it.id == id }
        if (index < 0) return null
        val updated = all[index].copy(
            calendarEventId = calendarEventId,
            calendarId = calendarId,
            fromCalendar = fromCalendar,
        )
        all[index] = updated
        saveAll(all)
        return updated
    }

    fun updateSyncedEvent(
        id: String,
        text: String,
        log: JournalLog,
        dateKey: String,
        calendarId: Long?,
    ): JournalEntry? {
        val all = getAll().toMutableList()
        val index = all.indexOfFirst { it.id == id }
        if (index < 0) return null
        val updated = all[index].copy(
            text = text,
            log = log,
            dateKey = dateKey,
            calendarId = calendarId ?: all[index].calendarId,
        )
        all[index] = updated
        saveAll(all)
        return updated
    }

    fun getById(id: String): JournalEntry? = getAll().find { it.id == id }

    fun ensureSampleData() {
        if (prefs.getBoolean(KEY_SEEDED, false)) return
        if (getAll().isNotEmpty()) {
            prefs.edit { putBoolean(KEY_SEEDED, true) }
            return
        }
        val today = todayKey()
        val month = currentMonthKey()
        add("Morning pages", BulletType.TASK, JournalLog.DAILY, today, true, tags = listOf("Personal"))
        add("Team standup", BulletType.EVENT, JournalLog.DAILY, today, false)
        add("Idea: simplify home gestures", BulletType.NOTE, JournalLog.DAILY, today, false)
        add("Review monthly goals", BulletType.TASK, JournalLog.DAILY, today, false, tags = listOf("Work"))
        add("Morning pages", BulletType.TASK, JournalLog.MONTHLY, today, true, tags = listOf("Personal"))
        add("Team standup", BulletType.EVENT, JournalLog.MONTHLY, today, false)
        add("Inbox triage", BulletType.TASK, JournalLog.UNSCHEDULED, JournalPages.UNSCHEDULED_KEY, false, tags = listOf("Work"))
        add("Read design notes", BulletType.TASK, JournalLog.UNSCHEDULED, JournalPages.UNSCHEDULED_KEY, true, tags = listOf("Personal"))
        val futureMonths = futureMonthKeys(3)
        if (futureMonths.size >= 2) {
            add("Vacation planning", BulletType.TASK, JournalLog.FUTURE, futureMonths[1], true, tags = listOf("Personal"))
            add("Conference", BulletType.EVENT, JournalLog.FUTURE, futureMonths[1], false)
            add("Ship v7", BulletType.TASK, JournalLog.FUTURE, futureMonths.getOrElse(2) { month }, false, tags = listOf("Work"))
        }
        prefs.edit { putBoolean(KEY_SEEDED, true) }
    }

    private fun saveAll(entries: List<JournalEntry>) {
        val array = JSONArray()
        entries.forEach { array.put(toJson(it)) }
        prefs.edit { putString(KEY_ENTRIES, array.toString()) }
    }

    private fun toJson(entry: JournalEntry): JSONObject =
        JSONObject().apply {
            put("id", entry.id)
            put("text", entry.text)
            put("type", entry.type.name)
            put("log", entry.log.name)
            put("dateKey", entry.dateKey)
            put("priority", entry.priority)
            put("completed", entry.completed)
            put("createdAt", entry.createdAt)
            if (entry.calendarEventId != null) {
                put("calendarEventId", entry.calendarEventId)
            }
            if (entry.calendarId != null) {
                put("calendarId", entry.calendarId)
            }
            put("fromCalendar", entry.fromCalendar)
            if (entry.tags.isNotEmpty()) {
                put("tags", JSONArray(entry.tags))
            }
        }

    private fun parseEntry(obj: JSONObject): JournalEntry? {
        return try {
            JournalEntry(
                id = obj.getString("id"),
                text = obj.getString("text"),
                type = BulletType.fromName(obj.getString("type")),
                log = JournalLog.fromName(obj.getString("log")),
                dateKey = obj.getString("dateKey"),
                priority = obj.optBoolean("priority", false),
                completed = obj.optBoolean("completed", false),
                createdAt = obj.optLong("createdAt", 0L),
                calendarEventId = obj.optLong("calendarEventId", -1L)
                    .takeIf { it > 0L },
                calendarId = obj.optLong("calendarId", -1L)
                    .takeIf { it > 0L },
                fromCalendar = obj.optBoolean("fromCalendar", false),
                tags = parseTags(obj.optJSONArray("tags")),
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun parseTags(array: JSONArray?): List<String> {
        if (array == null) return emptyList()
        return sanitizeTags((0 until array.length()).map { array.optString(it) })
    }

    companion object {
        private const val PREFS_NAME = "app.olauncher.journal"
        private const val KEY_ENTRIES = "ENTRIES_JSON"
        private const val KEY_SEEDED = "SEEDED_SAMPLE"

        fun normalizeTag(raw: String?): String? {
            val trimmed = raw?.trim().orEmpty()
            if (trimmed.isEmpty()) return null
            return trimmed.lowercase(Locale.US)
        }

        fun sanitizeTags(tags: List<String>): List<String> {
            val seen = linkedSetOf<String>()
            val result = mutableListOf<String>()
            tags.forEach { raw ->
                val key = normalizeTag(raw) ?: return@forEach
                if (seen.add(key)) result.add(raw.trim())
            }
            return result
        }
    }
}
