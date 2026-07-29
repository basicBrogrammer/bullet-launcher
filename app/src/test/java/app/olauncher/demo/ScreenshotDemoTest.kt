package app.olauncher.demo

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.os.Looper
import android.os.Process
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import app.olauncher.R
import app.olauncher.data.AppModel
import app.olauncher.data.BulletType
import app.olauncher.data.JournalEntry
import app.olauncher.data.JournalLog
import app.olauncher.helper.setBlackAndWhite
import app.olauncher.ui.AppDrawerAdapter
import app.olauncher.ui.JournalBulletAdapter
import app.olauncher.ui.JournalListItem
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

/**
 * JVM screenshot harness for demoing UI changes without an emulator/device.
 *
 * Runs the real Olauncher layouts through Robolectric with native graphics
 * (real pixel rendering) and writes PNGs to `app/build/demo-screenshots/`.
 * Run with: `./gradlew :app:renderDemoScreens`
 * (or `./gradlew :app:testDebugUnitTest --tests "app.olauncher.demo.*"`).
 *
 * To demo a change, tweak/add a render case below for the affected screen and
 * re-run; attach the produced PNGs (and the stitched MP4) to the PR.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class ScreenshotDemoTest {

    private val outDir = File("build/demo-screenshots").apply { mkdirs() }

    @Test
    fun dailyLog() = capture("01_daily_log", R.layout.page_journal_log) { _, root ->
        root.findViewById<TextView>(R.id.logTitle).setText(R.string.daily_log)
        root.findViewById<TextView>(R.id.logSubtitle).text = "Mon, 27 Jul"
        val recycler = root.findViewById<RecyclerView>(R.id.bulletList)
        recycler.layoutManager = LinearLayoutManager(root.context)
        val adapter = JournalBulletAdapter({}, {})
        recycler.adapter = adapter
        adapter.submit(
            listOf(
                demoEntry("Morning pages", BulletType.TASK, priority = true),
                demoEntry("Team standup · 10:00", BulletType.EVENT),
                demoEntry("Dentist · 15:30", BulletType.EVENT),
                demoEntry("Idea: simplify home gestures", BulletType.NOTE),
                demoEntry("Review monthly goals", BulletType.TASK),
            ).map { JournalListItem.Bullet(it) }
        )
        root.findViewById<View>(R.id.emptyHint).visibility = View.GONE
    }

    @Test
    fun monthlyLog() = capture("02_monthly_log", R.layout.page_journal_log) { _, root ->
        root.findViewById<TextView>(R.id.logTitle).setText(R.string.monthly_log)
        root.findViewById<TextView>(R.id.logSubtitle).text = "July 2026"
        val recycler = root.findViewById<RecyclerView>(R.id.bulletList)
        recycler.layoutManager = LinearLayoutManager(root.context)
        val adapter = JournalBulletAdapter({}, {})
        recycler.adapter = adapter
        adapter.submit(
            listOf(
                JournalListItem.Section(
                    "26 · Sun",
                    listOf(
                        demoEntry("Morning pages", BulletType.TASK, priority = true, day = "2026-07-26"),
                        demoEntry("Team standup", BulletType.EVENT, day = "2026-07-26"),
                    )
                ),
                JournalListItem.Section(
                    "27 · Mon",
                    listOf(
                        demoEntry("Ship journal home", BulletType.TASK, day = "2026-07-27"),
                        demoEntry("Dentist", BulletType.EVENT, day = "2026-07-27"),
                    )
                ),
            )
        )
        root.findViewById<View>(R.id.emptyHint).visibility = View.GONE
    }

    @Test
    fun futureLog() = capture("03_future_log", R.layout.page_journal_log) { _, root ->
        root.findViewById<TextView>(R.id.logTitle).setText(R.string.future_log)
        root.findViewById<TextView>(R.id.logSubtitle).setText(R.string.future_log_subtitle)
        val recycler = root.findViewById<RecyclerView>(R.id.bulletList)
        recycler.layoutManager = LinearLayoutManager(root.context)
        val adapter = JournalBulletAdapter({}, {})
        recycler.adapter = adapter
        adapter.submit(
            listOf(
                JournalListItem.Section(
                    "JUL 2026",
                    listOf(demoEntry("Finish redesign", BulletType.TASK, log = JournalLog.FUTURE, day = "2026-07"))
                ),
                JournalListItem.Section(
                    "AUG 2026",
                    listOf(
                        demoEntry("Vacation planning", BulletType.TASK, priority = true, log = JournalLog.FUTURE, day = "2026-08"),
                        demoEntry("Conference", BulletType.EVENT, log = JournalLog.FUTURE, day = "2026-08"),
                    )
                ),
                JournalListItem.Section(
                    "SEP 2026",
                    listOf(demoEntry("Ship v7", BulletType.TASK, log = JournalLog.FUTURE, day = "2026-09"))
                ),
            )
        )
        root.findViewById<View>(R.id.emptyHint).visibility = View.GONE
    }

    @Test
    fun index() = capture("04_index", R.layout.dialog_index) { _, root ->
        root.findViewById<TextView>(R.id.indexDaily).setText(R.string.index_daily_row)
        root.findViewById<TextView>(R.id.indexMonthly).setText(R.string.index_monthly_row)
        root.findViewById<TextView>(R.id.indexFuture).setText(R.string.index_future_row)
        // Give the dialog a readable backdrop for the screenshot.
        (root as? View)?.setBackgroundColor(Color.WHITE)
    }

    @Test
    fun addBullet() = capture("05_add_bullet", R.layout.dialog_add_bullet) { _, root ->
        root.findViewById<TextView>(R.id.typeTask).alpha = 0.45f
        root.findViewById<TextView>(R.id.typeEvent).alpha = 1f
        root.findViewById<TextView>(R.id.typeNote).alpha = 0.45f
        root.findViewById<TextView>(R.id.calendarSyncHint).visibility = View.VISIBLE
        root.setBackgroundColor(Color.WHITE)
    }

    @Test
    fun editBullet() = capture("05d_edit_bullet", R.layout.dialog_add_bullet) { _, root ->
        root.findViewById<TextView>(R.id.dialogTitle).setText(R.string.edit_entry)
        root.findViewById<EditText>(R.id.entryInput).setText("Team standup")
        root.findViewById<TextView>(R.id.typeTask).alpha = 0.45f
        root.findViewById<TextView>(R.id.typeEvent).alpha = 1f
        root.findViewById<TextView>(R.id.typeNote).alpha = 0.45f
        root.findViewById<TextView>(R.id.calendarSyncHint).visibility = View.VISIBLE
        root.findViewById<TextView>(R.id.deleteButton).visibility = View.VISIBLE
        root.setBackgroundColor(Color.WHITE)
    }

    @Test
    fun addBulletEventSelected() = capture("05b_add_bullet_event", R.layout.dialog_add_bullet) { _, root ->
        root.findViewById<TextView>(R.id.typeTask).alpha = 0.45f
        root.findViewById<TextView>(R.id.typeEvent).alpha = 1f
        root.findViewById<TextView>(R.id.typeNote).alpha = 0.45f
        root.findViewById<EditText>(R.id.entryInput).setText("Team standup")
        root.findViewById<TextView>(R.id.calendarSyncHint).visibility = View.VISIBLE
        root.setBackgroundColor(Color.WHITE)
    }

    @Test
    fun chooseCalendar() = capture("05c_choose_calendar", R.layout.dialog_choose_calendar) { activity, root ->
        val list = root.findViewById<android.widget.LinearLayout>(R.id.calendarList)
        listOf(
            "Personal · you@gmail.com",
            "Work · you@company.com",
            "Family · you@gmail.com",
        ).forEachIndexed { index, label ->
            val row = LayoutInflater.from(activity)
                .inflate(R.layout.item_calendar_choice, list, false) as TextView
            row.text = label
            row.alpha = if (index == 0) 1f else 0.75f
            list.addView(row)
        }
        root.setBackgroundColor(Color.WHITE)
    }

    @Test
    fun homeCollapsed() = capture("06_home_collapsed", R.layout.fragment_home) { activity, root ->
        populateHomeDemo(activity, root, expanded = false)
    }

    @Test
    fun homeExpanded() = capture("06b_home_expanded", R.layout.fragment_home) { activity, root ->
        populateHomeDemo(activity, root, expanded = true)
    }

    @Test
    fun homeWithAppDrawer() = capture("06c_home_app_drawer", R.layout.fragment_home) { activity, root ->
        populateHomeDemo(activity, root, expanded = true)
        val density = activity.resources.displayMetrics.density
        val overlay = root.findViewById<ViewGroup>(R.id.appDrawerOverlay)
        overlay.visibility = View.VISIBLE
        overlay.setPadding(0, 0, 0, (240 * density).toInt())
        val drawer = LayoutInflater.from(activity).inflate(R.layout.fragment_app_drawer, overlay, false)
        overlay.addView(
            drawer,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
        val recycler = drawer.findViewById<RecyclerView>(R.id.recyclerView)
        recycler.layoutManager = LinearLayoutManager(activity)
        val adapter = AppDrawerAdapter(
            flag = app.olauncher.data.Constants.FLAG_LAUNCH_APP,
            appLabelGravity = android.view.Gravity.START,
            appClickListener = {},
            appInfoListener = {},
            appDeleteListener = {},
            appHideListener = { _, _ -> },
            appRenameListener = { _, _ -> },
        )
        recycler.adapter = adapter
        adapter.setAppList(demoAppList())
        root.findViewById<View>(R.id.addBulletButton).visibility = View.GONE
    }

    private fun populateHomeDemo(activity: Activity, root: View, expanded: Boolean, scrim: Boolean = true) {
        root.findViewById<View>(R.id.homeScrim).visibility = if (scrim) View.VISIBLE else View.GONE
        root.findViewById<View>(R.id.dateTimeLayout).visibility = View.VISIBLE
        root.findViewById<TextView>(R.id.clock).apply {
            visibility = View.VISIBLE
            // TextClock may not tick under Robolectric; set text via reflection-friendly content.
            text = "9:41"
        }
        root.findViewById<View>(R.id.date).visibility = View.GONE
        root.findViewById<TextView>(R.id.weather).apply {
            visibility = View.VISIBLE
            text = "72° Clear"
        }
        root.findViewById<View>(R.id.journalPager).visibility = View.GONE

        val journal = LayoutInflater.from(activity).inflate(R.layout.page_journal_log, null)
        val host = root as ViewGroup
        host.addView(
            journal,
            3,
            FrameMatch(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
        val density = activity.resources.displayMetrics.density
        val bottomPad = if (expanded) 260 else 140
        journal.setPadding(
            0,
            (72 * density).toInt(),
            0,
            (bottomPad * density).toInt(),
        )
        journal.findViewById<TextView>(R.id.logTitle).setText(R.string.daily_log)
        journal.findViewById<TextView>(R.id.logSubtitle).text = "Mon, 27 Jul"
        val recycler = journal.findViewById<RecyclerView>(R.id.bulletList)
        recycler.layoutManager = LinearLayoutManager(activity)
        val adapter = JournalBulletAdapter({}, {})
        recycler.adapter = adapter
        adapter.submit(
            listOf(
                demoEntry("Morning pages", BulletType.TASK, priority = true),
                demoEntry("Team standup", BulletType.EVENT),
                demoEntry("Idea: simplify home gestures", BulletType.NOTE),
                demoEntry("Review monthly goals", BulletType.TASK),
            ).map { JournalListItem.Bullet(it) }
        )
        journal.findViewById<View>(R.id.emptyHint).visibility = View.GONE

        val homeApps = listOf(
            R.id.homeApp1 to "Phone",
            R.id.homeApp2 to "Messages",
            R.id.homeApp3 to "Chrome",
            R.id.homeApp4 to "Camera",
            R.id.homeApp5 to "Photos",
            R.id.homeApp6 to "Calendar",
            R.id.homeApp7 to "Maps",
            R.id.homeApp8 to "Gmail",
            R.id.homeApp9 to "Files",
            R.id.homeApp10 to "Clock",
            R.id.homeApp11 to "Settings",
            R.id.homeApp12 to "YouTube",
            R.id.homeApp13 to "Play Store",
            R.id.homeApp14 to "Contacts",
            R.id.homeApp15 to "Calculator",
        )
        val iconPx = (48 * density).toInt()
        val visibleCount = if (expanded) homeApps.size else 5
        homeApps.forEachIndexed { index, (id, label) ->
            root.findViewById<ImageView>(id).apply {
                if (index + 1 == app.olauncher.data.Constants.HOME_DRAWER_SLOT) {
                    setImageDrawable(BitmapDrawable(resources, demoDrawerIconBitmap(iconPx)))
                    setBlackAndWhite(false)
                    contentDescription = "App drawer"
                } else {
                    setImageDrawable(BitmapDrawable(resources, demoIconBitmap(label, iconPx, index)))
                    setBlackAndWhite(true)
                    contentDescription = label
                }
                visibility = if (index < visibleCount) View.VISIBLE else View.GONE
            }
        }
        root.findViewById<View>(R.id.homeAppsBottomSheet).visibility = View.VISIBLE
        root.findViewById<View>(R.id.addBulletButton).visibility = View.VISIBLE
        val fab = root.findViewById<View>(R.id.addBulletButton)
        val fabParams = fab.layoutParams as android.widget.FrameLayout.LayoutParams
        fabParams.bottomMargin = ((if (expanded) 240 else 120) * density).toInt()
        fab.layoutParams = fabParams
    }

    @Test
    fun homeWithScrim() = capture("06d_home_scrim", R.layout.fragment_home) { activity, root ->
        populateHomeDemo(activity, root, expanded = false, scrim = true)
    }

    @Test
    fun settingsCleaned() = capture("08_settings", R.layout.fragment_settings) { _, root ->
        root.findViewById<TextView>(R.id.syncCalendars).text = "2 selected"
        root.findViewById<TextView>(R.id.homeScrim).setText(R.string.on)
        root.findViewById<TextView>(R.id.dateTime).setText(R.string.on)
        root.findViewById<TextView>(R.id.dailyWallpaper).setText(R.string.off)
    }

    @Test
    fun appDrawer() = capture("07_app_drawer", R.layout.fragment_app_drawer) { activity, root ->
        val recycler = root.findViewById<RecyclerView>(R.id.recyclerView)
        recycler.layoutManager = LinearLayoutManager(activity)
        val adapter = AppDrawerAdapter(
            flag = app.olauncher.data.Constants.FLAG_LAUNCH_APP,
            appLabelGravity = android.view.Gravity.START,
            appClickListener = {},
            appInfoListener = {},
            appDeleteListener = {},
            appHideListener = { _, _ -> },
            appRenameListener = { _, _ -> },
        )
        recycler.adapter = adapter
        adapter.setAppList(demoAppList())
    }

    @Test
    @Config(sdk = [34], qualifiers = "w411dp-h891dp-night-xxhdpi")
    fun appDrawerDark() = capture("07b_app_drawer_dark", R.layout.fragment_app_drawer) { activity, root ->
        val recycler = root.findViewById<RecyclerView>(R.id.recyclerView)
        recycler.layoutManager = LinearLayoutManager(activity)
        val adapter = AppDrawerAdapter(
            flag = app.olauncher.data.Constants.FLAG_LAUNCH_APP,
            appLabelGravity = android.view.Gravity.START,
            appClickListener = {},
            appInfoListener = {},
            appDeleteListener = {},
            appHideListener = { _, _ -> },
            appRenameListener = { _, _ -> },
        )
        recycler.adapter = adapter
        adapter.setAppList(demoAppList())
    }

    private fun demoAppList(): MutableList<AppModel> =
        listOf(
            "Calculator", "Calendar", "Camera", "Chrome", "Clock",
            "Contacts", "Files", "Gmail", "Maps", "Messages",
            "Phone", "Photos", "Play Store", "Settings", "YouTube",
        ).map { label ->
            AppModel.App(
                appLabel = label,
                key = null,
                appPackage = "com.example.${label.lowercase().replace(" ", "")}",
                activityClassName = "MainActivity",
                isNew = false,
                user = Process.myUserHandle(),
            ) as AppModel
        }.toMutableList()

    private fun demoEntry(
        text: String,
        type: BulletType,
        priority: Boolean = false,
        completed: Boolean = false,
        log: JournalLog = JournalLog.DAILY,
        day: String = "2026-07-27",
    ) = JournalEntry(
        id = text,
        text = text,
        type = type,
        log = log,
        dateKey = day,
        priority = priority,
        completed = completed,
    )

    /** Synthetic colored icon; ImageView B&W filter turns it monochrome for demos. */
    private fun demoIconBitmap(label: String, size: Int, seed: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            // Distinct hues so grayscale still shows varied tones after desaturation.
            color = Color.HSVToColor(floatArrayOf((seed * 37f) % 360f, 0.55f, 0.75f))
        }
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            textSize = size * 0.42f
            isFakeBoldText = true
        }
        val cx = size / 2f
        val cy = size / 2f
        canvas.drawRoundRect(0f, 0f, size.toFloat(), size.toFloat(), size * 0.22f, size * 0.22f, fill)
        val letter = label.first().uppercaseChar().toString()
        val textY = cy - (textPaint.descent() + textPaint.ascent()) / 2f
        canvas.drawText(letter, cx, textY, textPaint)
        return bitmap
    }

    private fun demoDrawerIconBitmap(size: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK }
        val pad = size * 0.18f
        val gap = size * 0.1f
        val cell = (size - pad * 2 - gap) / 2f
        for (row in 0..1) {
            for (col in 0..1) {
                val left = pad + col * (cell + gap)
                val top = pad + row * (cell + gap)
                canvas.drawRoundRect(left, top, left + cell, top + cell, cell * 0.2f, cell * 0.2f, paint)
            }
        }
        return bitmap
    }

    private class FrameMatch(width: Int, height: Int) : ViewGroup.LayoutParams(width, height)

    private fun capture(name: String, layoutRes: Int, populate: (Activity, View) -> Unit) {
        val controller = Robolectric.buildActivity(DemoHostActivity::class.java).setup()
        val activity = controller.get()
        val root = LayoutInflater.from(activity).inflate(layoutRes, null)
        activity.setContentView(
            root,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
        controller.visible()
        populate(activity, root)
        shadowOf(Looper.getMainLooper()).idle()

        val metrics = activity.resources.displayMetrics
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val decor = activity.window.decorView
        decor.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
        )
        decor.layout(0, 0, width, height)
        shadowOf(Looper.getMainLooper()).idle()

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        // Launcher window is wallpaper-backed; use a theme-appropriate backdrop.
        val night = activity.resources.configuration.uiMode and
            android.content.res.Configuration.UI_MODE_NIGHT_MASK ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        canvas.drawColor(if (night) Color.parseColor("#121212") else Color.WHITE)
        decor.draw(canvas)

        val file = File(outDir, "$name.png")
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        println("Wrote demo screenshot: ${file.absolutePath} (${width}x$height)")
    }
}
