package app.olauncher.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import app.olauncher.R
import app.olauncher.data.JournalEntry
import app.olauncher.data.JournalPages
import app.olauncher.data.JournalStore
import app.olauncher.listener.EmptySpaceGestureListener
import app.olauncher.listener.setWallpaperGestures
import java.text.SimpleDateFormat
import java.util.Locale

class JournalPagerAdapter(
    private val store: JournalStore,
    private val onIndex: () -> Unit,
    private val onToggle: (JournalEntry) -> Unit,
    private val onLongPress: (JournalEntry) -> Unit,
    private val onEmptyLongPress: () -> Unit,
    private val onEmptySwipeUp: () -> Unit = {},
    private val onEmptySwipeDown: () -> Unit = {},
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
        private val adapter = JournalBulletAdapter(onToggle, onLongPress)
        private var todaySectionIndex: Int = RecyclerView.NO_POSITION

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
                layoutManager.scrollToPositionWithOffset(index, 0)
            }
        }

        private fun bindDaily() {
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
            val sections = mutableListOf<JournalListItem.Section>()
            var todayIndex = RecyclerView.NO_POSITION
            for (key in dayKeys) {
                val entries = byDay[key].orEmpty()
                if (entries.isEmpty() && key != today) continue
                if (key == today) todayIndex = sections.size
                sections.add(JournalListItem.Section(daySectionTitle(key), entries))
            }
            todaySectionIndex = todayIndex
            emptyHint.visibility = if (sections.isEmpty()) View.VISIBLE else View.GONE
            emptyHint.setText(R.string.journal_monthly_empty)
            adapter.submit(sections)
            // Re-pin when this page is off-screen (e.g. after refresh). Stay put while browsing it.
            if (currentPage != JournalPages.MONTHLY) {
                scrollToToday()
            }
        }

        private fun bindFuture() {
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
