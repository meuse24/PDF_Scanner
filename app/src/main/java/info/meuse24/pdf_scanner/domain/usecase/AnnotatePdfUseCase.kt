package info.meuse24.pdf_scanner.domain.usecase

import info.meuse24.pdf_scanner.domain.model.Document
import info.meuse24.pdf_scanner.domain.pdf.PdfAnnotationOps
import info.meuse24.pdf_scanner.domain.service.ScanArtifactPersister
import java.io.File
import javax.inject.Inject

class AnnotatePdfUseCase @Inject constructor(
    private val pdfAnnotationOps: PdfAnnotationOps,
    private val persister: ScanArtifactPersister
) {
    suspend operator fun invoke(
        record: Document,
        strokes: List<AnnotationStroke>,
        rects: List<AnnotationRect>,
        ovals: List<AnnotationOval>,
        comments: List<AnnotationText>,
        scansDir: File
    ): String {
        val resultFile = pdfAnnotationOps.applyAnnotations(
            input = File(record.filepath),
            outputDir = scansDir,
            strokes = strokes,
            rects = rects,
            ovals = ovals,
            comments = comments
        )
        persister.persistDerivedFrom(record, resultFile, scansDir) {
            it.copy(tags = record.tags)
        }
        return resultFile.nameWithoutExtension
    }
}

