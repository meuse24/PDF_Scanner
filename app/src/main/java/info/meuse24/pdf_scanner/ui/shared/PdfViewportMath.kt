package info.meuse24.pdf_scanner.ui.shared

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.IntSize
import java.util.Locale

internal fun normalizeViewportPoint(
    offset: Offset,
    canvasSize: IntSize,
    scale: Float,
    offsetX: Float,
    offsetY: Float,
    pdfOrigin: Offset = Offset.Zero,
    pdfDisplaySize: Size = Size.Zero
): Pair<Float, Float> {
    val contentOffset = mapViewportOffsetToCanvasOffset(
        offset = offset,
        canvasSize = canvasSize,
        scale = scale,
        offsetX = offsetX,
        offsetY = offsetY
    )
    val pdfWidth = if (pdfDisplaySize.width > 0f) pdfDisplaySize.width else canvasSize.width.toFloat()
    val pdfHeight = if (pdfDisplaySize.height > 0f) pdfDisplaySize.height else canvasSize.height.toFloat()
    return Pair(
        ((contentOffset.x - pdfOrigin.x) / pdfWidth).coerceIn(0f, 1f),
        ((contentOffset.y - pdfOrigin.y) / pdfHeight).coerceIn(0f, 1f)
    )
}

internal fun mapViewportOffsetToCanvasOffset(
    offset: Offset,
    canvasSize: IntSize,
    scale: Float,
    offsetX: Float,
    offsetY: Float
): Offset {
    if (canvasSize == IntSize.Zero) return Offset.Zero
    val width = canvasSize.width.toFloat()
    val height = canvasSize.height.toFloat()
    val clampedScale = scale.coerceAtLeast(1f)
    val centerX = width / 2f
    val centerY = height / 2f
    val unscaledX = centerX + ((offset.x - offsetX) - centerX) / clampedScale
    val unscaledY = centerY + ((offset.y - offsetY) - centerY) / clampedScale
    return Offset(
        x = unscaledX.coerceIn(0f, width),
        y = unscaledY.coerceIn(0f, height)
    )
}

internal fun clampPanOffset(
    canvasSize: IntSize,
    scale: Float,
    offsetX: Float,
    offsetY: Float
): Offset {
    val maxX = canvasSize.width * (scale - 1f) / 2f
    val maxY = canvasSize.height * (scale - 1f) / 2f
    return Offset(
        x = offsetX.coerceIn(-maxX, maxX),
        y = offsetY.coerceIn(-maxY, maxY)
    )
}

internal fun formatZoomScale(scale: Float): String = String.format(Locale.US, "%.1f", scale)
