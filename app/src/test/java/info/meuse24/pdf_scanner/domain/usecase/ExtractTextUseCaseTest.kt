package info.meuse24.pdf_scanner.domain.usecase

import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognizer
import info.meuse24.pdf_scanner.domain.model.Document
import info.meuse24.pdf_scanner.testutil.TestDispatcherProvider
import info.meuse24.pdf_scanner.util.OcrInputImageLoader
import info.meuse24.pdf_scanner.util.OcrModelInstaller
import info.meuse24.pdf_scanner.util.OcrManager
import info.meuse24.pdf_scanner.util.OcrPipeline
import info.meuse24.pdf_scanner.util.OcrPipelineResult
import info.meuse24.pdf_scanner.util.OcrPipelineStatus
import info.meuse24.pdf_scanner.util.OcrUsage
import info.meuse24.pdf_scanner.util.OcrResultStats
import info.meuse24.pdf_scanner.util.OcrScript
import info.meuse24.pdf_scanner.util.TextRecognizerRunner
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.io.File

class ExtractTextUseCaseTest {

    @get:Rule
    val tmpFolder = TemporaryFolder()

    private val testDispatcher = StandardTestDispatcher()

    @Test
    fun `uses thumbnail fallback and combines multiple OCR results`() = runTest(testDispatcher) {
        val recognizer = mock(TextRecognizer::class.java)
        val ocrPipeline = FakeOcrPipeline(recognizer)
        val inputImageLoader = mock(OcrInputImageLoader::class.java)
        val textRecognizerRunner = mock(TextRecognizerRunner::class.java)
        val image = mock(InputImage::class.java)
        val firstThumb = thumbnailFile("first.jpg")
        val secondThumb = thumbnailFile("second.jpg")

        `when`(inputImageLoader.loadFromFile(firstThumb)).thenReturn(image)
        `when`(inputImageLoader.loadFromFile(secondThumb)).thenReturn(image)

        // Mock recognizeWithStats
        val stats = OcrResultStats(0.9f, "en", 0f)
        val firstText = mock(com.google.mlkit.vision.text.Text::class.java).apply {
            `when`(text).thenReturn("Alpha")
        }
        val secondText = mock(com.google.mlkit.vision.text.Text::class.java).apply {
            `when`(text).thenReturn("Beta")
        }
        `when`(textRecognizerRunner.recognizeWithStats(recognizer, image)).thenReturn(
            firstText to stats,
            secondText to stats
        )

        val useCase = ExtractTextUseCase(
            ocrPipeline = ocrPipeline,
            inputImageLoader = inputImageLoader,
            dispatcherProvider = TestDispatcherProvider(testDispatcher),
            textRecognizerRunner = textRecognizerRunner
        )

        val result = useCase(
            records = listOf(
                record("first", firstThumb),
                record("second", secondThumb)
            ),
            languageCode = "de"
        )

        assertEquals(2, result.size)
        assertEquals("Alpha", result[0].fullText)
        assertEquals("Beta", result[1].fullText)
        assertEquals(listOf("Alpha"), result[0].pageTexts)
        assertEquals(listOf("Beta"), result[1].pageTexts)
        verify(recognizer).close()
        verify(inputImageLoader).loadFromFile(firstThumb)
        verify(inputImageLoader).loadFromFile(secondThumb)
    }

    @Test
    fun `throws when OCR result is blank for all records`() = runTest(testDispatcher) {
        val recognizer = mock(TextRecognizer::class.java)
        val ocrPipeline = FakeOcrPipeline(recognizer)
        val inputImageLoader = mock(OcrInputImageLoader::class.java)
        val textRecognizerRunner = mock(TextRecognizerRunner::class.java)
        val image = mock(InputImage::class.java)
        val thumb = thumbnailFile("blank.jpg")

        `when`(inputImageLoader.loadFromFile(thumb)).thenReturn(image)
        val blankText = mock(com.google.mlkit.vision.text.Text::class.java).apply {
            `when`(text).thenReturn("")
        }
        `when`(textRecognizerRunner.recognizeWithStats(recognizer, image)).thenReturn(
            blankText to OcrResultStats(0f, null, 0f)
        )

        val useCase = ExtractTextUseCase(
            ocrPipeline = ocrPipeline,
            inputImageLoader = inputImageLoader,
            dispatcherProvider = TestDispatcherProvider(testDispatcher),
            textRecognizerRunner = textRecognizerRunner
        )

        val error = runCatching {
            useCase(listOf(record("blank", thumb)), "en")
        }.exceptionOrNull()

        assertTrue(error is OcrNoTextException)
        verify(recognizer).close()
    }

