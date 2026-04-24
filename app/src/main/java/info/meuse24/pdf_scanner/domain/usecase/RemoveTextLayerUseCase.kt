package info.meuse24.pdf_scanner.domain.usecase

import info.meuse24.pdf_scanner.domain.model.Document
import info.meuse24.pdf_scanner.domain.pdf.PdfTextOps
import info.meuse24.pdf_scanner.domain.service.ScanArtifactPersister
import java.io.File
import javax.inject.Inject

class RemoveTextLayerUseCase @Inject constructor(
    private val pdfTextOps: PdfTextOps,
    private val persister: ScanArtifactPersister
) {
    suspend operator fun invoke(record: Document, scansDir: File): String {
        val resultFile = pdfTextOps.removeTextLayer(File(record.filepath), scansDir)
        persister.persistDerivedFrom(record, resultFile, scansDir) {
            it.copy(isSearchable = false)
        }
        return resultFile.nameWithoutExtension
    }
}

