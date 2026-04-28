package info.meuse24.pdf_scanner.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.core.graphics.createBitmap
import info.meuse24.pdf_scanner.domain.gateway.DispatcherProvider
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import kotlin.math.roundToInt

private const val PDF_VIEWER_MIN_RENDER_WIDTH_PX = 64
private const val PDF_VIEWER_MAX_RENDER_SIDE_PX = 4_096
private const val PDF_VIEWER_OOM_RETRY_DIVISOR = 2

data class RenderedPdfPage(
    val pageIndex: Int,
    val widthPt: Int,
    val heightPt: Int,
    val renderedWidthPx: Int,
    val bitmap: Bitmap
)

enum class PdfPageBitmapRenderFailure {
    FileMissing,
    FileEncrypted,
    FileCorrupted,
    InvalidPage,
    OutOfMemory,
    RendererFailure
}

class PdfPageBitmapRenderException(
    val failure: PdfPageBitmapRenderFailure,
    cause: Throwable? = null
) : Exception(cause)

interface PdfDocumentBitmapHandle : AutoCloseable {
    val pageCount: Int

    suspend fun renderPage(
        pageIndex: Int,
        targetWidthPx: Int,
        maxBitmapSidePx: Int = PDF_VIEWER_MAX_RENDER_SIDE_PX
    ): RenderedPdfPage
}

interface PdfPageBitmapRenderer {
    suspend fun openDocument(file: File): PdfDocumentBitmapHandle
}

class AndroidPdfPageBitmapRenderer @Inject constructor(
    private val dispatcherProvider: DispatcherProvider
) : PdfPageBitmapRenderer {

    override suspend fun openDocument(file: File): PdfDocumentBitmapHandle =
        withContext(dispatcherProvider.io) {
            if (!file.exists()) {
                throw PdfPageBitmapRenderException(PdfPageBitmapRenderFailure.FileMissing)
            }

            val pfd = try {
                ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            } catch (exception: SecurityException) {
                throw PdfPageBitmapRenderException(PdfPageBitmapRenderFailure.FileEncrypted, exception)
            } catch (exception: IOException) {
                throw PdfPageBitmapRenderException(PdfPageBitmapRenderFailure.FileCorrupted, exception)
            } catch (exception: RuntimeException) {
                throw PdfPageBitmapRenderException(PdfPageBitmapRenderFailure.RendererFailure, exception)
            }

            try {
                AndroidPdfDocumentBitmapHandle(
                    renderer = PdfRenderer(pfd),
                    pfd = pfd,
                    dispatcherProvider = dispatcherProvider
                )
            } catch (exception: SecurityException) {
                runCatching { pfd.close() }
                throw PdfPageBitmapRenderException(PdfPageBitmapRenderFailure.FileEncrypted, exception)
            } catch (exception: IOException) {
                runCatching { pfd.close() }
                throw PdfPageBitmapRenderException(PdfPageBitmapRenderFailure.FileCorrupted, exception)
            } catch (exception: RuntimeException) {
                runCatching { pfd.close() }
                throw PdfPageBitmapRenderException(PdfPageBitmapRenderFailure.RendererFailure, exception)
            }
        }
}

