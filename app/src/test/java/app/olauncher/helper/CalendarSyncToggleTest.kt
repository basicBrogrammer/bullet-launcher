package app.olauncher.helper

import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.CalendarContract
import app.olauncher.data.BulletType
import app.olauncher.data.JournalLog
import app.olauncher.data.JournalStore
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CalendarSyncToggleTest {

    private val dayFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private lateinit var provider: FakeCalendarProvider

    @Before
    fun setUp() {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("app.olauncher.journal", 0).edit().clear().commit()
        context.getSharedPreferences("app.olauncher", 0).edit().clear().commit()
        Shadows.shadowOf(context).grantPermissions(
            android.Manifest.permission.READ_CALENDAR,
            android.Manifest.permission.WRITE_CALENDAR,
        )
        provider = FakeCalendarProvider()
        ShadowContentResolver.registerProviderInternal(CalendarContract.AUTHORITY, provider)
    }

    @Test
    fun seriesWithAnInstanceTodayStaysOnTodayAcrossSyncs() {
        val store = journalStore()
        val today = store.todayKey()
        val otherDay = otherDayInSameMonth(today)
        store.add(
            text = "Standup",
            type = BulletType.EVENT,
            log = JournalLog.DAILY,
            dateKey = today,
            calendarEventId = EVENT_ID,
            calendarId = CALENDAR_ID,
            fromCalendar = true,
            timeMinutes = 10 * 60,
        )
        provider.instances = listOf(
            instance(today, "Standup"),
            instance(otherDay, "Standup"),
        )

        CalendarSyncHelper.syncIntoJournal(context(), store)
        val afterFirstHomeSync = linkedDates(store)
        CalendarSyncHelper.syncIntoJournal(context(), store)
        val afterSecondHomeSync = linkedDates(store)
        assertEquals(listOf(listOf(today), listOf(today)), listOf(afterFirstHomeSync, afterSecondHomeSync))
    }

    @Test
    fun seriesWithoutTodayStaysOnTheNearestDay() {
        val store = journalStore()
        val today = store.todayKey()
        val prefix = today.dropLast(2)
        val (firstDay, secondDay) = if (today.takeLast(2) in setOf("01", "02")) {
            "03" to "04"
        } else {
            "01" to "02"
        }
        val dayA = prefix + firstDay
        val dayB = prefix + secondDay
        val expected = if (dayA > today && dayB > today) minOf(dayA, dayB) else maxOf(dayA, dayB)
        store.add(
            text = "Standup",
            type = BulletType.EVENT,
            log = JournalLog.DAILY,
            dateKey = dayA,
            calendarEventId = EVENT_ID,
            calendarId = CALENDAR_ID,
            fromCalendar = true,
            timeMinutes = 10 * 60,
        )
        provider.instances = listOf(instance(dayA, "Standup"), instance(dayB, "Standup"))

        CalendarSyncHelper.syncIntoJournal(context(), store)
        val afterFirst = linkedDates(store)
        CalendarSyncHelper.syncIntoJournal(context(), store)
        val afterSecond = linkedDates(store)
        assertEquals(listOf(listOf(expected), listOf(expected)), listOf(afterFirst, afterSecond))
    }

    @Test
    fun deletedSeriesStaysOutOfTheJournal() {
        val store = journalStore()
        val today = store.todayKey()
        val entry = store.add(
            text = "Standup",
            type = BulletType.EVENT,
            log = JournalLog.DAILY,
            dateKey = today,
            calendarEventId = EVENT_ID,
            calendarId = CALENDAR_ID,
            fromCalendar = true,
            timeMinutes = 10 * 60,
        )
        provider.instances = listOf(
            instance(today, "Standup"),
            instance(otherDayInSameMonth(today), "Standup"),
        )
        store.deleteUserEntry(entry.id)

        CalendarSyncHelper.syncIntoJournal(context(), store)
        assertEquals(emptyList<String>(), linkedDates(store))
        CalendarSyncHelper.syncIntoJournal(context(), store)
        assertEquals(emptyList<String>(), linkedDates(store))
    }

    @Test
    fun seriesDeletedInCalendarStaysOutOfTheJournal() {
        val store = journalStore()
        val today = store.todayKey()
        store.add(
            text = "Standup",
            type = BulletType.EVENT,
            log = JournalLog.DAILY,
            dateKey = today,
            calendarEventId = EVENT_ID,
            calendarId = CALENDAR_ID,
            fromCalendar = true,
            timeMinutes = 10 * 60,
        )
        provider.instances = listOf(
            instance(today, "Standup"),
            instance(otherDayInSameMonth(today), "Standup"),
        )
        provider.eventDeleted = true

        CalendarSyncHelper.syncIntoJournal(context(), store)
        assertEquals(emptyList<String>(), linkedDates(store))
        CalendarSyncHelper.syncIntoJournal(context(), store)
        assertEquals(emptyList<String>(), linkedDates(store))
    }

    @Test
    fun seriesMissingFromCalendarStaysOutOfTheJournal() {
        val store = journalStore()
        val today = store.todayKey()
        store.add(
            text = "Standup",
            type = BulletType.EVENT,
            log = JournalLog.DAILY,
            dateKey = today,
            calendarEventId = EVENT_ID,
            calendarId = CALENDAR_ID,
            fromCalendar = true,
            timeMinutes = 10 * 60,
        )
        provider.instances = listOf(
            instance(today, "Standup"),
            instance(otherDayInSameMonth(today), "Standup"),
        )
        provider.eventAbsent = true

        CalendarSyncHelper.syncIntoJournal(context(), store)
        assertEquals(emptyList<String>(), linkedDates(store))
        CalendarSyncHelper.syncIntoJournal(context(), store)
        assertEquals(emptyList<String>(), linkedDates(store))
    }

    @Test
    fun oneOffRescheduleLandsOnceAndStays() {
        val store = journalStore()
        val today = store.todayKey()
        val moved = otherDayInSameMonth(today)
        store.add(
            text = "Dentist",
            type = BulletType.EVENT,
            log = JournalLog.DAILY,
            dateKey = today,
            calendarEventId = EVENT_ID,
            calendarId = CALENDAR_ID,
            fromCalendar = true,
            timeMinutes = 15 * 60,
        )
        provider.instances = listOf(instance(moved, "Dentist", hour = 15, minute = 0))

        CalendarSyncHelper.syncIntoJournal(context(), store)
        assertEquals(listOf(moved), linkedDates(store))
        CalendarSyncHelper.syncIntoJournal(context(), store)
        assertEquals(listOf(moved), linkedDates(store))
    }

    private fun journalStore(): JournalStore = JournalStore(context())

    private fun context(): Application = RuntimeEnvironment.getApplication()

    private fun linkedDates(store: JournalStore): List<String> =
        store.getAll()
            .filter { it.calendarEventId == EVENT_ID }
            .map { it.dateKey }
            .sorted()

    private fun otherDayInSameMonth(today: String): String {
        val day = today.takeLast(2).toInt()
        val partner = if (day < 28) day + 1 else day - 1
        return today.dropLast(2) + String.format(Locale.US, "%02d", partner)
    }

    private fun instance(dateKey: String, title: String, hour: Int = 10, minute: Int = 0): InstanceRow {
        val start = millisOn(dateKey, hour, minute)
        return InstanceRow(
            eventId = EVENT_ID,
            calendarId = CALENDAR_ID,
            title = title,
            beginMillis = start,
            endMillis = start + 60 * 60 * 1000,
            allDay = false,
        )
    }

    private fun millisOn(dateKey: String, hour: Int, minute: Int): Long {
        val cal = Calendar.getInstance()
        cal.time = dayFormat.parse(dateKey)!!
        cal.set(Calendar.HOUR_OF_DAY, hour)
        cal.set(Calendar.MINUTE, minute)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    private class FakeCalendarProvider : ContentProvider() {
        var instances: List<InstanceRow> = emptyList()
        var eventDeleted: Boolean = false
        var eventAbsent: Boolean = false

        override fun onCreate(): Boolean = true

        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            selection: String?,
            selectionArgs: Array<out String>?,
            sortOrder: String?,
        ): Cursor {
            val path = uri.path.orEmpty()
            if (!path.contains("instances")) return eventsCursor(projection)
            val cursor = MatrixCursor(
                arrayOf(
                    CalendarContract.Instances.EVENT_ID,
                    CalendarContract.Instances.CALENDAR_ID,
                    CalendarContract.Instances.TITLE,
                    CalendarContract.Instances.BEGIN,
                    CalendarContract.Instances.END,
                    CalendarContract.Instances.ALL_DAY,
                ),
            )
            instances.forEach { row ->
                cursor.addRow(
                    arrayOf<Any>(row.eventId, row.calendarId, row.title, row.beginMillis, row.endMillis, 0),
                )
            }
            return cursor
        }

        private fun eventsCursor(projection: Array<out String>?): Cursor {
            val columns = projection ?: arrayOf(CalendarContract.Events._ID)
            val cursor = MatrixCursor(columns)
            if (eventAbsent) return cursor
            val row = arrayOfNulls<Any>(columns.size)
            columns.forEachIndexed { index, column ->
                row[index] = when (column) {
                    CalendarContract.Events._ID -> EVENT_ID
                    CalendarContract.Events.DELETED -> if (eventDeleted) 1 else 0
                    CalendarContract.Events.STATUS -> if (eventDeleted) {
                        CalendarContract.Events.STATUS_CANCELED
                    } else {
                        CalendarContract.Events.STATUS_CONFIRMED
                    }
                    else -> null
                }
            }
            cursor.addRow(row)
            return cursor
        }

        override fun getType(uri: Uri): String? = null

        override fun insert(uri: Uri, values: ContentValues?): Uri? = null

        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

        override fun update(
            uri: Uri,
            values: ContentValues?,
            selection: String?,
            selectionArgs: Array<out String>?,
        ): Int = 0
    }

    private data class InstanceRow(
        val eventId: Long,
        val calendarId: Long,
        val title: String,
        val beginMillis: Long,
        val endMillis: Long,
        val allDay: Boolean,
    )

    companion object {
        private const val EVENT_ID = 42L
        private const val CALENDAR_ID = 7L
    }
}
