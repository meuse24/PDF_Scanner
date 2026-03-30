package info.meuse24.pdf_scanner.util

import org.junit.Assert.assertEquals
import org.junit.Test

class PdfPageInputImageLoaderTest {

    @Test
    fun `ocrBitmapSize keeps normal A4 pages at 150 dpi without cap`() {
        val (width, height) = ocrBitmapSize(pageWidthPts = 595, pageHeightPts = 842)

        assertEquals(1239, width)
        assertEquals(1754, height)
    }

    @Test
    fun `ocrBitmapSize caps oversized long edge and preserves aspect ratio`() {
        val (width, height) = ocrBitmapSize(pageWidthPts = 2384, pageHeightPts = 3370)

        assertEquals(PDF_OCR_MAX_BITMAP_SIDE, height)
        assertEquals(2122, width)
    }

    @Test
    fun `ocrBitmapSize never returns values below one pixel`() {
        val (width, height) = ocrBitmapSize(pageWidthPts = 0, pageHeightPts = 0)

        assertEquals(1, width)
        assertEquals(1, height)
    }
}
