package app.olauncher.ai

import app.olauncher.data.BulletType
import app.olauncher.data.JournalEntry
import app.olauncher.data.JournalLog
import org.junit.Assert.assertTrue
import org.junit.Test

class NanoJournalClientTest {

    @Test
    fun promptListsToolsAndOpenTasks() {
        val context = JournalAskContext(
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
        val prompt = NanoJournalClient.buildPrompt("done morning pages", context)
        assertTrue(prompt.contains("complete_task"))
        assertTrue(prompt.contains("add_task"))
        assertTrue(prompt.contains("2026-08-22"))
        assertTrue(prompt.contains("Morning pages"))
        assertTrue(prompt.contains("done morning pages"))
    }
}
