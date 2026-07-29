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
import app.olauncher.data.JournalStore
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Two-way sync between journal ○ Event bullets and the device calendar
 * (including Google Calendar accounts via the system Calendar Provider).
 */
object CalendarSyncHelper {

    private const val TAG = "CalendarSync"
    private const val EVENT_DESCRIPTION = "Added from Bullet Launcher journal"
    /** Pull events from the start of the previous month through N months ahead. */
    private const val SYNC_MONTHS_AHEAD = 6

    private val dayFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val monthFormat = SimpleDateFormat("yyyy-MM", Locale.US)
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    data class DeviceCalendar(
        val id: Long,
        val displayName: String,
        val accountName: String,
        val accountType: String,
        val isPrimary: Boolean,
        val canWrite: Boolean,
        val color: Int = 0,
    ) {
        fun label(): String = if (accountName.isBlank() || accountName == displayName) {
            displayName
        } else {
            "$displayName · $accountName"
        }
    }

    data class RemoteEvent(
        val eventId: Long,
        val calendarId: Long,
        val title: String,
        val beginMillis: Long,
        val endMillis: Long,
        val allDay: Boolean,
    )

    data class SyncResult(
        val added: Int = 0,
        val updated: Int = 0,
        val removed: Int = 0,
    ) {
        val changed: Boolean get() = added > 0 || updated > 0 || removed > 0
        val importedOrUpdated: Int get() = added + updated
    }

