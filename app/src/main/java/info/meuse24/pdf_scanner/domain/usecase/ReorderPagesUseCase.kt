package info.meuse24.pdf_scanner.domain.usecase

import info.meuse24.pdf_scanner.data.local.ScanRecord
import info.meuse24.pdf_scanner.data.repository.ScanRepository
import info.meuse24.pdf_scanner.util.PdfEditor
import java.io.File
import javax.inject.Inject

/**
 * Ordnet Seiten eines PDFs neu an.
 * saveAsCopy=false: Original wird atomar überschrieben.
 * saveAsCopy=true:  neue Datei mit Suffix „_Sortiert" angelegt.
 * isSearchable bleibt erhalten (PdfBox konserviert Text-Layer).
 */
class ReorderPagesUseCase @Inject constructor(
    private val pdfEditor:  PdfEditor,
    private val repository: ScanRepository
) {
    suspend operator fun invoke(
        record:     ScanRecord,
        newOrder:   List<Int>,
        saveAsCopy: Boolean,
        scansDir:   File
    ) {
        val resultFile = pdfEditor.reorderPages(File(record.filepath), newOrder, saveAsCopy)
        val thumbFile  = File(scansDir, "${resultFile.nameWithoutExtension}.jpg")
        pdfEditor.generateThumbnail(resultFile, thumbFile)

        if (saveAsCopy) {
            repository.saveScan(
                ScanRecord(
                    filename      = resultFile.nameWithoutExtension,
                    filepath      = resultFile.absolutePath,
                    timestamp     = System.currentTimeMillis(),
                    pageCount     = record.pageCount,
                    fileSize      = resultFile.length(),
                    thumbnailPath = thumbFile.takeIf { it.exists() }?.absolutePath,
                    isSearchable  = record.isSearchable
                )
            )
        } else {
            repository.updateFileSize(record.id, resultFile.length())
        }
    }
}
