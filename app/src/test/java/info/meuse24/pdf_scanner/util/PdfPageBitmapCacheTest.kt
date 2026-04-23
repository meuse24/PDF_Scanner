package info.meuse24.pdf_scanner.util

import android.graphics.Bitmap
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class PdfPageBitmapCacheTest {

    @Test
    fun `cache evicts least recently used pages when byte budget is exceeded`() {
        val cache = PdfPageBitmapCache(maxBytes = 10)
        val first = renderedPage(pageIndex = 0, targetWidthPx = 100, bytes = 6)
        val second = renderedPage(pageIndex = 1, targetWidthPx = 100, bytes = 6)

        cache.put(PdfPageBitmapCacheKey(0, 100), first)
        cache.put(PdfPageBitmapCacheKey(1, 100), second)

        assertNull(cache.get(PdfPageBitmapCacheKey(0, 100)))
        assertSame(second, cache.get(PdfPageBitmapCacheKey(1, 100)))
    }

    @Test
    fun `retainPages removes cached bitmaps outside current viewer window`() {
        val cache = PdfPageBitmapCache(maxBytes = 100)
        val first = renderedPage(pageIndex = 0, targetWidthPx = 100, bytes = 6)
        val second = renderedPage(pageIndex = 1, targetWidthPx = 100, bytes = 6)

        cache.put(PdfPageBitmapCacheKey(0, 100), first)
        cache.put(PdfPageBitmapCacheKey(1, 100), second)
        cache.retainPages(setOf(1))

        assertNull(cache.get(PdfPageBitmapCacheKey(0, 100)))
        assertSame(second, cache.get(PdfPageBitmapCacheKey(1, 100)))
    }

    @Test
    fun `overwriting same key updates byte budget before eviction`() {
        val cache = PdfPageBitmapCache(maxBytes = 10)
        val key = PdfPageBitmapCacheKey(0, 100)
        val large = renderedPage(pageIndex = 0, targetWidthPx = 100, bytes = 9)
        val small = renderedPage(pageIndex = 0, targetWidthPx = 100, bytes = 4)
        val second = renderedPage(pageIndex = 1, targetWidthPx = 100, bytes = 5)

        cache.put(key, large)
        cache.put(key, small)
        cache.put(PdfPageBitmapCacheKey(1, 100), second)

        assertSame(small, cache.get(key))
        assertSame(second, cache.get(PdfPageBitmapCacheKey(1, 100)))
    }

    private fun renderedPage(
        pageIndex: Int,
        targetWidthPx: Int,
        bytes: Int
    ): RenderedPdfPage {
        val bitmap = mock(Bitmap::class.java)
        `when`(bitmap.allocationByteCount).thenReturn(bytes)
        return RenderedPdfPage(
            pageIndex = pageIndex,
            widthPt = 600,
            heightPt = 800,
            renderedWidthPx = targetWidthPx,
            bitmap = bitmap
        )
    }
}
