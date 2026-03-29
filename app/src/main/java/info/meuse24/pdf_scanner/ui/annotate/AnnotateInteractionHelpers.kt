package info.meuse24.pdf_scanner.ui.annotate

import info.meuse24.pdf_scanner.domain.usecase.AnnotationOval
import info.meuse24.pdf_scanner.domain.usecase.AnnotationRect
import info.meuse24.pdf_scanner.domain.usecase.AnnotationShapeStyle
import info.meuse24.pdf_scanner.domain.usecase.AnnotationStroke
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

internal data class AnnotationSelection(
    val kind: AnnotationHistoryKind,
    val index: Int
)

internal data class AnnotationElementState(
    val strokes: List<AnnotationStroke>,
    val rects: List<AnnotationRect>,
    val ovals: List<AnnotationOval>,
    val comments: List<AnnotationTextDraft>
)

internal data class AnnotationSelectionFrame(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val handleX: Float,
    val handleY: Float
)

internal fun hitTestSelection(
    pageIndex: Int,
    point: Pair<Float, Float>,
    state: AnnotationElementState
): AnnotationSelection? {
    for (index in state.comments.indices.reversed()) {
        val comment = state.comments[index]
        if (comment.pageIndex == pageIndex && isPointNearComment(point, comment)) {
            return AnnotationSelection(AnnotationHistoryKind.COMMENT, index)
        }
    }
    for (index in state.ovals.indices.reversed()) {
        val oval = state.ovals[index]
        if (oval.pageIndex == pageIndex && isPointInOval(point, oval)) {
            return AnnotationSelection(AnnotationHistoryKind.OVAL, index)
        }
    }
    for (index in state.rects.indices.reversed()) {
        val rect = state.rects[index]
        if (rect.pageIndex == pageIndex && isPointInRect(point, rect)) {
            return AnnotationSelection(AnnotationHistoryKind.RECT, index)
        }
    }
    for (index in state.strokes.indices.reversed()) {
        val stroke = state.strokes[index]
        if (stroke.pageIndex == pageIndex && isPointNearStroke(point, stroke)) {
            return AnnotationSelection(AnnotationHistoryKind.STROKE, index)
        }
    }
    return null
}

internal fun moveSelectionBy(
    selection: AnnotationSelection,
    deltaX: Float,
    deltaY: Float,
    state: AnnotationElementState
): AnnotationElementState {
    return when (selection.kind) {
        AnnotationHistoryKind.STROKE -> {
            val stroke = state.strokes[selection.index]
            val bounds = strokeBounds(stroke)
            val moveX = deltaX.coerceIn(-bounds.left, 1f - bounds.right)
            val moveY = deltaY.coerceIn(-bounds.top, 1f - bounds.bottom)
            state.copy(
                strokes = state.strokes.toMutableList().apply {
                    this[selection.index] = stroke.copy(
                        points = stroke.points.map { point ->
                            (point.first + moveX).coerceIn(0f, 1f) to (point.second + moveY).coerceIn(0f, 1f)
                        }
                    )
                }
            )
        }
        AnnotationHistoryKind.RECT -> {
            val rect = state.rects[selection.index]
            val moveX = deltaX.coerceIn(-rect.left, 1f - rect.right)
            val moveY = deltaY.coerceIn(-rect.top, 1f - rect.bottom)
            state.copy(
                rects = state.rects.toMutableList().apply {
                    this[selection.index] = rect.copy(
                        left = rect.left + moveX,
                        right = rect.right + moveX,
                        top = rect.top + moveY,
                        bottom = rect.bottom + moveY
                    )
                }
            )
        }
        AnnotationHistoryKind.OVAL -> {
            val oval = state.ovals[selection.index]
            val moveX = deltaX.coerceIn(-oval.left, 1f - oval.right)
            val moveY = deltaY.coerceIn(-oval.top, 1f - oval.bottom)
            state.copy(
                ovals = state.ovals.toMutableList().apply {
                    this[selection.index] = oval.copy(
                        left = oval.left + moveX,
                        right = oval.right + moveX,
                        top = oval.top + moveY,
                        bottom = oval.bottom + moveY
                    )
                }
            )
        }
        AnnotationHistoryKind.COMMENT -> {
            val comment = state.comments[selection.index]
            state.copy(
                comments = state.comments.toMutableList().apply {
                    this[selection.index] = comment.copy(
                        anchorX = (comment.anchorX + deltaX).coerceIn(0f, 1f),
                        anchorY = (comment.anchorY + deltaY).coerceIn(0f, 1f)
                    )
                }
            )
        }
    }
}

internal fun selectionExists(
    selection: AnnotationSelection?,
    state: AnnotationElementState
): Boolean = selectionFrame(selection, state) != null

