package info.meuse24.pdf_scanner.domain.usecase

import info.meuse24.pdf_scanner.domain.common.sanitizeDownloadFolderName
import info.meuse24.pdf_scanner.domain.gateway.DownloadEntry
import info.meuse24.pdf_scanner.domain.gateway.DownloadsStorage
import info.meuse24.pdf_scanner.domain.gateway.PdfPageJpgRenderer
import info.meuse24.pdf_scanner.domain.model.Document
import java.io.File
import javax.inject.Inject

data class JpgExportResult(
    val folderName: String,
    val pageCount: Int
)

class ExportAsJpgUseCase @Inject constructor(
    private val downloadsStorage: DownloadsStorage,
    private val pdfPageJpgRenderer: PdfPageJpgRenderer
) {
    suspend operator fun invoke(record: Document): JpgExportResult {
        val sourceFile = File(record.filepath)
        if (!sourceFile.exists()) error("Quelldatei nicht gefunden: ${record.filepath}")

        val folderName = sanitizeDownloadFolderName(record.filename)
        val committed = mutableListOf<DownloadEntry>()

        try {
            pdfPageJpgRenderer.renderPages(sourceFile) { pageIndex, _, writeJpeg ->
                val entry = downloadsStorage.writeDownloadToSubfolder(
                    displayName = "page_${pageIndex + 1}.jpg",
                    mimeType = "image/jpeg",
                    subfolder = folderName,
                    writer = writeJpeg
                )
                committed.add(entry)
            }
        } catch (exception: Exception) {
            committed.forEach { entry -> runCatching { entry.delete() } }
            throw exception
        }

        return JpgExportResult(
            folderName = folderName,
            pageCount = committed.size
        )
    }
}
