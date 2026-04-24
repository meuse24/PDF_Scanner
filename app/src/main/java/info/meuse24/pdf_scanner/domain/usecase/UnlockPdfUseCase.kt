package info.meuse24.pdf_scanner.domain.usecase

import info.meuse24.pdf_scanner.domain.model.Document
import info.meuse24.pdf_scanner.domain.pdf.PdfSecurityOps
import info.meuse24.pdf_scanner.domain.service.ScanArtifactPersister
import java.io.File
import javax.inject.Inject

class UnlockPdfUseCase @Inject constructor(
    private val pdfSecurityOps: PdfSecurityOps,
    private val persister: ScanArtifactPersister
) {
    suspend operator fun invoke(record: Document, password: String, scansDir: File): String {
        val resultFile = pdfSecurityOps.unlockPdf(File(record.filepath), scansDir, password)
        persister.persistDerivedFrom(record, resultFile, scansDir)
        return resultFile.nameWithoutExtension
    }
}

