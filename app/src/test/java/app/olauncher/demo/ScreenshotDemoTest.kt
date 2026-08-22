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
                demoEntry("Morning pages", BulletType.TASK, priority = true, tags = listOf("Personal")),
                demoEntry("Team standup", BulletType.EVENT, timeMinutes = 10 * 60),
                demoEntry("Tip: tap a task to complete · long-press to edit", BulletType.NOTE),
                demoEntry("Idea: swipe between Monthly · Daily · Future", BulletType.NOTE),
                demoEntry("Review monthly goals", BulletType.TASK, tags = listOf("Work")),
                demoEntry("Dentist", BulletType.EVENT, timeMinutes = 15 * 60 + 30),
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
        // Earlier days fill the viewport so scrolling to today (30) is visible —
        // matches the default "scroll to today" landing position.
        val dayLabel = java.text.SimpleDateFormat("d · EEE", java.util.Locale.US)
        val earlier = (1..25).map { day ->
            val cal = java.util.Calendar.getInstance().apply {
                set(2026, java.util.Calendar.JULY, day)
            }
            JournalListItem.Section(
                dayLabel.format(cal.time),
                listOf(demoEntry("Day $day note", BulletType.NOTE, day = "2026-07-${day.toString().padStart(2, '0')}")),
            )
        }
        val sections = earlier + listOf(
            JournalListItem.Section(
                "26 · Sun",
                listOf(
                    demoEntry("Morning pages", BulletType.TASK, priority = true, day = "2026-07-26"),
                    demoEntry("Team standup", BulletType.EVENT, day = "2026-07-26", timeMinutes = 10 * 60),
                )
            ),
            JournalListItem.Section(
                "27 · Mon",
                listOf(
                    demoEntry("Ship journal home", BulletType.TASK, day = "2026-07-27"),
                    demoEntry("Dentist", BulletType.EVENT, day = "2026-07-27", timeMinutes = 15 * 60 + 30),
                )
            ),
            JournalListItem.Section(
                "28 · Tue",
                listOf(demoEntry("Write weekly review", BulletType.TASK, day = "2026-07-28"))
            ),
            JournalListItem.Section(
                "29 · Wed",
                listOf(
                    demoEntry("Grocery run", BulletType.TASK, day = "2026-07-29"),
                    demoEntry("Idea: denser monthly log", BulletType.NOTE, day = "2026-07-29"),
                )
            ),
            JournalListItem.Section(
                "30 · Thu",
                listOf(
                    demoEntry("Morning pages", BulletType.TASK, priority = true, day = "2026-07-30"),
                    demoEntry("Team standup", BulletType.EVENT, day = "2026-07-30", timeMinutes = 10 * 60),
                    demoEntry("Pin monthly log to today", BulletType.TASK, day = "2026-07-30"),
                )
            ),
        )
        adapter.submit(sections)
        root.findViewById<View>(R.id.emptyHint).visibility = View.GONE
        // Harness measures after populate; size the list, add bottom pad so the
        // last (today) section can sit at the top — same trick as production.
        val metrics = root.resources.displayMetrics
        fun layoutRoot() {
            root.measure(
                View.MeasureSpec.makeMeasureSpec(metrics.widthPixels, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(metrics.heightPixels, View.MeasureSpec.EXACTLY),
            )
            root.layout(0, 0, metrics.widthPixels, metrics.heightPixels)
        }
        layoutRoot()
        val bottomPad = (recycler.height - recycler.paddingTop).coerceAtLeast(recycler.paddingBottom)
        recycler.setPadding(recycler.paddingLeft, recycler.paddingTop, recycler.paddingRight, bottomPad)
        (recycler.layoutManager as LinearLayoutManager)
            .scrollToPositionWithOffset(sections.lastIndex, 0)
        layoutRoot()
    }

    @Test
    fun dailyLogTwelveHour() = capture("01b_daily_log_12h", R.layout.page_journal_log) { _, root ->
        root.findViewById<TextView>(R.id.logTitle).setText(R.string.daily_log)
        root.findViewById<TextView>(R.id.logSubtitle).text = "Mon, 27 Jul"
        val recycler = root.findViewById<RecyclerView>(R.id.bulletList)
        recycler.layoutManager = LinearLayoutManager(root.context)
        val adapter = JournalBulletAdapter({}, {}).also { it.militaryTime = false }
        recycler.adapter = adapter
        adapter.submit(
            listOf(
                demoEntry("Morning pages", BulletType.TASK, priority = true, tags = listOf("Personal")),
                demoEntry("Team standup", BulletType.EVENT, timeMinutes = 10 * 60),
                demoEntry("Dentist", BulletType.EVENT, timeMinutes = 15 * 60 + 30),
                demoEntry("Review monthly goals", BulletType.TASK, tags = listOf("Work")),
            ).map { JournalListItem.Bullet(it) }
        )
        root.findViewById<View>(R.id.emptyHint).visibility = View.GONE
    }

    @Test
    fun settingsTimeFormat() = capture("09_settings_time_format", R.layout.fragment_settings) { _, root ->
        root.findViewById<TextView>(R.id.syncCalendars).text = "All calendars"
        root.findViewById<TextView>(R.id.homeScrim).setText(R.string.off)
        root.findViewById<TextView>(R.id.dateTime).setText(R.string.on)
        root.findViewById<TextView>(R.id.journalTimeFormat).setText(R.string.time_format_12h)
        root.findViewById<TextView>(R.id.dailyWallpaper).setText(R.string.off)
        root.findViewById<TextView>(R.id.statusBar)?.setText(R.string.off)
        // Open the picker overlay so both 24-hour / 12-hour choices are visible.
        root.findViewById<View>(R.id.journalTimeFormatSelectLayout).visibility = View.VISIBLE
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
    fun index() = capture("04_index", R.layout.dialog_index) { activity, root ->
        val list = root.findViewById<android.widget.LinearLayout>(R.id.indexList)
        listOf(
            activity.getString(R.string.index_overdue_row),
            activity.getString(R.string.index_unscheduled_row),
            activity.getString(R.string.index_tag_row, 3, "Personal"),
            activity.getString(R.string.index_tag_row, 4, "Work"),
        ).forEach { label ->
            val row = TextView(activity).apply {
                setTextAppearance(activity, R.style.TextMedium)
                text = label
                setPadding(0, 24, 0, 24)
            }
            list.addView(row)
        }
        root.setBackgroundColor(Color.WHITE)
    }

    @Test
    fun overdue() = capture("04a_overdue", R.layout.page_journal_log) { _, root ->
        root.findViewById<TextView>(R.id.logTitle).setText(R.string.overdue_log)
        root.findViewById<TextView>(R.id.logSubtitle).setText(R.string.overdue_log_subtitle)
        val recycler = root.findViewById<RecyclerView>(R.id.bulletList)
        recycler.layoutManager = LinearLayoutManager(root.context)
        val adapter = JournalBulletAdapter({}, {})
        recycler.adapter = adapter
        adapter.submit(
            listOf(
                demoEntry("Grocery run", BulletType.TASK, log = JournalLog.MONTHLY, day = "2026-07-30", tags = listOf("Personal")),
                demoEntry("Call landlord", BulletType.TASK, priority = true, log = JournalLog.DAILY, day = "2026-07-29", tags = listOf("Personal")),
            ).map { JournalListItem.Bullet(it) }
        )
        root.findViewById<View>(R.id.emptyHint).visibility = View.GONE
    }

    @Test
    fun unscheduled() = capture("04b_unscheduled", R.layout.page_journal_log) { _, root ->
        root.findViewById<TextView>(R.id.logTitle).setText(R.string.unscheduled_log)
        root.findViewById<TextView>(R.id.logSubtitle).setText(R.string.unscheduled_log_subtitle)
        val recycler = root.findViewById<RecyclerView>(R.id.bulletList)
        recycler.layoutManager = LinearLayoutManager(root.context)
        val adapter = JournalBulletAdapter({}, {})
        recycler.adapter = adapter
        adapter.submit(
            listOf(
                demoEntry("Inbox triage", BulletType.TASK, tags = listOf("Work")),
                demoEntry("Read design notes", BulletType.TASK, priority = true, tags = listOf("Personal")),
            ).map { JournalListItem.Bullet(it) }
        )
        root.findViewById<View>(R.id.emptyHint).visibility = View.GONE
    }

    @Test
    fun tagCollection() = capture("04c_tag_work", R.layout.page_journal_log) { _, root ->
        root.findViewById<TextView>(R.id.logTitle).text = "Work"
        root.findViewById<TextView>(R.id.logSubtitle).setText(R.string.tag_log_subtitle)
        val recycler = root.findViewById<RecyclerView>(R.id.bulletList)
        recycler.layoutManager = LinearLayoutManager(root.context)
        val adapter = JournalBulletAdapter({}, {})
        recycler.adapter = adapter
        adapter.submit(
            listOf(
                demoEntry("Review monthly goals", BulletType.TASK, tags = listOf("Work")),
                demoEntry("Inbox triage", BulletType.TASK, tags = listOf("Work")),
                demoEntry("Ship v7", BulletType.TASK, log = JournalLog.FUTURE, day = "2026-09", tags = listOf("Work")),
            ).map { JournalListItem.Bullet(it) }
        )
        root.findViewById<View>(R.id.emptyHint).visibility = View.GONE
    }

    @Test
    fun addBullet() = capture("05_add_bullet", R.layout.dialog_add_bullet) { activity, root ->
        root.findViewById<TextView>(R.id.typeTask).alpha = 1f
        root.findViewById<TextView>(R.id.typeEvent).alpha = 0.45f
        root.findViewById<TextView>(R.id.typeNote).alpha = 0.45f
        root.findViewById<View>(R.id.tagsSection).visibility = View.VISIBLE
        root.findViewById<View>(R.id.calendarSection).visibility = View.GONE
        root.findViewById<EditText>(R.id.entryInput).setText("Morning pages")
        root.findViewById<TextView>(R.id.scheduleValue).text = "Wed, 30 Jul · 9:00 AM"
        root.findViewById<TextView>(R.id.scheduleClear).visibility = View.VISIBLE
        val tags = root.findViewById<android.widget.LinearLayout>(R.id.tagChipGroup)
        listOf("#Personal", "#Work").forEachIndexed { index, label ->
            tags.addView(
                TextView(activity).apply {
                    setTextAppearance(activity, R.style.TextMedium)
                    text = label
                    alpha = if (index == 0) 1f else 0.45f
                    setPadding(0, 20, 0, 20)
                }
            )
        }
        root.setBackgroundColor(Color.WHITE)
    }

    @Test
    fun askJournal() = capture("05e_ask_journal", R.layout.dialog_ask_journal) { _, root ->
        root.findViewById<EditText>(R.id.askInput).setText("add buy milk tomorrow")
        root.findViewById<TextView>(R.id.engineStatus).setText(R.string.ask_journal_engine_nano)
        root.findViewById<TextView>(R.id.askResult).apply {
            visibility = View.VISIBLE
            text = "Added task · Buy milk"
        }
        root.findViewById<View>(R.id.downloadButton).visibility = View.GONE
        root.setBackgroundColor(Color.WHITE)
    }

    @Test
    fun editBullet() = capture("05d_edit_bullet", R.layout.dialog_add_bullet) { _, root ->
        root.findViewById<TextView>(R.id.dialogTitle).setText(R.string.edit_entry)
        root.findViewById<EditText>(R.id.entryInput).setText("Team standup")
        root.findViewById<TextView>(R.id.typeTask).alpha = 0.45f
        root.findViewById<TextView>(R.id.typeEvent).alpha = 1f
        root.findViewById<TextView>(R.id.typeNote).alpha = 0.45f
        root.findViewById<View>(R.id.tagsSection).visibility = View.GONE
        root.findViewById<View>(R.id.calendarSection).visibility = View.VISIBLE
        root.findViewById<TextView>(R.id.scheduleValue).text = "Wed, 30 Jul · 10:30 AM"
        root.findViewById<TextView>(R.id.scheduleClear).visibility = View.VISIBLE
        root.findViewById<TextView>(R.id.deleteButton).visibility = View.VISIBLE
        root.setBackgroundColor(Color.WHITE)
    }

    @Test
    fun addBulletEventSelected() = capture("05b_add_bullet_event", R.layout.dialog_add_bullet) { activity, root ->
        root.findViewById<TextView>(R.id.typeTask).alpha = 0.45f
        root.findViewById<TextView>(R.id.typeEvent).alpha = 1f
        root.findViewById<TextView>(R.id.typeNote).alpha = 0.45f
        root.findViewById<EditText>(R.id.entryInput).setText("Team standup")
        root.findViewById<TextView>(R.id.scheduleValue).text = "Wed, 30 Jul · 10:30 AM"
        root.findViewById<TextView>(R.id.scheduleClear).visibility = View.VISIBLE
        root.findViewById<View>(R.id.tagsSection).visibility = View.GONE
        root.findViewById<View>(R.id.calendarSection).visibility = View.VISIBLE
        val spinner = root.findViewById<android.widget.Spinner>(R.id.calendarSpinner)
        spinner.adapter = android.widget.ArrayAdapter(
            activity,
            android.R.layout.simple_spinner_dropdown_item,
            listOf("Personal · you@gmail.com", "Work · you@company.com"),
        )
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
        root.findViewById<View>(R.id.journalActionButtons).visibility = View.GONE
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
            (92 * density).toInt(),
            0,
            (bottomPad * density).toInt(),
        )
        journal.findViewById<TextView>(R.id.logTitle).setText(R.string.daily_log)
        journal.findViewById<TextView>(R.id.logSubtitle).text = "Thu, 30 Jul"
        val recycler = journal.findViewById<RecyclerView>(R.id.bulletList)
        recycler.layoutManager = LinearLayoutManager(activity)
        val adapter = JournalBulletAdapter({}, {})
        recycler.adapter = adapter
        adapter.submit(
            listOf(
                demoEntry("Morning pages", BulletType.TASK, priority = true, tags = listOf("Personal")),
                demoEntry("Team standup", BulletType.EVENT, timeMinutes = 10 * 60),
                demoEntry("Tip: tap a task to complete · long-press to edit", BulletType.NOTE),
                demoEntry("Review monthly goals", BulletType.TASK, tags = listOf("Work")),
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
        root.findViewById<View>(R.id.journalActionButtons).visibility = View.VISIBLE
        val actions = root.findViewById<View>(R.id.journalActionButtons)
        val actionParams = actions.layoutParams as android.widget.FrameLayout.LayoutParams
        actionParams.bottomMargin = ((if (expanded) 240 else 120) * density).toInt()
        actions.layoutParams = actionParams
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
        root.findViewById<TextView>(R.id.journalTimeFormat).setText(R.string.time_format_24h)
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
        tags: List<String> = emptyList(),
        timeMinutes: Int? = null,
    ) = JournalEntry(
        id = text,
        text = text,
        type = type,
        log = log,
        dateKey = day,
        priority = priority,
        completed = completed,
        tags = tags,
        timeMinutes = timeMinutes,
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
