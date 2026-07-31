package app.olauncher.ui

import android.graphics.Canvas
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import app.olauncher.R
import app.olauncher.data.JournalEntry
import app.olauncher.data.JournalPages
import app.olauncher.data.JournalStore
import app.olauncher.data.MonthlyDropTimeResolver
import app.olauncher.data.Prefs
import app.olauncher.listener.EmptySpaceGestureListener
import app.olauncher.listener.setWallpaperGestures
import java.text.SimpleDateFormat
import java.util.Locale

class JournalPagerAdapter(
    private val store: JournalStore,
    private val prefs: Prefs,
    private val onIndex: () -> Unit,
    private val onToggle: (JournalEntry) -> Unit,
    private val onLongPress: (JournalEntry) -> Unit,
    private val onEmptyLongPress: () -> Unit,
    private val onEmptySwipeUp: () -> Unit = {},
    private val onEmptySwipeDown: () -> Unit = {},
    private val onEntryMoved: (JournalEntry) -> Unit = {},
) : RecyclerView.Adapter<JournalPagerAdapter.PageVH>() {

    private val dayLabelFormat = SimpleDateFormat("d · EEE", Locale.getDefault())
    private val dayParseFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private var monthlyPage: PageVH? = null
    /** Mirrors the journal ViewPager selection; used to avoid jumping while browsing monthly. */
    private var currentPage: Int = JournalPages.DAILY

    override fun getItemCount(): Int = JournalPages.COUNT

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageVH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.page_journal_log, parent, false)
        return PageVH(view)
    }

    override fun onBindViewHolder(holder: PageVH, position: Int) {
        holder.bind(position)
    }

    override fun onViewAttachedToWindow(holder: PageVH) {
        super.onViewAttachedToWindow(holder)
        if (holder.bindingAdapterPosition == JournalPages.MONTHLY) {
            monthlyPage = holder
            // Keep the off-screen monthly page pinned to today so the first
            // swipe-in (and any recycle) lands on the right day.
            holder.scrollToToday()
        }
    }

    override fun onViewDetachedFromWindow(holder: PageVH) {
        if (monthlyPage === holder) monthlyPage = null
        super.onViewDetachedFromWindow(holder)
    }

    fun refresh() {
        notifyDataSetChanged()
    }

    /**
     * Called when the journal pager settles on a page.
     * Entering monthly scrolls to today; leaving resets so the next visit starts there.
     */
    fun onPageSelected(page: Int) {
        currentPage = page
        scrollMonthlyToToday()
    }

    /** Pin the monthly log to today's section. */
    fun scrollMonthlyToToday() {
        monthlyPage?.scrollToToday()
    }

    inner class PageVH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val logTitle: TextView = itemView.findViewById(R.id.logTitle)
        private val logSubtitle: TextView = itemView.findViewById(R.id.logSubtitle)
        private val indexButton: TextView = itemView.findViewById(R.id.indexButton)
        private val emptyHint: TextView = itemView.findViewById(R.id.emptyHint)
        private val bulletList: RecyclerView = itemView.findViewById(R.id.bulletList)
        private val adapter = JournalBulletAdapter(
            onToggle = onToggle,
            onLongPress = onLongPress,
            onStartDrag = { vh -> itemTouchHelper?.startDrag(vh) },
        )
        private var todaySectionIndex: Int = RecyclerView.NO_POSITION
        private val defaultListPaddingBottom = bulletList.paddingBottom
        private var itemTouchHelper: ItemTouchHelper? = null
        private var boundPage: Int = -1

        init {
            bulletList.layoutManager = LinearLayoutManager(itemView.context)
            bulletList.adapter = adapter
            indexButton.setOnClickListener { onIndex() }
            // Wallpaper-visible chrome / empty areas open settings on long-press.
            listOf(logTitle, logSubtitle, emptyHint).forEach { view ->
                view.setWallpaperGestures(
                    onLongPress = onEmptyLongPress,
                    onSwipeUp = onEmptySwipeUp,
                    onSwipeDown = onEmptySwipeDown,
                )
            }
            bulletList.addOnItemTouchListener(
                EmptySpaceGestureListener(
                    recyclerView = bulletList,
                    onLongPress = onEmptyLongPress,
                    onSwipeUp = onEmptySwipeUp,
                    onSwipeDown = onEmptySwipeDown,
                )
            )
            // Page padding / gaps where wallpaper shows through.
            itemView.setWallpaperGestures(
                onLongPress = onEmptyLongPress,
                onSwipeUp = onEmptySwipeUp,
                onSwipeDown = onEmptySwipeDown,
            )
        }

        fun bind(page: Int) {
            todaySectionIndex = RecyclerView.NO_POSITION
            boundPage = page
            adapter.militaryTime = prefs.journalMilitaryTime
            when (page) {
                JournalPages.MONTHLY -> bindMonthly()
                JournalPages.FUTURE -> bindFuture()
                else -> bindDaily()
            }
        }

        fun scrollToToday() {
            val index = todaySectionIndex
            if (index == RecyclerView.NO_POSITION) return
            val layoutManager = bulletList.layoutManager as? LinearLayoutManager ?: return
            bulletList.post {
                if (todaySectionIndex != index) return@post
                if (index >= adapter.itemCount) return@post
                // Extra bottom space so today's section (often last) can sit at the top.
                val bottomPad = (bulletList.height - bulletList.paddingTop)
                    .coerceAtLeast(defaultListPaddingBottom)
                if (bulletList.paddingBottom != bottomPad) {
                    bulletList.setPadding(
                        bulletList.paddingLeft,
                        bulletList.paddingTop,
                        bulletList.paddingRight,
                        bottomPad,
                    )
                }
                layoutManager.scrollToPositionWithOffset(index, 0)
            }
        }

        private fun resetListPadding() {
            if (bulletList.paddingBottom == defaultListPaddingBottom) return
            bulletList.setPadding(
                bulletList.paddingLeft,
                bulletList.paddingTop,
                bulletList.paddingRight,
                defaultListPaddingBottom,
            )
        }

        private fun clearDragHelper() {
            itemTouchHelper?.attachToRecyclerView(null)
            itemTouchHelper = null
            adapter.dragEnabled = false
        }

        private fun bindDaily() {
            clearDragHelper()
            resetListPadding()
            logTitle.setText(R.string.daily_log)
            logSubtitle.text = store.formatDayHeader()
            val entries = store.getForDay(store.todayKey())
            emptyHint.visibility = if (entries.isEmpty()) View.VISIBLE else View.GONE
            emptyHint.setText(R.string.journal_empty_hint)
            adapter.submit(entries.map { JournalListItem.Bullet(it) })
        }

        private fun bindMonthly() {
            val monthKey = store.currentMonthKey()
            logTitle.setText(R.string.monthly_log)
            logSubtitle.text = store.formatMonthHeader(monthKey)
            val byDay = store.getForMonth(monthKey).groupBy { it.dateKey }
            val today = store.todayKey()
            val dayKeys = store.daysInMonth(monthKey).filter { key ->
                key <= today || byDay.containsKey(key)
            }
            val list = mutableListOf<JournalListItem>()
            var todayIndex = RecyclerView.NO_POSITION
            for (key in dayKeys) {
                val entries = byDay[key].orEmpty()
                if (entries.isEmpty() && key != today) continue
                if (key == today) todayIndex = list.size
                list.add(JournalListItem.DayHeader(daySectionTitle(key), key))
                entries.forEach { list.add(JournalListItem.Bullet(it)) }
            }
            todaySectionIndex = todayIndex
            emptyHint.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
            emptyHint.setText(R.string.journal_monthly_empty)
            adapter.submit(list)
            attachMonthlyDragHelper()
            // Re-pin when this page is off-screen (e.g. after refresh). Stay put while browsing it.
            if (currentPage != JournalPages.MONTHLY) {
                scrollToToday()
            }
        }

        private fun attachMonthlyDragHelper() {
            adapter.dragEnabled = true
            if (itemTouchHelper != null) return
            val callback = object : ItemTouchHelper.SimpleCallback(
                ItemTouchHelper.UP or ItemTouchHelper.DOWN,
                0,
            ) {
                private var dragging = false

                override fun isLongPressDragEnabled(): Boolean = false

                override fun getMovementFlags(
                    recyclerView: RecyclerView,
                    viewHolder: RecyclerView.ViewHolder,
                ): Int {
                    if (viewHolder !is JournalBulletAdapter.BulletVH) return 0
                    return makeMovementFlags(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0)
                }

                override fun onMove(
                    recyclerView: RecyclerView,
                    viewHolder: RecyclerView.ViewHolder,
                    target: RecyclerView.ViewHolder,
                ): Boolean {
                    val from = viewHolder.bindingAdapterPosition
                    val to = target.bindingAdapterPosition
                    if (from == RecyclerView.NO_POSITION || to == RecyclerView.NO_POSITION) return false
                    // Do not place a bullet above the first day header.
                    if (to == 0 && adapter.itemAt(0) is JournalListItem.DayHeader) return false
                    adapter.moveItem(from, to)
                    return true
                }

                override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) = Unit

                override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
                    super.onSelectedChanged(viewHolder, actionState)
                    if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
                        dragging = true
                        viewHolder?.itemView?.alpha = 0.7f
                    }
                }

                override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                    super.clearView(recyclerView, viewHolder)
                    viewHolder.itemView.alpha = 1f
                    if (!dragging) return
                    dragging = false
                    commitMonthlyDrop(viewHolder)
                }

                override fun onChildDraw(
                    c: Canvas,
                    recyclerView: RecyclerView,
                    viewHolder: RecyclerView.ViewHolder,
                    dX: Float,
                    dY: Float,
                    actionState: Int,
                    isCurrentlyActive: Boolean,
                ) {
                    super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
                }
            }
            itemTouchHelper = ItemTouchHelper(callback).also { it.attachToRecyclerView(bulletList) }
        }

        private fun commitMonthlyDrop(viewHolder: RecyclerView.ViewHolder) {
            val pos = viewHolder.bindingAdapterPosition
            if (pos == RecyclerView.NO_POSITION) {
                bindMonthly()
                return
            }
            val bullet = adapter.itemAt(pos) as? JournalListItem.Bullet ?: run {
                bindMonthly()
                return
            }
            val dateKey = adapter.dateKeyAt(pos) ?: run {
                bindMonthly()
                return
            }
            val (above, below) = adapter.bulletNeighbors(pos)
            val newTime = MonthlyDropTimeResolver.resolve(
                oldTime = bullet.entry.timeMinutes,
                aboveTime = above?.timeMinutes,
                belowTime = below?.timeMinutes,
            )
            val newCreatedAt = MonthlyDropTimeResolver.resolveCreatedAt(
                aboveCreatedAt = above?.createdAt,
                belowCreatedAt = below?.createdAt,
                fallback = bullet.entry.createdAt,
            )
            val unchanged = dateKey == bullet.entry.dateKey &&
                newTime == bullet.entry.timeMinutes &&
                newCreatedAt == bullet.entry.createdAt
            if (unchanged) {
                bindMonthly()
                return
            }
            val updated = store.moveToDay(
                id = bullet.entry.id,
                dateKey = dateKey,
                timeMinutes = newTime,
                createdAt = newCreatedAt,
            )
            if (updated != null) onEntryMoved(updated)
            // Rebuild so sort order (time within day) matches the store.
            bindMonthly()
        }

        private fun bindFuture() {
            clearDragHelper()
            resetListPadding()
            logTitle.setText(R.string.future_log)
            logSubtitle.setText(R.string.future_log_subtitle)
            val months = store.futureMonthKeys(6)
            val sections = months.map { monthKey ->
                JournalListItem.Section(
                    store.formatMonthShort(monthKey).uppercase(Locale.getDefault()),
                    store.getForFutureMonth(monthKey)
                )
            }
            emptyHint.visibility = View.GONE
            adapter.submit(sections)
        }

        private fun daySectionTitle(dateKey: String): String {
            return try {
                dayLabelFormat.format(dayParseFormat.parse(dateKey)!!)
            } catch (_: Exception) {
                dateKey
            }
        }
    }
}
