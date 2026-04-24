package info.meuse24.pdf_scanner.domain.usecase

import info.meuse24.pdf_scanner.domain.model.Document
import info.meuse24.pdf_scanner.domain.pdf.PdfRenderingOps
import info.meuse24.pdf_scanner.domain.pdf.PdfStructureOps
import info.meuse24.pdf_scanner.domain.repository.DocumentRepository
import info.meuse24.pdf_scanner.domain.service.ScanArtifactPersister
import java.io.File
import javax.inject.Inject

class RotatePagesUseCase @Inject constructor(
    private val pdfEditor: PdfStructureOps,
    private val persister: ScanArtifactPersister,
    private val repository: DocumentRepository,
    private val pdfRenderingOps: PdfRenderingOps = pdfEditor as PdfRenderingOps
) {
    suspend operator fun invoke(
        record: Document,
        pageIndexes: List<Int>,
        rotationDegrees: Int,
        saveAsCopy: Boolean,
        scansDir: File
    ) {
        val resultFile = pdfEditor.rotatePages(
            File(record.filepath),
            pageIndexes,
            rotationDegrees,
            saveAsCopy
        )

        if (saveAsCopy) {
            persister.persistDerivedFrom(record, resultFile, scansDir)
        } else {
            val thumbFile = thumbnailFile(record, resultFile, saveAsCopy, scansDir)
            pdfRenderingOps.generateThumbnail(resultFile, thumbFile)
            repository.updateFileSize(record.id, resultFile.length())
        }
    }
}

