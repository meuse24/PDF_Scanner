package info.meuse24.pdf_scanner.util

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDFont
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import com.tom_roush.pdfbox.text.TextPosition
import com.tom_roush.pdfbox.util.Matrix
import info.meuse24.pdf_scanner.domain.usecase.AnnotationOval
import info.meuse24.pdf_scanner.domain.usecase.AnnotationRect
import info.meuse24.pdf_scanner.domain.usecase.AnnotationShapeStyle
import info.meuse24.pdf_scanner.domain.usecase.AnnotationStroke
import info.meuse24.pdf_scanner.domain.usecase.AnnotationText
import info.meuse24.pdf_scanner.domain.usecase.TextLine
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

internal const val HIGHLIGHT_RECT_ALPHA = 0.3f
private const val MIN_TEXT_FONT_SIZE_PT = 4f
private const val LINE_VERTICAL_MERGE_FACTOR = 0.6f

private data class PdfArgbColor(
    val alpha: Float,
    val red: Int,
    val green: Int,
    val blue: Int
)

private data class AnnotationPdfBounds(
    val left: Float,
    val bottom: Float,
    val width: Float,
    val height: Float
)

internal data class NormalizedTextBox(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    val centerY: Float get() = (top + bottom) / 2f
    val height: Float get() = bottom - top
}

private fun Int.toPdfArgbColor(): PdfArgbColor = PdfArgbColor(
    alpha = ((this ushr 24) and 0xFF) / 255f,
    red = (this ushr 16) and 0xFF,
    green = (this ushr 8) and 0xFF,
    blue = this and 0xFF
)

private fun normalizedAnnotationBounds(
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
    pageWidth: Float,
    pageHeight: Float,
    rotation: Int
): AnnotationPdfBounds? {
    val topLeft = mapDisplayToPdfCoord(left, top, pageWidth, pageHeight, rotation)
    val bottomRight = mapDisplayToPdfCoord(right, bottom, pageWidth, pageHeight, rotation)
    val rectLeft = minOf(topLeft.first, bottomRight.first)
    val rectBottom = minOf(topLeft.second, bottomRight.second)
    val rectWidth = abs(bottomRight.first - topLeft.first)
    val rectHeight = abs(bottomRight.second - topLeft.second)
    if (rectWidth <= 0.5f || rectHeight <= 0.5f) return null
    return AnnotationPdfBounds(rectLeft, rectBottom, rectWidth, rectHeight)
}

private fun appendEllipsePath(
    contentStream: PDPageContentStream,
    bounds: AnnotationPdfBounds,
    segments: Int = 32
) {
    val centerX = bounds.left + bounds.width / 2f
    val centerY = bounds.bottom + bounds.height / 2f
    val radiusX = bounds.width / 2f
    val radiusY = bounds.height / 2f
    if (radiusX <= 0f || radiusY <= 0f) return

    repeat(segments + 1) { index ->
        val angle = (index.toDouble() / segments.toDouble()) * Math.PI * 2.0
        val x = centerX + radiusX * cos(angle).toFloat()
        val y = centerY + radiusY * sin(angle).toFloat()
        if (index == 0) contentStream.moveTo(x, y) else contentStream.lineTo(x, y)
    }
    contentStream.closePath()
}

internal fun mergeTextBoxesToLines(
    boxes: List<NormalizedTextBox>,
    pageIndex: Int
): List<TextLine> {
    if (boxes.isEmpty()) return emptyList()

    val sorted = boxes.sortedWith(compareBy<NormalizedTextBox> { it.centerY }.thenBy { it.left })
    val groups = mutableListOf<MutableList<NormalizedTextBox>>()
    var currentGroup = mutableListOf(sorted.first())
    var currentCenterY = sorted.first().centerY
    var currentHeight = sorted.first().height

    sorted.drop(1).forEach { box ->
        val tolerance = maxOf(currentHeight, box.height) * LINE_VERTICAL_MERGE_FACTOR
        if (abs(box.centerY - currentCenterY) <= tolerance) {
            currentGroup += box
            currentCenterY = currentGroup.map { it.centerY }.average().toFloat()
            currentHeight = currentGroup.maxOf { it.height }
        } else {
            groups += currentGroup
            currentGroup = mutableListOf(box)
            currentCenterY = box.centerY
            currentHeight = box.height
        }
    }
    groups += currentGroup

    return groups.map { lineBoxes ->
        TextLine(
            left = lineBoxes.minOf { it.left },
            top = lineBoxes.minOf { it.top },
            right = lineBoxes.maxOf { it.right },
            bottom = lineBoxes.maxOf { it.bottom },
            pageIndex = pageIndex
        )
    }.sortedBy { it.top }
}

