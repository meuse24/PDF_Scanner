package info.meuse24.pdf_scanner.domain.pdf

import info.meuse24.pdf_scanner.domain.usecase.AnnotationOval
import info.meuse24.pdf_scanner.domain.usecase.AnnotationRect
import info.meuse24.pdf_scanner.domain.usecase.AnnotationStroke
import info.meuse24.pdf_scanner.domain.usecase.AnnotationText
import info.meuse24.pdf_scanner.domain.usecase.HighlightRect
import info.meuse24.pdf_scanner.domain.usecase.HighlightStroke
import info.meuse24.pdf_scanner.domain.usecase.RedactionRect
import java.io.File

interface PdfAnnotationOps {
    fun applySecureRedaction(input: File, outputDir: File, rects: List<RedactionRect>): File
    fun applyHighlight(
        input: File,
        outputDir: File,
        strokes: List<HighlightStroke>,
        rects: List<HighlightRect> = emptyList()
    ): File

    fun applyAnnotations(
        input: File,
        outputDir: File,
        strokes: List<AnnotationStroke>,
        rects: List<AnnotationRect> = emptyList(),
        ovals: List<AnnotationOval> = emptyList(),
        comments: List<AnnotationText> = emptyList()
    ): File
}