    @Test
    fun `uses pdf page loader when pdf file exists`() = runTest(testDispatcher) {
        val recognizer = mock(TextRecognizer::class.java)
        val ocrPipeline = FakeOcrPipeline(recognizer)
        val inputImageLoader = FailingOcrInputImageLoader()
        val firstPage = mock(InputImage::class.java)
        val secondPage = mock(InputImage::class.java)

        val alphaText = mock(com.google.mlkit.vision.text.Text::class.java).apply {
            `when`(text).thenReturn("Alpha")
        }
        val betaText = mock(com.google.mlkit.vision.text.Text::class.java).apply {
            `when`(text).thenReturn("Beta")
        }
        val stats = OcrResultStats(0.9f, "en", 0f)

        val textRecognizerRunner = FakeTextRecognizerRunner(
            results = mapOf(
                firstPage to (alphaText to stats),
                secondPage to (betaText to stats)
            ),
            pageImages = listOf(firstPage, secondPage)
        )
        val pdfFile = tmpFolder.newFile("document.pdf").apply { writeText("pdf") }
        val thumb = thumbnailFile("document.jpg")

        val useCase = ExtractTextUseCase(
            ocrPipeline = ocrPipeline,
            inputImageLoader = inputImageLoader,
            dispatcherProvider = TestDispatcherProvider(testDispatcher),
            textRecognizerRunner = textRecognizerRunner
        )

        val result = useCase(
            records = listOf(
                Document(
                    id = 1L,
                    filename = "document",
                    filepath = pdfFile.absolutePath,
                    timestamp = 0L,
                    pageCount = 2,
                    fileSize = pdfFile.length(),
                    thumbnailPath = thumb.absolutePath
                )
            ),
            languageCode = "en"
        ).single()

        assertEquals("Alpha\n\nBeta", result.fullText)
        assertEquals(listOf("Alpha", "Beta"), result.pageTexts)
        assertEquals(listOf(pdfFile), textRecognizerRunner.sourceFiles)
        assertEquals(0, inputImageLoader.calls)
        verify(recognizer).close()
    }

    private fun record(name: String, thumbnail: File): Document {
        return Document(
            id = 1L,
            filename = name,
            filepath = File(tmpFolder.root, "$name.pdf").absolutePath,
            timestamp = 0L,
            pageCount = 1,
            fileSize = 0L,
            thumbnailPath = thumbnail.absolutePath
        )
    }

    private fun thumbnailFile(name: String): File {
        return tmpFolder.newFile(name).apply { writeText("thumb") }
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
        return try {
            val (value, stats) = block(recognizer, OcrScript.LATIN)
            OcrPipelineResult(
                value = value,
                script = OcrScript.LATIN,
                stats = stats
            )
        } finally {
            recognizer.close()
        }
    }
}

private class FailingOcrInputImageLoader : OcrInputImageLoader {
    var calls: Int = 0

    override fun loadFromFile(file: File): InputImage {
        calls++
        error("thumbnail fallback should not be used")
    }
}

private class FakeTextRecognizerRunner(
    private val results: Map<InputImage, Pair<com.google.mlkit.vision.text.Text, info.meuse24.pdf_scanner.util.OcrResultStats>>,
    private val pageImages: List<InputImage> = emptyList()
) : TextRecognizerRunner {
    val sourceFiles = mutableListOf<File>()

    override suspend fun recognizeText(recognizer: TextRecognizer, image: InputImage): String {
        return results.getValue(image).first.text
    }

    override suspend fun recognizeFullText(recognizer: TextRecognizer, image: InputImage) =
        results.getValue(image).first

    override suspend fun recognizeWithStats(
        recognizer: TextRecognizer,
        image: InputImage
    ): Pair<com.google.mlkit.vision.text.Text, info.meuse24.pdf_scanner.util.OcrResultStats> {
        return results.getValue(image)
    }

    override suspend fun processPages(
        pdfFile: File,
        recognizer: TextRecognizer,
        onPage: suspend (text: String, stats: info.meuse24.pdf_scanner.util.OcrResultStats?) -> Unit
    ) {
        sourceFiles += pdfFile
        pageImages.forEach { image ->
            val (text, stats) = recognizeWithStats(recognizer, image)
            onPage(text.text, stats)
        }
    }
}

