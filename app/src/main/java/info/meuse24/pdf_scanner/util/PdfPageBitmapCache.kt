package info.meuse24.pdf_scanner.util

import android.graphics.Bitmap
import java.util.LinkedHashMap

data class PdfPageBitmapCacheKey(
    val pageIndex: Int,
    val targetWidthPx: Int
)

class PdfPageBitmapCache(
    private val maxBytes: Int = defaultMaxBytes(),
    private val onEvict: ((PdfPageBitmapCacheKey) -> Unit)? = null
) {
    private val entries = LinkedHashMap<PdfPageBitmapCacheKey, RenderedPdfPage>(0, 0.75f, true)
    private var currentBytes = 0

    @Synchronized
    fun get(key: PdfPageBitmapCacheKey): RenderedPdfPage? = entries[key]

    fun put(key: PdfPageBitmapCacheKey, page: RenderedPdfPage) {
        val evicted = synchronized(this) {
            entries.remove(key)?.let { currentBytes -= it.bitmap.cacheSizeBytes() }
            entries[key] = page
            currentBytes += page.bitmap.cacheSizeBytes()
            trimToBudget()
        }
        evicted.forEach { onEvict?.invoke(it) }
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

    // Must be called while holding the object's intrinsic lock (from within synchronized(this)).
    private fun trimToBudget(): List<PdfPageBitmapCacheKey> {
        val evicted = mutableListOf<PdfPageBitmapCacheKey>()
        val iterator = entries.iterator()
        while (currentBytes > maxBytes && iterator.hasNext()) {
            val eldest = iterator.next()
            currentBytes -= eldest.value.bitmap.cacheSizeBytes()
            evicted += eldest.key
            iterator.remove()
        }
        return evicted
    }
}

private fun Bitmap.cacheSizeBytes(): Int = runCatching { allocationByteCount }.getOrElse { byteCount }

private fun defaultMaxBytes(): Int {
    val maxMemory = Runtime.getRuntime().maxMemory().coerceAtLeast(1L)
    return minOf(maxMemory / 8L, 96L * 1024L * 1024L).toInt().coerceAtLeast(8 * 1024 * 1024)
}
