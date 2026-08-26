package app.olauncher.ai

import android.content.Context
import app.olauncher.data.JournalStore

/**
 * Shared journal tools used by Gemini Nano (Ask journal) and by Android
 * App Functions (the system assistant). Both front doors execute here.
 */
class JournalAppFunctionHandler(
    private val store: JournalStore,
    private val context: Context,
) {
    fun addTask(text: String, date: String = "today", priority: Boolean = false): String {
        return run(JournalTool.AddTask(text.trim(), date.ifBlank { "today" }, priority))
    }

    fun completeTask(query: String): String {
        return run(JournalTool.CompleteTask(query.trim()))
    }

    fun addEvent(text: String, date: String = "today", time: String? = null): String {
        return run(
            JournalTool.AddEvent(
                text = text.trim(),
                dateToken = date.ifBlank { "today" },
                timeMinutes = JournalToolParser.parseTimeMinutes(time),
            )
        )
    }

    fun addNote(text: String, date: String = "today"): String {
        return run(JournalTool.AddNote(text.trim(), date.ifBlank { "today" }))
    }

    fun listOpenTasks(): String {
        return run(JournalTool.ListTasks)
    }

    private fun run(tool: JournalTool): String {
        val askContext = JournalToolExecutor.contextFrom(store)
        val result = JournalToolExecutor.execute(store, tool, askContext)
        return JournalToolCopy.describe(context, result)
    }
}
