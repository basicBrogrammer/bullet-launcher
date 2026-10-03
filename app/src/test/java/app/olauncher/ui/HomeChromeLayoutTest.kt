package app.olauncher.ui

import android.os.Looper
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import app.olauncher.MainActivity
import app.olauncher.R
import app.olauncher.data.Prefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HomeChromeLayoutTest {

    @Test
    fun collapsingSheet_keepsFabJustAboveSheet() {
        val activity = openHome(expanded = true)
        shadowOf(Looper.getMainLooper()).idle()

        val sheet = activity.findViewById<View>(R.id.homeAppsBottomSheet)
        val fab = activity.findViewById<View>(R.id.addBulletButton)
        val expandedSheetHeight = sheet.height
        val expandedFabBottom = fab.bottom
        assertTrue("expanded sheet should lay out", expandedSheetHeight > 0)

        val prefs = Prefs(activity)
        prefs.setAppName(1, "Phone")
        prefs.setAppPackage(1, "com.example.phone")
        prefs.setAppActivityClassName(1, "")
        prefs.setIsShortcut(1, false)
        activity.findViewById<View>(R.id.homeApp1).performClick()
        shadowOf(Looper.getMainLooper()).idle()

        val collapsedSheetHeight = sheet.height
        assertTrue(
            "sheet should shrink when rows 2-3 hide: expanded=$expandedSheetHeight collapsed=$collapsedSheetHeight",
            collapsedSheetHeight < expandedSheetHeight,
        )
        assertFabRidesSheet(fab, sheet)
        assertTrue(
            "FAB should move down with the sheet: expandedFabBottom=$expandedFabBottom collapsedFabBottom=${fab.bottom}",
            fab.bottom > expandedFabBottom,
        )
    }

    @Test
    fun openingAppDrawer_extendsListToCollapsedDock() {
        val activity = openHome(expanded = true)
        shadowOf(Looper.getMainLooper()).idle()
        val expandedSheetHeight = activity.findViewById<View>(R.id.homeAppsBottomSheet).height

        activity.findViewById<View>(R.id.homeApp13).performClick()
        val overlay = activity.findViewById<View>(R.id.appDrawerOverlay)
        assertEquals(View.VISIBLE, overlay.visibility)
        shadowOf(Looper.getMainLooper()).idle()

        val sheet = activity.findViewById<View>(R.id.homeAppsBottomSheet)
        val fab = activity.findViewById<View>(R.id.addBulletButton)
        assertTrue(
            "dock should collapse under the drawer: expanded=$expandedSheetHeight collapsed=${sheet.height}",
            sheet.height < expandedSheetHeight,
        )
        assertEquals(
            "drawer list should end at the collapsed dock, not the expanded-sheet / keyboard line",
            sheet.height,
            overlay.paddingBottom,
        )
        assertFabRidesSheet(fab, sheet)
        val list = overlay.findViewById<RecyclerView>(R.id.recyclerView)
        if (overlay.visibility == View.VISIBLE && list != null && list.bottom > list.top) {
            val overlayContentBottom = overlay.height - overlay.paddingBottom
            assertTrue(
                "drawer list should fill the overlay down to the dock: list.bottom=${list.bottom} contentBottom=$overlayContentBottom",
                overlayContentBottom - list.bottom <= 32,
            )
        }
    }

    private fun assertFabRidesSheet(fab: View, sheet: View) {
        val gap = sheet.top - fab.bottom
        assertTrue(
            "FAB should sit just above the sheet: fab.bottom=${fab.bottom} sheet.top=${sheet.top} gap=$gap",
            gap in 0..48,
        )
    }

    private fun openHome(expanded: Boolean): MainActivity {
        val prefs = Prefs(ApplicationProvider.getApplicationContext())
        prefs.firstOpen = false
        prefs.homeAppsNum = 15
        prefs.homeAppsSheetExpanded = expanded
        return Robolectric.buildActivity(MainActivity::class.java).setup().get()
    }
}
