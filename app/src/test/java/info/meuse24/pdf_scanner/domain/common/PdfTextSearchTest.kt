package info.meuse24.pdf_scanner.domain.common

import org.junit.Assert.assertEquals
import org.junit.Test

class PdfTextSearchTest {

    @Test
    fun `finds matching pages case insensitively`() {
        assertEquals(
            listOf(0, 2),
            findMatchingPages(
                pageTexts = listOf("Erste Rechnung", "Lieferschein", "RECHNUNG bezahlt"),
                query = "rechnung"
            )
        )
    }

    @Test
    fun `trims query and returns pages in source order`() {
        assertEquals(
            listOf(1, 3),
            findMatchingPages(listOf("a", "needle", "b", "two needles"), "  needle  ")
        )
    }

    @Test
    fun `empty query or pages return no matches`() {
        assertEquals(emptyList<Int>(), findMatchingPages(listOf("text"), " "))
        assertEquals(emptyList<Int>(), findMatchingPages(emptyList(), "text"))
    }

    @Test
    fun `finds individual matches after normalizing hyphenated line breaks`() {
        assertEquals(
            listOf(PdfSearchMatch(pageIndex = 0, rawRange = 0..16)),
            findMatches(listOf("Rechnungs-\nnummer"), "rechnungsnummer")
        )
    }

    @Test
    fun `normalization retains a source index for each search character`() {
        val normalized = normalizeForSearch("A\u00A0\u00ADB  C")

        assertEquals("A B C", normalized.text)
        assertEquals(normalized.text.length, normalized.sourceIndex.size)
        assertEquals(0, normalized.sourceIndex.first())
        assertEquals(6, normalized.sourceIndex.last())
    }

    @Test
    fun `overlapping search text yields non-overlapping matches`() {
        assertEquals(
            listOf(PdfSearchMatch(pageIndex = 0, rawRange = 0..2)),
            findMatches(listOf("aaaaa"), "aaa")
        )
    }
}
