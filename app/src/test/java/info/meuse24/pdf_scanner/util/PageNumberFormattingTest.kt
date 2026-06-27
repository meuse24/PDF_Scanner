package info.meuse24.pdf_scanner.util

import info.meuse24.pdf_scanner.domain.model.PageNumberSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PageNumberFormattingTest {

    @Test
    fun `default format contains only page number`() {
        assertEquals(
            "3",
            buildPageNumberLabel(
                pageNumber = 3,
                totalPageCount = 12,
                settings = PageNumberSettings()
            )
        )
    }

    @Test
    fun `prefix and total page count are formatted together`() {
        assertEquals(
            "Seite 3 / 12",
            buildPageNumberLabel(
                pageNumber = 3,
                totalPageCount = 12,
                settings = PageNumberSettings(
                    prefix = "  Seite  ",
                    includeTotalPageCount = true
                )
            )
        )
    }

    @Test
    fun `text fitting never exceeds available width`() {
        val (fontSize, textWidth) = fitPageNumberText(
            baseFontSize = 10f,
            textWidthAtUnitSize = 2f,
            availableWidth = 5f
        )

        assertEquals(2.5f, fontSize, 0.001f)
        assertTrue(textWidth <= 5f)
    }

    @Test
    fun `prefix longer than model limit is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            PageNumberSettings(
                prefix = "x".repeat(PageNumberSettings.MAX_PREFIX_LENGTH + 1)
            )
        }
    }
}
