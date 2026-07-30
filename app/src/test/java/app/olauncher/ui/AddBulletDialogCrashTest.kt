package app.olauncher.ui

import android.os.Looper
import app.olauncher.demo.DemoHostActivity
import app.olauncher.helper.CalendarSyncHelper
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Regression: FAB → AddBulletDialog.show() must survive dialog measure/layout.
 * Previously crashed with ClassCastException when content used plain
 * ViewGroup.LayoutParams inside the dialog FrameLayout.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AddBulletDialogCrashTest {

    @Test
    fun show_addDialog_doesNotCrash() {
        val activity = Robolectric.buildActivity(DemoHostActivity::class.java).setup().get()
        AddBulletDialog.show(
            context = activity,
            existingTags = listOf("Work", "Personal"),
            calendars = emptyList(),
            preferredCalendarId = -1L,
            onSave = {},
        )
        // Crash previously happened during measure after show(), not in show() itself.
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun show_addDialog_withCalendars_doesNotCrash() {
        val activity = Robolectric.buildActivity(DemoHostActivity::class.java).setup().get()
        val calendars = listOf(
            CalendarSyncHelper.DeviceCalendar(
                id = 1L,
                displayName = "Personal",
                accountName = "you@gmail.com",
                accountType = "com.google",
                isPrimary = true,
                canWrite = true,
                color = 0xFF0000,
            ),
        )
        AddBulletDialog.show(
            context = activity,
            existingTags = emptyList(),
            calendars = calendars,
            preferredCalendarId = 1L,
            onSave = {},
        )
        shadowOf(Looper.getMainLooper()).idle()
    }
}
