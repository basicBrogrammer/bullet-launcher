package app.olauncher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class JournalTimeFormatTest {

    @Test
    fun militaryFormat() {
        assertEquals("09:05", JournalTimeFormat.format(9 * 60 + 5, militaryTime = true))
        assertEquals("15:30", JournalTimeFormat.format(15 * 60 + 30, militaryTime = true))
        assertEquals("00:00", JournalTimeFormat.format(0, militaryTime = true))
    }

    @Test
    fun twelveHourFormatContainsAmPm() {
        val morning = JournalTimeFormat.format(9 * 60 + 5, militaryTime = false)
        val afternoon = JournalTimeFormat.format(15 * 60 + 30, militaryTime = false)
        assertEquals(true, morning.contains("9:05"))
        assertEquals(true, afternoon.contains("3:30"))
        assertEquals(true, morning.matches(Regex(".*(?i)am.*")))
        assertEquals(true, afternoon.matches(Regex(".*(?i)pm.*")))
    }

    @Test
    fun splitEmbeddedMilitary() {
        val (title, minutes) = JournalTimeFormat.splitEmbeddedTime("Team standup · 10:00")
        assertEquals("Team standup", title)
        assertEquals(10 * 60, minutes)
    }

    @Test
    fun splitEmbeddedAbsent() {
        val (title, minutes) = JournalTimeFormat.splitEmbeddedTime("Morning pages")
        assertEquals("Morning pages", title)
        assertNull(minutes)
    }

    @Test
    fun displayTextRespectsFormat() {
        val entry = JournalEntry(
            id = "1",
            text = "Dentist",
            type = BulletType.EVENT,
            log = JournalLog.DAILY,
            dateKey = "2026-07-30",
            timeMinutes = 15 * 60 + 30,
        )
        assertEquals("Dentist · 15:30", entry.displayText(militaryTime = true))
        val twelve = entry.displayText(militaryTime = false)
        assertEquals(true, twelve.startsWith("Dentist · "))
        assertEquals(true, twelve.contains("3:30"))
    }
}
