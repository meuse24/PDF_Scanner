package info.meuse24.pdf_scanner.domain.usecase

import info.meuse24.pdf_scanner.domain.gateway.CsvShareFileStore
import info.meuse24.pdf_scanner.domain.gateway.DownloadEntry
import info.meuse24.pdf_scanner.domain.gateway.DownloadsStorage
import info.meuse24.pdf_scanner.domain.model.CsvDelimiter
import info.meuse24.pdf_scanner.domain.model.DelimitedTextDialect
import info.meuse24.pdf_scanner.testutil.TestDispatcherProvider
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ExportTableCsvUseCaseTest {

    @get:Rule
    val tmpFolder = TemporaryFolder()

    private val testDispatcher = StandardTestDispatcher()

    private fun useCase(
        downloads: DownloadsStorage = FakeTableDownloadsStorage(),
        share: CsvShareFileStore = FakeCsvShareFileStore(tmpFolder.root)
    ) = ExportTableCsvUseCase(downloads, share, TestDispatcherProvider(testDispatcher))

    @Test
    fun `eine Seite wird ohne Seitenzahl im Dateinamen gespeichert`() = runTest(testDispatcher) {
        val storage = FakeTableDownloadsStorage()

        val outcome = useCase(downloads = storage).saveToDownloads(
            baseFilename = "invoice",
            pages = listOf(TableCsvPage(listOf(listOf("1", "Stuhl", "99.00")))),
            dialect = DelimitedTextDialect(CsvDelimiter.COMMA)
        )

        assertEquals(listOf("invoice_table.csv"), outcome.entries.map { it.displayName })
        assertEquals("text/csv", storage.written.single().second)
        assertTrue(storage.written.single().third.contains("1,Stuhl,99.00"))
    }

    @Test
    fun `mehrere Seiten erzeugen getrennte durchnummerierte Dateien`() = runTest(testDispatcher) {
        val storage = FakeTableDownloadsStorage()

        val outcome = useCase(downloads = storage).saveToDownloads(
            baseFilename = "invoice",
            pages = listOf(
                TableCsvPage(listOf(listOf("Seite1"))),
                TableCsvPage(listOf(listOf("Seite2")))
            ),
            dialect = DelimitedTextDialect(CsvDelimiter.TAB)
        )

        assertEquals(listOf("invoice_table_p001.tsv", "invoice_table_p002.tsv"), outcome.entries.map { it.displayName })
        assertTrue(storage.written.all { it.second == "text/tab-separated-values" })
    }

    @Test
    fun `Fehler auf Seite 2 loescht bereits erzeugte Downloads wieder`() = runTest(testDispatcher) {
        val storage = FakeTableDownloadsStorage(failOnDisplayName = "invoice_table_p002.csv")

        val error = runCatching {
            useCase(downloads = storage).saveToDownloads(
                baseFilename = "invoice",
                pages = listOf(
                    TableCsvPage(listOf(listOf("Seite1"))),
                    TableCsvPage(listOf(listOf("Seite2"))),
                    TableCsvPage(listOf(listOf("Seite3")))
                ),
                dialect = DelimitedTextDialect(CsvDelimiter.COMMA)
            )
        }.exceptionOrNull()

        assertTrue(error != null)
        // Seite 1 wurde bereits erfolgreich angelegt, muss beim Abbruch aber wieder geloescht werden.
        assertEquals(listOf("invoice_table_p001.csv"), storage.deleted)
    }

    @Test
    fun `Cancellation waehrend des Schreibens loest Rollback zuverlaessig aus`() = runTest(testDispatcher) {
        lateinit var job: Job
        val storage = FakeTableDownloadsStorage(
            onWriteDownload = { displayName -> if (displayName == "invoice_table_p001.csv") job.cancel() }
        )

        job = launch {
            useCase(downloads = storage).saveToDownloads(
                baseFilename = "invoice",
                pages = listOf(TableCsvPage(listOf(listOf("Seite1"))), TableCsvPage(listOf(listOf("Seite2")))),
                dialect = DelimitedTextDialect(CsvDelimiter.COMMA)
            )
        }
        job.join()

        assertTrue(job.isCancelled)
        // Seite 1 wurde noch geschrieben (Cancellation erfolgt waehrend des blockierenden
        // Schreibens), Seite 2 nie erreicht (ensureActive() zwischen den Seiten) - Seite 1 muss
        // trotzdem wieder geloescht werden, sonst bliebe ein verwaister Download zurueck.
        assertEquals(listOf("invoice_table_p001.csv"), storage.written.map { it.first })
        assertEquals(listOf("invoice_table_p001.csv"), storage.deleted)
    }

    @Test
    fun `Teilen schreibt reale Dateien in den Share-Cache`() = runTest(testDispatcher) {
        val shareStore = FakeCsvShareFileStore(tmpFolder.root)

        val outcome = useCase(share = shareStore).shareFiles(
            baseFilename = "invoice",
            pages = listOf(TableCsvPage(listOf(listOf("a", "b")))),
            dialect = DelimitedTextDialect(CsvDelimiter.COMMA)
        )

        val file = outcome.files.single()
        assertEquals("invoice_table.csv", file.name)
        assertTrue(file.exists())
        assertTrue(file.readText().contains("a,b"))
    }

    @Test
    fun `Fehler beim Teilen loescht bereits geschriebene Share-Dateien wieder`() = runTest(testDispatcher) {
        val shareStore = FakeCsvShareFileStore(tmpFolder.root, failOnDisplayName = "invoice_table_p002.csv")

        runCatching {
            useCase(share = shareStore).shareFiles(
                baseFilename = "invoice",
                pages = listOf(TableCsvPage(listOf(listOf("Seite1"))), TableCsvPage(listOf(listOf("Seite2")))),
                dialect = DelimitedTextDialect(CsvDelimiter.COMMA)
            )
        }

        assertFalse(File(tmpFolder.root, "invoice_table_p001.csv").exists())
    }
}

private class FakeTableDownloadsStorage(
    private val failOnDisplayName: String? = null,
    private val onWriteDownload: (String) -> Unit = {}
) : DownloadsStorage {
    val written = mutableListOf<Triple<String, String, String>>()
    val deleted = mutableListOf<String>()

    override fun writeDownload(
        displayName: String,
        mimeType: String,
        writer: (OutputStream) -> Unit
    ): DownloadEntry {
        onWriteDownload(displayName)
        if (displayName == failOnDisplayName) error("simulierter Fehler für $displayName")
        val output = ByteArrayOutputStream()
        writer(output)
        written += Triple(displayName, mimeType, output.toString(Charsets.UTF_8.name()))
        return object : DownloadEntry {
            override val displayName: String = displayName
            override fun delete() {
                deleted += displayName
            }
        }
    }
}

private class FakeCsvShareFileStore(
    private val baseDir: File,
    private val failOnDisplayName: String? = null
) : CsvShareFileStore {
    override fun writeShareFile(displayName: String, writer: (OutputStream) -> Unit): File {
        if (displayName == failOnDisplayName) error("simulierter Fehler für $displayName")
        val file = File(baseDir, displayName)
        file.outputStream().use { writer(it) }
        return file
    }
}
