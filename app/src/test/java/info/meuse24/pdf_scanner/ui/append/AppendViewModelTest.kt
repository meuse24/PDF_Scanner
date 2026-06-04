package info.meuse24.pdf_scanner.ui.append

import androidx.lifecycle.SavedStateHandle
import info.meuse24.pdf_scanner.R
import info.meuse24.pdf_scanner.data.repository.ScanRepository
import info.meuse24.pdf_scanner.domain.model.Document
import info.meuse24.pdf_scanner.domain.model.PdfMarginPreset
import info.meuse24.pdf_scanner.domain.model.PdfPageSetup
import info.meuse24.pdf_scanner.domain.usecase.AppendResult
import info.meuse24.pdf_scanner.domain.usecase.AppendSource
import info.meuse24.pdf_scanner.domain.usecase.AppendToPdfUseCase
import info.meuse24.pdf_scanner.domain.usecase.ImagePdfBuilder
import info.meuse24.pdf_scanner.domain.usecase.ImagePdfOptions
import info.meuse24.pdf_scanner.domain.usecase.ImagePdfPageMode
import info.meuse24.pdf_scanner.domain.usecase.ImagePageLayout
import info.meuse24.pdf_scanner.domain.workflow.AppendToPdfWorkflow
import info.meuse24.pdf_scanner.domain.workflow.WorkflowErrorMapper
import info.meuse24.pdf_scanner.testutil.FakeResourceProvider
import info.meuse24.pdf_scanner.testutil.FakeSettingsRepository
import info.meuse24.pdf_scanner.testutil.TestDispatcherProvider
import info.meuse24.pdf_scanner.util.FileUtil
import info.meuse24.pdf_scanner.util.PdfEditor
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class AppendViewModelTest {

    @get:Rule
    val tmpFolder = TemporaryFolder()

    private val dispatcher = StandardTestDispatcher()
    private lateinit var repository: ScanRepository
    private lateinit var workflow: AppendToPdfWorkflow
    private lateinit var appendUseCase: ConfigurableAppendToPdfUseCase
    private lateinit var resourceProvider: FakeResourceProvider
    private lateinit var record: Document

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        val targetFile = tmpFolder.newFile("target.pdf").apply { writeText("pdf") }
        record = Document(
            id = 5L,
            filename = "target",
            filepath = targetFile.absolutePath,
            timestamp = 0L,
            pageCount = 3,
            fileSize = targetFile.length()
        )
        repository = mock(ScanRepository::class.java)
        appendUseCase = ConfigurableAppendToPdfUseCase()
        workflow = AppendToPdfWorkflow(appendUseCase)
        `when`(repository.getAllScans()).thenReturn(flowOf(listOf(record)))
        resourceProvider = FakeResourceProvider(
            strings = mapOf(
                R.string.append_error to "Append failed"
            ),
            plurals = mapOf(
                R.plurals.append_success to "%d pages appended"
            )
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `appendPdf success exposes success message`() = runTest(dispatcher) {
        appendUseCase.handler = { _, _ -> AppendResult(pageCount = 5, fileSize = 96L, appendedPageCount = 2) }
        val viewModel = buildViewModel()
        val recordObserver = observeRecord(viewModel)

        runCurrent()
        viewModel.appendPdf(mock(android.net.Uri::class.java))
        advanceUntilIdle()

        assertEquals("2 pages appended", viewModel.success.value)
        assertNull(viewModel.error.value)
        recordObserver.cancel()
    }

    @Test
    fun `appendImages clears pending uris on success`() = runTest(dispatcher) {
        appendUseCase.handler = { _, _ -> AppendResult(pageCount = 4, fileSize = 90L, appendedPageCount = 1) }
        val viewModel = buildViewModel()
        val recordObserver = observeRecord(viewModel)
        runCurrent()
        viewModel.setPendingImageUris(listOf(mock(android.net.Uri::class.java)))

        viewModel.appendImages(ImagePdfOptions(ImagePageLayout.SINGLE))
        advanceUntilIdle()

        assertTrue(viewModel.pendingImageUris.value.isEmpty())
        recordObserver.cancel()
    }

    @Test
    fun `appendImages baut ImagePdfOptions mit aktuellem PageSetup`() = runTest(dispatcher) {
        appendUseCase.handler = { _, _ -> AppendResult(pageCount = 4, fileSize = 90L, appendedPageCount = 1) }
        val viewModel = buildViewModel()
        val recordObserver = observeRecord(viewModel)
        val setup = PdfPageSetup(marginPreset = PdfMarginPreset.SMALL)
        runCurrent()
        viewModel.setPendingImageUris(listOf(mock(android.net.Uri::class.java)))

        viewModel.updatePageSetup(setup)
        viewModel.appendImages(
            ImagePdfOptions(
                layout = ImagePageLayout.TWO_PER_PAGE,
                pageSetup = setup,
                pageMode = ImagePdfPageMode.PHOTO_PAGE
            )
        )
        advanceUntilIdle()

        val source = appendUseCase.invocations.single().second as AppendSource.Images
        assertEquals(ImagePageLayout.TWO_PER_PAGE, source.options.layout)
        assertEquals(setup, source.options.pageSetup)
        assertEquals(ImagePdfPageMode.PHOTO_PAGE, source.options.pageMode)
        recordObserver.cancel()
    }

    @Test
    fun `append guard ignores second request while loading`() = runTest(dispatcher) {
        val result = CompletableDeferred<AppendResult>()
        appendUseCase.handler = { _, _ -> result.await() }
        val viewModel = buildViewModel()
        val recordObserver = observeRecord(viewModel)
        runCurrent()

        viewModel.appendPdf(mock(android.net.Uri::class.java))
        viewModel.appendPdf(mock(android.net.Uri::class.java))
        runCurrent()
        result.complete(AppendResult(4, 80L, 1))
        advanceUntilIdle()

        assertEquals(1, appendUseCase.invocations.size)
        recordObserver.cancel()
    }

    @Test
    fun `workflow failure maps error`() = runTest(dispatcher) {
        appendUseCase.handler = { _, _ -> throw IllegalStateException("boom") }
        val viewModel = buildViewModel()
        val recordObserver = observeRecord(viewModel)

        runCurrent()
        viewModel.appendPdf(mock(android.net.Uri::class.java))
        advanceUntilIdle()

        assertEquals("Append failed", viewModel.error.value)
        viewModel.clearError()
        assertNull(viewModel.error.value)
        recordObserver.cancel()
    }

    @Test
    fun `clearSuccess resets success state`() = runTest(dispatcher) {
        appendUseCase.handler = { _, _ -> AppendResult(pageCount = 4, fileSize = 90L, appendedPageCount = 1) }
        val viewModel = buildViewModel()
        val recordObserver = observeRecord(viewModel)

        runCurrent()
        viewModel.appendPdf(mock(android.net.Uri::class.java))
        advanceUntilIdle()
        viewModel.clearSuccess()

        assertNull(viewModel.success.value)
        recordObserver.cancel()
    }

    private fun buildViewModel(): AppendViewModel {
        return AppendViewModel(
            repository = repository,
            appendWorkflow = workflow,
            workflowErrorMapper = WorkflowErrorMapper(resourceProvider),
            resourceProvider = resourceProvider,
            dispatcherProvider = TestDispatcherProvider(dispatcher),
            settingsRepository = FakeSettingsRepository(),
            savedStateHandle = SavedStateHandle(mapOf("scanId" to record.id))
        )
    }

    private fun TestScope.observeRecord(viewModel: AppendViewModel): Job =
        backgroundScope.launch { viewModel.record.collectLatest { } }
}

private class ConfigurableAppendToPdfUseCase : AppendToPdfUseCase(
    fileUtil = mock(FileUtil::class.java),
    pdfEditor = mock(PdfEditor::class.java),
    repository = mock(ScanRepository::class.java),
    imagePdfBuilder = mock(ImagePdfBuilder::class.java)
) {
    var handler: suspend (Document, AppendSource) -> AppendResult = { _, _ ->
        AppendResult(pageCount = 4, fileSize = 80L, appendedPageCount = 1)
    }
    val invocations = mutableListOf<Pair<Document, AppendSource>>()

    override suspend fun invoke(target: Document, source: AppendSource): AppendResult {
        invocations += target to source
        return handler(target, source)
    }
}

