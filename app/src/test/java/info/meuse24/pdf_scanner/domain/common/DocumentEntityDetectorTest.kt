package info.meuse24.pdf_scanner.domain.common

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentEntityDetectorTest {

    @Test
    fun `detects IBAN and euro amount across OCR whitespace`() {
        val result = detectDocumentEntities(
            "IBAN DE89 3704 0044\n0532 0130 00\nGesamtbetrag 1.234,56 EUR"
        )

        assertEquals(listOf("DE89 3704 0044 0532 0130 00"), result.ibans)
        assertEquals(listOf("1.234,56 EUR"), result.amounts)
    }

    @Test
    fun `prioritizes date near deadline context`() {
        val result = detectDocumentEntities(
            "Rechnungsdatum 01.06.2026. Zahlbar bis 15.06.2026."
        )

        assertEquals(
            listOf(LocalDate.of(2026, 6, 15), LocalDate.of(2026, 6, 1)),
            result.dates
        )
    }

    @Test
    fun `parses ISO and two digit year and discards invalid dates`() {
        val result = detectDocumentEntities(
            "Erstellt 2026-07-01, Termin 02.07.26, Fehler 31.02.2026"
        )

        assertEquals(
            listOf(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 2)),
            result.dates
        )
    }

    @Test
    fun `maps older two digit years to the twentieth century`() {
        val result = detectDocumentEntities("Geburtsdatum 12.05.98, Termin 02.07.26")

        assertEquals(
            listOf(LocalDate.of(1998, 5, 12), LocalDate.of(2026, 7, 2)),
            result.dates
        )
    }

    @Test
    fun `accepts IBAN directly before punctuation`() {
        val result = detectDocumentEntities("IBAN: DE89 3704 0044 0532 0130 00, danke.")

        assertEquals(listOf("DE89 3704 0044 0532 0130 00"), result.ibans)
    }

    @Test
    fun `prioritizes localized deadline context`() {
        val result = detectDocumentEntities(
            "Fecha 01.06.2026. Fecha de vencimiento 15.06.2026."
        )

        assertEquals(
            listOf(LocalDate.of(2026, 6, 15), LocalDate.of(2026, 6, 1)),
            result.dates
        )
    }

    @Test
    fun `returns distinct values capped per type`() {
        val result = detectDocumentEntities(
            "1,00 EUR 1,00 EUR 2,00 EUR 3,00 EUR 4,00 EUR"
        )

        assertEquals(listOf("1,00 EUR", "2,00 EUR", "3,00 EUR"), result.amounts)
    }

    @Test
    fun `blank and irrelevant text return empty entities`() {
        assertTrue(detectDocumentEntities("").isEmpty)
        assertTrue(detectDocumentEntities("Ein Text ohne strukturierte Daten.").isEmpty)
    }
}
