package info.meuse24.pdf_scanner.ui.viewer

import android.graphics.Bitmap
import androidx.lifecycle.SavedStateHandle
import info.meuse24.pdf_scanner.R
import info.meuse24.pdf_scanner.data.local.ScanRecord
import info.meuse24.pdf_scanner.data.repository.ScanRepository
import info.meuse24.pdf_scanner.domain.usecase.ExportScanUseCase
import info.meuse24.pdf_scanner.testutil.FakeResourceProvider
import info.meuse24.pdf_scanner.testutil.TestDispatcherProvider
import info.meuse24.pdf_scanner.util.PdfDocumentBitmapHandle
import info.meuse24.pdf_scanner.util.PdfPageBitmapRenderException
import info.meuse24.pdf_scanner.util.PdfPageBitmapRenderFailure
import info.meuse24.pdf_scanner.util.PdfPageBitmapRenderer
import info.meuse24.pdf_scanner.util.RenderedPdfPage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

    private fun buildViewModel(
        records: List<ScanRecord>,
        renderer: PdfPageBitmapRenderer,
        scanId: Long = 1L
    ): PdfViewerViewModel {
        val repository = mock(ScanRepository::class.java)
        `when`(repository.getAllScans()).thenReturn(flowOf(records))

        return PdfViewerViewModel(
            repository = repository,
            pageBitmapRenderer = renderer,
            exportScanUseCase = mock(ExportScanUseCase::class.java),
            resourceProvider = resourceProvider,
            dispatcherProvider = TestDispatcherProvider(testDispatcher),
            savedStateHandle = SavedStateHandle(mapOf("scanId" to scanId))
        )
    }

    private fun scanRecord(
        filepath: String = File(tmpFolder.root, "scan.pdf").absolutePath,
        pageCount: Int = 1,
        isEncrypted: Boolean = false
    ): ScanRecord = ScanRecord(
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

private class FakePdfPageBitmapRenderer(
    private val handle: PdfDocumentBitmapHandle
) : PdfPageBitmapRenderer {

    val openedFiles = mutableListOf<File>()

    override suspend fun openDocument(file: File): PdfDocumentBitmapHandle {
        openedFiles += file
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
