package info.meuse24.pdf_scanner.domain.workflow

import info.meuse24.pdf_scanner.domain.gateway.OcrPositionedTextExtractor
import info.meuse24.pdf_scanner.domain.model.Document
import info.meuse24.pdf_scanner.domain.model.OcrElement
import info.meuse24.pdf_scanner.domain.model.OcrLine
import info.meuse24.pdf_scanner.domain.model.OcrModelInstallException
import info.meuse24.pdf_scanner.domain.model.OcrPipelineStatus
import info.meuse24.pdf_scanner.domain.model.OcrPositionedPage
import info.meuse24.pdf_scanner.domain.model.OcrRect
import info.meuse24.pdf_scanner.domain.pdf.PdfSecurityOps
import info.meuse24.pdf_scanner.domain.usecase.ExtractTableUseCase
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ExtractTableWorkflowTest {

    @get:Rule
    val tmpFolder = TemporaryFolder()

    private fun record(exists: Boolean = true): Document {
        val file = if (exists) {
            tmpFolder.newFile("scan.pdf").apply { writeText("pdf") }
        } else {
            File(tmpFolder.root, "missing.pdf")
        }
        return Document(
            id = 1L,
            filename = "scan",
            filepath = file.absolutePath,
            timestamp = 0L,
            pageCount = 1,
            fileSize = file.length()
        )
    }

    private fun workflow(extractor: OcrPositionedTextExtractor, encrypted: Boolean = false): ExtractTableWorkflow {
        return ExtractTableWorkflow(ExtractTableUseCase(extractor), DocumentWorkflowGuard(FakePdfSecurityOps(encrypted)))
    }

    @Test
    fun `fehlende Datei liefert MissingFiles`() = runTest {
        val result = workflow(FakeOcrPositionedTextExtractor()).invoke(record(exists = false), "de")

        assertTrue(result is WorkflowResult.Failure)
        assertEquals(ScanWorkflowError.MissingFiles(listOf("scan")), (result as WorkflowResult.Failure).error)
    }

    @Test
    fun `verschluesseltes Dokument liefert ProtectedPdfUnsupported`() = runTest {
        val result = workflow(FakeOcrPositionedTextExtractor(), encrypted = true).invoke(record(), "de")

        assertTrue(result is WorkflowResult.Failure)
        assertEquals(ScanWorkflowError.ProtectedPdfUnsupported, (result as WorkflowResult.Failure).error)
    }

    @Test
    fun `Dokument ohne Tabelle liefert NoTableFound`() = runTest {
        val result = workflow(FakeOcrPositionedTextExtractor(pages = listOf(textOnlyPage(0)))).invoke(record(), "de")

        assertTrue(result is WorkflowResult.Failure)
        assertEquals(ScanWorkflowError.NoTableFound, (result as WorkflowResult.Failure).error)
    }

    @Test
    fun `Dokument mit Tabelle liefert Success`() = runTest {
        val result = workflow(FakeOcrPositionedTextExtractor(pages = listOf(tablePage(0)))).invoke(record(), "de")

        assertTrue(result is WorkflowResult.Success)
        val tables = (result as WorkflowResult.Success).value.tablesByPage
        assertTrue(tables[0] != null)
    }

    @Test
    fun `IOException wird als TableExtractionFailed statt StorageWriteFailed gemappt`() = runTest {
        val result = workflow(FakeOcrPositionedTextExtractor(throwing = IOException("boom"))).invoke(record(), "de")

        assertTrue(result is WorkflowResult.Failure)
        val error = (result as WorkflowResult.Failure).error
        assertTrue("erwartete TableExtractionFailed, war $error", error is ScanWorkflowError.TableExtractionFailed)
        assertTrue((error as ScanWorkflowError.TableExtractionFailed).cause is IOException)
    }

    @Test
    fun `OcrModelInstallException wird als TableExtractionFailed mit korrekter Ursache durchgereicht`() = runTest {
        val cause = OcrModelInstallException("model missing")

        val result = workflow(FakeOcrPositionedTextExtractor(throwing = cause)).invoke(record(), "de")

        assertTrue(result is WorkflowResult.Failure)
        val error = (result as WorkflowResult.Failure).error
        assertTrue(error is ScanWorkflowError.TableExtractionFailed)
        assertEquals(cause, (error as ScanWorkflowError.TableExtractionFailed).cause)
    }

    @Test
    fun `Status und Fortschritt werden durchgereicht`() = runTest {
        val extractor = FakeOcrPositionedTextExtractor(
            pages = listOf(tablePage(0)),
            statusToEmit = OcrPipelineStatus.DownloadingModel,
            progressToEmit = 1 to 1
        )
        val statuses = mutableListOf<OcrPipelineStatus>()
        val progress = mutableListOf<Pair<Int, Int>>()

        workflow(extractor).invoke(record(), "de", onStatus = { statuses += it }, onProgress = { c, t -> progress += c to t })

        assertEquals(listOf(OcrPipelineStatus.DownloadingModel), statuses)
        assertEquals(listOf(1 to 1), progress)
    }

    @Test
    fun `Cancellation wird nicht abgefangen sondern weitergeworfen`() = runTest {
        val error = runCatching {
            workflow(FakeOcrPositionedTextExtractor(throwing = CancellationException("cancelled"))).invoke(record(), "de")
        }.exceptionOrNull()

        assertTrue(error is CancellationException)
    }
}