private class AndroidPdfDocumentBitmapHandle(
    private val renderer: PdfRenderer,
    private val pfd: ParcelFileDescriptor,
    private val dispatcherProvider: DispatcherProvider
) : PdfDocumentBitmapHandle {

    private val renderMutex = Mutex()
    @Volatile private var closed = false

    override val pageCount: Int
        get() = renderer.pageCount

    override suspend fun renderPage(
        pageIndex: Int,
        targetWidthPx: Int,
        maxBitmapSidePx: Int
    ): RenderedPdfPage = withContext(dispatcherProvider.io) {
        renderMutex.withLock {
            if (closed) {
                throw PdfPageBitmapRenderException(PdfPageBitmapRenderFailure.RendererFailure)
            }
            if (pageIndex !in 0 until renderer.pageCount) {
                throw PdfPageBitmapRenderException(PdfPageBitmapRenderFailure.InvalidPage)
            }

            try {
                renderer.openPage(pageIndex).use { page ->
                    renderPageBitmap(
                        page = page,
                        pageIndex = pageIndex,
                        targetWidthPx = targetWidthPx,
                        maxBitmapSidePx = maxBitmapSidePx
                    )
                }
            } catch (exception: OutOfMemoryError) {
                throw PdfPageBitmapRenderException(PdfPageBitmapRenderFailure.OutOfMemory, exception)
            } catch (exception: SecurityException) {
                throw PdfPageBitmapRenderException(PdfPageBitmapRenderFailure.FileEncrypted, exception)
            } catch (exception: IllegalStateException) {
                throw PdfPageBitmapRenderException(PdfPageBitmapRenderFailure.RendererFailure, exception)
            } catch (exception: RuntimeException) {
                throw PdfPageBitmapRenderException(PdfPageBitmapRenderFailure.RendererFailure, exception)
            }
        }
    }

    @Synchronized
    override fun close() {
        if (closed) return
        closed = true
        runCatching { renderer.close() }
        runCatching { pfd.close() }
    }

    private fun renderPageBitmap(
        page: PdfRenderer.Page,
        pageIndex: Int,
        targetWidthPx: Int,
        maxBitmapSidePx: Int
    ): RenderedPdfPage {
        val dimensions = bitmapDimensions(
            pageWidthPt = page.width,
            pageHeightPt = page.height,
            targetWidthPx = targetWidthPx,
            maxBitmapSidePx = maxBitmapSidePx
        )

        return try {
            renderWithConfig(page, pageIndex, dimensions.first, dimensions.second, Bitmap.Config.ARGB_8888)
        } catch (exception: OutOfMemoryError) {
            val retryWidth = (dimensions.first / PDF_VIEWER_OOM_RETRY_DIVISOR)
                .coerceAtLeast(PDF_VIEWER_MIN_RENDER_WIDTH_PX)
            val retryHeight = (dimensions.second / PDF_VIEWER_OOM_RETRY_DIVISOR)
                .coerceAtLeast(PDF_VIEWER_MIN_RENDER_WIDTH_PX)
            renderWithConfig(page, pageIndex, retryWidth, retryHeight, Bitmap.Config.RGB_565)
        }
    }

    private fun renderWithConfig(
        page: PdfRenderer.Page,
        pageIndex: Int,
        widthPx: Int,
        heightPx: Int,
        config: Bitmap.Config
    ): RenderedPdfPage {
        val bitmap = createBitmap(widthPx, heightPx, config)
        try {
            Canvas(bitmap).drawColor(Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            return RenderedPdfPage(
                pageIndex = pageIndex,
                widthPt = page.width,
                heightPt = page.height,
                renderedWidthPx = widthPx,
                bitmap = bitmap
            )
        } catch (exception: Throwable) {
            bitmap.recycle()
            throw exception
        }
    }
}

private fun bitmapDimensions(
    pageWidthPt: Int,
    pageHeightPt: Int,
    targetWidthPx: Int,
    maxBitmapSidePx: Int
): Pair<Int, Int> {
    val safeMaxSide = maxBitmapSidePx.coerceAtLeast(PDF_VIEWER_MIN_RENDER_WIDTH_PX)
    val width = targetWidthPx
        .coerceAtLeast(PDF_VIEWER_MIN_RENDER_WIDTH_PX)
        .coerceAtMost(safeMaxSide)
    val pageRatio = pageHeightPt.toFloat() / pageWidthPt.toFloat().coerceAtLeast(1f)
    val rawHeight = (width * pageRatio).roundToInt().coerceAtLeast(1)

    if (rawHeight <= safeMaxSide) return width to rawHeight

    val scale = safeMaxSide.toFloat() / rawHeight.toFloat()
    val scaledWidth = (width * scale).roundToInt().coerceAtLeast(PDF_VIEWER_MIN_RENDER_WIDTH_PX)
    return scaledWidth to safeMaxSide
}
