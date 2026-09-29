package app.olauncher.helper

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.sqrt

/**
 * Recognizes a back-and-forth phone shake from accelerometer samples.
 *
 * A shake is [requiredPeaks] spikes at or above [thresholdG], each one
 * separated by a dip under [rearmG] and at least [minGapMs], all inside
 * [windowMs]. After a shake, samples are ignored for [cooldownMs].
 */
class ShakeTracker(
    private val thresholdG: Float = DEFAULT_THRESHOLD_G,
    private val rearmG: Float = DEFAULT_REARM_G,
    private val minGapMs: Long = DEFAULT_MIN_GAP_MS,
    private val windowMs: Long = DEFAULT_WINDOW_MS,
    private val requiredPeaks: Int = DEFAULT_REQUIRED_PEAKS,
    private val cooldownMs: Long = DEFAULT_COOLDOWN_MS,
) {
    private var armed = true
    private var peaks = 0
    private var windowStart = UNSET
    private var lastPeakAt = UNSET
    private var ignoreUntil = 0L

    /**
     * @return true when this sample completes a shake
     */
    fun onAcceleration(x: Float, y: Float, z: Float, nowMs: Long): Boolean {
        if (nowMs < ignoreUntil) return false
        val force = gForce(x, y, z)
        if (force < rearmG) {
            armed = true
            return false
        }
        if (!armed || force < thresholdG) return false
        if (lastPeakAt != UNSET && nowMs - lastPeakAt < minGapMs) return false

        armed = false
        if (windowStart == UNSET || nowMs - windowStart > windowMs) {
            windowStart = nowMs
            peaks = 1
            lastPeakAt = nowMs
            return false
        }
        peaks += 1
        lastPeakAt = nowMs
        if (peaks >= requiredPeaks) {
            ignoreUntil = nowMs + cooldownMs
            windowStart = UNSET
            peaks = 0
            lastPeakAt = UNSET
            armed = false
            return true
        }
        return false
    }

    companion object {
        /** Resting acceleration is about 1g. A deliberate shake clears this. */
        const val DEFAULT_THRESHOLD_G = 2.7f

        /** Force must fall back under this before the next peak counts. */
        const val DEFAULT_REARM_G = 1.5f
        const val DEFAULT_MIN_GAP_MS = 200L
        const val DEFAULT_WINDOW_MS = 1500L
        const val DEFAULT_REQUIRED_PEAKS = 2
        const val DEFAULT_COOLDOWN_MS = 1500L
        private const val UNSET = -1L

        fun gForce(x: Float, y: Float, z: Float): Float {
            val gravity = SensorManager.GRAVITY_EARTH
            val gx = x / gravity
            val gy = y / gravity
            val gz = z / gravity
            return sqrt(gx * gx + gy * gy + gz * gz)
        }
    }
}

/**
 * Listens to the accelerometer while [start]ed and invokes [onShake] on the
 * thread that registered the listener (the main thread when started from the UI).
 */
class ShakeDetector(
    private val sensorManager: SensorManager,
    private val onShake: () -> Unit,
    private val tracker: ShakeTracker = ShakeTracker(),
    private val nowMs: () -> Long = System::currentTimeMillis,
) : SensorEventListener {

    fun start() {
        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return
        sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI)
    }

    fun stop() {
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ACCELEROMETER) return
        val values = event.values
        if (values.size < 3) return
        if (tracker.onAcceleration(values[0], values[1], values[2], nowMs())) {
            onShake()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
