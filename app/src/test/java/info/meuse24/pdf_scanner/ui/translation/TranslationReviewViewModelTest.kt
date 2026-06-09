package info.meuse24.pdf_scanner.ui.translation

import androidx.lifecycle.SavedStateHandle
import info.meuse24.pdf_scanner.R
import info.meuse24.pdf_scanner.data.repository.ScanRepository
import info.meuse24.pdf_scanner.domain.gateway.TextTranslator
import info.meuse24.pdf_scanner.domain.model.Document
import info.meuse24.pdf_scanner.domain.model.TranslationResult
import info.meuse24.pdf_scanner.domain.usecase.TranslateTextUseCase
import info.meuse24.pdf_scanner.domain.usecase.TranslationNoTextException
import info.meuse24.pdf_scanner.testutil.FakeResourceProvider
import info.meuse24.pdf_scanner.testutil.TestDispatcherProvider
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

@OptIn(ExperimentalCoroutinesApi::class)
class TranslationReviewViewModelTest {

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
                R.string.translate_error_no_text to "No OCR text",
                R.string.translate_error_failed to "Translation failed"
            )
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `loads record from repository`() = runTest(dispatcher) {
        val record = doc(id = 42L)
        recordsFlow.value = listOf(record)
        val vm = buildViewModel(scanId = 42L)
        val job = backgroundScope.launch { vm.uiState.collect() }
        advanceUntilIdle()

        assertEquals(record, vm.uiState.value.record)
        job.cancel()
    }

    @Test
    fun `translate success updates result and clears loading`() = runTest(dispatcher) {
        val record = doc(pageTexts = listOf("Hallo"))
        recordsFlow.value = listOf(record)
        val expectedResult = TranslationResult("de", "en", listOf("Hello"))
        val translator = FixedTextTranslator(expectedResult.pageTranslations)
        val vm = buildViewModel(translator = translator)
        val job = backgroundScope.launch { vm.uiState.collect() }
        advanceUntilIdle()

        vm.translate("de", "en")
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(expectedResult, state.result)
        assertTrue(!state.isLoading)
        assertNull(state.error)
        job.cancel()
    }

    @Test
    fun `translate reports no-text error`() = runTest(dispatcher) {
        val record = doc(pageTexts = emptyList(), extractedText = null)
        recordsFlow.value = listOf(record)
        val vm = buildViewModel(translator = ThrowingTextTranslator(TranslationNoTextException()))
        val job = backgroundScope.launch { vm.uiState.collect() }
        advanceUntilIdle()

        vm.translate("de", "en")
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals("No OCR text", state.error)
        assertNull(state.result)
        assertTrue(!state.isLoading)
        job.cancel()
    }

    @Test
    fun `translate reports generic error on unexpected exception`() = runTest(dispatcher) {
        val record = doc(pageTexts = listOf("text"))
        recordsFlow.value = listOf(record)
        val vm = buildViewModel(translator = ThrowingTextTranslator(RuntimeException("network")))
        val job = backgroundScope.launch { vm.uiState.collect() }
        advanceUntilIdle()

        vm.translate("de", "en")
        advanceUntilIdle()

        assertEquals("Translation failed", vm.uiState.value.error)
        job.cancel()
    }

    @Test
    fun `clearError resets error state`() = runTest(dispatcher) {
        val record = doc(pageTexts = emptyList(), extractedText = null)
        recordsFlow.value = listOf(record)
        val vm = buildViewModel(translator = ThrowingTextTranslator(TranslationNoTextException()))
        val job = backgroundScope.launch { vm.uiState.collect() }
        advanceUntilIdle()
        vm.translate("de", "en")
        advanceUntilIdle()
        assertNotNull(vm.uiState.value.error)

        vm.clearError()
        advanceUntilIdle()

        assertNull(vm.uiState.value.error)
        job.cancel()
    }

    @Test
    fun `progress is cleared after successful translation`() = runTest(dispatcher) {
        val record = doc(pageTexts = listOf("A", "B"))
        recordsFlow.value = listOf(record)
        val vm = buildViewModel(translator = FixedTextTranslator(listOf("X", "Y")))
        val job = backgroundScope.launch { vm.uiState.collect() }
        advanceUntilIdle()

        vm.translate("de", "en")
        advanceUntilIdle()

        val state = vm.uiState.value
        assertNull("progress must be null after completion", state.progress)
        assertTrue("loading must be false after completion", !state.isLoading)
        assertNotNull("result must be set", state.result)
        job.cancel()
    }

    @Test
    fun `second translate call is ignored while first is in progress`() = runTest(dispatcher) {
        val record = doc(pageTexts = listOf("text"))
        recordsFlow.value = listOf(record)
        val translator = RecordingCallCountTranslator()
        val vm = buildViewModel(translator = translator)
        val job = backgroundScope.launch { vm.uiState.collect() }
        advanceUntilIdle()

        vm.translate("de", "en")
        vm.translate("de", "fr")
        advanceUntilIdle()

        assertEquals(1, translator.callCount)
        job.cancel()
    }

    private fun buildViewModel(
        scanId: Long = 1L,
        translator: TextTranslator = FixedTextTranslator(emptyList())
    ) = TranslationReviewViewModel(
        repository = repository,
        translateTextUseCase = TranslateTextUseCase(translator),
        resourceProvider = resourceProvider,
        dispatcherProvider = TestDispatcherProvider(dispatcher),
        savedStateHandle = SavedStateHandle(mapOf("scanId" to scanId))
    )

    private fun doc(
        id: Long = 1L,
        pageTexts: List<String> = listOf("Text"),
        extractedText: String? = "Text",
        ocrLanguage: String? = null
    ) = Document(
        id = id,
        filename = "doc.pdf",
        filepath = "/tmp/doc.pdf",
        timestamp = 0L,
        pageCount = pageTexts.size.coerceAtLeast(1),
        fileSize = 0L,
        pageTexts = pageTexts,
        extractedText = extractedText,
        ocrLanguage = ocrLanguage
    )
}

private class FixedTextTranslator(private val result: List<String>) : TextTranslator {
    override suspend fun translate(
        pageTexts: List<String>,
        sourceLanguage: String,
        targetLanguage: String,
        onProgress: (Int, Int) -> Unit
    ): List<String> {
        pageTexts.forEachIndexed { i, _ -> onProgress(i + 1, pageTexts.size) }
        return result
    }
}

private class ThrowingTextTranslator(private val error: Throwable) : TextTranslator {
    override suspend fun translate(
        pageTexts: List<String>,
        sourceLanguage: String,
        targetLanguage: String,
        onProgress: (Int, Int) -> Unit
    ): List<String> = throw error
}

private class RecordingCallCountTranslator : TextTranslator {
    var callCount = 0
    override suspend fun translate(
        pageTexts: List<String>,
        sourceLanguage: String,
        targetLanguage: String,
        onProgress: (Int, Int) -> Unit
    ): List<String> {
        callCount++
        return pageTexts
    }
}
