package info.meuse24.pdf_scanner.domain.workflow

import info.meuse24.pdf_scanner.data.local.ScanRecord
import info.meuse24.pdf_scanner.data.repository.ScanRepository
import info.meuse24.pdf_scanner.domain.usecase.DeleteScansUseCase
import info.meuse24.pdf_scanner.domain.usecase.FakeScanDao
import info.meuse24.pdf_scanner.domain.usecase.MakeSearchableUseCase
import info.meuse24.pdf_scanner.domain.usecase.RedactPdfUseCase
import info.meuse24.pdf_scanner.domain.usecase.RedactionRect
import info.meuse24.pdf_scanner.util.OcrManager
import info.meuse24.pdf_scanner.util.PdfEditor
import info.meuse24.pdf_scanner.util.SearchablePdfBuilder
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Mockito.mock
import java.io.File
import java.io.IOException

class RedactPdfWorkflowTest {

    @get:Rule val tmpFolder = TemporaryFolder()

    private fun record(
        id: Long,
        exists: Boolean = true,
        isSearchable: Boolean = true,
        extractedText: String? = "secret text",
        tags: String? = "invoice,bank"
    ): ScanRecord {
        val file = if (exists) {
            tmpFolder.newFile("scan_$id.pdf").apply { writeText("pdf") }
        } else {
            File(tmpFolder.root, "missing_$id.pdf")
        }
        return ScanRecord(
            id = id,
            filename = "scan_$id",
            filepath = file.absolutePath,
            timestamp = 0L,
            pageCount = 2,
            fileSize = file.length(),
            isSearchable = isSearchable,
            extractedText = extractedText,
            tags = tags
        )
    }

    private fun rect(page: Int = 0) = RedactionRect(
        left = 0.10f,
        top = 0.20f,
        right = 0.60f,
        bottom = 0.35f,
        pageIndex = page
    )

    private fun workflow(
        pdfEditor: PdfEditor,
        searchablePdfBuilder: SearchablePdfBuilder = FakeSearchablePdfBuilder()
    ): Pair<RedactPdfWorkflow, FakeScanDao> {
        val dao = FakeScanDao()
        val repository = ScanRepository(dao)
        val redactUseCase = RedactPdfUseCase(pdfEditor, repository)
        val makeSearchableUseCase = MakeSearchableUseCase(searchablePdfBuilder, repository)
        val makeSearchableWorkflow = MakeSearchableWorkflow(makeSearchableUseCase)
        val deleteScansUseCase = DeleteScansUseCase(repository)
        return RedactPdfWorkflow(
            redactPdfUseCase = redactUseCase,
            makeSearchableWorkflow = makeSearchableWorkflow,
            deleteScansUseCase = deleteScansUseCase,
            pdfEditor = pdfEditor
        ) to dao
    }

    @Test
    fun `leere Bereiche liefern NoRedactionAreas`() = runTest {
        val (workflow, _) = workflow(FakeRedactionPdfEditor())

        val result = workflow(record(1L), emptyList(), tmpFolder.root)

        assertTrue(result is WorkflowResult.Failure)
        assertEquals(
            ScanWorkflowError.NoRedactionAreas,
            (result as WorkflowResult.Failure).error
        )
    }

    @Test
    fun `fehlende Datei liefert MissingFiles`() = runTest {
        val (workflow, _) = workflow(FakeRedactionPdfEditor())

        val result = workflow(record(2L, exists = false), listOf(rect()), tmpFolder.root)

        assertTrue(result is WorkflowResult.Failure)
        assertEquals(
            ScanWorkflowError.MissingFiles(listOf("scan_2")),
            (result as WorkflowResult.Failure).error
        )
    }

    @Test
    fun `geschuetzte pdf liefert ProtectedPdfUnsupported`() = runTest {
        val (workflow, _) = workflow(FakeRedactionPdfEditor(encrypted = true))

        val result = workflow(record(3L), listOf(rect()), tmpFolder.root)

        assertTrue(result is WorkflowResult.Failure)
        assertEquals(
            ScanWorkflowError.ProtectedPdfUnsupported,
            (result as WorkflowResult.Failure).error
        )
    }

    @Test
    fun `io Fehler werden als StorageWriteFailed gemappt`() = runTest {
        val (workflow, _) = workflow(
            FakeRedactionPdfEditor(onApply = { _, _, _ -> throw IOException("disk full") })
        )

        val result = workflow(record(4L), listOf(rect()), tmpFolder.root)

        assertTrue(result is WorkflowResult.Failure)
        assertTrue((result as WorkflowResult.Failure).error is ScanWorkflowError.StorageWriteFailed)
    }

