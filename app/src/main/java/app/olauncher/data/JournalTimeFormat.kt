package app.olauncher.data

import java.util.Calendar
import java.util.Locale
import java.util.regex.Pattern

/** Formats and parses journal bullet timestamps. */
object JournalTimeFormat {

    private val embeddedMilitary = Pattern.compile("^(.*) · ([01]?\\d|2[0-3]):([0-5]\\d)$")
    private val embedded12Hour = Pattern.compile(
        "^(.*) · (1[0-2]|0?[1-9]):([0-5]\\d)\\s*([AaPp][Mm])$"
    )

    fun format(timeMinutes: Int, militaryTime: Boolean): String {
        val hour = timeMinutes.coerceIn(0, 1439) / 60
        val minute = timeMinutes.coerceIn(0, 1439) % 60
        return if (militaryTime) {
            String.format(Locale.getDefault(), "%02d:%02d", hour, minute)
        } else {
            val cal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
            }
            java.text.SimpleDateFormat("h:mm a", Locale.getDefault()).format(cal.time)
        }
    }

    /**
     * Splits legacy "Title · HH:mm" (or 12-hour) text into title + minutes.
     * Returns [text] with null minutes when no trailing time is present.
     */
    fun splitEmbeddedTime(text: String): Pair<String, Int?> {
        embeddedMilitary.matcher(text).let { m ->
            if (m.matches()) {
                val minutes = m.group(2)!!.toInt() * 60 + m.group(3)!!.toInt()
                return m.group(1)!!.trimEnd() to minutes
            }
        }
        embedded12Hour.matcher(text).let { m ->
            if (m.matches()) {
                var hour = m.group(2)!!.toInt() % 12
                if (m.group(4)!!.startsWith("P", ignoreCase = true)) hour += 12
                val minutes = hour * 60 + m.group(3)!!.toInt()
                return m.group(1)!!.trimEnd() to minutes
            }
        }
        return text to null
    }
}
