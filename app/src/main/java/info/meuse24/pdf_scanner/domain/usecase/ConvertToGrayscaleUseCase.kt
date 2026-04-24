package info.meuse24.pdf_scanner.domain.usecase

import info.meuse24.pdf_scanner.domain.model.Document
import info.meuse24.pdf_scanner.domain.pdf.PdfRenderingOps
import info.meuse24.pdf_scanner.domain.service.ScanArtifactPersister
import java.io.File
import javax.inject.Inject

class ConvertToGrayscaleUseCase @Inject constructor(
    private val pdfRenderingOps: PdfRenderingOps,
    private val persister: ScanArtifactPersister
) {
    suspend operator fun invoke(record: Document, scansDir: File): String {
        val resultFile = pdfRenderingOps.convertToGrayscale(File(record.filepath), scansDir)
        persister.persistDerivedFrom(record, resultFile, scansDir) {
            it.copy(isSearchable = false)
        }
        return resultFile.nameWithoutExtension
    }
}

