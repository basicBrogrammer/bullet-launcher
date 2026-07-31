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

class MonthlyDropTimeResolverTest {

    @Test
    fun keepsTimeWhenItFitsBetweenNeighbors() {
        assertEquals(
            10 * 60,
            MonthlyDropTimeResolver.resolve(
                oldTime = 10 * 60,
                aboveTime = 9 * 60,
                belowTime = 11 * 60,
            ),
        )
    }

    @Test
    fun midpointWhenTimeDoesNotFit() {
        assertEquals(
            10 * 60,
            MonthlyDropTimeResolver.resolve(
                oldTime = 15 * 60,
                aboveTime = 9 * 60,
                belowTime = 11 * 60,
            ),
        )
    }

    @Test
    fun keepsTimeAtEndsOrEmptyDay() {
        assertEquals(15 * 60, MonthlyDropTimeResolver.resolve(15 * 60, null, null))
        assertEquals(15 * 60, MonthlyDropTimeResolver.resolve(15 * 60, 9 * 60, null))
        assertEquals(15 * 60, MonthlyDropTimeResolver.resolve(15 * 60, null, 9 * 60))
    }

    @Test
    fun untimedStaysUntimed() {
        assertNull(MonthlyDropTimeResolver.resolve(null, 9 * 60, 11 * 60))
    }

    @Test
    fun createdAtMidpoint() {
        assertEquals(150L, MonthlyDropTimeResolver.resolveCreatedAt(100L, 200L, 999L))
        assertEquals(101L, MonthlyDropTimeResolver.resolveCreatedAt(100L, null, 999L))
        assertEquals(99L, MonthlyDropTimeResolver.resolveCreatedAt(null, 100L, 999L))
        assertEquals(999L, MonthlyDropTimeResolver.resolveCreatedAt(null, null, 999L))
    }
}
