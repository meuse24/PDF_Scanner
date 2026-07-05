package info.meuse24.pdf_scanner.domain.common

import info.meuse24.pdf_scanner.domain.model.CsvDelimiter

/**
 * Lokalisierter Delimiter-Default: keine hart codierte Sprachliste, sondern eine Näherung über
 * das Dezimaltrennzeichen der aktuellen Format-Locale. Dezimalkomma → Semikolon (der übliche
 * Listentrenner in diesen Locales), alles andere → Komma. Pure Domain-Funktion ohne
 * Android-/Locale-Import; die Ermittlung des tatsächlichen Dezimaltrennzeichens ist
 * Aufgabe der Android-Schicht (siehe `util/CsvDelimiterDefaults.kt`).
 */
fun defaultCsvDelimiterFor(decimalSeparator: Char): CsvDelimiter =
    if (decimalSeparator == ',') CsvDelimiter.SEMICOLON else CsvDelimiter.COMMA
