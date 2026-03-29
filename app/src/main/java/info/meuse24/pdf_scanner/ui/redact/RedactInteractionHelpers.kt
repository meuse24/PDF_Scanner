package info.meuse24.pdf_scanner.ui.redact

import androidx.compose.ui.graphics.Color
import info.meuse24.pdf_scanner.domain.usecase.RedactionRect

internal fun createRedactionRect(
    start: Pair<Float, Float>,
    end: Pair<Float, Float>,
    pageIndex: Int
): RedactionRect {
    return RedactionRect(
        left = minOf(start.first, end.first),
        top = minOf(start.second, end.second),
        right = maxOf(start.first, end.first),
        bottom = maxOf(start.second, end.second),
        pageIndex = pageIndex
    )
}

internal fun hasMinimumArea(rect: RedactionRect): Boolean {
    return (rect.right - rect.left) >= MIN_REDACTION_SIDE &&
        (rect.bottom - rect.top) >= MIN_REDACTION_SIDE
}

internal val AppliedRedactionColor = Color.Black.copy(alpha = 0.82f)
internal val DraftRedactionColor = Color.Black.copy(alpha = 0.55f)