internal fun TextPosition.toNormalizedTextBox(
    displayedWidth: Float,
    displayedHeight: Float
): NormalizedTextBox? {
    if (displayedWidth <= 0f || displayedHeight <= 0f) return null
    if (fontSizeInPt < MIN_TEXT_FONT_SIZE_PT) return null

    val left = (xDirAdj / displayedWidth).coerceIn(0f, 1f)
    val top = ((yDirAdj - heightDir) / displayedHeight).coerceIn(0f, 1f)
    val right = ((xDirAdj + widthDirAdj) / displayedWidth).coerceIn(0f, 1f)
    val bottom = (yDirAdj / displayedHeight).coerceIn(0f, 1f)
    if (right <= left || bottom <= top) return null

    return NormalizedTextBox(left, top, right, bottom)
}

internal fun PdfEditor.appendAnnotationStroke(
    contentStream: PDPageContentStream,
    stroke: AnnotationStroke,
    displayedWidth: Float,
    pageWidth: Float,
    pageHeight: Float,
    rotation: Int
) {
    if (stroke.points.isEmpty()) return

    val color = stroke.color.toPdfArgbColor()
    contentStream.setGraphicsStateParameters(
        PDExtendedGraphicsState().apply { strokingAlphaConstant = color.alpha }
    )
    contentStream.setStrokingColor(color.red, color.green, color.blue)
    contentStream.setLineWidth((displayedWidth * stroke.strokeWidthFraction).coerceIn(3f, 36f))
    contentStream.setLineCapStyle(1)
    contentStream.setLineJoinStyle(1)

    if (stroke.points.size == 1) {
        val (px, py) = mapDisplayToPdfCoord(
            stroke.points[0].first,
            stroke.points[0].second,
            pageWidth,
            pageHeight,
            rotation
        )
        contentStream.moveTo(px - 0.5f, py)
        contentStream.lineTo(px + 0.5f, py)
        contentStream.stroke()
        return
    }

    val first = stroke.points.first()
    val (fx, fy) = mapDisplayToPdfCoord(first.first, first.second, pageWidth, pageHeight, rotation)
    contentStream.moveTo(fx, fy)
    stroke.points.drop(1).forEach { (nx, ny) ->
        val (px, py) = mapDisplayToPdfCoord(nx, ny, pageWidth, pageHeight, rotation)
        contentStream.lineTo(px, py)
    }
    contentStream.stroke()
}

internal fun PdfEditor.appendAnnotationRect(
    contentStream: PDPageContentStream,
    rect: AnnotationRect,
    displayedWidth: Float,
    pageWidth: Float,
    pageHeight: Float,
    rotation: Int
) {
    val bounds = normalizedAnnotationBounds(
        rect.left,
        rect.top,
        rect.right,
        rect.bottom,
        pageWidth,
        pageHeight,
        rotation
    ) ?: return

    val color = rect.color.toPdfArgbColor()
    when (rect.style) {
        AnnotationShapeStyle.FILLED -> {
            contentStream.setGraphicsStateParameters(
                PDExtendedGraphicsState().apply { nonStrokingAlphaConstant = color.alpha }
            )
            contentStream.setNonStrokingColor(color.red, color.green, color.blue)
            contentStream.addRect(bounds.left, bounds.bottom, bounds.width, bounds.height)
            contentStream.fill()
        }
        AnnotationShapeStyle.FRAME -> {
            contentStream.setGraphicsStateParameters(
                PDExtendedGraphicsState().apply { strokingAlphaConstant = color.alpha }
            )
            contentStream.setStrokingColor(color.red, color.green, color.blue)
            contentStream.setLineWidth((displayedWidth * rect.strokeWidthFraction).coerceIn(2f, 36f))
            contentStream.setLineJoinStyle(1)
            contentStream.addRect(bounds.left, bounds.bottom, bounds.width, bounds.height)
            contentStream.stroke()
        }
    }
}

