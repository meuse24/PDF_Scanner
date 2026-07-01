package info.meuse24.pdf_scanner.domain.usecase

import android.content.Context
import info.meuse24.pdf_scanner.domain.gateway.OcrDocumentTextExtractor
import info.meuse24.pdf_scanner.domain.model.Document
import info.meuse24.pdf_scanner.data.repository.ScanRepository
import info.meuse24.pdf_scanner.testutil.FakeSettingsRepository
import info.meuse24.pdf_scanner.domain.model.AppSettings
import info.meuse24.pdf_scanner.util.OcrPipeline
import info.meuse24.pdf_scanner.domain.model.OcrPipelineStatus
import info.meuse24.pdf_scanner.util.SearchablePdfBuilder
import info.meuse24.pdf_scanner.util.TextRecognizerRunner
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Mockito.mock
import java.io.File

/**
 * Unit-Tests für MakeSearchableUseCase.
 * Idempotenz: nur Records mit isSearchable=false ODER extractedText=null werden verarbeitet.
 */
class MakeSearchableUseCaseTest {

    @get:Rule val tmpFolder = TemporaryFolder()

    private fun makeUseCase(
        onMakeSearchable: (File, String) -> Unit = { _, _ -> },
        extractedText: String = "",
        settings: AppSettings = AppSettings()
    ): Pair<MakeSearchableUseCase, FakeScanDao> {
        val dao        = FakeScanDao()
        val repository = ScanRepository(dao)
        val builder    = FakeSearchablePdfBuilder(onMakeSearchable, extractedText)
        return MakeSearchableUseCase(
            builder,
            repository,
            AutoTagUseCase(),
            FakeSettingsRepository(settings),
            FakeExtractTextUseCase()
        ) to dao
    }

    private fun record(
        id: Long,
        isSearchable: Boolean,
        exists: Boolean = true,
        extractedText: String? = null
    ): Document {
        val filepath = if (exists) {
            tmpFolder.newFile("scan_$id.pdf").absolutePath
        } else {
            "/nicht/vorhanden/scan_$id.pdf"
        }
        return Document(
            id            = id,
            filename      = "scan_$id",
            filepath      = filepath,
            timestamp     = 0L,
            pageCount     = 1,
            fileSize      = 0L,
            isSearchable  = isSearchable,
            extractedText = extractedText,
            pageTexts = extractedText?.let(::listOf).orEmpty()
        )
    }

    @Test
    fun `verarbeitet nur nicht-durchsuchbare Records`() = runTest {
        val processedFiles = mutableListOf<File>()
        val (useCase, _) = makeUseCase(onMakeSearchable = { file, _ -> processedFiles.add(file) })

        // isSearchable=true + extractedText gesetzt → wirklich vollständig verarbeitet → wird übersprungen
        val alreadySearchable = record(1L, isSearchable = true, extractedText = "Rechnung")
        val notYetSearchable  = record(2L, isSearchable = false)

        val (count, _) = useCase(listOf(alreadySearchable, notYetSearchable), "de")

        assertEquals(1, count)
        assertEquals(1, processedFiles.size)
        assertEquals(notYetSearchable.filepath, processedFiles[0].absolutePath)
    }

    @Test
    fun `leere Liste oder alle vollstaendig verarbeitet gibt 0 zurueck`() = runTest {
        val (useCase, dao) = makeUseCase()

        // Beide Records haben isSearchable=true + extractedText gesetzt → werden übersprungen
        val (result, _) = useCase(
            listOf(
                record(1L, isSearchable = true, extractedText = "Text A"),
                record(2L, isSearchable = true, extractedText = "Text B")
            ),
            "en"
        )

        assertEquals(0, result)
        assertTrue(dao.searchableWithContentUpdates.isEmpty())
    }

    @Test
    fun `isSearchable=true aber extractedText=null wird nachverarbeitet (Backfill v4)`() = runTest {
        val processedFiles = mutableListOf<File>()
        val (useCase, dao) = makeUseCase(onMakeSearchable = { file, _ -> processedFiles.add(file) })

        // Simuliert v4-Migration: isSearchable=true, aber extractedText noch nicht gespeichert
        val legacyRecord = record(1L, isSearchable = true, extractedText = null)

        val (count, _) = useCase(listOf(legacyRecord), "de")

        assertEquals(1, count)
        assertTrue(processedFiles.isEmpty())
        assertEquals(1, dao.searchableWithContentUpdates.size)
    }

