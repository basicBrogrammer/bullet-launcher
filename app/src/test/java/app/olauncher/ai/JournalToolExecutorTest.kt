package app.olauncher.ai

import app.olauncher.data.BulletType
import app.olauncher.data.JournalLog
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
class JournalToolExecutorTest {

    private fun store(): JournalStore {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("app.olauncher.journal", 0).edit().clear().commit()
        return JournalStore(context)
    }

    @Test
    fun addTaskToday() {
        val store = store()
        val ctx = JournalAskContext("2026-08-22", "2026-08", emptyList())
        val result = JournalToolExecutor.execute(
            store,
            JournalTool.AddTask("Buy milk", "today"),
            ctx,
        ) as JournalToolResult.Added
        assertEquals("Buy milk", result.entry.text)
        assertEquals(BulletType.TASK, result.entry.type)
        assertEquals(JournalLog.DAILY, result.entry.log)
        assertEquals("2026-08-22", result.entry.dateKey)
        assertEquals(1, store.getForDay("2026-08-22").size)
    }

    @Test
    fun completeMatchesOpenTask() {
        val store = store()
        val ctx = JournalAskContext("2026-08-22", "2026-08", emptyList())
        JournalToolExecutor.execute(store, JournalTool.AddTask("Morning pages", "today"), ctx)
        val result = JournalToolExecutor.execute(
            store,
            JournalTool.CompleteTask("morning"),
            ctx,
        ) as JournalToolResult.Completed
        assertEquals(true, result.entry.completed)
    }

    @Test
    fun completeAmbiguous() {
        val store = store()
        val ctx = JournalAskContext("2026-08-22", "2026-08", emptyList())
        JournalToolExecutor.execute(store, JournalTool.AddTask("Call mom", "today"), ctx)
        JournalToolExecutor.execute(store, JournalTool.AddTask("Call dentist", "today"), ctx)
        val result = JournalToolExecutor.execute(store, JournalTool.CompleteTask("call"), ctx)
        assertTrue(result is JournalToolResult.Ambiguous)
        assertEquals(2, (result as JournalToolResult.Ambiguous).matches.size)
    }

    @Test
    fun completeNotFound() {
        val store = store()
        val ctx = JournalAskContext("2026-08-22", "2026-08", emptyList())
        val result = JournalToolExecutor.execute(store, JournalTool.CompleteTask("groceries"), ctx)
        assertTrue(result is JournalToolResult.NotFound)
    }

    @Test
    fun listOpenTasks() {
        val store = store()
        val ctx = JournalAskContext("2026-08-22", "2026-08", emptyList())
        JournalToolExecutor.execute(store, JournalTool.AddTask("A", "today"), ctx)
        JournalToolExecutor.execute(store, JournalTool.AddNote("not a task", "today"), ctx)
        val result = JournalToolExecutor.execute(store, JournalTool.ListTasks, ctx) as JournalToolResult.Listed
        assertEquals(1, result.entries.size)
        assertEquals("A", result.entries.first().text)
    }
}
