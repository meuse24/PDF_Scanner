package info.meuse24.pdf_scanner.domain.usecase

import info.meuse24.pdf_scanner.domain.model.Document
import info.meuse24.pdf_scanner.util.DownloadsStorage
import java.io.File
import javax.inject.Inject

/**
 * Exportiert einen Scan via MediaStore in den Downloads-Ordner.
 * Nutzt das IS_PENDING-Pattern für atomares Schreiben.
 * @return den angezeigten Dateinamen (für den Erfolgs-Toast)
 */
class ExportScanUseCase @Inject constructor(
    private val downloadsStorage: DownloadsStorage
) {
    suspend operator fun invoke(record: Document): String {
        val sourceFile = File(record.filepath)
        if (!sourceFile.exists()) error("Quelldatei nicht gefunden: ${record.filepath}")

        val downloadEntry = downloadsStorage.writeDownload(
            displayName = "${record.filename}.pdf",
            mimeType = "application/pdf"
        ) { output ->
            sourceFile.inputStream().use { input -> input.copyTo(output) }
        }
        return downloadEntry.displayName
    }
}

