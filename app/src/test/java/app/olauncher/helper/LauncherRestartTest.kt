package app.olauncher.helper

import app.olauncher.data.Constants
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherRestartTest {

    @Test
    fun timestampZeroIsNotDue() {
        assertFalse(shouldRestartLauncher(0L, 1_000_000L))
    }

    @Test
    fun recentRestartIsNotDue() {
        val now = 10_000_000L
        val threeHoursAgo = now - 3 * Constants.ONE_HOUR_IN_MILLIS
        assertFalse(shouldRestartLauncher(threeHoursAgo, now))
    }

    @Test
    fun fourHourRestartIsDue() {
        val now = 10_000_000L
        val fourHoursAgo = now - 4 * Constants.ONE_HOUR_IN_MILLIS
        assertTrue(shouldRestartLauncher(fourHoursAgo, now))
    }
}
