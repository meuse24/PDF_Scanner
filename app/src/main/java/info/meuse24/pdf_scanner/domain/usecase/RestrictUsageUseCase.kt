package info.meuse24.pdf_scanner.domain.usecase

import info.meuse24.pdf_scanner.domain.model.Document
import info.meuse24.pdf_scanner.domain.pdf.PdfSecurityOps
import info.meuse24.pdf_scanner.domain.service.ScanArtifactPersister
import java.io.File
import javax.inject.Inject

class RestrictUsageUseCase @Inject constructor(
    private val pdfSecurityOps: PdfSecurityOps,
    private val persister: ScanArtifactPersister
) {
    suspend operator fun invoke(
        record: Document,
        scansDir: File,
        ownerPassword: String,
        canPrint: Boolean,
        canCopy: Boolean,
        canEdit: Boolean
    ): String {
        val resultFile = pdfSecurityOps.restrictUsage(
            File(record.filepath), scansDir, ownerPassword, canPrint, canCopy, canEdit
        )
        persister.persistDerivedFrom(record, resultFile, scansDir) {
            it.copy(isSearchable = false, isEncrypted = true)
        }
        return resultFile.nameWithoutExtension
    }
}

