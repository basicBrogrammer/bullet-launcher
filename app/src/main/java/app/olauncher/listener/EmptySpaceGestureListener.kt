package app.olauncher.listener

import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.abs

/**
 * Detects long-press / vertical swipes on RecyclerView empty space (wallpaper showing
 * through), without stealing scrolls or item clicks.
 */
class EmptySpaceGestureListener(
    private val recyclerView: RecyclerView,
    private val onLongPress: () -> Unit,
    private val onSwipeUp: () -> Unit = {},
    private val onSwipeDown: () -> Unit = {},
) : RecyclerView.SimpleOnItemTouchListener() {

    private val swipeThreshold = 100
    private val swipeVelocityThreshold = 100

    private val detector = GestureDetector(
        recyclerView.context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true

            override fun onLongPress(e: MotionEvent) {
                if (isEmptySpace(e.x, e.y)) onLongPress()
            }

            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float,
            ): Boolean {
                val start = e1 ?: return false
                if (!isEmptySpace(start.x, start.y)) return false
                val diffY = e2.y - start.y
                val diffX = e2.x - start.x
                if (abs(diffY) <= abs(diffX)) return false
                if (abs(diffY) <= swipeThreshold || abs(velocityY) <= swipeVelocityThreshold) {
                    return false
                }
                if (diffY < 0) onSwipeUp() else onSwipeDown()
                return true
            }
        }
    )

    private fun isEmptySpace(x: Float, y: Float): Boolean =
        recyclerView.findChildViewUnder(x, y) == null

    override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
        if (isEmptySpace(e.x, e.y) || e.actionMasked == MotionEvent.ACTION_UP ||
            e.actionMasked == MotionEvent.ACTION_CANCEL
        ) {
            detector.onTouchEvent(e)
        }
        return false
    }
}

/** Long-press (and optional vertical swipes) on a wallpaper-transparent view. */
fun View.setWallpaperGestures(
    onLongPress: () -> Unit,
    onSwipeUp: () -> Unit = {},
    onSwipeDown: () -> Unit = {},
) {
    setOnTouchListener(object : OnSwipeTouchListener(context) {
        override fun onLongClick() {
            onLongPress()
        }

        override fun onSwipeUp() {
            onSwipeUp()
        }

        override fun onSwipeDown() {
            onSwipeDown()
        }
    })
}
