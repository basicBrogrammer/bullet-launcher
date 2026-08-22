package app.olauncher.ai

import app.olauncher.data.BulletType
import app.olauncher.data.JournalEntry
import app.olauncher.data.JournalLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class JournalToolParserTest {

    private val context = JournalAskContext(
        todayKey = "2026-08-22",
        currentMonthKey = "2026-08",
        openTasks = listOf(
            JournalEntry(
                id = "1",
                text = "Morning pages",
                type = BulletType.TASK,
                log = JournalLog.DAILY,
                dateKey = "2026-08-22",
            )
        ),
    )

    @Test
    fun parsesAddTaskJson() {
        val tool = JournalToolParser.parseModelOutput(
            """{"tool":"add_task","text":"Buy milk","date":"tomorrow","priority":true}""",
            context,
        )
        assertTrue(tool is JournalTool.AddTask)
        val add = tool as JournalTool.AddTask
        assertEquals("Buy milk", add.text)
        assertEquals("tomorrow", add.dateToken)
        assertEquals(true, add.priority)
    }

    @Test
    fun extractsJsonFromMarkdownFence() {
        val tool = JournalToolParser.parseModelOutput(
            "```json\n{\"tool\":\"complete_task\",\"query\":\"standup\"}\n```",
            context,
        )
        assertEquals(JournalTool.CompleteTask("standup"), tool)
    }

    @Test
    fun parsesEventTime() {
        val tool = JournalToolParser.parseModelOutput(
            """{"tool":"add_event","text":"Dentist","date":"today","time":"15:30"}""",
            context,
        )
        val event = tool as JournalTool.AddEvent
        assertEquals("Dentist", event.text)
        assertEquals(15 * 60 + 30, event.timeMinutes)
    }

    @Test
    fun fallbackAddTask() {
        val tool = JournalToolParser.parseFallback("add buy milk tomorrow", context)
        val add = tool as JournalTool.AddTask
        assertEquals("buy milk", add.text)
        assertEquals("tomorrow", add.dateToken)
    }

    @Test
    fun fallbackComplete() {
        val tool = JournalToolParser.parseFallback("done morning pages", context)
        assertEquals(JournalTool.CompleteTask("morning pages"), tool)
    }

    @Test
    fun fallbackEventWithTime() {
        val tool = JournalToolParser.parseFallback("event dentist at 3:30 PM", context)
        val event = tool as JournalTool.AddEvent
        assertEquals("dentist", event.text)
        assertEquals(15 * 60 + 30, event.timeMinutes)
    }

    @Test
    fun fallbackList() {
        assertEquals(JournalTool.ListTasks, JournalToolParser.parseFallback("what's on my list", context))
    }

    @Test
    fun fallbackBareTextIsTask() {
        val tool = JournalToolParser.parseFallback("Call the dentist", context)
        assertEquals(JournalTool.AddTask("Call the dentist", "today"), tool)
    }

    @Test
    fun resolveTomorrow() {
        val schedule = JournalToolParser.resolveSchedule("tomorrow", context)
        assertEquals(JournalLog.DAILY, schedule.log)
        assertEquals("2026-08-23", schedule.dateKey)
    }

    @Test
    fun resolveFutureMonth() {
        val schedule = JournalToolParser.resolveSchedule("2026-10-03", context)
        assertEquals(JournalLog.FUTURE, schedule.log)
        assertEquals("2026-10", schedule.dateKey)
    }

    @Test
    fun resolveUnscheduled() {
        val schedule = JournalToolParser.resolveSchedule("unscheduled", context)
        assertEquals(JournalLog.UNSCHEDULED, schedule.log)
    }

    @Test
    fun rejectsUnknownJsonTool() {
        assertNull(JournalToolParser.parseModelOutput("""{"tool":"delete_all"}""", context))
    }
}
