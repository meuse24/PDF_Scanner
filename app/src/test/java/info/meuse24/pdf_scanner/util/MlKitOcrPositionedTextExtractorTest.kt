package info.meuse24.pdf_scanner.util

import android.graphics.Rect
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognizer
import info.meuse24.pdf_scanner.domain.model.Document
import info.meuse24.pdf_scanner.domain.model.OcrPipelineResult
import info.meuse24.pdf_scanner.domain.model.OcrPipelineStatus
import info.meuse24.pdf_scanner.domain.model.OcrResultStats
import info.meuse24.pdf_scanner.domain.model.OcrScript
import info.meuse24.pdf_scanner.domain.model.OcrUsage
import info.meuse24.pdf_scanner.testutil.TestDispatcherProvider
import java.io.File
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Mockito.mock
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`

class MlKitOcrPositionedTextExtractorTest {

    @get:Rule
    val tmpFolder = TemporaryFolder()

    private val testDispatcher = StandardTestDispatcher()

    @Test
    fun `fehlende PDF-Datei liefert leere Liste ohne OCR-Aufruf`() = runTest(testDispatcher) {
        val ocrPipeline = mock(OcrPipeline::class.java)
        val extractor = MlKitOcrPositionedTextExtractor(
            ocrPipeline = ocrPipeline,
            textRecognizerRunner = mock(TextRecognizerRunner::class.java),
            dispatcherProvider = TestDispatcherProvider(testDispatcher)
        )
        val document = document(File(tmpFolder.root, "missing.pdf").absolutePath)

        val result = extractor.extract(document, "de")

        assertTrue(result.isEmpty())
        verifyNoInteractions(ocrPipeline)
    }

    @Test
    fun `mappt Geometrie mit 220-DPI-Scale, ueberspringt boxlose Objekte und behaelt leere Seiten`() = runTest(testDispatcher) {
        val recognizer = mock(TextRecognizer::class.java)
        val pdfFile = tmpFolder.newFile("document.pdf").apply { writeText("pdf") }

        val keptElement = mockElement(text = "Alpha", box = mock(Rect::class.java), confidence = 0.87f, angle = 1.5f)
        val skippedElement = mockElement(text = "Ghost", box = null)
        val keptLine = mockLine(text = "Alpha", box = mock(Rect::class.java), elements = listOf(keptElement, skippedElement))
        val boxlessLine = mockLine(text = "Unsichtbar", box = null, elements = listOf(mockElement("Unsichtbar", mock(Rect::class.java))))
        val block = mockTextBlock(listOf(keptLine, boxlessLine))
        val pageWithContent = mockText(listOf(block))
        val emptyPage = mockText(emptyList())

        val fakeRunner = FakePositionedTextRecognizerRunner(
            pages = listOf(pageWithContent, emptyPage),
            bitmapDims = listOf(1800 to 2500, 1800 to 2500)
        )
        val extractor = MlKitOcrPositionedTextExtractor(
            ocrPipeline = FakeOcrPipeline(recognizer),
            textRecognizerRunner = fakeRunner,
            dispatcherProvider = TestDispatcherProvider(testDispatcher)
        )
        val document = document(pdfFile.absolutePath)
        val progressCalls = mutableListOf<Pair<Int, Int>>()

        val result = extractor.extract(document, "de", onProgress = { current, total -> progressCalls += current to total })

        assertEquals(2, result.size)

        val firstPage = result[0]
        assertEquals(0, firstPage.pageIndex)
        assertEquals(1800, firstPage.widthPx)
        assertEquals(2500, firstPage.heightPx)
        // boxlose Line wird komplett uebersprungen; von der behaltenen Line bleibt nur das
        // Element mit Bounding-Box erhalten.
        assertEquals(1, firstPage.lines.size)
        val onlyLine = firstPage.lines.single()
        assertEquals("Alpha", onlyLine.text)
        assertEquals(1, onlyLine.elements.size)
        assertEquals(0.87f, onlyLine.elements.single().confidence, 0.0001f)
        assertEquals(1.5f, onlyLine.elements.single().angleDeg, 0.0001f)

        val secondPage = result[1]
        assertEquals(1, secondPage.pageIndex)
        assertTrue("leere Seite muss trotzdem im Ergebnis enthalten sein", secondPage.lines.isEmpty())

        assertEquals(PDF_TABLE_RENDER_SCALE, fakeRunner.capturedScale)
        assertEquals(listOf(1 to 2, 2 to 2), progressCalls)
    }

    @Test
    fun `Line mit gueltiger Box aber ausschliesslich boxlosen Elements erhaelt Fallback-Element`() = runTest(testDispatcher) {
        val recognizer = mock(TextRecognizer::class.java)
        val pdfFile = tmpFolder.newFile("document.pdf").apply { writeText("pdf") }

        val boxlessElementA = mockElement(text = "Total", box = null, confidence = 0.6f, angle = 2f)
        val boxlessElementB = mockElement(text = "Betrag", box = null, confidence = 0.8f, angle = 4f)
        val lineWithBoxOnly = mockLine(
            text = "Total Betrag",
            box = mock(Rect::class.java),
            elements = listOf(boxlessElementA, boxlessElementB)
        )
        val block = mockTextBlock(listOf(lineWithBoxOnly))
        val page = mockText(listOf(block))

        val fakeRunner = FakePositionedTextRecognizerRunner(pages = listOf(page), bitmapDims = listOf(1800 to 2500))
        val extractor = MlKitOcrPositionedTextExtractor(
            ocrPipeline = FakeOcrPipeline(recognizer),
            textRecognizerRunner = fakeRunner,
            dispatcherProvider = TestDispatcherProvider(testDispatcher)
        )

        val result = extractor.extract(document(pdfFile.absolutePath), "de")

        // Ohne Fallback wuerde die Line trotz gueltiger eigener Box verworfen (leer), weil alle
        // Elements boxlos sind - der TableReconstructor-Fallback fuer genau diesen Fall bliebe
        // sonst fuer reale OCR-Daten unerreichbar.
        val onlyLine = result.single().lines.single()
        assertEquals("Total Betrag", onlyLine.text)
        assertEquals(1, onlyLine.elements.size)
        val fallbackElement = onlyLine.elements.single()
        assertEquals("Total Betrag", fallbackElement.text)
        // Konfidenz/Winkel werden aus den (boxlosen) Original-Elementen gemittelt statt verworfen.
        assertEquals(0.7f, fallbackElement.confidence, 0.0001f)
        assertEquals(3f, fallbackElement.angleDeg, 0.0001f)
    }

    @Test
    fun `mlKitRectToOcrRect uebernimmt konkrete Koordinaten unveraendert`() {
        // android.graphics.Rect liefert unter dem Unit-Test-Stub-Jar (isReturnDefaultValues)
        // sowohl bei echter Konstruktion als auch bei Mockito-Mocks nur Nullfelder (leere
        // Konstruktor-/Methoden-Bodies; Mockito kann zudem öffentliche Felder nicht stubben).
        // Deshalb wird hier die reine Umrechnungslogik direkt mit konkreten Koordinaten geprüft,
        // losgelöst von Rect selbst.
        val ocrRect = mlKitRectToOcrRect(left = 10, top = 20, right = 110, bottom = 40)

        assertEquals(10f, ocrRect.left, 0.0001f)
        assertEquals(20f, ocrRect.top, 0.0001f)
        assertEquals(110f, ocrRect.right, 0.0001f)
        assertEquals(40f, ocrRect.bottom, 0.0001f)
        assertEquals(100f, ocrRect.width, 0.0001f)
        assertEquals(20f, ocrRect.height, 0.0001f)
    }

    private fun document(filepath: String): Document = Document(
        id = 1L,
        filename = "document",
        filepath = filepath,
        timestamp = 0L,
        pageCount = 1,
        fileSize = 0L
    )

    private fun mockElement(text: String, box: Rect?, confidence: Float = 0.9f, angle: Float = 0f): Text.Element =
        mock(Text.Element::class.java).apply {
            `when`(this.text).thenReturn(text)
            `when`(this.boundingBox).thenReturn(box)
            `when`(this.confidence).thenReturn(confidence)
            `when`(this.angle).thenReturn(angle)
        }

    private fun mockLine(text: String, box: Rect?, elements: List<Text.Element>): Text.Line =
        mock(Text.Line::class.java).apply {
            `when`(this.text).thenReturn(text)
            `when`(this.boundingBox).thenReturn(box)
            `when`(this.elements).thenReturn(elements)
        }

    private fun mockTextBlock(lines: List<Text.Line>): Text.TextBlock =
        mock(Text.TextBlock::class.java).apply {
            `when`(this.lines).thenReturn(lines)
        }

    private fun mockText(blocks: List<Text.TextBlock>): Text =
        mock(Text::class.java).apply {
            `when`(this.textBlocks).thenReturn(blocks)
        }
}

private class FakePositionedTextRecognizerRunner(
    private val pages: List<Text>,
    private val bitmapDims: List<Pair<Int, Int>>
) : TextRecognizerRunner {
    var capturedScale: Float? = null
        private set

    override suspend fun recognizeText(recognizer: TextRecognizer, image: InputImage): String =
        error("not used in this test")

    override suspend fun recognizeFullText(recognizer: TextRecognizer, image: InputImage): Text =
        error("not used in this test")

    override suspend fun recognizeWithStats(recognizer: TextRecognizer, image: InputImage): Pair<Text, OcrResultStats> =
        error("not used in this test")

    override suspend fun processPages(
        pdfFile: File,
        recognizer: TextRecognizer,
        onPage: suspend (text: String, stats: OcrResultStats?) -> Unit
    ) = error("not used in this test")

    override suspend fun processPagesFullText(
        pdfFile: File,
        recognizer: TextRecognizer,
        renderScale: Float,
        onPage: suspend (pageIndex: Int, pageCount: Int, bitmapWidth: Int, bitmapHeight: Int, text: Text) -> Unit
    ) {
        capturedScale = renderScale
        pages.forEachIndexed { index, text ->
            val (w, h) = bitmapDims[index]
            onPage(index, pages.size, w, h, text)
        }
    }
}

private class FakeOcrPipeline(
    private val recognizer: TextRecognizer
) : OcrPipeline(
    ocrManager = mock(OcrManager::class.java),
    ocrModelInstaller = mock(OcrModelInstaller::class.java)
) {
    override suspend fun <T> runWithFallback(
        languageCode: String,
        usage: OcrUsage,
        onStatus: (OcrPipelineStatus) -> Unit,
        emptyValue: () -> T,
        isSuccess: (T, OcrResultStats?) -> Boolean,
        block: suspend (TextRecognizer, OcrScript) -> Pair<T, OcrResultStats?>
    ): OcrPipelineResult<T> {
        val (value, stats) = block(recognizer, OcrScript.LATIN)
        return OcrPipelineResult(value = value, script = OcrScript.LATIN, stats = stats)
    }
}
