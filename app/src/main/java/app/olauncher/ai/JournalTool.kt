package app.olauncher.ai

import app.olauncher.data.JournalEntry
import app.olauncher.data.JournalLog

/**
 * Local tools the on-device model (or fallback parser) may invoke.
 *
 * Gemini Nano's Prompt API has no native function calling, so the launcher
 * owns this schema, asks the model for JSON, and executes the result here.
 */
sealed class JournalTool {
    data class AddTask(
        val text: String,
        val dateToken: String? = "today",
        val priority: Boolean = false,
        val tags: List<String> = emptyList(),
    ) : JournalTool()

    data class CompleteTask(
        val query: String,
    ) : JournalTool()

    data class AddEvent(
        val text: String,
        val dateToken: String? = "today",
        val timeMinutes: Int? = null,
    ) : JournalTool()

    data class AddNote(
        val text: String,
        val dateToken: String? = "today",
    ) : JournalTool()

    data object ListTasks : JournalTool()

    data class Reply(
        val text: String,
    ) : JournalTool()
}

data class JournalAskContext(
    val todayKey: String,
    val currentMonthKey: String,
    val openTasks: List<JournalEntry>,
)

enum class JournalEngine {
    NANO,
    FALLBACK,
}

data class JournalInterpretation(
    val tool: JournalTool,
    val engine: JournalEngine,
)

sealed class JournalToolResult {
    data class Added(val entry: JournalEntry) : JournalToolResult()
    data class Completed(val entry: JournalEntry) : JournalToolResult()
    data class AlreadyComplete(val entry: JournalEntry) : JournalToolResult()
    data class Ambiguous(val matches: List<JournalEntry>) : JournalToolResult()
    data class NotFound(val query: String) : JournalToolResult()
    data class Listed(val entries: List<JournalEntry>) : JournalToolResult()
    data class Message(val text: String) : JournalToolResult()
    data class Empty(val reason: String) : JournalToolResult()
}

data class ResolvedSchedule(
    val log: JournalLog,
    val dateKey: String,
)
