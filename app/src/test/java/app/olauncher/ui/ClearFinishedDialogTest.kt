package app.olauncher.ui

import android.os.Looper
import android.view.View
import android.widget.TextView
import app.olauncher.R
import app.olauncher.demo.DemoHostActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ClearFinishedDialogTest {

    @Test
    fun confirmDeletesAndDismisses() {
        val activity = Robolectric.buildActivity(DemoHostActivity::class.java).setup().get()
        var confirmed = false
        val dialog = ClearFinishedDialog.show(activity, 2) { confirmed = true }
        shadowOf(Looper.getMainLooper()).idle()

        val message = dialog.findViewById<TextView>(R.id.clearFinishedMessage).text.toString()
        assertEquals(
            activity.resources.getQuantityString(R.plurals.clear_finished_message, 2, 2),
            message,
        )
        dialog.findViewById<View>(R.id.confirmButton).performClick()
        shadowOf(Looper.getMainLooper()).idle()

        assertTrue(confirmed)
        assertFalse(dialog.isShowing)
    }

    @Test
    fun cancelDoesNotDelete() {
        val activity = Robolectric.buildActivity(DemoHostActivity::class.java).setup().get()
        var confirmed = false
        val dialog = ClearFinishedDialog.show(activity, 3) { confirmed = true }
        shadowOf(Looper.getMainLooper()).idle()

        dialog.findViewById<View>(R.id.cancelButton).performClick()
        shadowOf(Looper.getMainLooper()).idle()

        assertFalse(confirmed)
        assertFalse(dialog.isShowing)
    }

    @Test
    fun noneOffersOnlyDismiss() {
        val activity = Robolectric.buildActivity(DemoHostActivity::class.java).setup().get()
        var confirmed = false
        val dialog = ClearFinishedDialog.show(activity, 0) { confirmed = true }
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(View.GONE, dialog.findViewById<View>(R.id.confirmButton).visibility)
        assertEquals(
            activity.getString(R.string.clear_finished_none),
            dialog.findViewById<TextView>(R.id.clearFinishedMessage).text.toString(),
        )
        dialog.findViewById<View>(R.id.cancelButton).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse(confirmed)
    }
}
