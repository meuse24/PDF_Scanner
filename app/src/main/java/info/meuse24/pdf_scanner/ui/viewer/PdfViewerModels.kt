package info.meuse24.pdf_scanner.ui.viewer

import android.graphics.Bitmap
import info.meuse24.pdf_scanner.data.local.ScanRecord

data class PdfViewerUiState(
    val record: ScanRecord? = null,
    val loading: Boolean = true,
    val errorMessage: String? = null,
    val transientMessage: String? = null,
    val pageCount: Int = 0,
    val currentPageIndex: Int = 0,
    val zoomScale: Float = 1f,
    val pages: Map<Int, PdfViewerPageState> = emptyMap()
)

data class PdfViewerPageState(
    val pageIndex: Int,
    val widthPt: Int? = null,
    val heightPt: Int? = null,
    val bitmap: Bitmap? = null,
    val renderedWidthPx: Int = 0,
    val loading: Boolean = false,
    val errorMessage: String? = null
) {
    val aspectRatio: Float
        get() {
            val width = widthPt ?: return PDF_VIEWER_DEFAULT_ASPECT_RATIO
            val height = heightPt ?: return PDF_VIEWER_DEFAULT_ASPECT_RATIO
            if (width <= 0 || height <= 0) return PDF_VIEWER_DEFAULT_ASPECT_RATIO
            return width.toFloat() / height.toFloat()
        }
}

internal const val PDF_VIEWER_DEFAULT_ASPECT_RATIO = 0.707f
internal const val PDF_VIEWER_PREFETCH_DISTANCE = 1
internal const val PDF_VIEWER_FIT_MAX_BITMAP_SIDE_PX = 2_048
internal const val PDF_VIEWER_ZOOM_MAX_BITMAP_SIDE_PX = 4_096
