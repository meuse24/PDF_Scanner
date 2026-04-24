package info.meuse24.pdf_scanner.util

import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import info.meuse24.pdf_scanner.domain.model.PdfMarginPreset
import info.meuse24.pdf_scanner.domain.model.PdfPageOrientation
import info.meuse24.pdf_scanner.domain.model.PdfPageSetup
import info.meuse24.pdf_scanner.domain.model.PdfPageSizePreset
import info.meuse24.pdf_scanner.domain.usecase.ImagePageLayout
import info.meuse24.pdf_scanner.domain.usecase.ImagePdfOptions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfEditorImageOpsTest {

    @Test
    fun `pageRectangle returns expected portrait sizes`() {
        assertRectangle(PDRectangle.A3, pageRectangle(PdfPageSetup(PdfPageSizePreset.ISO_A3)))
        assertRectangle(PDRectangle.A4, pageRectangle(PdfPageSetup(PdfPageSizePreset.ISO_A4)))
        assertRectangle(PDRectangle.A5, pageRectangle(PdfPageSetup(PdfPageSizePreset.ISO_A5)))
        assertRectangle(PDRectangle.LETTER, pageRectangle(PdfPageSetup(PdfPageSizePreset.NA_LETTER)))
    }

    @Test
    fun `pageRectangle swaps width and height for landscape`() {
        val portrait = PDRectangle.A4
        val landscape = pageRectangle(
            PdfPageSetup(
                sizePreset = PdfPageSizePreset.ISO_A4,
                orientation = PdfPageOrientation.LANDSCAPE
            )
        )

        assertEquals(portrait.height, landscape.width, DELTA)
        assertEquals(portrait.width, landscape.height, DELTA)
    }

    @Test
    fun `marginPoints maps presets to point values`() {
        assertEquals(10f, marginPoints(PdfPageSetup(marginPreset = PdfMarginPreset.SMALL)), DELTA)
        assertEquals(20f, marginPoints(PdfPageSetup(marginPreset = PdfMarginPreset.MEDIUM)), DELTA)
        assertEquals(35f, marginPoints(PdfPageSetup(marginPreset = PdfMarginPreset.LARGE)), DELTA)
    }

    @Test
    fun `default single layout matches previous A4 portrait medium margin`() {
        val cells = layoutCells(ImagePdfOptions(ImagePageLayout.SINGLE, PdfPageSetup()))

        assertEquals(listOf(CellRect(20f, 20f, PDRectangle.A4.width - 40f, PDRectangle.A4.height - 40f)), cells)
    }

    @Test
    fun `layoutCells creates positive cells for every layout size and orientation`() {
        ImagePageLayout.entries.forEach { layout ->
            PdfPageSizePreset.entries.forEach { sizePreset ->
                PdfPageOrientation.entries.forEach { orientation ->
                    val cells = layoutCells(
                        ImagePdfOptions(
                            layout = layout,
                            pageSetup = PdfPageSetup(sizePreset = sizePreset, orientation = orientation)
                        )
                    )

                    assertEquals(layout.imagesPerPage, cells.size)
                    assertTrue(cells.all { it.w > 0f && it.h > 0f })
                }
            }
        }
    }

    private fun assertRectangle(expected: PDRectangle, actual: PDRectangle) {
        assertEquals(expected.width, actual.width, DELTA)
        assertEquals(expected.height, actual.height, DELTA)
    }

    private companion object {
        const val DELTA = 0.01f
    }
}