    @Test
    fun `repariert falsch ausgerichtete Alt-OCR ohne neuen PDF-Textlayer`() = runTest {
        val processedFiles = mutableListOf<File>()
        val (useCase, dao) = makeUseCase(onMakeSearchable = { file, _ -> processedFiles.add(file) })
        val legacyRecord = record(3L, isSearchable = true, extractedText = "Alter Text")
            .copy(pageCount = 2, pageTexts = listOf("Alter Text"))

        val (count, _) = useCase(listOf(legacyRecord), "de")

        assertEquals(1, count)
        assertTrue(processedFiles.isEmpty())
        assertEquals("Recovered text", dao.searchableWithContentUpdates.single().text)
    }

    @Test
    fun `ruft markSearchableWithContent in der DB für jeden verarbeiteten Record auf`() = runTest {
        val (useCase, dao) = makeUseCase()
        val rec = record(42L, isSearchable = false)

        useCase(listOf(rec), "en")

        assertEquals(1, dao.searchableWithContentUpdates.size)
        assertEquals(42L, dao.searchableWithContentUpdates[0].id)
    }

    @Test
    fun `überspringt Records deren Datei nicht existiert`() = runTest {
        val processedFiles = mutableListOf<File>()
        val (useCase, dao) = makeUseCase(onMakeSearchable = { file, _ -> processedFiles.add(file) })

        val missing = record(1L, isSearchable = false, exists = false)
        val present = record(2L, isSearchable = false, exists = true)

        useCase(listOf(missing, present), "de")

        assertEquals(1, processedFiles.size)
        assertEquals(1, dao.searchableWithContentUpdates.size)
    }

    @Test
    fun `speichert keine Auto-Tags wenn Auto-Tagging deaktiviert ist`() = runTest {
        val (useCase, dao) = makeUseCase(
            extractedText = "Rechnung Rechnungsnummer Betrag IBAN",
            settings = AppSettings(autoTaggingEnabled = false)
        )
        val rec = record(55L, isSearchable = false)

        useCase(listOf(rec), "de")

        assertEquals(1, dao.searchableWithContentUpdates.size)
        assertEquals("Rechnung Rechnungsnummer Betrag IBAN", dao.searchableWithContentUpdates.single().text)
        assertNull(dao.searchableWithContentUpdates.single().tags)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Fake SearchablePdfBuilder – überschreibt makeSearchable ohne echte Implementierung
// ─────────────────────────────────────────────────────────────────────────────

class FakeSearchablePdfBuilder(
    private val onMakeSearchable: (File, String) -> Unit,
    private val extractedText: String = ""
) : SearchablePdfBuilder(
    context = mock(Context::class.java),
    ocrPipeline = mock(OcrPipeline::class.java),
    textRecognizerRunner = mock(TextRecognizerRunner::class.java),
    dispatcherProvider = info.meuse24.pdf_scanner.testutil.TestDispatcherProvider(kotlinx.coroutines.test.StandardTestDispatcher())
) {
    override suspend fun makeSearchable(
        pdfFile:      File,
        languageCode: String,
        onProgress:   (Int, Int) -> Unit,
        onStatus:     (OcrPipelineStatus) -> Unit
    ): SearchableResult {
        onMakeSearchable(pdfFile, languageCode)
        return SearchableResult(
            extractedText = extractedText,
            pageTexts = emptyList(),
            stats = null
        )
    }
}

class FakeExtractTextUseCase(
    private val fullText: String = "Recovered text",
    private val pageTexts: List<String> = listOf(fullText)
) : ExtractTextUseCase(
    ocrDocumentTextExtractor = object : OcrDocumentTextExtractor {
        override suspend fun extract(
            records: List<Document>,
            languageCode: String,
            onStatus: (OcrPipelineStatus) -> Unit
        ): List<OcrDocumentResult> = emptyList()
    }
) {
    var invocationCount: Int = 0

    override suspend fun invoke(
        records: List<Document>,
        languageCode: String,
        onStatus: (OcrPipelineStatus) -> Unit
    ): List<OcrDocumentResult> {
        invocationCount++
        return records.map { record ->
            OcrDocumentResult(
                recordId = record.id,
                fullText = fullText,
                pageTexts = pageTexts,
                stats = null
            )
        }
    }
}

