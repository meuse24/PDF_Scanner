package info.meuse24.pdf_scanner.ui.shared

import info.meuse24.pdf_scanner.domain.usecase.HighlightRect
import info.meuse24.pdf_scanner.domain.usecase.TextLine

internal fun snapStrokeToTextLines(
    stroke: List<Pair<Float, Float>>,
    textLines: List<TextLine>
): List<HighlightRect> {
    if (stroke.isEmpty() || textLines.isEmpty()) return emptyList()

    val minY = stroke.minOf { it.second }
    val maxY = stroke.maxOf { it.second }

    return textLines.filter { line ->
        line.top <= maxY && line.bottom >= minY
    }.distinctBy { line ->
        listOf(line.pageIndex, line.left, line.top, line.right, line.bottom)
    }.map { line ->
        HighlightRect(
            left = line.left,
            top = line.top,
            right = line.right,
            bottom = line.bottom,
            pageIndex = line.pageIndex
        )
    }
}
