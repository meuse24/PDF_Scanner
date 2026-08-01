package info.meuse24.pdf_scanner.ui.viewer

import android.graphics.Bitmap
import info.meuse24.pdf_scanner.domain.common.DetectedEntities
import info.meuse24.pdf_scanner.domain.common.PdfSearchMatch
import info.meuse24.pdf_scanner.domain.pdf.NormalizedBox
import info.meuse24.pdf_scanner.domain.model.Document
import info.meuse24.pdf_scanner.domain.model.AcroFormCapability

data class PdfViewerUiState(
    val record: Document? = null,
    val loading: Boolean = true,
    val errorMessage: String? = null,
    val transientMessage: String? = null,
    val pageCount: Int = 0,
    val currentPageIndex: Int = 0,
    val zoomScale: Float = 1f,
    val pages: Map<Int, PdfViewerPageState> = emptyMap(),
    val detectedEntities: DetectedEntities = DetectedEntities(),
    val pageSearchAvailable: Boolean = false,
    val searchExtractionRunning: Boolean = false,
    val searchActive: Boolean = false,
    val searchQuery: String = "",
    val searchMatches: List<PdfSearchMatch> = emptyList(),
    val searchCurrentIndex: Int = -1,
    val searching: Boolean = false,
    val searchHighlights: PdfSearchHighlights? = null,
    val formCapability: AcroFormCapability = AcroFormCapability.NONE
)

data class PdfSearchHighlights(
    val pageIndex: Int,
    val active: List<NormalizedBox>,
    val others: List<NormalizedBox>
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
internal const val PDF_VIEWER_ZOOM_MAX_BITMAP_SIDE_PX = 3_072
internal const val PDF_VIEWER_MAX_ZOOM_SCALE = 5f