internal fun selectionColor(
    selection: AnnotationSelection?,
    state: AnnotationElementState
): Int? {
    val safeSelection = selection ?: return null
    return when (safeSelection.kind) {
        AnnotationHistoryKind.STROKE -> state.strokes.getOrNull(safeSelection.index)?.color
        AnnotationHistoryKind.RECT -> state.rects.getOrNull(safeSelection.index)?.color
        AnnotationHistoryKind.OVAL -> state.ovals.getOrNull(safeSelection.index)?.color
        AnnotationHistoryKind.COMMENT -> state.comments.getOrNull(safeSelection.index)?.color
    }
}

internal fun selectionWidthFraction(
    selection: AnnotationSelection?,
    state: AnnotationElementState
): Float? {
    val safeSelection = selection ?: return null
    return when (safeSelection.kind) {
        AnnotationHistoryKind.STROKE -> state.strokes.getOrNull(safeSelection.index)?.strokeWidthFraction
        AnnotationHistoryKind.RECT -> state.rects.getOrNull(safeSelection.index)?.strokeWidthFraction
        AnnotationHistoryKind.OVAL -> state.ovals.getOrNull(safeSelection.index)?.strokeWidthFraction
        AnnotationHistoryKind.COMMENT -> state.comments.getOrNull(safeSelection.index)?.let {
            widthFractionFromCommentFontSize(it.fontSizeFraction)
        }
    }
}

internal fun selectionText(
    selection: AnnotationSelection?,
    state: AnnotationElementState
): String? = selection
    ?.takeIf { it.kind == AnnotationHistoryKind.COMMENT }
    ?.let { state.comments.getOrNull(it.index)?.text }

internal fun applySelectionColor(
    selection: AnnotationSelection,
    color: Int,
    state: AnnotationElementState
): AnnotationElementState = when (selection.kind) {
    AnnotationHistoryKind.STROKE -> state.copy(
        strokes = state.strokes.toMutableList().apply {
            getOrNull(selection.index)?.let { this[selection.index] = it.copy(color = color) }
        }
    )
    AnnotationHistoryKind.RECT -> state.copy(
        rects = state.rects.toMutableList().apply {
            getOrNull(selection.index)?.let { this[selection.index] = it.copy(color = color) }
        }
    )
    AnnotationHistoryKind.OVAL -> state.copy(
        ovals = state.ovals.toMutableList().apply {
            getOrNull(selection.index)?.let { this[selection.index] = it.copy(color = color) }
        }
    )
    AnnotationHistoryKind.COMMENT -> state.copy(
        comments = state.comments.toMutableList().apply {
            getOrNull(selection.index)?.let { this[selection.index] = it.copy(color = color.toOpaqueColor()) }
        }
    )
}

internal fun applySelectionWidth(
    selection: AnnotationSelection,
    widthFraction: Float,
    state: AnnotationElementState
): AnnotationElementState = when (selection.kind) {
    AnnotationHistoryKind.STROKE -> state.copy(
        strokes = state.strokes.toMutableList().apply {
            getOrNull(selection.index)?.let { this[selection.index] = it.copy(strokeWidthFraction = widthFraction) }
        }
    )
    AnnotationHistoryKind.RECT -> state.copy(
        rects = state.rects.toMutableList().apply {
            getOrNull(selection.index)?.let { this[selection.index] = it.copy(strokeWidthFraction = widthFraction) }
        }
    )
    AnnotationHistoryKind.OVAL -> state.copy(
        ovals = state.ovals.toMutableList().apply {
            getOrNull(selection.index)?.let { this[selection.index] = it.copy(strokeWidthFraction = widthFraction) }
        }
    )
    AnnotationHistoryKind.COMMENT -> state.copy(
        comments = state.comments.toMutableList().apply {
            getOrNull(selection.index)?.let {
                this[selection.index] = it.copy(fontSizeFraction = commentFontSizeFromWidth(widthFraction))
            }
        }
    )
}

internal fun applySelectionText(
    selection: AnnotationSelection,
    text: String,
    state: AnnotationElementState
): AnnotationElementState {
    if (selection.kind != AnnotationHistoryKind.COMMENT) return state
    return state.copy(
        comments = state.comments.toMutableList().apply {
            getOrNull(selection.index)?.let { this[selection.index] = it.copy(text = text) }
        }
    )
}

internal fun deleteSelection(
    selection: AnnotationSelection,
    state: AnnotationElementState
): AnnotationElementState = when (selection.kind) {
    AnnotationHistoryKind.STROKE -> state.copy(
        strokes = state.strokes.toMutableList().apply {
            if (selection.index in indices) removeAt(selection.index)
        }
    )
    AnnotationHistoryKind.RECT -> state.copy(
        rects = state.rects.toMutableList().apply {
            if (selection.index in indices) removeAt(selection.index)
        }
    )
    AnnotationHistoryKind.OVAL -> state.copy(
        ovals = state.ovals.toMutableList().apply {
            if (selection.index in indices) removeAt(selection.index)
        }
    )
    AnnotationHistoryKind.COMMENT -> state.copy(
        comments = state.comments.toMutableList().apply {
            if (selection.index in indices) removeAt(selection.index)
        }
    )
}

