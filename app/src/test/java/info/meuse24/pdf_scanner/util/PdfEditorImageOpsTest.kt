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

    @Test
    fun `photoPageRectangle keeps landscape photo inside max page`() {
        val rect = photoPageRectangle(4000f, 3000f, PdfPageSetup(PdfPageSizePreset.ISO_A4))
        val expectedScale = PDRectangle.A4.width / 3000f

        assertEquals(4000f * expectedScale, rect.width, DELTA)
        assertEquals(PDRectangle.A4.width, rect.height, DELTA)
    }

    @Test
    fun `photoPageRectangle keeps portrait photo inside max page`() {
        val rect = photoPageRectangle(3000f, 4000f, PdfPageSetup(PdfPageSizePreset.ISO_A4))
        val expectedScale = PDRectangle.A4.width / 3000f

        assertEquals(PDRectangle.A4.width, rect.width, DELTA)
        assertEquals(4000f * expectedScale, rect.height, DELTA)
    }

    @Test
    fun `photoPageRectangle treats square image as square page`() {
        val rect = photoPageRectangle(2000f, 2000f, PdfPageSetup(PdfPageSizePreset.ISO_A4))

        assertEquals(PDRectangle.A4.width, rect.width, DELTA)
        assertEquals(PDRectangle.A4.width, rect.height, DELTA)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `photoPageRectangle rejects invalid image dimensions`() {
        photoPageRectangle(0f, 100f, PdfPageSetup())
    }

    private fun assertRectangle(expected: PDRectangle, actual: PDRectangle) {
        assertEquals(expected.width, actual.width, DELTA)
        assertEquals(expected.height, actual.height, DELTA)
    }

    private companion object {
        const val DELTA = 0.01f
    }
}
