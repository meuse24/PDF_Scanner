package info.meuse24.pdf_scanner.util

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import info.meuse24.pdf_scanner.domain.pdf.NormalizedBox
import info.meuse24.pdf_scanner.domain.pdf.PdfPageTextContent
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.File

/**
 * Search-only PDFBox extraction. Unlike annotation text extraction it deliberately retains
 * small glyphs: searchable PDFs often use a scaled, invisible text layer.
 */
internal fun extractSearchTextFromPdf(file: File): Flow<PdfPageTextContent> = flow {
    PDDocument.load(file).use { document ->
        repeat(document.numberOfPages) { pageIndex ->
            currentCoroutineContext().ensureActive()
            emit(PdfPageTextContent(pageIndex, extractPageSearchContent(document, pageIndex).text))
        }
    }
}

internal fun extractPageGlyphBoxesFromPdf(file: File, pageIndex: Int): List<NormalizedBox?> {
    if (pageIndex < 0) return emptyList()
    PDDocument.load(file).use { document ->
        if (pageIndex >= document.numberOfPages) return emptyList()
        return extractPageSearchContent(document, pageIndex).boxes
    }
}

private data class PageSearchContent(
    val text: String,
    val boxes: List<NormalizedBox?>
)

private fun extractPageSearchContent(document: PDDocument, pageIndex: Int): PageSearchContent {
    val page = document.getPage(pageIndex)
    val rotation = normalizeRotation(page.rotation)
    val cropBox = page.cropBox
    val text = StringBuilder()
    val boxes = mutableListOf<NormalizedBox?>()
    var firstSegment = true

    val stripper = object : PDFTextStripper() {
        override fun writeString(segment: String?, positions: MutableList<TextPosition>?) {
            if (!firstSegment) {
                text.append('\n')
                boxes += null
            }
            firstSegment = false
            positions.orEmpty().forEach { position ->
                val unicode = position.unicode.orEmpty()
                if (unicode.isEmpty()) return@forEach
                unicode.forEach { character ->
                    text.append(character)
                    boxes += position.toSearchNormalizedBox(
                        pageWidth = cropBox.width,
                        pageHeight = cropBox.height,
                        rotation = rotation
                    )
                }
            }
        }
    }
    stripper.startPage = pageIndex + 1
    stripper.endPage = pageIndex + 1
    stripper.sortByPosition = true
    stripper.getText(document)
    return PageSearchContent(text.toString(), boxes)
}

private fun TextPosition.toSearchNormalizedBox(
    pageWidth: Float,
    pageHeight: Float,
    rotation: Int
): NormalizedBox? {
    if (pageWidth <= 0f || pageHeight <= 0f) return null
    return mapPdfBoxToDisplay(
        left = (xDirAdj / pageWidth).coerceIn(0f, 1f),
        top = ((yDirAdj - heightDir) / pageHeight).coerceIn(0f, 1f),
        right = ((xDirAdj + widthDirAdj) / pageWidth).coerceIn(0f, 1f),
        bottom = (yDirAdj / pageHeight).coerceIn(0f, 1f),
        rotation = rotation
    )?.let { (left, top, right, bottom) -> NormalizedBox(left, top, right, bottom) }
}
