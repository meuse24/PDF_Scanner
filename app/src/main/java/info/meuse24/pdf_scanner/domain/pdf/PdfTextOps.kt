package info.meuse24.pdf_scanner.domain.pdf

import info.meuse24.pdf_scanner.domain.usecase.TextLine
import java.io.File

interface PdfTextOps {
    fun removeTextLayer(input: File, outputDir: File): File
    fun extractTextLines(file: File, pageIndex: Int): List<TextLine>
}
