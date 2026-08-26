package app.olauncher.ai

import app.olauncher.data.JournalLog
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Turns model JSON (or a typed command) into a [JournalTool].
 */
object JournalToolParser {

    private val dayFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val completePrefixes = listOf(
        "check off ",
        "mark done ",
        "complete ",
        "completed ",
        "finish ",
        "done ",
    )
    private val eventPrefixes = listOf("appointment ", "meeting ", "event ")
    private val notePrefixes = listOf("remember ", "note ", "jot ")
    private val taskPrefixes = listOf(
        "remind me to ",
        "add task ",
        "add a ",
        "todo ",
        "task ",
        "add ",
    )
    private val listPatterns = listOf(
        "what's on my list",
        "whats on my list",
        "what's on today",
        "whats on today",
        "what is on today",
        "show tasks",
        "show my tasks",
        "list tasks",
        "list my tasks",
        "what do i have",
    )

    fun parseModelOutput(raw: String, context: JournalAskContext): JournalTool? {
        val json = extractJsonObject(raw) ?: return null
        val tool = json.optString("tool").trim().lowercase(Locale.US)
        return when (tool) {
            "add_task" -> {
                val text = json.optString("text").trim()
                if (text.isEmpty()) return null
                JournalTool.AddTask(
                    text = text,
                    dateToken = normalizeDateToken(json.optString("date").ifBlank { "today" }),
                    priority = json.optBoolean("priority", false),
                    tags = parseTags(json.opt("tags")),
                )
            }
            "complete_task" -> {
                val query = json.optString("query").ifBlank { json.optString("text") }.trim()
                if (query.isEmpty()) return null
                JournalTool.CompleteTask(query)
            }
            "add_event" -> {
                val text = json.optString("text").trim()
                if (text.isEmpty()) return null
                JournalTool.AddEvent(
                    text = text,
                    dateToken = normalizeDateToken(json.optString("date").ifBlank { "today" }),
                    timeMinutes = parseTimeMinutes(json.optString("time")),
                )
            }
            "add_note" -> {
                val text = json.optString("text").trim()
                if (text.isEmpty()) return null
                JournalTool.AddNote(
                    text = text,
                    dateToken = normalizeDateToken(json.optString("date").ifBlank { "today" }),
                )
            }
            "list_tasks" -> JournalTool.ListTasks
            "reply" -> {
                val text = json.optString("text").trim()
                if (text.isEmpty()) return null
                JournalTool.Reply(text)
            }
            else -> null
        }
    }

    fun parseFallback(input: String, context: JournalAskContext): JournalTool {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return JournalTool.Reply("")
        val lower = trimmed.lowercase(Locale.US)
        if (listPatterns.any { lower.contains(it) } || lower == "list" || lower == "tasks") {
            return JournalTool.ListTasks
        }
        completePrefixes.firstOrNull { lower.startsWith(it) }?.let { prefix ->
            val query = stripDateWords(trimmed.substring(prefix.length)).first.trim()
            if (query.isNotEmpty()) return JournalTool.CompleteTask(query)
        }
        eventPrefixes.firstOrNull { lower.startsWith(it) }?.let { prefix ->
            val (text, dateToken, timeMinutes) = splitEntry(trimmed.substring(prefix.length))
            if (text.isNotEmpty()) {
                return JournalTool.AddEvent(text, dateToken, timeMinutes)
            }
        }
        notePrefixes.firstOrNull { lower.startsWith(it) }?.let { prefix ->
            val (text, dateToken, _) = splitEntry(trimmed.substring(prefix.length))
            if (text.isNotEmpty()) return JournalTool.AddNote(text, dateToken)
        }
        val withoutTaskPrefix = taskPrefixes.firstOrNull { lower.startsWith(it) }
            ?.let { trimmed.substring(it.length) }
            ?: trimmed
        val (text, dateToken, timeMinutes) = splitEntry(withoutTaskPrefix)
        if (text.isEmpty()) return JournalTool.ListTasks
        if (timeMinutes != null) {
            return JournalTool.AddEvent(text, dateToken, timeMinutes)
        }
        return JournalTool.AddTask(text, dateToken)
    }

