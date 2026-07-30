package app.olauncher.data

/**
 * Bullet Journal rapid-logging entry.
 *
 * Symbols:
 * • task / to-do
 * ○ event
 * – note / thought
 * ★ high-priority signifier (star icon next to the bullet)
 */
enum class BulletType(val symbol: String) {
    TASK("•"),
    EVENT("○"),
    NOTE("–");

    companion object {
        fun fromName(name: String): BulletType =
            entries.find { it.name.equals(name, ignoreCase = true) } ?: TASK
    }
}

enum class JournalLog {
    DAILY,
    MONTHLY,
    FUTURE,
    /** Tasks not assigned to a daily / monthly / future date. */
    UNSCHEDULED;

    companion object {
        fun fromName(name: String): JournalLog =
            entries.find { it.name.equals(name, ignoreCase = true) } ?: DAILY
    }
}

data class JournalEntry(
    val id: String,
    val text: String,
    val type: BulletType,
    val log: JournalLog,
    /** Day key yyyy-MM-dd for daily/monthly; month key yyyy-MM for future; blank for unscheduled. */
    val dateKey: String,
    val priority: Boolean = false,
    val completed: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    /** CalendarContract.Events row id when linked to the device calendar. */
    val calendarEventId: Long? = null,
    /** CalendarContract.Calendars row id this event belongs to. */
    val calendarId: Long? = null,
    /** True when this bullet was imported from the device/Google calendar. */
    val fromCalendar: Boolean = false,
    /** Topic tags (primarily for tasks); used by the Index collections. */
    val tags: List<String> = emptyList(),
    /** Minutes from midnight for timed events; null = all-day / no time. */
    val timeMinutes: Int? = null,
) {
    fun displaySymbol(): String = when {
        type == BulletType.TASK && completed -> "×"
        else -> type.symbol
    }

    /** Title with optional timed-event suffix for list rows. */
    fun displayText(): String {
        val minutes = timeMinutes ?: return text
        if (type != BulletType.EVENT) return text
        // Imported events may already include " · HH:mm" in [text].
        if (text.contains(" · ")) return text
        val hour = minutes / 60
        val minute = minutes % 60
        return String.format(java.util.Locale.getDefault(), "%s · %02d:%02d", text, hour, minute)
    }
}

/** Destinations listed by the Index (collections). */
sealed class IndexDestination {
    data object Unscheduled : IndexDestination()
    data class Tag(val name: String) : IndexDestination()
}

object JournalPages {
    const val MONTHLY = 0
    const val DAILY = 1
    const val FUTURE = 2
    const val COUNT = 3

    const val UNSCHEDULED_KEY = "unscheduled"
}
