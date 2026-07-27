package app.olauncher.helper

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import android.util.Log
import androidx.core.content.ContextCompat
import app.olauncher.data.BulletType
import app.olauncher.data.JournalEntry
import app.olauncher.data.JournalLog
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * Syncs journal ○ Event bullets into the device calendar (including Google Calendar
 * accounts that sync via the system Calendar Provider).
 */
object CalendarSyncHelper {

    private const val TAG = "CalendarSync"
    private const val EVENT_DESCRIPTION = "Added from Olauncher journal"

    private val dayFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val monthFormat = SimpleDateFormat("yyyy-MM", Locale.US)

    fun hasCalendarPermissions(context: Context): Boolean {
        val read = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR)
        val write = ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR)
        return read == PackageManager.PERMISSION_GRANTED && write == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Inserts an all-day calendar event for the journal entry.
     * Returns the new CalendarContract event row id, or null on failure.
     */
    fun insertEvent(context: Context, entry: JournalEntry): Long? {
        if (entry.type != BulletType.EVENT) return null
        if (!hasCalendarPermissions(context)) return null

        val calendarId = findWritableCalendarId(context) ?: run {
            Log.w(TAG, "No writable calendar found")
            return null
        }
        val (startMillis, endMillis) = eventBoundsUtc(entry) ?: return null

        val values = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calendarId)
            put(CalendarContract.Events.TITLE, entry.text)
            put(CalendarContract.Events.DESCRIPTION, EVENT_DESCRIPTION)
            put(CalendarContract.Events.DTSTART, startMillis)
            put(CalendarContract.Events.DTEND, endMillis)
            put(CalendarContract.Events.ALL_DAY, 1)
            put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getTimeZone("UTC").id)
            put(CalendarContract.Events.STATUS, CalendarContract.Events.STATUS_CONFIRMED)
            put(CalendarContract.Events.HAS_ALARM, 0)
            put(CalendarContract.Events.CUSTOM_APP_PACKAGE, context.packageName)
        }

        return try {
            val uri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
            uri?.let { ContentUris.parseId(it) }.also { id ->
                Log.d(TAG, "Inserted calendar event id=$id for journal ${entry.id}")
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Missing calendar permission", e)
            null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to insert calendar event", e)
            null
        }
    }

    /** Deletes a previously synced calendar event. Safe no-op if id is null/invalid. */
    fun deleteEvent(context: Context, calendarEventId: Long?): Boolean {
        if (calendarEventId == null || calendarEventId <= 0L) return false
        if (!hasCalendarPermissions(context)) return false
        return try {
            val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, calendarEventId)
            val deleted = context.contentResolver.delete(uri, null, null)
            deleted > 0
        } catch (e: SecurityException) {
            Log.e(TAG, "Missing calendar permission on delete", e)
            false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete calendar event $calendarEventId", e)
            false
        }
    }

    /**
     * Prefer the primary Google calendar, then any primary calendar, then any
     * calendar the user can contribute to.
     */
    private fun findWritableCalendarId(context: Context): Long? {
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.ACCOUNT_TYPE,
            CalendarContract.Calendars.IS_PRIMARY,
            CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,
        )
        val selection =
            "${CalendarContract.Calendars.VISIBLE} = 1 AND " +
                "${CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL} >= ?"
        val selectionArgs = arrayOf(
            CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR.toString()
        )

        return try {
            context.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                null,
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndexOrThrow(CalendarContract.Calendars._ID)
                val typeIdx = cursor.getColumnIndexOrThrow(CalendarContract.Calendars.ACCOUNT_TYPE)
                val primaryIdx = cursor.getColumnIndexOrThrow(CalendarContract.Calendars.IS_PRIMARY)

                var googlePrimary: Long? = null
                var anyPrimary: Long? = null
                var firstWritable: Long? = null

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idIdx)
                    val accountType = cursor.getString(typeIdx).orEmpty()
                    val isPrimary = cursor.getInt(primaryIdx) == 1

                    if (firstWritable == null) firstWritable = id
                    if (isPrimary && anyPrimary == null) anyPrimary = id
                    if (isPrimary && accountType == "com.google" && googlePrimary == null) {
                        googlePrimary = id
                    }
                }
                googlePrimary ?: anyPrimary ?: firstWritable
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Missing calendar permission while listing calendars", e)
            null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to query calendars", e)
            null
        }
    }

    /**
     * All-day event bounds in UTC midnight, as required by CalendarContract.
     * Daily/Monthly (yyyy-MM-dd) → that day.
     * Future (yyyy-MM) → first day of that month.
     */
    private fun eventBoundsUtc(entry: JournalEntry): Pair<Long, Long>? {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        cal.clear()
        return try {
            when (entry.log) {
                JournalLog.FUTURE -> {
                    val parsed = monthFormat.parse(entry.dateKey) ?: return null
                    val local = Calendar.getInstance().apply { time = parsed }
                    cal.set(
                        local.get(Calendar.YEAR),
                        local.get(Calendar.MONTH),
                        1,
                        0, 0, 0
                    )
                }
                JournalLog.DAILY, JournalLog.MONTHLY -> {
                    val parsed = dayFormat.parse(entry.dateKey) ?: return null
                    val local = Calendar.getInstance().apply { time = parsed }
                    cal.set(
                        local.get(Calendar.YEAR),
                        local.get(Calendar.MONTH),
                        local.get(Calendar.DAY_OF_MONTH),
                        0, 0, 0
                    )
                }
            }
            cal.set(Calendar.MILLISECOND, 0)
            val start = cal.timeInMillis
            cal.add(Calendar.DAY_OF_MONTH, 1)
            val end = cal.timeInMillis
            start to end
        } catch (e: Exception) {
            Log.e(TAG, "Bad dateKey ${entry.dateKey}", e)
            null
        }
    }
}
