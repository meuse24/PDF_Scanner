package info.meuse24.pdf_scanner.domain.usecase

import info.meuse24.pdf_scanner.domain.gateway.OcrPositionedTextExtractor
import info.meuse24.pdf_scanner.domain.model.Document
import info.meuse24.pdf_scanner.domain.model.OcrElement
import info.meuse24.pdf_scanner.domain.model.OcrLine
import info.meuse24.pdf_scanner.domain.model.OcrPipelineStatus
import info.meuse24.pdf_scanner.domain.model.OcrPositionedPage
import info.meuse24.pdf_scanner.domain.model.OcrRect
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtractTableUseCaseTest {

    private val document = Document(
        id = 1L,
        filename = "scan",
        filepath = "/tmp/scan.pdf",
        timestamp = 0L,
        pageCount = 1,
        fileSize = 0L
    )

    @Test
    fun `mindestens eine Seite mit Tabelle liefert Ergebnis ohne Exception`() = runTest {
        val useCase = ExtractTableUseCase(FakeOcrPositionedTextExtractor(listOf(tablePage(0), textOnlyPage(1))))

        val result = useCase(document, "de")

        assertNotNull(result.tablesByPage[0])
        assertNull(result.tablesByPage[1])
    }

    @Test
    fun `keine Seite mit Tabelle wirft NoTableFoundException`() = runTest {
        val useCase = ExtractTableUseCase(FakeOcrPositionedTextExtractor(listOf(textOnlyPage(0), textOnlyPage(1))))

        val error = runCatching { useCase(document, "de") }.exceptionOrNull()

        assertTrue(error is NoTableFoundException)
    }

    @Test
    fun `leere Seitenliste wirft NoTableFoundException`() = runTest {
        val useCase = ExtractTableUseCase(FakeOcrPositionedTextExtractor(emptyList()))

        val error = runCatching { useCase(document, "de") }.exceptionOrNull()

        assertTrue(error is NoTableFoundException)
    }

    @Test
    fun `Status und Fortschritt werden unveraendert an den Extractor durchgereicht`() = runTest {
        val extractor = FakeOcrPositionedTextExtractor(
            pages = listOf(tablePage(0)),
            statusToEmit = OcrPipelineStatus.DownloadingModel,
            progressToEmit = 1 to 1
        )
        val useCase = ExtractTableUseCase(extractor)
        val statuses = mutableListOf<OcrPipelineStatus>()
        val progress = mutableListOf<Pair<Int, Int>>()

        useCase(document, "de", onStatus = { statuses += it }, onProgress = { c, t -> progress += c to t })

        assertEquals(listOf(OcrPipelineStatus.DownloadingModel), statuses)
        assertEquals(listOf(1 to 1), progress)
        assertEquals(document, extractor.capturedDocument)
        assertEquals("de", extractor.capturedLanguageCode)
    }

    @Test
    fun `Cancellation aus dem Extractor wird nicht abgefangen`() = runTest {
        val useCase = ExtractTableUseCase(FakeOcrPositionedTextExtractor(throwing = CancellationException("cancelled")))

        val error = runCatching { useCase(document, "de") }.exceptionOrNull()

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

/** Drei Zeilen x zwei Spalten - eine per [reconstructTable] robust erkennbare Tabelle. */
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

/** Reiner Fließtext ohne Mehrspaltenstruktur - reconstructTable liefert hierfür null. */
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
    var capturedDocument: Document? = null
        private set
    var capturedLanguageCode: String? = null
        private set

    override suspend fun extract(
        document: Document,
        languageCode: String,
        onStatus: (OcrPipelineStatus) -> Unit,
        onProgress: (current: Int, total: Int) -> Unit
    ): List<OcrPositionedPage> {
        capturedDocument = document
        capturedLanguageCode = languageCode
        statusToEmit?.let(onStatus)
        progressToEmit?.let { (current, total) -> onProgress(current, total) }
        throwing?.let { throw it }
        return pages
    }
}