internal fun selectionFrame(
    selection: AnnotationSelection?,
    state: AnnotationElementState
): AnnotationSelectionFrame? {
    val safeSelection = selection ?: return null
    return when (safeSelection.kind) {
        AnnotationHistoryKind.STROKE -> state.strokes.getOrNull(safeSelection.index)?.let(::strokeFrame)
        AnnotationHistoryKind.RECT -> state.rects.getOrNull(safeSelection.index)?.let(::rectFrame)
        AnnotationHistoryKind.OVAL -> state.ovals.getOrNull(safeSelection.index)?.let(::ovalFrame)
        AnnotationHistoryKind.COMMENT -> state.comments.getOrNull(safeSelection.index)?.let(::commentFrame)
    }
}

internal fun selectionKindLabelRes(
    selection: AnnotationSelection?
): Int? = when (selection?.kind) {
    AnnotationHistoryKind.STROKE -> info.meuse24.pdf_scanner.R.string.annotate_selection_mark
    AnnotationHistoryKind.RECT -> info.meuse24.pdf_scanner.R.string.annotate_selection_rect
    AnnotationHistoryKind.OVAL -> info.meuse24.pdf_scanner.R.string.annotate_selection_oval
    AnnotationHistoryKind.COMMENT -> info.meuse24.pdf_scanner.R.string.annotate_selection_text
    null -> null
}

internal fun createTapStroke(
    pageIndex: Int,
    point: Pair<Float, Float>,
    widthFraction: Float,
    color: Int
): AnnotationStroke = AnnotationStroke(
    points = listOf(point),
    pageIndex = pageIndex,
    strokeWidthFraction = widthFraction,
    color = color
)

internal fun createTapRect(
    pageIndex: Int,
    center: Pair<Float, Float>,
    widthFraction: Float,
    color: Int,
    filled: Boolean
): AnnotationRect {
    val halfWidth = 0.09f
    val halfHeight = 0.055f
    val left = (center.first - halfWidth).coerceIn(0f, 1f)
    val right = (center.first + halfWidth).coerceIn(0f, 1f)
    val top = (center.second - halfHeight).coerceIn(0f, 1f)
    val bottom = (center.second + halfHeight).coerceIn(0f, 1f)
    return AnnotationRect(
        left = min(left, right),
        top = min(top, bottom),
        right = max(left, right),
        bottom = max(top, bottom),
        pageIndex = pageIndex,
        color = color,
        style = if (filled) AnnotationShapeStyle.FILLED else AnnotationShapeStyle.FRAME,
        strokeWidthFraction = widthFraction
    )
}

internal fun createTapOval(
    pageIndex: Int,
    center: Pair<Float, Float>,
    widthFraction: Float,
    color: Int,
    filled: Boolean
): AnnotationOval {
    val halfWidth = 0.09f
    val halfHeight = 0.055f
    val left = (center.first - halfWidth).coerceIn(0f, 1f)
    val right = (center.first + halfWidth).coerceIn(0f, 1f)
    val top = (center.second - halfHeight).coerceIn(0f, 1f)
    val bottom = (center.second + halfHeight).coerceIn(0f, 1f)
    return AnnotationOval(
        left = min(left, right),
        top = min(top, bottom),
        right = max(left, right),
        bottom = max(top, bottom),
        pageIndex = pageIndex,
        color = color,
        style = if (filled) AnnotationShapeStyle.FILLED else AnnotationShapeStyle.FRAME,
        strokeWidthFraction = widthFraction
    )
}

private data class StrokeBounds(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
)

private fun strokeBounds(stroke: AnnotationStroke): StrokeBounds {
    val xs = stroke.points.map { it.first }
    val ys = stroke.points.map { it.second }
    return StrokeBounds(
        left = xs.minOrNull() ?: 0f,
        top = ys.minOrNull() ?: 0f,
        right = xs.maxOrNull() ?: 0f,
        bottom = ys.maxOrNull() ?: 0f
    )
}

private fun isPointNearComment(point: Pair<Float, Float>, comment: AnnotationTextDraft): Boolean {
    val bounds = commentBounds(comment)
    if (point.first in (bounds.left - 0.02f)..(bounds.right + 0.02f) &&
        point.second in (bounds.top - 0.02f)..(bounds.bottom + 0.02f)
    ) {
        return true
    }
    val dx = point.first - comment.anchorX
    val dy = point.second - comment.anchorY
    return dx * dx + dy * dy <= 0.06f * 0.06f
}

