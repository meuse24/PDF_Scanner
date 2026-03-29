package info.meuse24.pdf_scanner.ui.annotate

import androidx.compose.runtime.saveable.listSaver
import info.meuse24.pdf_scanner.R
import info.meuse24.pdf_scanner.domain.usecase.AnnotationOval
import info.meuse24.pdf_scanner.domain.usecase.AnnotationRect
import info.meuse24.pdf_scanner.domain.usecase.AnnotationShapeStyle
import info.meuse24.pdf_scanner.domain.usecase.AnnotationStroke
import info.meuse24.pdf_scanner.domain.usecase.DEFAULT_ANNOTATION_COLOR_ARGB

internal enum class AnnotateTool {
    MARK,
    TEXT,
    RECT_FILLED,
    RECT_FRAME,
    OVAL_FILLED,
    OVAL_FRAME,
    ZOOM
}

internal enum class AnnotationHistoryKind {
    STROKE,
    RECT,
    OVAL,
    COMMENT
}

internal data class AnnotationTextDraft(
    val pageIndex: Int,
    val anchorX: Float,
    val anchorY: Float,
    val text: String,
    val fontSizeFraction: Float,
    val color: Int
)

internal data class AnnotationShapeDraft(
    val tool: AnnotateTool,
    val pageIndex: Int,
    val start: Pair<Float, Float>,
    val end: Pair<Float, Float>
)

internal data class AnnotationHistoryEntry(
    val pageIndex: Int,
    val kind: AnnotationHistoryKind
)

internal data class MarkerWidthOption(
    val fraction: Float,
    val labelRes: Int
)

internal data class AnnotationColorOption(
    val color: Int,
    val labelRes: Int
)

internal val markerWidthOptions = listOf(
    MarkerWidthOption(0.015f, R.string.highlight_stroke_thin),
    MarkerWidthOption(0.025f, R.string.highlight_stroke_medium),
    MarkerWidthOption(0.04f, R.string.highlight_stroke_thick)
)

internal val annotationPalette = listOf(
    AnnotationColorOption(0x66FFDC00, R.string.annotate_color_yellow),
    AnnotationColorOption(0x6600C853, R.string.annotate_color_green),
    AnnotationColorOption(0x66FF1744, R.string.annotate_color_red),
    AnnotationColorOption(0x660091EA, R.string.annotate_color_blue),
    AnnotationColorOption(0x66FF6D00, R.string.annotate_color_orange),
    AnnotationColorOption(0x66AA00FF, R.string.annotate_color_violet)
)

internal val annotatePointListSaver = listSaver<List<Pair<Float, Float>>, Float>(
    save = { points -> points.flatMap { listOf(it.first, it.second) } },
    restore = { values ->
        values.chunked(2).mapNotNull { pair ->
            if (pair.size == 2) pair[0] to pair[1] else null
        }
    }
)

internal val annotateStrokeListSaver = listSaver<List<AnnotationStroke>, List<String>>(
    save = { strokes ->
        strokes.map { stroke ->
            buildList {
                add(stroke.pageIndex.toString())
                add(stroke.strokeWidthFraction.toString())
                add(stroke.color.toString())
                add(stroke.points.size.toString())
                stroke.points.forEach { point ->
                    add(point.first.toString())
                    add(point.second.toString())
                }
            }
        }
    },
    restore = { values ->
        values.mapNotNull { saved ->
            if (saved.size < 4) return@mapNotNull null
            val pageIndex = saved[0].toIntOrNull() ?: return@mapNotNull null
            val strokeWidthFraction = saved[1].toFloatOrNull() ?: return@mapNotNull null
            val color = saved[2].toIntOrNull() ?: return@mapNotNull null
            val pointCount = saved[3].toIntOrNull() ?: return@mapNotNull null
            val pointValues = saved.drop(4)
            if (pointValues.size < pointCount * 2) return@mapNotNull null
            val points = pointValues
                .take(pointCount * 2)
                .chunked(2)
                .mapNotNull { pair ->
                    val x = pair.getOrNull(0)?.toFloatOrNull()
                    val y = pair.getOrNull(1)?.toFloatOrNull()
                    if (x != null && y != null) x to y else null
                }
            AnnotationStroke(
                points = points,
                pageIndex = pageIndex,
                strokeWidthFraction = strokeWidthFraction,
                color = color
            )
        }
    }
)

internal val annotateRectListSaver = listSaver<List<AnnotationRect>, List<String>>(
    save = { rects ->
        rects.map { rect ->
            listOf(
                rect.pageIndex.toString(),
                rect.left.toString(),
                rect.top.toString(),
                rect.right.toString(),
                rect.bottom.toString(),
                rect.color.toString(),
                rect.style.name,
                rect.strokeWidthFraction.toString()
            )
        }
    },
    restore = { values ->
        values.mapNotNull { saved ->
            if (saved.size < 8) return@mapNotNull null
            AnnotationRect(
                pageIndex = saved[0].toIntOrNull() ?: return@mapNotNull null,
                left = saved[1].toFloatOrNull() ?: return@mapNotNull null,
                top = saved[2].toFloatOrNull() ?: return@mapNotNull null,
                right = saved[3].toFloatOrNull() ?: return@mapNotNull null,
                bottom = saved[4].toFloatOrNull() ?: return@mapNotNull null,
                color = saved[5].toIntOrNull() ?: return@mapNotNull null,
                style = saved[6].toAnnotationShapeStyleOrNull() ?: return@mapNotNull null,
                strokeWidthFraction = saved[7].toFloatOrNull() ?: return@mapNotNull null
            )
        }
    }
)

