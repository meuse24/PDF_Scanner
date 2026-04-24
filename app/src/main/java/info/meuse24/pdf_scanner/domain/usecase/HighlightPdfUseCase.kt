package info.meuse24.pdf_scanner.domain.usecase

import info.meuse24.pdf_scanner.domain.model.Document
import info.meuse24.pdf_scanner.domain.pdf.PdfAnnotationOps
import info.meuse24.pdf_scanner.domain.service.ScanArtifactPersister
import java.io.File
import javax.inject.Inject

class HighlightPdfUseCase @Inject constructor(
    private val pdfAnnotationOps: PdfAnnotationOps,
    private val persister: ScanArtifactPersister
) {
    suspend operator fun invoke(
        record: Document,
        strokes: List<HighlightStroke>,
        rects: List<HighlightRect>,
        scansDir: File
    ): String {
        val resultFile = pdfAnnotationOps.applyHighlight(
            input = File(record.filepath),
            outputDir = scansDir,
            strokes = strokes,
            rects = rects
        )
        persister.persistDerivedFrom(record, resultFile, scansDir) {
            it.copy(tags = record.tags)
        }
        return resultFile.nameWithoutExtension
    }
}