    @Test
    fun `unbekannter Fehler wird als RedactionFailed gemappt`() = runTest {
        val (workflow, _) = workflow(
            FakeRedactionPdfEditor(onApply = { _, _, _ -> throw RuntimeException("unexpected") })
        )

        val result = workflow(record(5L), listOf(rect()), tmpFolder.root)

        assertTrue(result is WorkflowResult.Failure)
        assertTrue((result as WorkflowResult.Failure).error is ScanWorkflowError.RedactionFailed)
    }

    @Test
    fun `erfolgreicher Ablauf speichert nicht mehr suchbaren Record ohne Text und Tags`() = runTest {
        val (workflow, dao) = workflow(FakeRedactionPdfEditor())

        val result = workflow(record(6L), listOf(rect(0), rect(1)), tmpFolder.root)

        assertTrue(result is WorkflowResult.Success)
        assertEquals(1, dao.inserted.size)
        val saved = dao.inserted.single()
        assertEquals(false, saved.isSearchable)
        assertNull(saved.extractedText)
        assertNull(saved.tags)
    }

    @Test
    fun `optional searchable macht die geschwaerzte Kopie wieder durchsuchbar`() = runTest {
        val (workflow, dao) = workflow(
            pdfEditor = FakeRedactionPdfEditor(),
            searchablePdfBuilder = FakeSearchablePdfBuilder(returnedText = "visible text")
        )

        val result = workflow(
            record = record(7L),
            rects = listOf(rect()),
            scansDir = tmpFolder.root,
            makeSearchable = true,
            languageCode = "de"
        )

        assertTrue(result is WorkflowResult.Success)
        assertEquals(1, dao.searchableWithContentUpdates.size)
        val update = dao.searchableWithContentUpdates.single()
        assertEquals(1L, update.first)
        assertEquals("visible text", update.third)
    }

    @Test
    fun `ocr Fehler nach Redaction loescht die erzeugte Kopie wieder`() = runTest {
        val (workflow, dao) = workflow(
            pdfEditor = FakeRedactionPdfEditor(),
            searchablePdfBuilder = FakeSearchablePdfBuilder(throwable = IllegalStateException("ocr failed"))
        )

        val result = workflow(
            record = record(8L),
            rects = listOf(rect()),
            scansDir = tmpFolder.root,
            makeSearchable = true,
            languageCode = "en"
        )

        assertTrue(result is WorkflowResult.Failure)
        assertTrue((result as WorkflowResult.Failure).error is ScanWorkflowError.OcrFailed)
        assertEquals(1, dao.deleted.size)
        val saved = dao.inserted.single()
        assertFalse(File(saved.filepath).exists())
        saved.thumbnailPath?.let { thumbnailPath ->
            assertFalse(File(thumbnailPath).exists())
        }
    }

    @Test
    fun `unerwarteter searchable follow-up Fehler wird als RedactionFailed gekapselt`() = runTest {
        val mapped = mapSearchableFollowUpError(ScanWorkflowError.NoEligibleScans)
        assertTrue(mapped is ScanWorkflowError.RedactionFailed)
    }

    @Test
    fun `ocr Fehler im searchable follow-up wird durchgereicht`() = runTest {
        val error = ScanWorkflowError.OcrFailed(IllegalStateException("ocr failed"))

        val mapped = mapSearchableFollowUpError(error)

        assertSame(error, mapped)
    }

    @Test
    fun `storage Fehler im searchable follow-up wird durchgereicht`() = runTest {
        val error = ScanWorkflowError.StorageWriteFailed(IOException("disk full"))

        val mapped = mapSearchableFollowUpError(error)

        assertSame(error, mapped)
    }

    @Test
    fun `nothing selected im searchable follow-up wird als RedactionFailed gekapselt`() = runTest {
        val mapped = mapSearchableFollowUpError(ScanWorkflowError.NothingSelected)

        assertTrue(mapped is ScanWorkflowError.RedactionFailed)
    }
}

private class FakeRedactionPdfEditor(
    private val encrypted: Boolean = false,
    private val onApply: (File, File, List<RedactionRect>) -> File = { input, outputDir, _ ->
        File(outputDir, "${input.nameWithoutExtension}_Geschwaerzt.pdf").apply { writeText("copy") }
    }
) : PdfEditor() {
    override fun isPdfEncrypted(input: File): Boolean = encrypted

    override fun applySecureRedaction(
        input: File,
        outputDir: File,
        rects: List<RedactionRect>
    ): File = onApply(input, outputDir, rects)

    override fun generateThumbnail(pdfFile: File, outputFile: File): Boolean {
        outputFile.writeText("thumb")
        return true
    }
}

private class FakeSearchablePdfBuilder(
    private val returnedText: String = "",
    private val throwable: Throwable? = null
) : SearchablePdfBuilder(mock(OcrManager::class.java)) {
    override suspend fun makeSearchable(
        pdfFile: File,
        languageCode: String,
        onProgress: (current: Int, total: Int) -> Unit
    ): String {
        throwable?.let { throw it }
        return returnedText
    }
}
