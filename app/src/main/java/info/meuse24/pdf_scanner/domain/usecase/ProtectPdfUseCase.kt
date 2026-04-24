package info.meuse24.pdf_scanner.domain.usecase

import info.meuse24.pdf_scanner.domain.model.Document
import info.meuse24.pdf_scanner.domain.pdf.PdfSecurityOps
import info.meuse24.pdf_scanner.domain.service.ScanArtifactPersister
import java.io.File
import javax.inject.Inject

class ProtectPdfUseCase @Inject constructor(
    private val pdfSecurityOps: PdfSecurityOps,
    private val persister: ScanArtifactPersister
) {
    suspend operator fun invoke(record: Document, password: String, scansDir: File): String {
        val resultFile = pdfSecurityOps.protectPdf(File(record.filepath), scansDir, password)
        persister.persistDerivedFrom(
            sourceDocument = record,
            outputFile = resultFile,
            scansDir = scansDir,
            thumbnailStrategy = ScanArtifactPersister.ThumbnailStrategy.CopyFromPath(record.thumbnailPath)
        ) {
            it.copy(isEncrypted = true)
        }
        return resultFile.nameWithoutExtension
    }
}

