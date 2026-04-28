package info.meuse24.pdf_scanner.domain.usecase

import info.meuse24.pdf_scanner.domain.model.Document
import info.meuse24.pdf_scanner.domain.gateway.DownloadEntry
import info.meuse24.pdf_scanner.domain.gateway.DownloadsStorage
import info.meuse24.pdf_scanner.domain.gateway.PdfPageJpgRenderer
import java.io.File
import javax.inject.Inject

/**
 * Rendert alle Seiten eines PDFs als JPEG und exportiert sie in MediaStore.Downloads.
 * Nutzt das IS_PENDING-Pattern für atomares Schreiben.
 *
 * Fehlerverhalten:
 * - Null-OutputStream → wirft sofort (kein stiller Fehlschlag, kein exported++)
 * - bitmap.compress() == false → wirft sofort
 * - resolver.update() == 0 → wirft sofort
 * - Fehler bei Seite N → alle bereits geschriebenen Seiten werden aus Downloads gelöscht (Rollback)
 *
 * @return Anzahl der erfolgreich exportierten Seiten
 */
class ExportAsJpgUseCase @Inject constructor(
    private val downloadsStorage: DownloadsStorage,
    private val pdfPageJpgRenderer: PdfPageJpgRenderer
) {
    suspend operator fun invoke(record: Document): Int {
        val sourceFile = File(record.filepath)
        if (!sourceFile.exists()) error("Quelldatei nicht gefunden: ${record.filepath}")

        val baseName    = record.filename
        val committed   = mutableListOf<DownloadEntry>()

        try {
            pdfPageJpgRenderer.renderPages(sourceFile) { pageIndex, pageCount, writeJpeg ->
                val displayName = if (pageCount == 1) "$baseName.jpg"
                else "${baseName}_p${pageIndex + 1}.jpg"

                val entry = downloadsStorage.writeDownload(
                    displayName = displayName,
                    mimeType = "image/jpeg",
                    writer = writeJpeg
                )
                committed.add(entry)
            }
        } catch (e: Exception) {
            // Rollback: bereits in Downloads geschriebene Seiten löschen
            committed.forEach { entry -> runCatching { entry.delete() } }
            throw e
        }

        return committed.size
    }
}

