package app.olauncher.demo

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
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
import app.olauncher.data.Constants
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
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Guided product walkthrough for the README: renders seeded journal screens
 * with caption banners, writes PNGs under `build/demo-screenshots/walkthrough/`.
 *
 * Run via `./gradlew :app:renderDemoScreens`, then stitch with ffmpeg.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class GuidedWalkthroughDemoTest {

    private val outDir = File("build/demo-screenshots/walkthrough").apply { mkdirs() }

    private val today = "2026-07-30"
    private val todayLabel = "Thu, 30 Jul"
    private val monthLabel = "July 2026"

    @Test
    fun wt01_dailyLog() = capture(
        "01_daily_log",
        R.layout.fragment_home,
        "1 · Daily Log",
        "Your home screen is a bullet journal. Tasks • Events ○ Notes –",
    ) { activity, root ->
        populateHome(activity, root, expanded = false, bullets = dailyBullets())
    }

    @Test
    fun wt02_rapidLogging() = capture(
        "02_rapid_logging",
        R.layout.page_journal_log,
        "2 · Rapid logging",
        "Tap a task to complete it · long-press any bullet to edit or delete",
    ) { _, root ->
        root.findViewById<TextView>(R.id.logTitle).setText(R.string.daily_log)
        root.findViewById<TextView>(R.id.logSubtitle).text = todayLabel
        bindBullets(root, dailyBullets().map { JournalListItem.Bullet(it) })
    }

    @Test
    fun wt03_addBullet() = capture(
        "03_add_bullet",
        R.layout.dialog_add_bullet,
        "3 · Add a bullet",
        "FAB + adds a task, event, or note — pick a type, schedule, and tags",
    ) { activity, root ->
        root.findViewById<TextView>(R.id.typeTask).alpha = 1f
        root.findViewById<TextView>(R.id.typeEvent).alpha = 0.45f
        root.findViewById<TextView>(R.id.typeNote).alpha = 0.45f
        root.findViewById<View>(R.id.tagsSection).visibility = View.VISIBLE
        root.findViewById<View>(R.id.calendarSection).visibility = View.GONE
        root.findViewById<EditText>(R.id.entryInput).setText("Morning pages")
        root.findViewById<TextView>(R.id.scheduleValue).text = "Thu, 30 Jul · 9:00 AM"
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
    fun wt04_eventCalendar() = capture(
        "04_event_calendar",
        R.layout.dialog_add_bullet,
        "4 · Calendar sync",
        "Event bullets two-way sync with your phone / Google Calendar",
    ) { activity, root ->
        root.findViewById<TextView>(R.id.typeTask).alpha = 0.45f
        root.findViewById<TextView>(R.id.typeEvent).alpha = 1f
        root.findViewById<TextView>(R.id.typeNote).alpha = 0.45f
        root.findViewById<EditText>(R.id.entryInput).setText("Team standup")
        root.findViewById<TextView>(R.id.scheduleValue).text = "Thu, 30 Jul · 10:00 AM"
        root.findViewById<TextView>(R.id.scheduleClear).visibility = View.VISIBLE
        root.findViewById<View>(R.id.tagsSection).visibility = View.GONE
        root.findViewById<View>(R.id.calendarSection).visibility = View.VISIBLE
        root.findViewById<android.widget.Spinner>(R.id.calendarSpinner).adapter =
            android.widget.ArrayAdapter(
                activity,
                android.R.layout.simple_spinner_dropdown_item,
                listOf("Personal · you@gmail.com", "Work · you@company.com"),
            )
        root.setBackgroundColor(Color.WHITE)
    }

    @Test
    fun wt05_monthlyLog() = capture(
        "05_monthly_log",
        R.layout.page_journal_log,
        "5 · Monthly Log",
        "Swipe right for the month — days group under headers; lands on today",
    ) { _, root ->
        root.findViewById<TextView>(R.id.logTitle).setText(R.string.monthly_log)
        root.findViewById<TextView>(R.id.logSubtitle).text = monthLabel
        val dayLabel = SimpleDateFormat("d · EEE", Locale.US)
        val earlier = (24..29).map { day ->
            val cal = Calendar.getInstance().apply { set(2026, Calendar.JULY, day) }
            JournalListItem.Section(
                dayLabel.format(cal.time),
                listOf(
                    entry(
                        when (day) {
                            24 -> "Inbox triage"
                            25 -> "Grocery run"
                            26 -> "Morning pages"
                            27 -> "Ship journal home"
                            28 -> "Write weekly review"
                            else -> "Idea: denser monthly log"
                        },
                        when (day) {
                            26, 27 -> BulletType.TASK
                            28 -> BulletType.NOTE
                            29 -> BulletType.NOTE
                            else -> BulletType.TASK
                        },
                        day = "2026-07-${day.toString().padStart(2, '0')}",
                        priority = day == 26,
                    )
                ),
            )
        }
        val todaySection = JournalListItem.Section(
            "30 · Thu",
            dailyBullets(),
        )
        val sections = earlier + todaySection
        bindBullets(root, sections)
        val metrics = root.resources.displayMetrics
        fun layoutRoot() {
            root.measure(
                View.MeasureSpec.makeMeasureSpec(metrics.widthPixels, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(metrics.heightPixels, View.MeasureSpec.EXACTLY),
            )
            root.layout(0, 0, metrics.widthPixels, metrics.heightPixels)
        }
        layoutRoot()
        val recycler = root.findViewById<RecyclerView>(R.id.bulletList)
        val bottomPad = (recycler.height - recycler.paddingTop).coerceAtLeast(recycler.paddingBottom)
        recycler.setPadding(recycler.paddingLeft, recycler.paddingTop, recycler.paddingRight, bottomPad)
        (recycler.layoutManager as LinearLayoutManager)
            .scrollToPositionWithOffset(sections.lastIndex, 0)
        layoutRoot()
    }

    @Test
    fun wt06_futureLog() = capture(
        "06_future_log",
        R.layout.page_journal_log,
        "6 · Future Log",
        "Swipe left to plan ahead — months hold tasks and events still far out",
    ) { _, root ->
        root.findViewById<TextView>(R.id.logTitle).setText(R.string.future_log)
        root.findViewById<TextView>(R.id.logSubtitle).setText(R.string.future_log_subtitle)
        bindBullets(
            root,
            listOf(
                JournalListItem.Section(
                    "AUG 2026",
                    listOf(
                        entry("Vacation planning", BulletType.TASK, priority = true, log = JournalLog.FUTURE, day = "2026-08"),
                        entry("Conference", BulletType.EVENT, log = JournalLog.FUTURE, day = "2026-08"),
                        entry("Tip: swipe up for apps · drawer for all apps", BulletType.NOTE, log = JournalLog.FUTURE, day = "2026-08"),
                    ),
                ),
                JournalListItem.Section(
                    "SEP 2026",
                    listOf(entry("Ship v7", BulletType.TASK, log = JournalLog.FUTURE, day = "2026-09", tags = listOf("Work"))),
                ),
            ),
        )
    }

    @Test
    fun wt07_index() = capture(
        "07_index",
        R.layout.dialog_index,
        "7 · Index",
        "Collections: Unscheduled inbox plus tags like Personal and Work",
    ) { activity, root ->
        val list = root.findViewById<android.widget.LinearLayout>(R.id.indexList)
        listOf(
            activity.getString(R.string.index_unscheduled_row),
            activity.getString(R.string.index_tag_row, 2, "Personal"),
            activity.getString(R.string.index_tag_row, 3, "Work"),
        ).forEach { label ->
            list.addView(
                TextView(activity).apply {
                    setTextAppearance(activity, R.style.TextMedium)
                    text = label
                    setPadding(0, 24, 0, 24)
                }
            )
        }
        root.setBackgroundColor(Color.WHITE)
    }

    @Test
    fun wt08_homeApps() = capture(
        "08_home_apps",
        R.layout.fragment_home,
        "8 · Home apps",
        "Swipe up to expand the dock (5×3). Slot 13 opens the full app drawer",
    ) { activity, root ->
        populateHome(activity, root, expanded = true, bullets = dailyBullets())
    }

    @Test
    fun wt09_appDrawer() = capture(
        "09_app_drawer",
        R.layout.fragment_home,
        "9 · App drawer",
        "Tap the drawer button — overlay stays above home so dock apps stay droppable",
    ) { activity, root ->
        populateHome(activity, root, expanded = true, bullets = dailyBullets())
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
            flag = Constants.FLAG_LAUNCH_APP,
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

    @Test
    fun wt10_gestures() = capture(
        "10_gestures",
        R.layout.fragment_home,
        "10 · Gestures",
        "Swipe down closes drawer/sheet · Back closes drawer · Home returns to Daily",
    ) { activity, root ->
        populateHome(activity, root, expanded = false, bullets = dailyBullets())
    }

    private fun dailyBullets(): List<JournalEntry> = listOf(
        entry("Morning pages", BulletType.TASK, priority = true, tags = listOf("Personal")),
        entry("Team standup", BulletType.EVENT, timeMinutes = 10 * 60),
        entry("Tip: tap a task to complete · long-press to edit", BulletType.NOTE),
        entry("Idea: swipe between Monthly · Daily · Future", BulletType.NOTE),
        entry("Review monthly goals", BulletType.TASK, tags = listOf("Work")),
        entry("Dentist", BulletType.EVENT, timeMinutes = 15 * 60 + 30),
    )

    private fun bindBullets(root: View, items: List<JournalListItem>) {
        val recycler = root.findViewById<RecyclerView>(R.id.bulletList)
        recycler.layoutManager = LinearLayoutManager(root.context)
        val adapter = JournalBulletAdapter({}, {})
        recycler.adapter = adapter
        adapter.submit(items)
        root.findViewById<View>(R.id.emptyHint).visibility = View.GONE
    }

    private fun populateHome(
        activity: Activity,
        root: View,
        expanded: Boolean,
        bullets: List<JournalEntry>,
    ) {
        root.findViewById<View>(R.id.homeScrim).visibility = View.VISIBLE
        root.findViewById<View>(R.id.dateTimeLayout).visibility = View.VISIBLE
        root.findViewById<TextView>(R.id.clock).apply {
            visibility = View.VISIBLE
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
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
        val density = activity.resources.displayMetrics.density
        val bottomPad = if (expanded) 260 else 140
        journal.setPadding(0, (92 * density).toInt(), 0, (bottomPad * density).toInt())
        journal.findViewById<TextView>(R.id.logTitle).setText(R.string.daily_log)
        journal.findViewById<TextView>(R.id.logSubtitle).text = todayLabel
        bindBullets(journal, bullets.map { JournalListItem.Bullet(it) })

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
                if (index + 1 == Constants.HOME_DRAWER_SLOT) {
                    setImageDrawable(BitmapDrawable(resources, drawerIcon(iconPx)))
                    setBlackAndWhite(false)
                    contentDescription = "App drawer"
                } else {
                    setImageDrawable(BitmapDrawable(resources, appIcon(label, iconPx, index)))
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

    private fun entry(
        text: String,
        type: BulletType,
        priority: Boolean = false,
        log: JournalLog = JournalLog.DAILY,
        day: String = today,
        tags: List<String> = emptyList(),
        timeMinutes: Int? = null,
    ) = JournalEntry(
        id = text,
        text = text,
        type = type,
        log = log,
        dateKey = day,
        priority = priority,
        tags = tags,
        timeMinutes = timeMinutes,
    )

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

    private fun appIcon(label: String, size: Int, seed: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.HSVToColor(floatArrayOf((seed * 37f) % 360f, 0.55f, 0.75f))
        }
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            textSize = size * 0.42f
            isFakeBoldText = true
        }
        canvas.drawRoundRect(0f, 0f, size.toFloat(), size.toFloat(), size * 0.22f, size * 0.22f, fill)
        val cy = size / 2f
        val textY = cy - (textPaint.descent() + textPaint.ascent()) / 2f
        canvas.drawText(label.first().uppercaseChar().toString(), size / 2f, textY, textPaint)
        return bitmap
    }

    private fun drawerIcon(size: Int): Bitmap {
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

    private fun capture(
        name: String,
        layoutRes: Int,
        title: String,
        subtitle: String,
        populate: (Activity, View) -> Unit,
    ) {
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
        canvas.drawColor(Color.WHITE)
        decor.draw(canvas)
        drawCaption(canvas, width, height, title, subtitle)

        val file = File(outDir, "$name.png")
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        println("Wrote walkthrough frame: ${file.absolutePath}")
    }

    private fun drawCaption(canvas: Canvas, width: Int, height: Int, title: String, subtitle: String) {
        val density = width / 411f
        val pad = 18f * density
        val bannerH = 92f * density
        val top = height - bannerH - 16f * density
        val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#E61C1C1E") }
        canvas.drawRoundRect(
            RectF(pad, top, width - pad, top + bannerH),
            14f * density,
            14f * density,
            bg,
        )
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 17f * density
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#D0FFFFFF")
            textSize = 13f * density
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }
        canvas.drawText(title, pad + 16f * density, top + 34f * density, titlePaint)
        wrapText(subtitle, subPaint, width - 2 * pad - 32f * density).forEachIndexed { i, line ->
            canvas.drawText(line, pad + 16f * density, top + 58f * density + i * 18f * density, subPaint)
        }
    }

    private fun wrapText(text: String, paint: Paint, maxWidth: Float): List<String> {
        val words = text.split(' ')
        val lines = mutableListOf<String>()
        var current = StringBuilder()
        words.forEach { word ->
            val trial = if (current.isEmpty()) word else "$current $word"
            if (paint.measureText(trial) <= maxWidth) {
                current = StringBuilder(trial)
            } else {
                if (current.isNotEmpty()) lines += current.toString()
                current = StringBuilder(word)
            }
        }
        if (current.isNotEmpty()) lines += current.toString()
        return lines.take(2)
    }
}
