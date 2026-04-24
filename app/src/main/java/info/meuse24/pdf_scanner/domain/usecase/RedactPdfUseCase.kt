package info.meuse24.pdf_scanner.domain.usecase

import info.meuse24.pdf_scanner.domain.model.Document
import info.meuse24.pdf_scanner.domain.pdf.PdfAnnotationOps
import info.meuse24.pdf_scanner.domain.service.ScanArtifactPersister
import java.io.File
import javax.inject.Inject

class RedactPdfUseCase @Inject constructor(
    private val pdfAnnotationOps: PdfAnnotationOps,
    private val persister: ScanArtifactPersister
) {
    suspend operator fun invoke(
        record: Document,
        rects: List<RedactionRect>,
        scansDir: File
    ): Document {
        val resultFile = pdfAnnotationOps.applySecureRedaction(
            input = File(record.filepath),
            outputDir = scansDir,
            rects = rects
        )
        return persister.persistDerivedFrom(record, resultFile, scansDir) {
            it.copy(
                isSearchable = false,
                extractedText = null,
                tags = null,
                ocrConfidence = null,
                ocrLanguage = null,
                pageTexts = emptyList()
            )
        }
    }
}

