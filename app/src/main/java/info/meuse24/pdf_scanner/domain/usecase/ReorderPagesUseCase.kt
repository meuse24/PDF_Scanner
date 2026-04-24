package info.meuse24.pdf_scanner.domain.usecase

import info.meuse24.pdf_scanner.domain.model.Document
import info.meuse24.pdf_scanner.domain.pdf.PdfRenderingOps
import info.meuse24.pdf_scanner.domain.pdf.PdfStructureOps
import info.meuse24.pdf_scanner.domain.repository.DocumentRepository
import info.meuse24.pdf_scanner.domain.service.ScanArtifactPersister
import java.io.File
import javax.inject.Inject

/**
 * Ordnet Seiten eines PDFs neu an.
 * saveAsCopy=false: Original wird atomar überschrieben.
 * saveAsCopy=true:  neue Datei mit Suffix „_Sortiert" angelegt.
 * isSearchable bleibt erhalten (PdfBox konserviert Text-Layer).
 */
class ReorderPagesUseCase @Inject constructor(
    private val pdfEditor: PdfStructureOps,
    private val persister: ScanArtifactPersister,
    private val repository: DocumentRepository,
    private val pdfRenderingOps: PdfRenderingOps = pdfEditor as PdfRenderingOps
) {
    suspend operator fun invoke(
        record: Document,
        newOrder: List<Int>,
        saveAsCopy: Boolean,
        scansDir: File
    ) {
        val resultFile = pdfEditor.reorderPages(File(record.filepath), newOrder, saveAsCopy)

        if (saveAsCopy) {
            persister.persistDerivedFrom(record, resultFile, scansDir)
        } else {
            val thumbFile = File(scansDir, "${resultFile.nameWithoutExtension}.jpg")
            pdfRenderingOps.generateThumbnail(resultFile, thumbFile)
            repository.updateFileSize(record.id, resultFile.length())
        }
    }
}

