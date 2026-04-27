package info.meuse24.pdf_scanner.ui.ocr

import androidx.lifecycle.SavedStateHandle
import info.meuse24.pdf_scanner.R
import info.meuse24.pdf_scanner.domain.model.Document
import info.meuse24.pdf_scanner.data.repository.ScanRepository
import info.meuse24.pdf_scanner.domain.usecase.ExportOcrTextUseCase
import info.meuse24.pdf_scanner.domain.usecase.ExtractTextUseCase
import info.meuse24.pdf_scanner.domain.usecase.OcrDocumentResult
import info.meuse24.pdf_scanner.domain.usecase.OcrNoTextException
import info.meuse24.pdf_scanner.testutil.FakeResourceProvider
import info.meuse24.pdf_scanner.testutil.TestDispatcherProvider
import info.meuse24.pdf_scanner.util.OcrInputImageLoader
import info.meuse24.pdf_scanner.util.OcrPipeline
import info.meuse24.pdf_scanner.util.OcrResultStats
import info.meuse24.pdf_scanner.util.PdfPageInputImageLoader
import info.meuse24.pdf_scanner.util.TextRecognizerRunner
import info.meuse24.pdf_scanner.util.DownloadEntry
import info.meuse24.pdf_scanner.util.DownloadsStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