internal val annotateOvalListSaver = listSaver<List<AnnotationOval>, List<String>>(
    save = { ovals ->
        ovals.map { oval ->
            listOf(
                oval.pageIndex.toString(),
                oval.left.toString(),
                oval.top.toString(),
                oval.right.toString(),
                oval.bottom.toString(),
                oval.color.toString(),
                oval.style.name,
                oval.strokeWidthFraction.toString()
            )
        }
    },
    restore = { values ->
        values.mapNotNull { saved ->
            if (saved.size < 8) return@mapNotNull null
            AnnotationOval(
                pageIndex = saved[0].toIntOrNull() ?: return@mapNotNull null,
                left = saved[1].toFloatOrNull() ?: return@mapNotNull null,
                top = saved[2].toFloatOrNull() ?: return@mapNotNull null,
                right = saved[3].toFloatOrNull() ?: return@mapNotNull null,
                bottom = saved[4].toFloatOrNull() ?: return@mapNotNull null,
                color = saved[5].toIntOrNull() ?: return@mapNotNull null,
                style = saved[6].toAnnotationShapeStyleOrNull() ?: return@mapNotNull null,
                strokeWidthFraction = saved[7].toFloatOrNull() ?: return@mapNotNull null
            )
        }
    }
)

internal val annotateCommentListSaver = listSaver<List<AnnotationTextDraft>, List<String>>(
    save = { comments ->
        comments.map { comment ->
            listOf(
                comment.pageIndex.toString(),
                comment.anchorX.toString(),
                comment.anchorY.toString(),
                comment.fontSizeFraction.toString(),
                comment.color.toString(),
                comment.text
            )
        }
    },
    restore = { values ->
        values.mapNotNull { saved ->
            if (saved.size < 6) return@mapNotNull null
            AnnotationTextDraft(
                pageIndex = saved[0].toIntOrNull() ?: return@mapNotNull null,
                anchorX = saved[1].toFloatOrNull() ?: return@mapNotNull null,
                anchorY = saved[2].toFloatOrNull() ?: return@mapNotNull null,
                fontSizeFraction = saved[3].toFloatOrNull() ?: return@mapNotNull null,
                color = saved[4].toIntOrNull() ?: return@mapNotNull null,
                text = saved[5]
            )
        }
    }
)

internal val annotateHistorySaver = listSaver<List<AnnotationHistoryEntry>, List<String>>(
    save = { history ->
        history.map { entry -> listOf(entry.pageIndex.toString(), entry.kind.name) }
    },
    restore = { values ->
        values.mapNotNull { saved ->
            if (saved.size < 2) return@mapNotNull null
            AnnotationHistoryEntry(
                pageIndex = saved[0].toIntOrNull() ?: return@mapNotNull null,
                kind = saved[1].toAnnotationHistoryKindOrNull() ?: return@mapNotNull null
            )
        }
    }
)

internal val annotationSelectionSaver = listSaver<AnnotationSelection?, String>(
    save = { selection ->
        if (selection == null) {
            emptyList()
        } else {
            listOf(selection.kind.name, selection.index.toString())
        }
    },
    restore = { values ->
        if (values.size < 2) return@listSaver null
        val kind = values[0].toAnnotationHistoryKindOrNull() ?: return@listSaver null
        val index = values[1].toIntOrNull() ?: return@listSaver null
        AnnotationSelection(kind = kind, index = index)
    }
)

internal fun AnnotationShapeDraft.toPreviewShape(color: Int, widthFraction: Float): Any? {
    val left = kotlin.math.min(start.first, end.first)
    val top = kotlin.math.min(start.second, end.second)
    val right = kotlin.math.max(start.first, end.first)
    val bottom = kotlin.math.max(start.second, end.second)
    if (right - left <= 0.002f || bottom - top <= 0.002f) return null

    return when (tool) {
        AnnotateTool.RECT_FILLED -> AnnotationRect(
            left = left,
            top = top,
            right = right,
            bottom = bottom,
            pageIndex = pageIndex,
            color = color,
            style = AnnotationShapeStyle.FILLED,
            strokeWidthFraction = widthFraction
        )
        AnnotateTool.RECT_FRAME -> AnnotationRect(
            left = left,
            top = top,
            right = right,
            bottom = bottom,
            pageIndex = pageIndex,
            color = color,
            style = AnnotationShapeStyle.FRAME,
            strokeWidthFraction = widthFraction
        )
        AnnotateTool.OVAL_FILLED -> AnnotationOval(
            left = left,
            top = top,
            right = right,
            bottom = bottom,
            pageIndex = pageIndex,
            color = color,
            style = AnnotationShapeStyle.FILLED,
            strokeWidthFraction = widthFraction
        )
        AnnotateTool.OVAL_FRAME -> AnnotationOval(
            left = left,
            top = top,
            right = right,
            bottom = bottom,
            pageIndex = pageIndex,
            color = color,
            style = AnnotationShapeStyle.FRAME,
            strokeWidthFraction = widthFraction
        )
        else -> null
    }
}

internal fun String.toAnnotationShapeStyleOrNull(): AnnotationShapeStyle? =
    AnnotationShapeStyle.entries.firstOrNull { it.name == this }

internal fun String.toAnnotationHistoryKindOrNull(): AnnotationHistoryKind? =
    AnnotationHistoryKind.entries.firstOrNull { it.name == this }

internal fun Int.toOpaqueColor(): Int = this or (0xFF shl 24)

internal fun defaultAnnotationColor(): Int = DEFAULT_ANNOTATION_COLOR_ARGB

internal fun commentFontSizeFromWidth(widthFraction: Float): Float =
    (widthFraction + 0.003f).coerceIn(0.01f, 0.1f)

internal fun widthFractionFromCommentFontSize(fontSizeFraction: Float): Float =
    (fontSizeFraction - 0.003f).coerceIn(0.005f, 0.1f)
