package info.meuse24.pdf_scanner.util

import android.content.Intent
import android.provider.CalendarContract
import java.time.LocalDate
import java.time.ZoneId

fun buildCalendarInsertIntent(
    title: String,
    date: LocalDate,
    zoneId: ZoneId = ZoneId.systemDefault()
): Intent {
    val startMillis = date.atStartOfDay(zoneId).toInstant().toEpochMilli()
    val endMillis = date.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
    return Intent(Intent.ACTION_INSERT).apply {
        data = CalendarContract.Events.CONTENT_URI
        putExtra(CalendarContract.Events.TITLE, title)
        putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, startMillis)
        putExtra(CalendarContract.EXTRA_EVENT_END_TIME, endMillis)
        putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, true)
    }
}