private fun isPointInRect(point: Pair<Float, Float>, rect: AnnotationRect): Boolean {
    val padding = if (rect.style == AnnotationShapeStyle.FRAME) max(0.025f, rect.strokeWidthFraction * 1.5f) else 0.025f
    return point.first in (rect.left - padding)..(rect.right + padding) &&
        point.second in (rect.top - padding)..(rect.bottom + padding)
}

private fun isPointInOval(point: Pair<Float, Float>, oval: AnnotationOval): Boolean {
    val centerX = (oval.left + oval.right) / 2f
    val centerY = (oval.top + oval.bottom) / 2f
    val radiusX = max((oval.right - oval.left) / 2f, 0.03f)
    val radiusY = max((oval.bottom - oval.top) / 2f, 0.03f)
    val normX = (point.first - centerX) / radiusX
    val normY = (point.second - centerY) / radiusY
    return normX * normX + normY * normY <= 1.35f
}

private fun isPointNearStroke(point: Pair<Float, Float>, stroke: AnnotationStroke): Boolean {
    if (stroke.points.isEmpty()) return false
    if (stroke.points.size == 1) {
        val dx = point.first - stroke.points.first().first
        val dy = point.second - stroke.points.first().second
        return dx * dx + dy * dy <= 0.03f * 0.03f
    }
    val tolerance = max(0.025f, stroke.strokeWidthFraction * 1.8f)
    return stroke.points.zipWithNext().any { (start, end) ->
        distancePointToSegment(point, start, end) <= tolerance
    }
}

private fun distancePointToSegment(
    point: Pair<Float, Float>,
    start: Pair<Float, Float>,
    end: Pair<Float, Float>
): Float {
    val vx = end.first - start.first
    val vy = end.second - start.second
    val wx = point.first - start.first
    val wy = point.second - start.second
    val lenSq = vx * vx + vy * vy
    if (lenSq <= 0.000001f) {
        val dx = point.first - start.first
        val dy = point.second - start.second
        return sqrt(dx * dx + dy * dy)
    }
    val t = ((wx * vx + wy * vy) / lenSq).coerceIn(0f, 1f)
    val projX = start.first + t * vx
    val projY = start.second + t * vy
    val dx = point.first - projX
    val dy = point.second - projY
    return sqrt(dx * dx + dy * dy)
}

private fun strokeFrame(stroke: AnnotationStroke): AnnotationSelectionFrame {
    val bounds = strokeBounds(stroke)
    return selectionFrameFromBounds(bounds.left, bounds.top, bounds.right, bounds.bottom)
}

private fun rectFrame(rect: AnnotationRect): AnnotationSelectionFrame =
    selectionFrameFromBounds(rect.left, rect.top, rect.right, rect.bottom)

private fun ovalFrame(oval: AnnotationOval): AnnotationSelectionFrame =
    selectionFrameFromBounds(oval.left, oval.top, oval.right, oval.bottom)

private fun commentFrame(comment: AnnotationTextDraft): AnnotationSelectionFrame {
    val bounds = commentBounds(comment)
    return selectionFrameFromBounds(bounds.left, bounds.top, bounds.right, bounds.bottom)
}

private fun selectionFrameFromBounds(
    left: Float,
    top: Float,
    right: Float,
    bottom: Float
): AnnotationSelectionFrame {
    val width = max(right - left, 0.035f)
    val height = max(bottom - top, 0.035f)
    val frameLeft = (left - ((width - (right - left)) / 2f)).coerceIn(0f, 1f)
    val frameTop = (top - ((height - (bottom - top)) / 2f)).coerceIn(0f, 1f)
    val frameRight = (frameLeft + width).coerceIn(0f, 1f)
    val frameBottom = (frameTop + height).coerceIn(0f, 1f)
    return AnnotationSelectionFrame(
        left = frameLeft,
        top = frameTop,
        right = frameRight,
        bottom = frameBottom,
        handleX = frameRight,
        handleY = frameTop
    )
}

private fun commentBounds(comment: AnnotationTextDraft): StrokeBounds {
    val lines = comment.text.split("\n")
    val lineCount = max(lines.size, 1)
    val maxChars = max(lines.maxOfOrNull { it.length } ?: 1, 1)
    val width = max(comment.fontSizeFraction * maxChars * 0.62f, comment.fontSizeFraction * 1.4f)
    val height = max(comment.fontSizeFraction * lineCount * 1.3f, comment.fontSizeFraction * 1.4f)
    return StrokeBounds(
        left = (comment.anchorX - 0.008f).coerceIn(0f, 1f),
        top = (comment.anchorY - comment.fontSizeFraction - 0.01f).coerceIn(0f, 1f),
        right = (comment.anchorX + width + 0.012f).coerceIn(0f, 1f),
        bottom = (comment.anchorY + height - comment.fontSizeFraction + 0.012f).coerceIn(0f, 1f)
    )
}
