package info.meuse24.pdf_scanner.domain.pdf

import info.meuse24.pdf_scanner.domain.usecase.TextLine
import kotlinx.coroutines.flow.Flow
import java.io.File

interface PdfTextOps {
    fun removeTextLayer(input: File, outputDir: File): File
    fun extractTextLines(file: File, pageIndex: Int): List<TextLine>
    fun extractSearchText(file: File): Flow<PdfPageTextContent>
    fun extractPageGlyphBoxes(file: File, pageIndex: Int): List<NormalizedBox?>
}
