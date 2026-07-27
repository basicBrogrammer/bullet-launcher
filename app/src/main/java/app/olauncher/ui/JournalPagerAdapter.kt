package app.olauncher.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import app.olauncher.R
import app.olauncher.data.JournalEntry
import app.olauncher.data.JournalLog
import app.olauncher.data.JournalPages
import app.olauncher.data.JournalStore
import java.text.SimpleDateFormat
import java.util.Locale

class JournalPagerAdapter(
    private val store: JournalStore,
    private val onIndex: () -> Unit,
    private val onToggle: (JournalEntry) -> Unit,
    private val onLongPress: (JournalEntry) -> Unit,
) : RecyclerView.Adapter<JournalPagerAdapter.PageVH>() {

    private val dayLabelFormat = SimpleDateFormat("d · EEE", Locale.getDefault())
    private val dayParseFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    override fun getItemCount(): Int = JournalPages.COUNT

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageVH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.page_journal_log, parent, false)
        return PageVH(view)
    }

    override fun onBindViewHolder(holder: PageVH, position: Int) {
        holder.bind(position)
    }

    fun refresh() {
        notifyDataSetChanged()
    }

    inner class PageVH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val logTitle: TextView = itemView.findViewById(R.id.logTitle)
        private val logSubtitle: TextView = itemView.findViewById(R.id.logSubtitle)
        private val indexButton: TextView = itemView.findViewById(R.id.indexButton)
        private val emptyHint: TextView = itemView.findViewById(R.id.emptyHint)
        private val bulletList: RecyclerView = itemView.findViewById(R.id.bulletList)
        private val adapter = JournalBulletAdapter(onToggle, onLongPress)

        init {
            bulletList.layoutManager = LinearLayoutManager(itemView.context)
            bulletList.adapter = adapter
            indexButton.setOnClickListener { onIndex() }
        }

        fun bind(page: Int) {
            when (page) {
                JournalPages.MONTHLY -> bindMonthly()
                JournalPages.FUTURE -> bindFuture()
                else -> bindDaily()
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
            // Show remaining days of month as light sections only for days with entries,
            // plus past/today always — keep list scannable.
            val sections = dayKeys.mapNotNull { key ->
                val entries = byDay[key].orEmpty()
                if (entries.isEmpty() && key != today) return@mapNotNull null
                JournalListItem.Section(daySectionTitle(key), entries)
            }
            emptyHint.visibility = if (sections.isEmpty()) View.VISIBLE else View.GONE
            emptyHint.setText(R.string.journal_monthly_empty)
            adapter.submit(sections)
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