internal fun PdfEditor.appendAnnotationOval(
    contentStream: PDPageContentStream,
    oval: AnnotationOval,
    displayedWidth: Float,
    pageWidth: Float,
    pageHeight: Float,
    rotation: Int
) {
    val bounds = normalizedAnnotationBounds(
        oval.left,
        oval.top,
        oval.right,
        oval.bottom,
        pageWidth,
        pageHeight,
        rotation
    ) ?: return

    val color = oval.color.toPdfArgbColor()
    when (oval.style) {
        AnnotationShapeStyle.FILLED -> {
            contentStream.setGraphicsStateParameters(
                PDExtendedGraphicsState().apply { nonStrokingAlphaConstant = color.alpha }
            )
            contentStream.setNonStrokingColor(color.red, color.green, color.blue)
            contentStream.setLineJoinStyle(1)
        }
        AnnotationShapeStyle.FRAME -> {
            contentStream.setGraphicsStateParameters(
                PDExtendedGraphicsState().apply { strokingAlphaConstant = color.alpha }
            )
            contentStream.setStrokingColor(color.red, color.green, color.blue)
            contentStream.setLineWidth((displayedWidth * oval.strokeWidthFraction).coerceIn(2f, 36f))
            contentStream.setLineJoinStyle(1)
        }
    }

    appendEllipsePath(contentStream, bounds)
    if (oval.style == AnnotationShapeStyle.FILLED) contentStream.fill() else contentStream.stroke()
}

internal fun PdfEditor.appendTextComment(
    page: PDPage,
    document: PDDocument,
    font: PDFont,
    comment: AnnotationText,
    pageWidth: Float,
    pageHeight: Float,
    rotation: Int,
    displayedWidth: Float
) {
    val rawText = sanitizeCommentText(comment.text, font)
    if (rawText.isBlank()) return

    val fontSizePt = (displayedWidth * comment.fontSizeFraction).coerceIn(10f, 28f)
    val maxWidthPt = displayedWidth * 0.40f
    val (anchorPdfX, anchorPdfY) = mapDisplayToPdfCoord(
        comment.anchorX,
        comment.anchorY,
        pageWidth,
        pageHeight,
        rotation
    )

    val allLines = mutableListOf<String>()
    rawText.split("\n").forEach { paragraph ->
        val words = paragraph.split(" ")
        var currentLine = StringBuilder()
        for (word in words) {
            val testLine = if (currentLine.isEmpty()) word else "${currentLine} $word"
            val lineWidthPt = try {
                font.getStringWidth(testLine) / 1000f * fontSizePt
            } catch (_: Exception) {
                0f
            }
            if (lineWidthPt > maxWidthPt && currentLine.isNotEmpty()) {
                allLines += currentLine.toString()
                currentLine = StringBuilder(word)
            } else {
                currentLine = StringBuilder(testLine)
            }
        }
        if (currentLine.isNotEmpty()) allLines += currentLine.toString()
    }
    if (allLines.isEmpty()) return

    val lineHeight = fontSizePt * 1.2f
    data class TextVecs(
        val a: Float,
        val b: Float,
        val c: Float,
        val d: Float,
        val lineDX: Float,
        val lineDY: Float
    )
    val vecs = when (rotation) {
        90 -> TextVecs(0f, 1f, -1f, 0f, -lineHeight, 0f)
        180 -> TextVecs(-1f, 0f, 0f, -1f, 0f, lineHeight)
        270 -> TextVecs(0f, -1f, 1f, 0f, lineHeight, 0f)
        else -> TextVecs(1f, 0f, 0f, 1f, 0f, -lineHeight)
    }

    PDPageContentStream(
        document,
        page,
        PDPageContentStream.AppendMode.APPEND,
        true,
        true
    ).use { cs ->
        val color = comment.color.toPdfArgbColor()
        cs.setGraphicsStateParameters(
            PDExtendedGraphicsState().apply { nonStrokingAlphaConstant = color.alpha }
        )
        cs.setNonStrokingColor(color.red, color.green, color.blue)
        cs.setFont(font, fontSizePt)
        allLines.forEachIndexed { lineIdx, line ->
            val x = anchorPdfX + vecs.lineDX * lineIdx
            val y = anchorPdfY + vecs.lineDY * lineIdx
            cs.beginText()
            cs.setTextMatrix(Matrix(vecs.a, vecs.b, vecs.c, vecs.d, x, y))
            cs.showText(line)
            cs.endText()
        }
    }
}

internal fun sanitizeCommentText(text: String, font: PDFont): String {
    if (font is PDType1Font) {
        return text
            .replace("\t", "  ")
            .filter { it.code in 32..126 || it == '\n' }
            .trim()
    }
    return text
        .replace("\t", "  ")
        .replace("\r", "")
        .trim()
}
