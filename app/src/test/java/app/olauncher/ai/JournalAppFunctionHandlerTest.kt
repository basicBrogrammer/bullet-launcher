package app.olauncher.ai

import app.olauncher.data.JournalStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class JournalAppFunctionHandlerTest {

    private fun handler(): JournalAppFunctionHandler {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("app.olauncher.journal", 0).edit().clear().commit()
        return JournalAppFunctionHandler(JournalStore(context), context)
    }

    @Test
    fun addTaskThenComplete() {
        val handler = handler()
        val added = handler.addTask("Buy milk", "today")
        assertTrue(added.contains("Buy milk"))
        val done = handler.completeTask("milk")
        assertTrue(done.contains("Buy milk"))
    }

    @Test
    fun completeAmbiguousDoesNotFlip() {
        val handler = handler()
        handler.addTask("Call mom")
        handler.addTask("Call dentist")
        val result = handler.completeTask("call")
        assertTrue(result.contains("Call mom"))
        assertTrue(result.contains("Call dentist"))
        val listed = handler.listOpenTasks()
        assertTrue(listed.contains("Call mom"))
        assertTrue(listed.contains("Call dentist"))
    }

    @Test
    fun addEventWithTime() {
        val handler = handler()
        val result = handler.addEvent("Dentist", "today", "15:30")
        assertTrue(result.lowercase().contains("event"))
        assertTrue(result.contains("Dentist"))
    }

    @Test
    fun listEmpty() {
        val handler = handler()
        assertEquals(
            RuntimeEnvironment.getApplication().getString(app.olauncher.R.string.ask_journal_list_empty),
            handler.listOpenTasks(),
        )
    }

    @Test
    fun blankDateDefaultsToToday() {
        val handler = handler()
        val added = handler.addTask("Walk", "")
        assertTrue(added.contains("Walk"))
        val listed = handler.listOpenTasks()
        assertTrue(listed.contains("Walk"))
    }

    @Test
    fun blankEventTimeIsAllowed() {
        val handler = handler()
        val result = handler.addEvent("Standup", "today", null)
        assertTrue(result.contains("Standup"))
    }
}
