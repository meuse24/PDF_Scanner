package info.meuse24.pdf_scanner.domain.model

import kotlin.math.max
import kotlin.math.min

data class OcrRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f
    val isValid: Boolean get() = width > 0f && height > 0f

    fun horizontalOverlap(other: OcrRect): Float =
        max(0f, min(right, other.right) - max(left, other.left))

    fun verticalOverlap(other: OcrRect): Float =
        max(0f, min(bottom, other.bottom) - max(top, other.top))

    /** Vertikale Überlappung relativ zur kleineren der beiden Höhen. */
    fun verticalOverlapRatio(other: OcrRect): Float {
        val minHeight = min(height, other.height)
        if (minHeight <= 0f) return 0f
        return verticalOverlap(other) / minHeight
    }

    /** Horizontale Überlappung relativ zur kleineren der beiden Breiten. */
    fun horizontalOverlapRatio(other: OcrRect): Float {
        val minWidth = min(width, other.width)
        if (minWidth <= 0f) return 0f
        return horizontalOverlap(other) / minWidth
    }
}

data class OcrElement(
    val text: String,
    val bounds: OcrRect,
    val confidence: Float,
    val angleDeg: Float
)

data class OcrLine(
    val text: String,
    val bounds: OcrRect,
    val elements: List<OcrElement>,
    val confidence: Float,
    val angleDeg: Float
)

data class OcrPositionedPage(
    val pageIndex: Int,
    val widthPx: Int,
    val heightPx: Int,
    val lines: List<OcrLine>
)
