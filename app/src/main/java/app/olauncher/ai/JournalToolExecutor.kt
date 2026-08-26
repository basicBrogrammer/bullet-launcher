package app.olauncher.ai

import app.olauncher.data.BulletType
import app.olauncher.data.JournalEntry
import app.olauncher.data.JournalPages
import app.olauncher.data.JournalStore
import java.util.Locale

/** Applies a [JournalTool] to [JournalStore]. */
object JournalToolExecutor {

    fun execute(
        store: JournalStore,
        tool: JournalTool,
        context: JournalAskContext,
    ): JournalToolResult {
        return when (tool) {
            is JournalTool.AddTask -> add(
                store = store,
                text = tool.text,
                type = BulletType.TASK,
                dateToken = tool.dateToken,
                priority = tool.priority,
                tags = tool.tags,
                timeMinutes = null,
                context = context,
            )
            is JournalTool.AddEvent -> add(
                store = store,
                text = tool.text,
                type = BulletType.EVENT,
                dateToken = tool.dateToken,
                priority = false,
                tags = emptyList(),
                timeMinutes = tool.timeMinutes,
                context = context,
            )
            is JournalTool.AddNote -> add(
                store = store,
                text = tool.text,
                type = BulletType.NOTE,
                dateToken = tool.dateToken,
                priority = false,
                tags = emptyList(),
                timeMinutes = null,
                context = context,
            )
            is JournalTool.CompleteTask -> complete(store, tool.query)
            is JournalTool.ListTasks -> {
                val open = openTasks(store)
                if (open.isEmpty()) JournalToolResult.Listed(emptyList())
                else JournalToolResult.Listed(open)
            }
            is JournalTool.Reply -> {
                if (tool.text.isBlank()) JournalToolResult.Empty("empty")
                else JournalToolResult.Message(tool.text)
            }
        }
    }

    fun openTasks(store: JournalStore): List<JournalEntry> =
        store.getAll()
            .filter { it.type == BulletType.TASK && !it.completed }
            .sortedWith(
                compareBy<JournalEntry> { it.dateKey }
                    .thenByDescending { it.priority }
                    .thenBy { it.createdAt }
            )

    fun contextFrom(store: JournalStore): JournalAskContext =
        JournalAskContext(
            todayKey = store.todayKey(),
            currentMonthKey = store.currentMonthKey(),
            openTasks = openTasks(store),
        )

    private fun add(
        store: JournalStore,
        text: String,
        type: BulletType,
        dateToken: String?,
        priority: Boolean,
        tags: List<String>,
        timeMinutes: Int?,
        context: JournalAskContext,
    ): JournalToolResult {
        val cleaned = text.trim()
        if (cleaned.isEmpty()) return JournalToolResult.Empty("empty text")
        val schedule = JournalToolParser.resolveSchedule(dateToken, context)
        val dateKey = if (schedule.dateKey == "unscheduled") {
            JournalPages.UNSCHEDULED_KEY
        } else {
            schedule.dateKey
        }
        val entry = store.add(
            text = cleaned,
            type = type,
            log = schedule.log,
            dateKey = dateKey,
            priority = priority,
            tags = tags,
            timeMinutes = timeMinutes,
        )
        return JournalToolResult.Added(entry)
    }

    private fun complete(store: JournalStore, query: String): JournalToolResult {
        val needle = normalize(query)
        if (needle.length < 2) return JournalToolResult.Empty("query too short")
        val tasks = store.getAll().filter { it.type == BulletType.TASK }
        val matches = tasks.filter { matchesQuery(it.text, needle) }
        if (matches.isEmpty()) return JournalToolResult.NotFound(query.trim())
        val open = matches.filter { !it.completed }
        if (open.isEmpty()) return JournalToolResult.AlreadyComplete(matches.first())
        if (open.size > 1) {
            val exact = open.filter { normalize(it.text) == needle }
            if (exact.size == 1) {
                val updated = store.toggleCompleted(exact.first().id) ?: exact.first()
                return JournalToolResult.Completed(updated)
            }
            return JournalToolResult.Ambiguous(open)
        }
        val updated = store.toggleCompleted(open.first().id) ?: open.first()
        return JournalToolResult.Completed(updated)
    }

    private fun matchesQuery(text: String, needle: String): Boolean {
        val hay = normalize(text)
        return hay.contains(needle) || needle.contains(hay)
    }

    private fun normalize(value: String): String =
        value.lowercase(Locale.US).replace(Regex("[^a-z0-9\\s]"), " ").replace(Regex("\\s+"), " ").trim()
}
