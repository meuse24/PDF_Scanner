package info.meuse24.pdf_scanner.ui.viewer

import android.graphics.Bitmap
import androidx.lifecycle.SavedStateHandle
import info.meuse24.pdf_scanner.R
import info.meuse24.pdf_scanner.domain.model.Document
import info.meuse24.pdf_scanner.domain.model.PdfMetadata
import info.meuse24.pdf_scanner.domain.model.PdfPageSizeCategory
import info.meuse24.pdf_scanner.domain.pdf.PdfMetadataOps
import info.meuse24.pdf_scanner.data.repository.ScanRepository
import info.meuse24.pdf_scanner.domain.usecase.CheckPrintPageSizeWarningUseCase
import info.meuse24.pdf_scanner.domain.usecase.ExportScanUseCase
import info.meuse24.pdf_scanner.testutil.FakeResourceProvider
import info.meuse24.pdf_scanner.testutil.TestDispatcherProvider
import info.meuse24.pdf_scanner.util.PdfDocumentBitmapHandle
import info.meuse24.pdf_scanner.util.PdfPageBitmapRenderException
import info.meuse24.pdf_scanner.util.PdfPageBitmapRenderFailure
import info.meuse24.pdf_scanner.util.PdfPageBitmapRenderer
import info.meuse24.pdf_scanner.util.RenderedPdfPage
import kotlinx.coroutines.async
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.io.File
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class PdfViewerViewModelTest {

    @get:Rule
    val tmpFolder = TemporaryFolder()

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var resourceProvider: FakeResourceProvider

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        resourceProvider = FakeResourceProvider(
            strings = mapOf(
                R.string.pdf_viewer_file_missing to "File missing",
                R.string.pdf_viewer_file_encrypted to "Encrypted PDF",
                R.string.pdf_viewer_file_corrupted to "Corrupted PDF",
                R.string.pdf_viewer_invalid_page to "Invalid page",
                R.string.pdf_viewer_out_of_memory to "Out of memory",
                R.string.pdf_viewer_renderer_failed to "Renderer failed",
                R.string.export_success to "Exported %s",
                R.string.error_export_failed to "Export failed"
            )
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `opens document and renders visible page with neighbors`() = runTest(testDispatcher) {
        val pdf = tmpFolder.newFile("scan.pdf").apply { writeText("pdf") }
        val handle = FakePdfDocumentBitmapHandle(pageCount = 3)
        val renderer = FakePdfPageBitmapRenderer(handle)
        val viewModel = buildViewModel(
            records = listOf(scanRecord(filepath = pdf.absolutePath, pageCount = 3)),
            renderer = renderer
        )

        advanceUntilIdle()
        viewModel.onVisiblePagesChanged(pageIndexes = listOf(1), targetWidthPx = 320)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(1, renderer.openedFiles.size)
        assertEquals(3, state.pageCount)
        assertEquals(1, state.currentPageIndex)
        assertEquals(setOf(0, 1, 2), state.pages.keys)
        assertTrue(state.pages.values.all { it.bitmap != null && !it.loading })
        assertEquals(listOf(0, 1, 2), handle.requests.map { it.pageIndex }.sorted())
    }

    @Test
    fun `encrypted scan reports viewer error without opening renderer`() = runTest(testDispatcher) {
        val pdf = tmpFolder.newFile("encrypted.pdf").apply { writeText("pdf") }
        val renderer = FakePdfPageBitmapRenderer(FakePdfDocumentBitmapHandle(pageCount = 1))
        val viewModel = buildViewModel(
            records = listOf(scanRecord(filepath = pdf.absolutePath, isEncrypted = true)),
            renderer = renderer
        )

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("Encrypted PDF", state.errorMessage)
        assertEquals(0, state.pageCount)
        assertTrue(renderer.openedFiles.isEmpty())
    }

    @Test
    fun `render failure is exposed on the affected page`() = runTest(testDispatcher) {
        val pdf = tmpFolder.newFile("broken-page.pdf").apply { writeText("pdf") }
        val handle = FakePdfDocumentBitmapHandle(
            pageCount = 2,
            failingPages = mapOf(0 to PdfPageBitmapRenderFailure.InvalidPage)
        )
        val viewModel = buildViewModel(
            records = listOf(scanRecord(filepath = pdf.absolutePath, pageCount = 2)),
            renderer = FakePdfPageBitmapRenderer(handle)
        )

        advanceUntilIdle()
        viewModel.onVisiblePagesChanged(pageIndexes = listOf(0), targetWidthPx = 320)
        advanceUntilIdle()

        val pageState = viewModel.uiState.value.pages[0]
        assertNotNull(pageState)
        assertEquals("Invalid page", pageState?.errorMessage)
    }

    @Test
    fun `missing repository record reports missing file error`() = runTest(testDispatcher) {
        val viewModel = buildViewModel(
            records = emptyList(),
            renderer = FakePdfPageBitmapRenderer(FakePdfDocumentBitmapHandle(pageCount = 1))
        )

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("File missing", state.errorMessage)
        assertEquals(0, state.pageCount)
    }

    @Test
    fun `open cancellation is not mapped to renderer error`() = runTest(testDispatcher) {
        val pdf = tmpFolder.newFile("cancelled.pdf").apply { writeText("pdf") }
        val viewModel = buildViewModel(
            records = listOf(scanRecord(filepath = pdf.absolutePath)),
            renderer = CancellingPdfPageBitmapRenderer()
        )

        advanceUntilIdle()

        assertEquals(null, viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `record file change closes old handle and reopens document`() = runTest(testDispatcher) {
        val firstPdf = tmpFolder.newFile("first.pdf").apply { writeText("first") }
        val secondPdf = tmpFolder.newFile("second.pdf").apply { writeText("second") }
        val records = MutableStateFlow(listOf(scanRecord(filepath = firstPdf.absolutePath, pageCount = 2)))
        val firstHandle = FakePdfDocumentBitmapHandle(pageCount = 2)
        val secondHandle = FakePdfDocumentBitmapHandle(pageCount = 3)
        val renderer = FakePdfPageBitmapRenderer(firstHandle, secondHandle)
        val viewModel = buildViewModel(recordsFlow = records, renderer = renderer)

        advanceUntilIdle()
        records.value = listOf(scanRecord(filepath = secondPdf.absolutePath, pageCount = 3))
        advanceUntilIdle()

        assertTrue(firstHandle.closed)
        assertEquals(2, renderer.openedFiles.size)
        assertEquals(3, viewModel.uiState.value.pageCount)
    }

    @Test
    fun `handle returned after viewer is cleared is closed`() = runTest(testDispatcher) {
        val pdf = tmpFolder.newFile("cleared.pdf").apply { writeText("pdf") }
        val handle = FakePdfDocumentBitmapHandle(pageCount = 2)
        lateinit var viewModel: PdfViewerViewModel
        val renderer = CallbackPdfPageBitmapRenderer(handle) {
            viewModel.invokeOnClearedForTest()
        }
        viewModel = buildViewModel(
            records = listOf(scanRecord(filepath = pdf.absolutePath, pageCount = 2)),
            renderer = renderer
        )

        advanceUntilIdle()

        assertTrue(handle.closed)
    }

    @Test
    fun `visible page and zoom scale are saved in SavedStateHandle`() = runTest(testDispatcher) {
        val pdf = tmpFolder.newFile("saved-state.pdf").apply { writeText("pdf") }
        val savedStateHandle = SavedStateHandle(mapOf("scanId" to 1L))
        val viewModel = buildViewModel(
            records = listOf(scanRecord(filepath = pdf.absolutePath, pageCount = 3)),
            renderer = FakePdfPageBitmapRenderer(FakePdfDocumentBitmapHandle(pageCount = 3)),
            savedStateHandle = savedStateHandle
        )

        advanceUntilIdle()
        viewModel.onVisiblePagesChanged(pageIndexes = listOf(2), targetWidthPx = 320)
        viewModel.setZoomScale(3.25f)

        assertEquals(2, savedStateHandle["pdf_viewer_current_page"])
        assertEquals(3.25f, savedStateHandle["pdf_viewer_zoom_scale"])
    }

    @Test
    fun `scrolling clears bitmaps outside retained page window`() = runTest(testDispatcher) {
        val pdf = tmpFolder.newFile("scroll.pdf").apply { writeText("pdf") }
        val viewModel = buildViewModel(
            records = listOf(scanRecord(filepath = pdf.absolutePath, pageCount = 5)),
            renderer = FakePdfPageBitmapRenderer(FakePdfDocumentBitmapHandle(pageCount = 5))
        )

        advanceUntilIdle()
        viewModel.onVisiblePagesChanged(pageIndexes = listOf(1), targetWidthPx = 320)
        advanceUntilIdle()
        viewModel.onVisiblePagesChanged(pageIndexes = listOf(4), targetWidthPx = 320)
        advanceUntilIdle()

        val pages = viewModel.uiState.value.pages
        assertEquals(null, pages[0]?.bitmap)
        assertEquals(null, pages[1]?.bitmap)
        assertNotNull(pages[4]?.bitmap)
    }

    @Test
    fun `requestPrint emits print request for standard page sizes`() = runTest(testDispatcher) {
        val record = scanRecord()
        val viewModel = buildViewModel(
            recordsFlow = flowOf(listOf(record)),
            renderer = FakePdfPageBitmapRenderer(FakePdfDocumentBitmapHandle(pageCount = 1)),
            pdfMetadataOps = testPdfMetadataOps { PdfPageSizeCategory.UNIFORM_STANDARD }
        )
        val printEvent = async { viewModel.printRequests.first() }
        advanceUntilIdle()

        viewModel.requestPrint(record)
        advanceUntilIdle()

        assertEquals(record, printEvent.await())
        assertEquals(null, viewModel.pendingPrintDocument.value)
    }

    @Test
    fun `requestPrint stores pending warning for mixed page sizes`() = runTest(testDispatcher) {
        val record = scanRecord()
        val viewModel = buildViewModel(
            recordsFlow = flowOf(listOf(record)),
            renderer = FakePdfPageBitmapRenderer(FakePdfDocumentBitmapHandle(pageCount = 1)),
            pdfMetadataOps = testPdfMetadataOps { PdfPageSizeCategory.MIXED }
        )
        advanceUntilIdle()

        viewModel.requestPrint(record)
        advanceUntilIdle()

        assertEquals(record, viewModel.pendingPrintDocument.value)
    }

    @Test
    fun `requestPrint emits print request when page size classification fails`() = runTest(testDispatcher) {
        val record = scanRecord()
        val viewModel = buildViewModel(
            recordsFlow = flowOf(listOf(record)),
            renderer = FakePdfPageBitmapRenderer(FakePdfDocumentBitmapHandle(pageCount = 1)),
            pdfMetadataOps = testPdfMetadataOps { throw IOException("missing") }
        )
        val printEvent = async { viewModel.printRequests.first() }
        advanceUntilIdle()

        viewModel.requestPrint(record)
        advanceUntilIdle()

        assertEquals(record, printEvent.await())
        assertEquals(null, viewModel.pendingPrintDocument.value)
    }

    private fun buildViewModel(
        records: List<Document>,
        renderer: PdfPageBitmapRenderer,
        scanId: Long = 1L,
        savedStateHandle: SavedStateHandle = SavedStateHandle(mapOf("scanId" to scanId)),
        pdfMetadataOps: PdfMetadataOps = testPdfMetadataOps { PdfPageSizeCategory.UNIFORM_STANDARD }
    ): PdfViewerViewModel = buildViewModel(
        recordsFlow = flowOf(records),
        renderer = renderer,
        savedStateHandle = savedStateHandle,
        pdfMetadataOps = pdfMetadataOps
    )

    private fun buildViewModel(
        recordsFlow: Flow<List<Document>>,
        renderer: PdfPageBitmapRenderer,
        scanId: Long = 1L,
        savedStateHandle: SavedStateHandle = SavedStateHandle(mapOf("scanId" to scanId)),
        pdfMetadataOps: PdfMetadataOps = testPdfMetadataOps { PdfPageSizeCategory.UNIFORM_STANDARD }
    ): PdfViewerViewModel {
        val repository = mock(ScanRepository::class.java)
        `when`(repository.getAllScans()).thenReturn(recordsFlow)

        return PdfViewerViewModel(
            repository = repository,
            pageBitmapRenderer = renderer,
            exportScanUseCase = mock(ExportScanUseCase::class.java),
            checkPrintPageSizeWarningUseCase = CheckPrintPageSizeWarningUseCase(
                pdfMetadataOps = pdfMetadataOps,
                dispatcherProvider = TestDispatcherProvider(testDispatcher)
            ),
            resourceProvider = resourceProvider,
            dispatcherProvider = TestDispatcherProvider(testDispatcher),
            savedStateHandle = savedStateHandle
        )
    }

    private fun testPdfMetadataOps(classifier: () -> PdfPageSizeCategory): PdfMetadataOps =
        object : PdfMetadataOps {
            override fun addPageNumbers(input: File, outputDir: File): File = input
            override fun applyTextWatermark(input: File, outputDir: File, text: String): File = input
            override fun readMetadata(input: File): PdfMetadata =
                PdfMetadata(null, null, null, null, null, null, null)
            override fun classifyPageSizes(pdfFile: File): PdfPageSizeCategory = classifier()
            override fun updateMetadata(input: File, metadata: PdfMetadata): File = input
            override fun applySignatureStamp(
                input: File,
                outputDir: File,
                signatureBitmap: Any,
                pageIndex: Int,
                scaleFraction: Float
            ): File = input
        }

    private fun scanRecord(
        filepath: String = File(tmpFolder.root, "scan.pdf").absolutePath,
        pageCount: Int = 1,
        isEncrypted: Boolean = false
    ): Document = Document(
        id = 1L,
        filename = "scan",
        filepath = filepath,
        timestamp = 0L,
        pageCount = pageCount,
        fileSize = File(filepath).length(),
        isEncrypted = isEncrypted
    )
}

private data class RenderRequest(
    val pageIndex: Int,
    val targetWidthPx: Int,
    val maxBitmapSidePx: Int
)

private class FakePdfPageBitmapRenderer : PdfPageBitmapRenderer {

    val openedFiles = mutableListOf<File>()
    private val handles: ArrayDeque<PdfDocumentBitmapHandle>

    constructor(vararg handles: PdfDocumentBitmapHandle) {
        this.handles = ArrayDeque(handles.toList())
    }

    override suspend fun openDocument(file: File): PdfDocumentBitmapHandle {
        openedFiles += file
        return handles.removeFirst()
    }
}

private class CancellingPdfPageBitmapRenderer : PdfPageBitmapRenderer {
    override suspend fun openDocument(file: File): PdfDocumentBitmapHandle {
        throw CancellationException("cancelled")
    }
}

private class CallbackPdfPageBitmapRenderer(
    private val handle: PdfDocumentBitmapHandle,
    private val beforeReturn: () -> Unit
) : PdfPageBitmapRenderer {
    override suspend fun openDocument(file: File): PdfDocumentBitmapHandle {
        beforeReturn()
        return handle
    }
}

private class FakePdfDocumentBitmapHandle(
    override val pageCount: Int,
    private val failingPages: Map<Int, PdfPageBitmapRenderFailure> = emptyMap()
) : PdfDocumentBitmapHandle {

    val requests = mutableListOf<RenderRequest>()
    var closed = false
        private set

    override suspend fun renderPage(
        pageIndex: Int,
        targetWidthPx: Int,
        maxBitmapSidePx: Int
    ): RenderedPdfPage {
        failingPages[pageIndex]?.let { failure ->
            throw PdfPageBitmapRenderException(failure)
        }
        requests += RenderRequest(pageIndex, targetWidthPx, maxBitmapSidePx)
        return RenderedPdfPage(
            pageIndex = pageIndex,
            widthPt = 600,
            heightPt = 800,
            renderedWidthPx = targetWidthPx,
            bitmap = mock(Bitmap::class.java)
        )
    }

    override fun close() {
        closed = true
    }
}

private fun PdfViewerViewModel.invokeOnClearedForTest() {
    val method = this::class.java.getDeclaredMethod("onCleared")
    method.isAccessible = true
    method.invoke(this)
}