    fun resolveSchedule(dateToken: String?, context: JournalAskContext): ResolvedSchedule {
        val token = normalizeDateToken(dateToken) ?: "today"
        return when {
            token == "unscheduled" || token == "inbox" ->
                ResolvedSchedule(JournalLog.UNSCHEDULED, "unscheduled")
            token == "today" ->
                ResolvedSchedule(JournalLog.DAILY, context.todayKey)
            token == "tomorrow" ->
                ResolvedSchedule(JournalLog.DAILY, offsetDay(context.todayKey, 1))
            token.matches(DAY_KEY) -> {
                val month = token.take(7)
                if (month > context.currentMonthKey) {
                    ResolvedSchedule(JournalLog.FUTURE, month)
                } else {
                    ResolvedSchedule(JournalLog.DAILY, token)
                }
            }
            token.matches(MONTH_KEY) ->
                ResolvedSchedule(JournalLog.FUTURE, token)
            else ->
                ResolvedSchedule(JournalLog.DAILY, context.todayKey)
        }
    }

    internal fun extractJsonObject(raw: String): JSONObject? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        val start = trimmed.indexOf('{')
        val end = trimmed.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return try {
            JSONObject(trimmed.substring(start, end + 1))
        } catch (_: Exception) {
            null
        }
    }

    private fun parseTags(raw: Any?): List<String> {
        return when (raw) {
            is JSONArray -> (0 until raw.length()).mapNotNull { raw.optString(it).trim().ifEmpty { null } }
            is String -> raw.split(',').map { it.trim() }.filter { it.isNotEmpty() }
            else -> emptyList()
        }
    }

    internal fun parseTimeMinutes(raw: String?): Int? {
        val value = raw?.trim().orEmpty()
        if (value.isEmpty() || value == "null") return null
        val military = Regex("^([01]?\\d|2[0-3]):([0-5]\\d)$").matchEntire(value)
        if (military != null) {
            return military.groupValues[1].toInt() * 60 + military.groupValues[2].toInt()
        }
        val twelve = Regex("^(1[0-2]|0?[1-9]):([0-5]\\d)\\s*([AaPp][Mm])$").matchEntire(value)
        if (twelve != null) {
            var hour = twelve.groupValues[1].toInt() % 12
            if (twelve.groupValues[3].startsWith("P", ignoreCase = true)) hour += 12
            return hour * 60 + twelve.groupValues[2].toInt()
        }
        return null
    }

    private fun splitEntry(raw: String): Triple<String, String, Int?> {
        var text = raw.trim()
        var dateToken = "today"
        val (stripped, token) = stripDateWords(text)
        text = stripped
        if (token != null) dateToken = token
        val timeMatch = Regex(
            "(?:at\\s+)?((?:[01]?\\d|2[0-3]):[0-5]\\d|(?:1[0-2]|0?[1-9]):[0-5]\\d\\s*[AaPp][Mm])$"
        ).find(text)
        val timeMinutes = timeMatch?.let { parseTimeMinutes(it.groupValues[1]) }
        if (timeMatch != null) {
            text = text.removeRange(timeMatch.range).trim()
            text = text.removeSuffix("at").trim()
        }
        val dayKey = Regex("\\b(\\d{4}-\\d{2}-\\d{2})\\b").find(text)
        if (dayKey != null) {
            dateToken = dayKey.groupValues[1]
            text = text.removeRange(dayKey.range).trim()
        }
        if (text.isEmpty()) text = raw.trim()
        return Triple(text, dateToken, timeMinutes)
    }

    private fun stripDateWords(raw: String): Pair<String, String?> {
        var text = raw.trim()
        var token: String? = null
        val patterns = listOf(
            Regex("\\b(tomorrow)\\b", RegexOption.IGNORE_CASE) to "tomorrow",
            Regex("\\b(today)\\b", RegexOption.IGNORE_CASE) to "today",
            Regex("\\b(unscheduled|inbox)\\b", RegexOption.IGNORE_CASE) to "unscheduled",
        )
        patterns.forEach { (regex, value) ->
            if (regex.containsMatchIn(text)) {
                token = value
                text = regex.replace(text, " ").replace(Regex("\\s+"), " ").trim()
            }
        }
        return text to token
    }

    private fun normalizeDateToken(raw: String?): String? {
        val token = raw?.trim()?.lowercase(Locale.US).orEmpty()
        if (token.isEmpty() || token == "null") return null
        return token
    }

    private fun offsetDay(todayKey: String, days: Int): String {
        val cal = Calendar.getInstance()
        cal.time = dayFormat.parse(todayKey) ?: return todayKey
        cal.add(Calendar.DAY_OF_YEAR, days)
        return dayFormat.format(cal.time)
    }

    private val DAY_KEY = Regex("^\\d{4}-\\d{2}-\\d{2}$")
    private val MONTH_KEY = Regex("^\\d{4}-\\d{2}$")
}
