package app.olauncher.data

/**
 * Bullet Journal rapid-logging entry.
 *
 * Symbols:
 * • task / to-do
 * ○ event
 * – note / thought
 * * high-priority signifier (shown next to the bullet)
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
    FUTURE;

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
    /** Day key yyyy-MM-dd for daily/monthly; month key yyyy-MM for future. */
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
) {
    fun displaySymbol(): String {
        val base = when {
            type == BulletType.TASK && completed -> "×"
            else -> type.symbol
        }
        return if (priority) "*$base" else base
    }
}

object JournalPages {
    const val MONTHLY = 0
    const val DAILY = 1
    const val FUTURE = 2
    const val COUNT = 3
}
