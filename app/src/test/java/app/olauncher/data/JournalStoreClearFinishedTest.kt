package app.olauncher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class JournalStoreClearFinishedTest {

    private fun store(): JournalStore {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("app.olauncher.journal", 0).edit().clear().commit()
        return JournalStore(context)
    }

    @Test
    fun deleteCompletedTasksRemovesOnlyFinishedTasks() {
        val store = store()
        val open = store.add("Call mom", BulletType.TASK, JournalLog.DAILY, "2026-09-29")
        val doneDaily = store.add("Morning pages", BulletType.TASK, JournalLog.DAILY, "2026-09-29")
        val doneMonthly = store.add("Grocery run", BulletType.TASK, JournalLog.MONTHLY, "2026-09-28")
        val doneFuture = store.add("Ship v7", BulletType.TASK, JournalLog.FUTURE, "2026-11")
        val doneInbox = store.add(
            "Inbox triage",
            BulletType.TASK,
            JournalLog.UNSCHEDULED,
            JournalPages.UNSCHEDULED_KEY,
        )
        val event = store.add("Dentist", BulletType.EVENT, JournalLog.DAILY, "2026-09-29")
        val note = store.add("Idea", BulletType.NOTE, JournalLog.DAILY, "2026-09-29")

        listOf(doneDaily, doneMonthly, doneFuture, doneInbox).forEach {
            store.toggleCompleted(it.id)
        }

        assertEquals(4, store.countCompletedTasks())
        assertEquals(4, store.deleteCompletedTasks())
        assertEquals(0, store.countCompletedTasks())
        assertEquals(0, store.deleteCompletedTasks())

        val remaining = store.getAll().map { it.id }.toSet()
        assertEquals(setOf(open.id, event.id, note.id), remaining)
        assertTrue(store.getAll().none { it.completed })
    }
}
