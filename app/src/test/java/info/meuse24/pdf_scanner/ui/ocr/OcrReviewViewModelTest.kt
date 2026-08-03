package info.meuse24.pdf_scanner.ui.ocr

import androidx.lifecycle.SavedStateHandle
import info.meuse24.pdf_scanner.R
import info.meuse24.pdf_scanner.domain.model.Document
import info.meuse24.pdf_scanner.domain.model.AppSettings
import info.meuse24.pdf_scanner.domain.common.OcrAiPromptBuilder
import info.meuse24.pdf_scanner.domain.common.OcrAiPromptPurpose
import info.meuse24.pdf_scanner.data.repository.ScanRepository
import info.meuse24.pdf_scanner.domain.usecase.ExportOcrTextUseCase
import info.meuse24.pdf_scanner.domain.usecase.ExtractTextUseCase
import info.meuse24.pdf_scanner.domain.usecase.OcrDocumentResult
import info.meuse24.pdf_scanner.domain.usecase.OcrNoTextException
import info.meuse24.pdf_scanner.testutil.FakeResourceProvider
import info.meuse24.pdf_scanner.testutil.FakeSettingsRepository
import info.meuse24.pdf_scanner.testutil.TestDispatcherProvider
import info.meuse24.pdf_scanner.util.OcrInputImageLoader
import info.meuse24.pdf_scanner.util.OcrPipeline
import info.meuse24.pdf_scanner.domain.model.OcrResultStats
import info.meuse24.pdf_scanner.util.PdfPageInputImageLoader
import info.meuse24.pdf_scanner.util.TextRecognizerRunner
import info.meuse24.pdf_scanner.domain.gateway.DownloadEntry
import info.meuse24.pdf_scanner.domain.gateway.DownloadsStorage
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
import org.junit.Assert.assertNotNull
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
                R.string.ocr_model_download_failed to "Model download failed",
                R.string.ocr_ai_prompt_instruction to "Instruction",
                R.string.ocr_ai_prompt_summary_instruction to "Summary instruction",
                R.string.ocr_ai_prompt_output_language_rule to "Keep source language",
                R.string.ocr_ai_prompt_page_hint to "Page %1${'$'}d of %2${'$'}d",
                R.string.ocr_ai_prompt_page_hint_short to "Page %1${'$'}d",
                R.string.ocr_ai_prompt_too_long to "Too long"
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
        assertEquals(info.meuse24.pdf_scanner.domain.model.OcrQuality.HIGH, state.quality)
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
    fun `blank OCR pages stay hidden while retaining their physical page numbers`() = runTest(dispatcher) {
        val record = scanRecord(
            extractedText = "First page\n\n\n\nThird page",
            pageTexts = listOf("First page", "", "Third page")
        )
        recordsFlow.value = listOf(record)
        val viewModel = buildViewModel(RecordingExtractTextUseCase(), record.id)
        val collection = backgroundScope.launch { viewModel.uiState.collect() }
        advanceUntilIdle()

        assertEquals(
            listOf(
                OcrReviewPage(pageIndex = 0, text = "First page", canCopyAiPrompt = true),
                OcrReviewPage(pageIndex = 2, text = "Third page", canCopyAiPrompt = true)
            ),
            viewModel.uiState.value.displayPages
        )
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

    @Test
    fun `AI prompt requires consent then releases exactly once`() = runTest(dispatcher) {
        val record = scanRecord(extractedText = "Receipt", pageTexts = listOf("Receipt"))
        recordsFlow.value = listOf(record)
        val settings = FakeSettingsRepository()
        val viewModel = buildViewModel(RecordingExtractTextUseCase(), record.id, settings)
        val collection = backgroundScope.launch { viewModel.uiState.collect() }
        advanceUntilIdle()

        viewModel.requestAiPrompt()
        assertNotNull(viewModel.pendingAiPrompt.value)
        assertNull(viewModel.aiPromptToCopy.value)

        viewModel.confirmAiPrompt(
            target = info.meuse24.pdf_scanner.domain.model.AiChatbotTarget("Test", "https://example.com/"),
            consentAccepted = true
        )
        assertNull(viewModel.pendingAiPrompt.value)
        assertNotNull(viewModel.aiPromptToCopy.value)
        assertTrue(settings.settings.value.aiPromptNoticeAccepted)

        viewModel.onAiPromptCopied()
        assertNull(viewModel.aiPromptToCopy.value)
        collection.cancel()
    }

    @Test
    fun `dismissing AI prompt keeps consent false and does not copy`() = runTest(dispatcher) {
        val record = scanRecord(extractedText = "Receipt")
        recordsFlow.value = listOf(record)
        val settings = FakeSettingsRepository()
        val viewModel = buildViewModel(RecordingExtractTextUseCase(), record.id, settings)
        val collection = backgroundScope.launch { viewModel.uiState.collect() }
        advanceUntilIdle()

        viewModel.requestAiPrompt()
        viewModel.dismissAiPrompt()

        assertNull(viewModel.pendingAiPrompt.value)
        assertNull(viewModel.aiPromptToCopy.value)
        assertTrue(!settings.settings.value.aiPromptNoticeAccepted)
        collection.cancel()
    }

    @Test
    fun `accepted consent skips dialog and page fallback uses short hint`() = runTest(dispatcher) {
        val record = scanRecord(extractedText = "First", pageTexts = listOf("First"))
        recordsFlow.value = listOf(record)
        val settings = FakeSettingsRepository(AppSettings(aiPromptNoticeAccepted = true))
        val viewModel = buildViewModel(RecordingExtractTextUseCase(), record.id, settings)
        val collection = backgroundScope.launch { viewModel.uiState.collect() }
        advanceUntilIdle()

        viewModel.requestAiPrompt(pageIndex = 0)

        assertTrue(requireNotNull(viewModel.pendingAiPrompt.value).contains("Page 1"))
        assertTrue(!requireNotNull(viewModel.pendingAiPrompt.value).contains("of 2"))
        collection.cancel()
    }

    @Test
    fun `summary prompt uses the summary instruction and accepted consent`() = runTest(dispatcher) {
        val record = scanRecord(extractedText = "Receipt", pageTexts = listOf("Receipt"))
        recordsFlow.value = listOf(record)
        val settings = FakeSettingsRepository(AppSettings(aiPromptNoticeAccepted = true))
        val viewModel = buildViewModel(RecordingExtractTextUseCase(), record.id, settings)
        val collection = backgroundScope.launch { viewModel.uiState.collect() }
        advanceUntilIdle()

        viewModel.requestAiPrompt(purpose = OcrAiPromptPurpose.SUMMARY)

        assertTrue(requireNotNull(viewModel.pendingAiPrompt.value).startsWith("Summary instruction"))
        collection.cancel()
    }

    @Test
    fun `oversized AI prompt is disabled without general error`() = runTest(dispatcher) {
        val text = "x".repeat(OcrAiPromptBuilder.MAX_PROMPT_CHARS)
        val record = scanRecord(extractedText = text, pageTexts = listOf(text))
        recordsFlow.value = listOf(record)
        val viewModel = buildViewModel(RecordingExtractTextUseCase(), record.id)
        val collection = backgroundScope.launch { viewModel.uiState.collect() }
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isAiPromptTooLong)
        assertTrue(!viewModel.uiState.value.canCopyAiPrompt)
        viewModel.requestAiPrompt()
        assertNull(viewModel.uiState.value.error)
        assertNull(viewModel.aiPromptToCopy.value)
        collection.cancel()
    }

    private fun buildViewModel(
        extractTextUseCase: ExtractTextUseCase,
        scanId: Long,
        settingsRepository: FakeSettingsRepository = FakeSettingsRepository()
    ): OcrReviewViewModel {
        return OcrReviewViewModel(
            repository = repository,
            extractTextUseCase = extractTextUseCase,
            exportOcrTextUseCase = testExportOcrTextUseCase(),
            settingsRepository = settingsRepository,
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
    ocrDocumentTextExtractor = object : info.meuse24.pdf_scanner.domain.gateway.OcrDocumentTextExtractor {
        override suspend fun extract(
            records: List<Document>,
            languageCode: String,
            onStatus: (info.meuse24.pdf_scanner.domain.model.OcrPipelineStatus) -> Unit
        ): List<OcrDocumentResult> = emptyList()
    }
) {
    val invocations = mutableListOf<Pair<List<Document>, String>>()

    override suspend fun invoke(
        records: List<Document>,
        languageCode: String,
        onStatus: (info.meuse24.pdf_scanner.domain.model.OcrPipelineStatus) -> Unit
    ): List<OcrDocumentResult> {
        invocations += records to languageCode
        return handler(records, languageCode)
    }
}

