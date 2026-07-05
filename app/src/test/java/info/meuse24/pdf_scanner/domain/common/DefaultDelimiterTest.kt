package info.meuse24.pdf_scanner.domain.common

import info.meuse24.pdf_scanner.domain.model.CsvDelimiter
import org.junit.Assert.assertEquals
import org.junit.Test

class DefaultDelimiterTest {

    @Test
    fun `Dezimalkomma ergibt Semikolon-Default`() {
        assertEquals(CsvDelimiter.SEMICOLON, defaultCsvDelimiterFor(','))
    }

    @Test
    fun `Dezimalpunkt ergibt Komma-Default`() {
        assertEquals(CsvDelimiter.COMMA, defaultCsvDelimiterFor('.'))
    }

    @Test
    fun `unbekanntes Dezimaltrennzeichen ergibt Komma-Default`() {
        assertEquals(CsvDelimiter.COMMA, defaultCsvDelimiterFor('٫'))
    }
}
