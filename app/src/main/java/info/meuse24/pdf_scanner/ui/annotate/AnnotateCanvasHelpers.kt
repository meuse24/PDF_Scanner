package info.meuse24.pdf_scanner.ui.annotate

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import info.meuse24.pdf_scanner.domain.usecase.AnnotationOval
import info.meuse24.pdf_scanner.domain.usecase.AnnotationRect
import info.meuse24.pdf_scanner.domain.usecase.AnnotationShapeStyle
import info.meuse24.pdf_scanner.domain.usecase.AnnotationStroke

internal fun removeLastStrokeForPage(
    strokes: List<AnnotationStroke>,
    pageIndex: Int
): List<AnnotationStroke> {
    val lastIndex = strokes.indexOfLast { it.pageIndex == pageIndex }
    if (lastIndex < 0) return strokes
    return strokes.toMutableList().apply { removeAt(lastIndex) }
}

internal fun removeLastRectForPage(
    rects: List<AnnotationRect>,
    pageIndex: Int
): List<AnnotationRect> {
    val lastIndex = rects.indexOfLast { it.pageIndex == pageIndex }
    if (lastIndex < 0) return rects
    return rects.toMutableList().apply { removeAt(lastIndex) }
}

internal fun removeLastOvalForPage(
    ovals: List<AnnotationOval>,
    pageIndex: Int
): List<AnnotationOval> {
    val lastIndex = ovals.indexOfLast { it.pageIndex == pageIndex }
    if (lastIndex < 0) return ovals
    return ovals.toMutableList().apply { removeAt(lastIndex) }
}

internal fun removeLastCommentForPage(
    comments: List<AnnotationTextDraft>,
    pageIndex: Int
): List<AnnotationTextDraft> {
    val lastIndex = comments.indexOfLast { it.pageIndex == pageIndex }
    if (lastIndex < 0) return comments
    return comments.toMutableList().apply { removeAt(lastIndex) }
}

internal fun DrawScope.drawAnnotationStrokePreview(
    stroke: AnnotationStroke,
    pdfX: Float,
    pdfY: Float,
    pdfWidth: Float,
    pdfHeight: Float
) {
    if (stroke.points.isEmpty()) return

    val strokeWidthPx = pdfWidth * stroke.strokeWidthFraction.coerceIn(0.005f, 0.1f)
    val color = Color(stroke.color)
    val first = Offset(
        x = pdfX + stroke.points.first().first * pdfWidth,
        y = pdfY + stroke.points.first().second * pdfHeight
    )
    val path = Path().apply {
        moveTo(first.x, first.y)
        stroke.points.drop(1).forEach { (x, y) ->
            lineTo(pdfX + x * pdfWidth, pdfY + y * pdfHeight)
        }
    }
    drawPath(
        path = path,
        color = color,
        style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round, join = StrokeJoin.Round)
    )
    if (stroke.points.size == 1) {
        drawCircle(color = color, radius = strokeWidthPx / 2f, center = first)
    }
}

internal fun DrawScope.drawAnnotationRectPreview(
    rect: AnnotationRect,
    pdfX: Float,
    pdfY: Float,
    pdfWidth: Float,
    pdfHeight: Float
) {
    val topLeft = Offset(pdfX + rect.left * pdfWidth, pdfY + rect.top * pdfHeight)
    val size = Size((rect.right - rect.left) * pdfWidth, (rect.bottom - rect.top) * pdfHeight)
    val color = Color(rect.color)
    when (rect.style) {
        AnnotationShapeStyle.FILLED -> drawRect(color = color, topLeft = topLeft, size = size)
        AnnotationShapeStyle.FRAME -> drawRect(
            color = color,
            topLeft = topLeft,
            size = size,
            style = Stroke(width = (pdfWidth * rect.strokeWidthFraction).coerceIn(2f, 36f))
        )
    }
}

internal fun DrawScope.drawAnnotationOvalPreview(
    oval: AnnotationOval,
    pdfX: Float,
    pdfY: Float,
    pdfWidth: Float,
    pdfHeight: Float
) {
    val topLeft = Offset(pdfX + oval.left * pdfWidth, pdfY + oval.top * pdfHeight)
    val size = Size((oval.right - oval.left) * pdfWidth, (oval.bottom - oval.top) * pdfHeight)
    val color = Color(oval.color)
    when (oval.style) {
        AnnotationShapeStyle.FILLED -> drawOval(color = color, topLeft = topLeft, size = size, style = Fill)
        AnnotationShapeStyle.FRAME -> drawOval(
            color = color,
            topLeft = topLeft,
            size = size,
            style = Stroke(width = (pdfWidth * oval.strokeWidthFraction).coerceIn(2f, 36f))
        )
    }
}

internal fun DrawScope.drawAnnotationTextPreview(
    comment: AnnotationTextDraft,
    pdfX: Float,
    pdfY: Float,
    pdfWidth: Float,
    pdfHeight: Float
) {
    val anchorX = pdfX + comment.anchorX * pdfWidth
    val anchorY = pdfY + comment.anchorY * pdfHeight
    val textSizePx = (pdfWidth * comment.fontSizeFraction).coerceIn(10f, 72f)
    val textColor = android.graphics.Color.argb(
        255,
        android.graphics.Color.red(comment.color),
        android.graphics.Color.green(comment.color),
        android.graphics.Color.blue(comment.color)
    )
    val paint = android.graphics.Paint().apply {
        color = textColor
        textSize = textSizePx
        isAntiAlias = true
    }
    val lines = comment.text.split("\n")
    val lineHeight = textSizePx * 1.3f
    lines.forEachIndexed { index, line ->
        drawContext.canvas.nativeCanvas.drawText(line, anchorX, anchorY + lineHeight * index, paint)
    }
}

internal fun DrawScope.drawSelectionFramePreview(
    frame: AnnotationSelectionFrame,
    pdfX: Float,
    pdfY: Float,
    pdfWidth: Float,
    pdfHeight: Float
) {
    val topLeft = Offset(pdfX + frame.left * pdfWidth, pdfY + frame.top * pdfHeight)
    val size = Size(
        width = ((frame.right - frame.left) * pdfWidth).coerceAtLeast(14f),
        height = ((frame.bottom - frame.top) * pdfHeight).coerceAtLeast(14f)
    )
    val handleCenter = Offset(
        x = pdfX + frame.handleX * pdfWidth,
        y = pdfY + frame.handleY * pdfHeight
    )
    val accent = Color(0xFF1565C0)
    drawRoundRect(
        color = accent,
        topLeft = topLeft,
        size = size,
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(10f, 10f),
        style = Stroke(width = 3f)
    )
    drawCircle(color = Color.White, radius = 9f, center = handleCenter)
    drawCircle(color = accent, radius = 6f, center = handleCenter)
}
