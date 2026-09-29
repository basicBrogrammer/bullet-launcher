package app.olauncher.helper

import android.hardware.SensorManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ShakeTrackerTest {

    private val gravity = SensorManager.GRAVITY_EARTH

    @Test
    fun restingPhoneDoesNotShake() {
        val tracker = ShakeTracker()
        repeat(20) { step ->
            assertFalse(tracker.onAcceleration(0f, 0f, gravity, step * 50L))
        }
    }

    @Test
    fun singleBumpDoesNotShake() {
        val tracker = ShakeTracker()
        assertFalse(tracker.onAcceleration(0f, 0f, gravity * 4f, 0L))
        assertFalse(tracker.onAcceleration(0f, 0f, gravity, 300L))
        assertFalse(tracker.onAcceleration(0f, 0f, gravity, 800L))
    }

    @Test
    fun backAndForthShakeFiresOnce() {
        val tracker = ShakeTracker()
        assertFalse(spike(tracker, 0L))
        assertFalse(tracker.onAcceleration(0f, 0f, gravity, 250L))
        assertTrue(spike(tracker, 500L))
        // Tail of the same motion stays inside the cooldown.
        assertFalse(tracker.onAcceleration(0f, 0f, gravity, 700L))
        assertFalse(spike(tracker, 900L))
        assertFalse(tracker.onAcceleration(0f, 0f, gravity, 1100L))
        assertFalse(spike(tracker, 1400L))
    }

    @Test
    fun sustainedForceDoesNotCountAsTwoPeaks() {
        val tracker = ShakeTracker()
        assertFalse(tracker.onAcceleration(0f, 0f, gravity * 4f, 0L))
        assertFalse(tracker.onAcceleration(0f, 0f, gravity * 4f, 400L))
        assertFalse(tracker.onAcceleration(0f, 0f, gravity * 4f, 800L))
    }

    @Test
    fun peaksOutsideTheWindowStartOver() {
        val tracker = ShakeTracker()
        assertFalse(spike(tracker, 0L))
        assertFalse(tracker.onAcceleration(0f, 0f, gravity, 200L))
        assertFalse(spike(tracker, 2_000L))
        assertFalse(tracker.onAcceleration(0f, 0f, gravity, 2_200L))
        assertTrue(spike(tracker, 2_500L))
    }

    @Test
    fun gravityAloneIsAboutOneG() {
        val force = ShakeTracker.gForce(0f, 0f, gravity)
        assertEquals(1f, force, 0.01f)
    }

    private fun spike(tracker: ShakeTracker, atMs: Long): Boolean =
        tracker.onAcceleration(gravity * 3f, gravity * 2f, gravity, atMs)
}
