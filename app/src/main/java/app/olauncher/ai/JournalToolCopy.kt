package app.olauncher.ai

import android.content.Context
import app.olauncher.R
import app.olauncher.data.BulletType

object JournalToolCopy {

    fun describe(context: Context, result: JournalToolResult): String {
        return when (result) {
            is JournalToolResult.Added -> {
                val kind = when (result.entry.type) {
                    BulletType.TASK -> context.getString(R.string.ask_journal_kind_task)
                    BulletType.EVENT -> context.getString(R.string.ask_journal_kind_event)
                    BulletType.NOTE -> context.getString(R.string.ask_journal_kind_note)
                }
                context.getString(R.string.ask_journal_added, kind, result.entry.text)
            }
            is JournalToolResult.Completed ->
                context.getString(R.string.ask_journal_completed, result.entry.text)
            is JournalToolResult.AlreadyComplete ->
                context.getString(R.string.ask_journal_already_done, result.entry.text)
            is JournalToolResult.Ambiguous -> {
                val names = result.matches.take(3).joinToString(", ") { it.text }
                context.getString(R.string.ask_journal_ambiguous, names)
            }
            is JournalToolResult.NotFound ->
                context.getString(R.string.ask_journal_not_found, result.query)
            is JournalToolResult.Listed -> {
                if (result.entries.isEmpty()) {
                    context.getString(R.string.ask_journal_list_empty)
                } else {
                    val names = result.entries.take(8).joinToString(" · ") { it.text }
                    context.getString(R.string.ask_journal_list, names)
                }
            }
            is JournalToolResult.Message -> result.text
            is JournalToolResult.Empty -> context.getString(R.string.ask_journal_empty)
        }
    }

    fun engineLabel(context: Context, availability: NanoAvailability, usedNano: Boolean): String {
        return when {
            usedNano -> context.getString(R.string.ask_journal_engine_nano)
            availability == NanoAvailability.DOWNLOADABLE ->
                context.getString(R.string.ask_journal_engine_downloadable)
            availability == NanoAvailability.DOWNLOADING ->
                context.getString(R.string.ask_journal_engine_downloading)
            availability == NanoAvailability.AVAILABLE ->
                context.getString(R.string.ask_journal_engine_nano)
            else -> context.getString(R.string.ask_journal_engine_fallback)
        }
    }
}