@OptIn(ExperimentalCoroutinesApi::class)
class OcrReviewViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var repository: ScanRepository
    private lateinit var recordsFlow: MutableStateFlow<List<Document>>
    private lateinit var resourceProvider: FakeResourceProvider

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = mock(ScanRepository::class.java)
        recordsFlow = MutableStateFlow(emptyList())
        `when`(repository.getAllScans()).thenReturn(recordsFlow)
        resourceProvider = FakeResourceProvider(
            strings = mapOf(
                R.string.ocr_review_no_text to "No text recognized",
                R.string.ocr_review_load_failed to "Load failed",
                R.string.ocr_review_reextract_failed to "Update failed",
                R.string.ocr_model_download_failed to "Model download failed"
            )
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `loads cached OCR data without running OCR`() = runTest(dispatcher) {
        val record = scanRecord(
            extractedText = "First page\n\nSecond page",
            ocrConfidence = 0.78f,
            ocrLanguage = "en",
            pageTexts = listOf("First page", "Second page")
        )
        recordsFlow.value = listOf(record)
        val extractTextUseCase = RecordingExtractTextUseCase()

        val viewModel = buildViewModel(extractTextUseCase, record.id)
        val collection = backgroundScope.launch { viewModel.uiState.collect() }
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("First page\n\nSecond page", state.text)
        assertEquals(0.78f, state.confidence)
        assertEquals("en", state.recognizedLanguage)
        assertEquals(info.meuse24.pdf_scanner.util.OcrQuality.HIGH, state.quality)
        assertTrue(extractTextUseCase.invocations.isEmpty())
        collection.cancel()
    }

    @Test
    fun `legacy cached text without page json stays a single block`() = runTest(dispatcher) {
        val record = scanRecord(
            extractedText = "Paragraph one\n\nParagraph two",
            pageTexts = emptyList()
        )
        recordsFlow.value = listOf(record)
        val extractTextUseCase = RecordingExtractTextUseCase()

        val viewModel = buildViewModel(extractTextUseCase, record.id)
        val collection = backgroundScope.launch { viewModel.uiState.collect() }
        advanceUntilIdle()

        assertEquals(listOf("Paragraph one\n\nParagraph two"), viewModel.uiState.value.pageTexts)
        assertTrue(extractTextUseCase.invocations.isEmpty())
        collection.cancel()
    }

    @Test
    fun `reExtract persists updated OCR data`() = runTest(dispatcher) {
        val record = scanRecord(
            extractedText = "Old text",
            pageTexts = listOf("Old text")
        )
        recordsFlow.value = listOf(record)
        val result = OcrDocumentResult(
            recordId = record.id,
            fullText = "Updated text",
            pageTexts = listOf("Updated text"),
            stats = OcrResultStats(0.82f, "en", 0f)
        )
        val extractTextUseCase = RecordingExtractTextUseCase { _, _ -> listOf(result) }
        val viewModel = buildViewModel(extractTextUseCase, record.id)
        val collection = backgroundScope.launch { viewModel.uiState.collect() }
        advanceUntilIdle()

        viewModel.reExtract("en")
        advanceUntilIdle()

        verify(repository).updateExtractedTextAndOcrStats(
            record.id,
            "Updated text",
            0.82f,
            "en",
            listOf("Updated text")
        )

        recordsFlow.value = listOf(
            record.copy(
                extractedText = result.fullText,
                ocrConfidence = result.stats?.confidence,
                ocrLanguage = result.stats?.recognizedLanguage,
                pageTexts = result.pageTexts
            )
        )
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("Updated text", state.text)
        assertEquals(listOf("Updated text"), state.pageTexts)
        assertEquals(0.82f, state.confidence)
        assertNull(state.error)
        collection.cancel()
    }

    @Test
    fun `reExtract failure keeps cached text and reports update error`() = runTest(dispatcher) {
        val record = scanRecord(
            extractedText = "Old text",
            pageTexts = listOf("Old text")
        )
        recordsFlow.value = listOf(record)
        val extractTextUseCase = RecordingExtractTextUseCase { _, _ -> throw IllegalStateException("boom") }
        val viewModel = buildViewModel(extractTextUseCase, record.id)
        val collection = backgroundScope.launch { viewModel.uiState.collect() }
        advanceUntilIdle()

        viewModel.reExtract("en")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("Old text", state.text)
        assertEquals(listOf("Old text"), state.pageTexts)
        assertEquals("Update failed", state.error)
        collection.cancel()
    }

    @Test
    fun `missing cached text triggers OCR and surfaces no-text error`() = runTest(dispatcher) {
        val record = scanRecord(extractedText = null, pageTexts = emptyList())
        recordsFlow.value = listOf(record)
        val extractTextUseCase = RecordingExtractTextUseCase { _, _ -> throw OcrNoTextException() }

        val viewModel = buildViewModel(extractTextUseCase, record.id)
        val collection = backgroundScope.launch { viewModel.uiState.collect() }
        advanceUntilIdle()

        assertEquals("No text recognized", viewModel.uiState.value.error)
        assertEquals(1, extractTextUseCase.invocations.size)
        collection.cancel()
    }

    @Test
    fun `clearError resets error state`() = runTest(dispatcher) {
        val record = scanRecord(extractedText = null, pageTexts = emptyList())
        recordsFlow.value = listOf(record)
        val extractTextUseCase = RecordingExtractTextUseCase { _, _ -> throw OcrNoTextException() }
        val viewModel = buildViewModel(extractTextUseCase, record.id)
        val collection = backgroundScope.launch { viewModel.uiState.collect() }
        advanceUntilIdle()

        viewModel.clearError()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.error)
        collection.cancel()
    }

    private fun buildViewModel(
        extractTextUseCase: ExtractTextUseCase,
        scanId: Long
    ): OcrReviewViewModel {
        return OcrReviewViewModel(
            repository = repository,
            extractTextUseCase = extractTextUseCase,
            exportOcrTextUseCase = testExportOcrTextUseCase(),
            resourceProvider = resourceProvider,
            dispatcherProvider = TestDispatcherProvider(dispatcher),
            savedStateHandle = SavedStateHandle(mapOf("scanId" to scanId))
        )
    }

    private fun testExportOcrTextUseCase() = ExportOcrTextUseCase(
        object : DownloadsStorage {
            override fun writeDownload(
                displayName: String,
                mimeType: String,
                writer: (java.io.OutputStream) -> Unit
            ): DownloadEntry = error("DownloadsStorage not configured")
        }
    )

    private fun scanRecord(
        id: Long = 7L,
        extractedText: String? = "Cached text",
        ocrConfidence: Float? = null,
        ocrLanguage: String? = null,
        pageTexts: List<String> = listOf("Cached text")
    ) = Document(
        id = id,
        filename = "scan",
        filepath = "/tmp/scan.pdf",
        timestamp = 0L,
        pageCount = 2,
        fileSize = 0L,
        extractedText = extractedText,
        ocrConfidence = ocrConfidence,
        ocrLanguage = ocrLanguage,
        pageTexts = pageTexts
    )
}

private class RecordingExtractTextUseCase(
    private val handler: suspend (List<Document>, String) -> List<OcrDocumentResult> = { _, _ -> emptyList() }
) : ExtractTextUseCase(
    ocrPipeline = mock(OcrPipeline::class.java),
    inputImageLoader = mock(OcrInputImageLoader::class.java),
    dispatcherProvider = TestDispatcherProvider(StandardTestDispatcher()),
    textRecognizerRunner = mock(TextRecognizerRunner::class.java)
) {
    val invocations = mutableListOf<Pair<List<Document>, String>>()

    override suspend fun invoke(
        records: List<Document>,
        languageCode: String,
        onStatus: (info.meuse24.pdf_scanner.util.OcrPipelineStatus) -> Unit
    ): List<OcrDocumentResult> {
        invocations += records to languageCode
        return handler(records, languageCode)
    }
}

