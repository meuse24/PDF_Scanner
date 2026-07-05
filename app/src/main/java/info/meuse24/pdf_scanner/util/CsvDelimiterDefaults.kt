package info.meuse24.pdf_scanner.util

import info.meuse24.pdf_scanner.domain.common.defaultCsvDelimiterFor
import info.meuse24.pdf_scanner.domain.model.CsvDelimiter
import java.text.DecimalFormatSymbols
import java.util.Locale

/** Liest das Dezimaltrennzeichen der aktuellen Format-Locale und leitet daraus den CSV-Default ab. */
fun systemDefaultCsvDelimiter(): CsvDelimiter {
    val decimalSeparator = DecimalFormatSymbols.getInstance(Locale.getDefault(Locale.Category.FORMAT)).decimalSeparator
    return defaultCsvDelimiterFor(decimalSeparator)
}
