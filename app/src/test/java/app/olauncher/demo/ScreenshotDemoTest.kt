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
import android.widget.ImageView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import app.olauncher.R
import app.olauncher.data.AppModel
import app.olauncher.helper.setBlackAndWhite
import app.olauncher.ui.AppDrawerAdapter
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
    fun homeScreen() = capture("01_home_screen", R.layout.fragment_home) { activity, root ->
        root.findViewById<View>(R.id.dateTimeLayout).visibility = View.VISIBLE
        root.findViewById<android.widget.TextView>(R.id.date).text = "Sun, 26 Jul"
        // TextClock does not tick under Robolectric; show a static time via the clock TextView.
        root.findViewById<android.widget.TextView>(R.id.clock).text = "2:34"

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
        val density = activity.resources.displayMetrics.density
        val iconPx = (48 * density).toInt()
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
                visibility = View.VISIBLE
            }
        }
        root.findViewById<View>(R.id.homeAppsBottomSheet).visibility = View.VISIBLE
    }

    @Test
    fun appDrawer() = capture("02_app_drawer", R.layout.fragment_app_drawer) { activity, root ->
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
        val apps = listOf(
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
        adapter.setAppList(apps)
    }

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
        // Window background is transparent (launcher draws over wallpaper); use a light backdrop.
        canvas.drawColor(Color.WHITE)
        decor.draw(canvas)

        val file = File(outDir, "$name.png")
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        println("Wrote demo screenshot: ${file.absolutePath} (${width}x$height)")
    }
}
