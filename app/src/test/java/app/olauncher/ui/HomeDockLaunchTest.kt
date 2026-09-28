package app.olauncher.ui

import android.os.Looper
import android.view.View
import androidx.test.core.app.ApplicationProvider
import app.olauncher.MainActivity
import app.olauncher.R
import app.olauncher.data.Prefs
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
class HomeDockLaunchTest {

    @Test
    fun tapApp_onExpandedSheet_collapsesSheet() {
        val activity = openExpandedHome()
        val prefs = Prefs(activity)
        prefs.setAppName(1, "Phone")
        prefs.setAppPackage(1, "com.example.phone")
        prefs.setAppActivityClassName(1, "")
        prefs.setIsShortcut(1, false)
        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.homeApp6).visibility)

        activity.findViewById<View>(R.id.homeApp1).performClick()
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(View.GONE, activity.findViewById<View>(R.id.homeApp6).visibility)
        assertFalse(prefs.homeAppsSheetExpanded)
        assertEquals(View.GONE, activity.findViewById<View>(R.id.appDrawerOverlay).visibility)
    }

    @Test
    fun tapDrawerIcon_onExpandedSheet_opensDrawerAndCollapsesSheet() {
        val activity = openExpandedHome()
        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.homeApp6).visibility)

        activity.findViewById<View>(R.id.homeApp13).performClick()

        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.appDrawerOverlay).visibility)
        assertEquals(View.GONE, activity.findViewById<View>(R.id.homeApp6).visibility)
        assertFalse(Prefs(activity).homeAppsSheetExpanded)

        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(View.GONE, activity.findViewById<View>(R.id.homeApp6).visibility)
        assertFalse(Prefs(activity).homeAppsSheetExpanded)
    }

    @Test
    fun tapEmptySlot_onExpandedSheet_leavesSheetOpen() {
        val activity = openExpandedHome()
        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.homeApp6).visibility)

        activity.findViewById<View>(R.id.homeApp2).performClick()
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.homeApp6).visibility)
        assertTrue(Prefs(activity).homeAppsSheetExpanded)
        assertEquals(View.GONE, activity.findViewById<View>(R.id.appDrawerOverlay).visibility)
    }

    private fun openExpandedHome(): MainActivity {
        val prefs = Prefs(ApplicationProvider.getApplicationContext())
        prefs.firstOpen = false
        prefs.homeAppsNum = 15
        prefs.homeAppsSheetExpanded = true
        return Robolectric.buildActivity(MainActivity::class.java).setup().get()
    }
}
