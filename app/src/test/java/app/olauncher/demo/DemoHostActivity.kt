package app.olauncher.demo

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import app.olauncher.R

/**
 * Minimal themed host activity used only by the JVM screenshot harness
 * ([ScreenshotDemoTest]) to render real Olauncher layouts to PNG without an
 * emulator or device. Not part of the shipped app.
 */
class DemoHostActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.AppTheme)
        super.onCreate(savedInstanceState)
    }
}
