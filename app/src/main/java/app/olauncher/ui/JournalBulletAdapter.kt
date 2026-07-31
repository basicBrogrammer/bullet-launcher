package app.olauncher.ui

import android.graphics.Paint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import app.olauncher.R
import app.olauncher.data.BulletType
import app.olauncher.data.JournalEntry
import app.olauncher.helper.getColorFromAttr

sealed class JournalListItem {
    data class Bullet(val entry: JournalEntry) : JournalListItem()
    data class Section(val title: String, val entries: List<JournalEntry>) : JournalListItem()
    /** Flat monthly day header — bullets that follow belong to [dateKey] until the next header. */
    data class DayHeader(val title: String, val dateKey: String) : JournalListItem()
}

class JournalBulletAdapter(
    private val onToggle: (JournalEntry) -> Unit,
    private val onLongPress: (JournalEntry) -> Unit,
    private val onStartDrag: ((RecyclerView.ViewHolder) -> Unit)? = null,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val items = mutableListOf<JournalListItem>()
    var militaryTime: Boolean = true
        set(value) {
            if (field == value) return
            field = value
            notifyDataSetChanged()
        }
    var dragEnabled: Boolean = false

    fun submit(list: List<JournalListItem>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    fun itemAt(position: Int): JournalListItem? = items.getOrNull(position)

    fun moveItem(from: Int, to: Int) {
        if (from == to || from !in items.indices || to !in items.indices) return
        val item = items.removeAt(from)
        items.add(to, item)
        notifyItemMoved(from, to)
    }

    /** Date key for the day that owns the bullet (or empty slot) at [position]. */
    fun dateKeyAt(position: Int): String? {
        for (i in position downTo 0) {
            when (val item = items[i]) {
                is JournalListItem.DayHeader -> return item.dateKey
                else -> Unit
            }
        }
        return null
    }

    /** Bullet neighbors within the same day around [position] (excluding the item itself). */
    fun bulletNeighbors(position: Int): Pair<JournalEntry?, JournalEntry?> {
        var above: JournalEntry? = null
        var below: JournalEntry? = null
        for (i in position - 1 downTo 0) {
            when (val item = items[i]) {
                is JournalListItem.DayHeader -> break
                is JournalListItem.Bullet -> {
                    above = item.entry
                    break
                }
                else -> Unit
            }
        }
        for (i in position + 1 until items.size) {
            when (val item = items[i]) {
                is JournalListItem.DayHeader -> break
                is JournalListItem.Bullet -> {
                    below = item.entry
                    break
                }
                else -> Unit
            }
        }
        return above to below
    }

    override fun getItemViewType(position: Int): Int = when (items[position]) {
        is JournalListItem.Bullet -> TYPE_BULLET
        is JournalListItem.Section -> TYPE_SECTION
        is JournalListItem.DayHeader -> TYPE_DAY_HEADER
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_SECTION -> SectionVH(inflater.inflate(R.layout.item_journal_section, parent, false))
            TYPE_DAY_HEADER -> DayHeaderVH(
                inflater.inflate(R.layout.item_journal_section, parent, false)
            )
            else -> BulletVH(inflater.inflate(R.layout.item_journal_bullet, parent, false))
        }
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is JournalListItem.Bullet -> (holder as BulletVH).bind(item.entry)
            is JournalListItem.Section -> (holder as SectionVH).bind(item)
            is JournalListItem.DayHeader -> (holder as DayHeaderVH).bind(item)
        }
    }

    inner class BulletVH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val star: ImageView = itemView.findViewById(R.id.priorityStar)
        private val symbol: TextView = itemView.findViewById(R.id.bulletSymbol)
        private val text: TextView = itemView.findViewById(R.id.bulletText)

        fun bind(entry: JournalEntry) {
            bindBulletRow(star, symbol, text, entry)
            itemView.setOnClickListener { onToggle(entry) }
            itemView.setOnLongClickListener {
                onLongPress(entry)
                true
            }
            val startDrag = onStartDrag
            if (dragEnabled && startDrag != null) {
                val dragListener = View.OnLongClickListener {
                    startDrag(this)
                    true
                }
                // Bullet / star starts a drag; row long-press still edits via the text.
                symbol.setOnLongClickListener(dragListener)
                star.setOnLongClickListener(dragListener)
                symbol.contentDescription = itemView.context.getString(R.string.drag_bullet)
                star.contentDescription = itemView.context.getString(R.string.drag_bullet)
                text.setOnLongClickListener {
                    onLongPress(entry)
                    true
                }
                // Avoid the row stealing the symbol long-press.
                itemView.setOnLongClickListener(null)
            } else {
                symbol.setOnLongClickListener(null)
                star.setOnLongClickListener(null)
                text.setOnLongClickListener(null)
            }
        }
    }

    inner class DayHeaderVH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val title: TextView = itemView.findViewById(R.id.sectionTitle)
        private val bullets: LinearLayout = itemView.findViewById(R.id.sectionBullets)

        fun bind(header: JournalListItem.DayHeader) {
            title.text = header.title
            bullets.removeAllViews()
            // Empty day drop target — keep a little space under the header.
            bullets.minimumHeight = (itemView.resources.displayMetrics.density * 8).toInt()
        }
    }

    inner class SectionVH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val title: TextView = itemView.findViewById(R.id.sectionTitle)
        private val bullets: LinearLayout = itemView.findViewById(R.id.sectionBullets)

        fun bind(section: JournalListItem.Section) {
            title.text = section.title
            bullets.removeAllViews()
            bullets.minimumHeight = 0
            val inflater = LayoutInflater.from(itemView.context)
            section.entries.forEach { entry ->
                val row = inflater.inflate(R.layout.item_journal_bullet, bullets, false)
                val star = row.findViewById<ImageView>(R.id.priorityStar)
                val symbol = row.findViewById<TextView>(R.id.bulletSymbol)
                val textView = row.findViewById<TextView>(R.id.bulletText)
                bindBulletRow(star, symbol, textView, entry)
                row.setOnClickListener { onToggle(entry) }
                row.setOnLongClickListener {
                    onLongPress(entry)
                    true
                }
                bullets.addView(row)
            }
            if (section.entries.isEmpty()) {
                val empty = TextView(itemView.context).apply {
                    setText(R.string.journal_section_empty)
                    setTextAppearance(itemView.context, R.style.TextSmallLight)
                    setPadding(40, 4, 8, 8)
                }
                bullets.addView(empty)
            }
        }
    }

    private fun bindBulletRow(
        star: ImageView,
        symbol: TextView,
        text: TextView,
        entry: JournalEntry,
    ) {
        text.text = entry.displayText(militaryTime)
        text.paintFlags = if (entry.completed) {
            text.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
        } else {
            text.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
        }
        // Priority uses the star as the sole marker (not star + bullet).
        // Completed tasks still show × so status stays obvious.
        val showStar = entry.priority && !(entry.type == BulletType.TASK && entry.completed)
        star.isVisible = showStar
        symbol.isVisible = !showStar
        if (!showStar) {
            symbol.text = entry.displaySymbol()
        }
        if (showStar) {
            star.setColorFilter(star.context.getColorFromAttr(R.attr.primaryColor))
        }
    }

    companion object {
        private const val TYPE_BULLET = 0
        private const val TYPE_SECTION = 1
        private const val TYPE_DAY_HEADER = 2
    }
}
