package info.meuse24.pdf_scanner.ui.redact

import androidx.compose.runtime.saveable.listSaver
import info.meuse24.pdf_scanner.domain.usecase.RedactionRect

internal const val MIN_REDACTION_SIDE = 0.003f

internal val redactionRectListSaver = listSaver<List<RedactionRect>, List<Float>>(
    save = { rects ->
        rects.map { rect ->
            listOf(
                rect.pageIndex.toFloat(),
                rect.left,
                rect.top,
                rect.right,
                rect.bottom
            )
        }
    },
    restore = { values ->
        values.mapNotNull { savedRect ->
            if (savedRect.size != 5) return@mapNotNull null
            RedactionRect(
                pageIndex = savedRect[0].toInt(),
                left = savedRect[1],
                top = savedRect[2],
                right = savedRect[3],
                bottom = savedRect[4]
            )
        }
    }
)
