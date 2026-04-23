package info.meuse24.pdf_scanner.util

import android.graphics.Bitmap
import java.util.LinkedHashMap

data class PdfPageBitmapCacheKey(
    val pageIndex: Int,
    val targetWidthPx: Int
)

class PdfPageBitmapCache(
    private val maxBytes: Int = defaultMaxBytes()
) {
    private val entries = LinkedHashMap<PdfPageBitmapCacheKey, RenderedPdfPage>(0, 0.75f, true)
    private var currentBytes = 0

    @Synchronized
    fun get(key: PdfPageBitmapCacheKey): RenderedPdfPage? = entries[key]

    @Synchronized
    fun put(key: PdfPageBitmapCacheKey, page: RenderedPdfPage) {
        entries.remove(key)?.let { currentBytes -= it.bitmap.cacheSizeBytes() }
        entries[key] = page
        currentBytes += page.bitmap.cacheSizeBytes()
        trimToBudget()
    }

    @Synchronized
    fun retainPages(pageIndexes: Set<Int>) {
        val iterator = entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.key.pageIndex !in pageIndexes) {
                currentBytes -= entry.value.bitmap.cacheSizeBytes()
                iterator.remove()
            }
        }
    }

    @Synchronized
    fun clear() {
        entries.clear()
        currentBytes = 0
    }

    private fun trimToBudget() {
        val iterator = entries.iterator()
        while (currentBytes > maxBytes && iterator.hasNext()) {
            val eldest = iterator.next()
            currentBytes -= eldest.value.bitmap.cacheSizeBytes()
            iterator.remove()
        }
    }
}

private fun Bitmap.cacheSizeBytes(): Int = runCatching { allocationByteCount }.getOrElse { byteCount }

private fun defaultMaxBytes(): Int {
    val maxMemory = Runtime.getRuntime().maxMemory().coerceAtLeast(1L)
    return minOf(maxMemory / 8L, 96L * 1024L * 1024L).toInt().coerceAtLeast(8 * 1024 * 1024)
}