private fun rect(left: Float, top: Float, width: Float, height: Float = 20f) =
    OcrRect(left, top, left + width, top + height)

private fun line(text: String, left: Float, top: Float, width: Float): OcrLine {
    val bounds = rect(left, top, width)
    val element = OcrElement(text, bounds, confidence = 0.9f, angleDeg = 0f)
    return OcrLine(text, bounds, listOf(element), element.confidence, element.angleDeg)
}

private fun tablePage(pageIndex: Int): OcrPositionedPage {
    val lines = (0 until 3).flatMap { row ->
        val top = row * 30f
        listOf(
            line("R${row}C0", 0f, top, 100f),
            line("R${row}C1", 150f, top, 100f)
        )
    }
    return OcrPositionedPage(pageIndex = pageIndex, widthPx = 600, heightPx = 800, lines = lines)
}

private fun textOnlyPage(pageIndex: Int): OcrPositionedPage {
    val lines = (0 until 3).map { idx ->
        line("Zeile $idx normaler Fließtext ohne jede Tabellenstruktur.", 0f, idx * 30f, 400f)
    }
    return OcrPositionedPage(pageIndex = pageIndex, widthPx = 600, heightPx = 800, lines = lines)
}

private class FakeOcrPositionedTextExtractor(
    private val pages: List<OcrPositionedPage> = emptyList(),
    private val throwing: Throwable? = null,
    private val statusToEmit: OcrPipelineStatus? = null,
    private val progressToEmit: Pair<Int, Int>? = null
) : OcrPositionedTextExtractor {
    override suspend fun extract(
        document: Document,
        languageCode: String,
        onStatus: (OcrPipelineStatus) -> Unit,
        onProgress: (current: Int, total: Int) -> Unit
    ): List<OcrPositionedPage> {
        statusToEmit?.let(onStatus)
        progressToEmit?.let { (current, total) -> onProgress(current, total) }
        throwing?.let { throw it }
        return pages
    }
}

private class FakePdfSecurityOps(private val encrypted: Boolean) : PdfSecurityOps {
    override fun protectPdf(input: File, outputDir: File, password: String): File = error("not used in this test")
    override fun unlockPdf(input: File, outputDir: File, password: String): File = error("not used in this test")
    override fun removePassword(input: File, outputDir: File): File = error("not used in this test")
    override fun restrictUsage(
        input: File,
        outputDir: File,
        ownerPassword: String,
        canPrint: Boolean,
        canCopy: Boolean,
        canEdit: Boolean
    ): File = error("not used in this test")

    override fun isPdfEncrypted(input: File): Boolean = encrypted
}