    fun hasCalendarPermissions(context: Context): Boolean {
        val read = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR)
        val write = ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR)
        return read == PackageManager.PERMISSION_GRANTED && write == PackageManager.PERMISSION_GRANTED
    }

    fun listWritableCalendars(context: Context): List<DeviceCalendar> =
        listCalendars(context).filter { it.canWrite }

    fun listCalendars(context: Context): List<DeviceCalendar> {
        if (!hasCalendarPermissions(context)) return emptyList()
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.ACCOUNT_TYPE,
            CalendarContract.Calendars.IS_PRIMARY,
            CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,
            CalendarContract.Calendars.CALENDAR_COLOR,
        )
        val selection = "${CalendarContract.Calendars.VISIBLE} = 1"
        return try {
            context.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                projection,
                selection,
                null,
                "${CalendarContract.Calendars.IS_PRIMARY} DESC, ${CalendarContract.Calendars.CALENDAR_DISPLAY_NAME} ASC",
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndexOrThrow(CalendarContract.Calendars._ID)
                val nameIdx = cursor.getColumnIndexOrThrow(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME)
                val accountIdx = cursor.getColumnIndexOrThrow(CalendarContract.Calendars.ACCOUNT_NAME)
                val typeIdx = cursor.getColumnIndexOrThrow(CalendarContract.Calendars.ACCOUNT_TYPE)
                val primaryIdx = cursor.getColumnIndexOrThrow(CalendarContract.Calendars.IS_PRIMARY)
                val accessIdx = cursor.getColumnIndexOrThrow(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL)
                val colorIdx = cursor.getColumnIndexOrThrow(CalendarContract.Calendars.CALENDAR_COLOR)
                buildList {
                    while (cursor.moveToNext()) {
                        val access = cursor.getInt(accessIdx)
                        add(
                            DeviceCalendar(
                                id = cursor.getLong(idIdx),
                                displayName = cursor.getString(nameIdx).orEmpty().ifBlank { "Calendar" },
                                accountName = cursor.getString(accountIdx).orEmpty(),
                                accountType = cursor.getString(typeIdx).orEmpty(),
                                isPrimary = cursor.getInt(primaryIdx) == 1,
                                canWrite = access >= CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR,
                                color = cursor.getInt(colorIdx),
                            )
                        )
                    }
                }
            } ?: emptyList()
        } catch (e: SecurityException) {
            Log.e(TAG, "Missing calendar permission while listing calendars", e)
            emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to query calendars", e)
            emptyList()
        }
    }

    fun defaultWritableCalendarId(context: Context, preferredId: Long = -1L): Long? {
        val writable = listWritableCalendars(context)
        if (writable.isEmpty()) return null
        if (preferredId > 0L) {
            writable.find { it.id == preferredId }?.let { return it.id }
        }
        return writable.find { it.isPrimary && it.accountType == "com.google" }?.id
            ?: writable.find { it.isPrimary }?.id
            ?: writable.first().id
    }

    /**
     * Inserts an all-day calendar event for the journal entry into [calendarId].
     * Returns the new CalendarContract event row id, or null on failure.
     */
    fun insertEvent(context: Context, entry: JournalEntry, calendarId: Long): Long? {
        if (entry.type != BulletType.EVENT) return null
        if (!hasCalendarPermissions(context)) return null
        if (calendarId <= 0L) return null

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
                Log.d(TAG, "Inserted calendar event id=$id calendar=$calendarId for journal ${entry.id}")
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Missing calendar permission", e)
            null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to insert calendar event", e)
            null
        }
    }

    /**
     * Updates the title of a previously synced calendar event.
     * Returns true if the row was updated.
     */
    fun updateEvent(context: Context, entry: JournalEntry): Boolean {
        val calendarEventId = entry.calendarEventId ?: return false
        if (calendarEventId <= 0L) return false
        if (entry.type != BulletType.EVENT) return false
        if (!hasCalendarPermissions(context)) return false

        val values = ContentValues().apply {
            put(CalendarContract.Events.TITLE, entry.text)
        }
        return try {
            val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, calendarEventId)
            context.contentResolver.update(uri, values, null, null) > 0
        } catch (e: SecurityException) {
            Log.e(TAG, "Missing calendar permission on update", e)
            false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update calendar event $calendarEventId", e)
            false
        }
    }

    /**
     * Deletes a previously synced calendar event.
     * Skips deleting recurring series that were imported (avoids wiping the whole series).
     */
    fun deleteEvent(
        context: Context,
        calendarEventId: Long?,
        fromCalendar: Boolean = false,
    ): Boolean {
        if (calendarEventId == null || calendarEventId <= 0L) return false
        if (!hasCalendarPermissions(context)) return false
        if (fromCalendar && isRecurringEvent(context, calendarEventId)) {
            Log.d(TAG, "Skip deleting recurring imported event $calendarEventId")
            return false
        }
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
     * Pulls device/Google Calendar events into the journal and removes journal
     * events whose calendar counterparts were deleted. Returns counts of changes.
     */
    fun syncIntoJournal(context: Context, store: JournalStore): SyncResult {
        if (!hasCalendarPermissions(context)) return SyncResult()

        val (rangeStart, rangeEnd) = syncRangeMillis()
        val remotes = queryInstances(context, rangeStart, rangeEnd)
        val linked = store.getAll()
            .filter { it.type == BulletType.EVENT && it.calendarEventId != null }
        val linkedByKey = linked.associateBy { syncKey(it.calendarEventId!!, it.dateKey) }
        val linkedByEventId = linked.groupBy { it.calendarEventId!! }

        var added = 0
        var updated = 0
        var removed = 0
        val seenKeys = mutableSetOf<String>()
        val seenEntryIds = mutableSetOf<String>()
        val currentMonth = store.currentMonthKey()

        for (remote in remotes) {
            val title = remote.title.ifBlank { "Event" }
            val dateKey = dayKeyForEvent(remote.beginMillis, remote.allDay)
            val key = syncKey(remote.eventId, dateKey)
            seenKeys.add(key)

            val text = formatEventText(title, remote.beginMillis, remote.allDay)
            val monthKey = dateKey.take(7)
            val (log, storeKey) = when {
                monthKey > currentMonth -> JournalLog.FUTURE to monthKey
                else -> JournalLog.DAILY to dateKey
            }

            val existing = findLinkedEntry(
                linkedByKey = linkedByKey,
                linkedByEventId = linkedByEventId,
                eventId = remote.eventId,
                dateKey = dateKey,
                monthKey = monthKey,
                exactKey = key,
            )
            if (existing != null) {
                seenEntryIds.add(existing.id)
                seenKeys.add(syncKey(remote.eventId, existing.dateKey))
                if (existing.text != text || existing.dateKey != storeKey || existing.log != log) {
                    store.updateSyncedEvent(existing.id, text, log, storeKey, remote.calendarId)
                    updated++
                }
            } else {
                store.add(
                    text = text,
                    type = BulletType.EVENT,
                    log = log,
                    dateKey = storeKey,
                    priority = false,
                    calendarEventId = remote.eventId,
                    calendarId = remote.calendarId,
                    fromCalendar = true,
                )
                added++
                Log.d(TAG, "Imported calendar event ${remote.eventId} → $storeKey ($text)")
            }
        }

        // Drop journal events that disappeared from the calendar within the sync window.
        for (entry in linked) {
            if (entry.id in seenEntryIds) continue
            val eventId = entry.calendarEventId ?: continue
            val key = syncKey(eventId, entry.dateKey)
            if (key in seenKeys) continue
            if (!dateKeyInSyncWindow(entry.dateKey, rangeStart, rangeEnd)) continue
            store.delete(entry.id)
            removed++
            Log.d(TAG, "Removed journal event ${entry.id}; calendar event $eventId gone")
        }

        return SyncResult(added = added, updated = updated, removed = removed)
    }

    private fun findLinkedEntry(
        linkedByKey: Map<String, JournalEntry>,
        linkedByEventId: Map<Long, List<JournalEntry>>,
        eventId: Long,
        dateKey: String,
        monthKey: String,
        exactKey: String,
    ): JournalEntry? {
        linkedByKey[exactKey]?.let { return it }
        linkedByKey[syncKey(eventId, monthKey)]?.let { return it }
        val matches = linkedByEventId[eventId].orEmpty()
        if (matches.isEmpty()) return null
        matches.find { it.dateKey == dateKey || it.dateKey == monthKey }?.let { return it }
        matches.find { dateKey.startsWith(it.dateKey) || it.dateKey.startsWith(monthKey) }?.let { return it }
        // Single non-recurring link: reuse it even if the stored key was month-only.
        return matches.singleOrNull()
    }

    private fun syncKey(eventId: Long, dateKey: String): String = "$eventId|$dateKey"

    private fun syncRangeMillis(): Pair<Long, Long> {
        val start = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            add(Calendar.MONTH, -1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val end = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            add(Calendar.MONTH, SYNC_MONTHS_AHEAD)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.MONTH, 1) // end of the Nth month ahead
        }.timeInMillis
        return start to end
    }

    private fun dateKeyInSyncWindow(dateKey: String, rangeStart: Long, rangeEnd: Long): Boolean {
        return try {
            val key = if (dateKey.length == 7) "$dateKey-01" else dateKey
            val millis = dayFormat.parse(key)?.time ?: return false
            millis in rangeStart until rangeEnd
        } catch (_: Exception) {
            false
        }
    }

    private fun queryInstances(
        context: Context,
        rangeStart: Long,
        rangeEnd: Long,
    ): List<RemoteEvent> {
        val builder = CalendarContract.Instances.CONTENT_URI.buildUpon()
        ContentUris.appendId(builder, rangeStart)
        ContentUris.appendId(builder, rangeEnd)
        val uri = builder.build()

        val projection = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.CALENDAR_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.STATUS,
        )

        return try {
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                val eventIdIdx = cursor.getColumnIndexOrThrow(CalendarContract.Instances.EVENT_ID)
                val calIdIdx = cursor.getColumnIndexOrThrow(CalendarContract.Instances.CALENDAR_ID)
                val titleIdx = cursor.getColumnIndexOrThrow(CalendarContract.Instances.TITLE)
                val beginIdx = cursor.getColumnIndexOrThrow(CalendarContract.Instances.BEGIN)
                val endIdx = cursor.getColumnIndexOrThrow(CalendarContract.Instances.END)
                val allDayIdx = cursor.getColumnIndexOrThrow(CalendarContract.Instances.ALL_DAY)
                val statusIdx = cursor.getColumnIndexOrThrow(CalendarContract.Instances.STATUS)
                buildList {
                    while (cursor.moveToNext()) {
                        val status = cursor.getInt(statusIdx)
                        if (status == CalendarContract.Events.STATUS_CANCELED) continue
                        val title = cursor.getString(titleIdx).orEmpty()
                        // Skip empty cancelled shells
                        if (title.isBlank()) continue
                        add(
                            RemoteEvent(
                                eventId = cursor.getLong(eventIdIdx),
                                calendarId = cursor.getLong(calIdIdx),
                                title = title,
                                beginMillis = cursor.getLong(beginIdx),
                                endMillis = cursor.getLong(endIdx),
                                allDay = cursor.getInt(allDayIdx) == 1,
                            )
                        )
                    }
                }
            } ?: emptyList()
        } catch (e: SecurityException) {
            Log.e(TAG, "Missing calendar permission while querying instances", e)
            emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to query calendar instances", e)
            emptyList()
        }
    }

    private fun formatEventText(title: String, beginMillis: Long, allDay: Boolean): String {
        if (allDay) return title
        return try {
            "$title · ${timeFormat.format(Date(beginMillis))}"
        } catch (_: Exception) {
            title
        }
    }

    private fun dayKeyForEvent(beginMillis: Long, allDay: Boolean): String {
        return if (allDay) {
            // All-day events are stored as UTC midnight; format in UTC to avoid day shift.
            val utc = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            utc.format(Date(beginMillis))
        } else {
            dayFormat.format(Date(beginMillis))
        }
    }

    private fun isRecurringEvent(context: Context, eventId: Long): Boolean {
        return try {
            context.contentResolver.query(
                ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId),
                arrayOf(CalendarContract.Events.RRULE),
                null,
                null,
                null,
            )?.use { cursor ->
                if (!cursor.moveToFirst()) return false
                !cursor.getString(0).isNullOrBlank()
            } ?: false
        } catch (_: Exception) {
            false
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
