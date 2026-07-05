package info.meuse24.pdf_scanner.domain.usecase

import info.meuse24.pdf_scanner.domain.common.buildTableExportFilenames
import info.meuse24.pdf_scanner.domain.common.encodeDelimitedText
import info.meuse24.pdf_scanner.domain.gateway.CsvShareFileStore
import info.meuse24.pdf_scanner.domain.gateway.DispatcherProvider
import info.meuse24.pdf_scanner.domain.gateway.DownloadEntry
import info.meuse24.pdf_scanner.domain.gateway.DownloadsStorage
import info.meuse24.pdf_scanner.domain.model.DelimitedTextDialect
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Eine zu exportierende Tabellenseite: bereits editierte/ausgewählte Zeilen als reine Zellwerte. */
data class TableCsvPage(val rows: List<List<String>>)

sealed interface TableExportOutcome {
    data class SavedToDownloads(val entries: List<DownloadEntry>) : TableExportOutcome
    data class SharedFiles(val files: List<File>) : TableExportOutcome
}

/**
 * Erzeugt pro ausgewählter Tabellenseite eine eigene CSV-/TSV-Datei (siehe
 * [buildTableExportFilenames]). Bei einem Fehler werden bereits erzeugte Dateien wieder entfernt,
 * damit kein unvollständiger Mehrdatei-Export zurückbleibt.
 */
class ExportTableCsvUseCase @Inject constructor(
    private val downloadsStorage: DownloadsStorage,
    private val csvShareFileStore: CsvShareFileStore,
    private val dispatcherProvider: DispatcherProvider
) {
    suspend fun saveToDownloads(
        baseFilename: String,
        pages: List<TableCsvPage>,
        dialect: DelimitedTextDialect
    ): TableExportOutcome.SavedToDownloads {
        val filenames = buildTableExportFilenames(baseFilename, pages.size, dialect.fileExtension)
        val created = mutableListOf<DownloadEntry>()
        // Der try/catch liegt bewusst außerhalb von withContext(dispatcherProvider.io): das
        // Schreiben selbst ist blockierend/nicht suspendierbar, eine Cancellation während des
        // Schreibens einer Seite wird deshalb erst beim Rückwechsel aus withContext bemerkt -
        // zu spät für einen internen catch. ensureActive() zwischen den Seiten sorgt zusätzlich
        // dafür, dass eine Cancellation zwischen zwei Schreibvorgängen sofort erkannt wird,
        // statt erst nach der letzten Seite.
        try {
            withContext(dispatcherProvider.io) {
                filenames.zip(pages).forEach { (filename, page) ->
                    ensureActive()
                    created += downloadsStorage.writeDownload(filename, dialect.mimeType) { output ->
                        encodeDelimitedText(output, page.rows, dialect)
                    }
                }
            }
        } catch (throwable: Throwable) {
            // Aufräumen muss auch bei Cancellation zuverlässig laufen, sonst blieben bereits
            // erzeugte Downloads liegen.
            withContext(NonCancellable) {
                created.forEach { it.delete() }
            }
            throw throwable
        }
        return TableExportOutcome.SavedToDownloads(created)
    }

    suspend fun shareFiles(
        baseFilename: String,
        pages: List<TableCsvPage>,
        dialect: DelimitedTextDialect
    ): TableExportOutcome.SharedFiles {
        val filenames = buildTableExportFilenames(baseFilename, pages.size, dialect.fileExtension)
        val created = mutableListOf<File>()
        try {
            withContext(dispatcherProvider.io) {
                filenames.zip(pages).forEach { (filename, page) ->
                    ensureActive()
                    created += csvShareFileStore.writeShareFile(filename) { output ->
                        encodeDelimitedText(output, page.rows, dialect)
                    }
                }
            }
        } catch (throwable: Throwable) {
            withContext(NonCancellable) {
                created.forEach { it.delete() }
            }
            throw throwable
        }
        return TableExportOutcome.SharedFiles(created)
    }
}
