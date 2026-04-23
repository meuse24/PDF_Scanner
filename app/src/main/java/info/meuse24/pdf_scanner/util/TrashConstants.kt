package info.meuse24.pdf_scanner.util

import java.util.concurrent.TimeUnit

object TrashConstants {
    const val RETENTION_DAYS = 30
    val RETENTION_MILLIS: Long = TimeUnit.DAYS.toMillis(RETENTION_DAYS.toLong())
}
