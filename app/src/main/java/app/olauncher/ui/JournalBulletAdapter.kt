package app.olauncher.ui

import android.graphics.Paint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import app.olauncher.R
import app.olauncher.data.JournalEntry

sealed class JournalListItem {
    data class Bullet(val entry: JournalEntry) : JournalListItem()
    data class Section(val title: String, val entries: List<JournalEntry>) : JournalListItem()
}

class JournalBulletAdapter(
    private val onToggle: (JournalEntry) -> Unit,
    private val onLongPress: (JournalEntry) -> Unit,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val items = mutableListOf<JournalListItem>()

    fun submit(list: List<JournalListItem>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int = when (items[position]) {
        is JournalListItem.Bullet -> TYPE_BULLET
        is JournalListItem.Section -> TYPE_SECTION
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_SECTION -> SectionVH(inflater.inflate(R.layout.item_journal_section, parent, false))
            else -> BulletVH(inflater.inflate(R.layout.item_journal_bullet, parent, false))
        }
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is JournalListItem.Bullet -> (holder as BulletVH).bind(item.entry)
            is JournalListItem.Section -> (holder as SectionVH).bind(item)
        }
    }

    inner class BulletVH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val symbol: TextView = itemView.findViewById(R.id.bulletSymbol)
        private val text: TextView = itemView.findViewById(R.id.bulletText)

        fun bind(entry: JournalEntry) {
            symbol.text = entry.displaySymbol()
            text.text = entry.text
            text.paintFlags = if (entry.completed) {
                text.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
            } else {
                text.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
            }
            itemView.setOnClickListener { onToggle(entry) }
            itemView.setOnLongClickListener {
                onLongPress(entry)
                true
            }
        }
    }

    inner class SectionVH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val title: TextView = itemView.findViewById(R.id.sectionTitle)
        private val bullets: LinearLayout = itemView.findViewById(R.id.sectionBullets)

        fun bind(section: JournalListItem.Section) {
            title.text = section.title
            bullets.removeAllViews()
            val inflater = LayoutInflater.from(itemView.context)
            section.entries.forEach { entry ->
                val row = inflater.inflate(R.layout.item_journal_bullet, bullets, false)
                row.findViewById<TextView>(R.id.bulletSymbol).text = entry.displaySymbol()
                val textView = row.findViewById<TextView>(R.id.bulletText)
                textView.text = entry.text
                textView.paintFlags = if (entry.completed) {
                    textView.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                } else {
                    textView.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
                }
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

    companion object {
        private const val TYPE_BULLET = 0
        private const val TYPE_SECTION = 1
    }
}
