package info.meuse24.pdf_scanner.ui.viewer

import android.graphics.Bitmap
import androidx.lifecycle.SavedStateHandle
import info.meuse24.pdf_scanner.R
import info.meuse24.pdf_scanner.domain.model.Document
import info.meuse24.pdf_scanner.domain.model.AcroFormCapability
import info.meuse24.pdf_scanner.domain.model.PdfMetadata
import info.meuse24.pdf_scanner.domain.model.PdfPageSizeCategory
import info.meuse24.pdf_scanner.domain.pdf.PdfMetadataOps
import info.meuse24.pdf_scanner.domain.pdf.PdfTextOps
import info.meuse24.pdf_scanner.domain.pdf.PdfPageTextContent
import info.meuse24.pdf_scanner.domain.pdf.NormalizedBox
import info.meuse24.pdf_scanner.data.repository.ScanRepository
import info.meuse24.pdf_scanner.domain.usecase.CheckPrintPageSizeWarningUseCase
import info.meuse24.pdf_scanner.domain.usecase.ExportAsJpgUseCase
import info.meuse24.pdf_scanner.domain.usecase.ExportDocxUseCase
import info.meuse24.pdf_scanner.domain.usecase.ExportOcrTextUseCase
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
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
                R.string.pdf_viewer_copy_iban_success to "IBAN copied",
                R.string.pdf_viewer_copy_amount_success to "Amount copied",
                R.string.pdf_viewer_no_calendar_app to "No calendar app",
                R.string.pdf_viewer_search_needs_ocr to "Run OCR first",
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
    fun `fillable AcroForm capability is exposed to viewer`() = runTest(testDispatcher) {
        val pdf = tmpFolder.newFile("form.pdf").apply { writeText("pdf") }
        val viewModel = buildViewModel(
            records = listOf(scanRecord(filepath = pdf.absolutePath)),
            renderer = FakePdfPageBitmapRenderer(FakePdfDocumentBitmapHandle(pageCount = 1)),
            formCapability = AcroFormCapability.FILLABLE
        )

        advanceUntilIdle()

        assertEquals(AcroFormCapability.FILLABLE, viewModel.uiState.value.formCapability)
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
    fun `visible zoom render rerenders retained pages at stepped zoom width`() = runTest(testDispatcher) {
        val pdf = tmpFolder.newFile("zoom.pdf").apply { writeText("pdf") }
        val savedStateHandle = SavedStateHandle(mapOf("scanId" to 1L))
        val handle = FakePdfDocumentBitmapHandle(pageCount = 4)
        val viewModel = buildViewModel(
            records = listOf(scanRecord(filepath = pdf.absolutePath, pageCount = 4)),
            renderer = FakePdfPageBitmapRenderer(handle),
            savedStateHandle = savedStateHandle
        )

        advanceUntilIdle()
        viewModel.onVisiblePagesChanged(pageIndexes = listOf(2), targetWidthPx = 320)
        advanceUntilIdle()
        handle.requests.clear()

        viewModel.requestVisibleZoomRender(viewportWidthPx = 320, zoomScale = 2.2f)
        advanceUntilIdle()

        assertEquals(2.2f, savedStateHandle["pdf_viewer_zoom_scale"])
        assertEquals(listOf(1, 2, 3), handle.requests.map { it.pageIndex }.sorted())
        assertTrue(handle.requests.all { it.targetWidthPx == 960 })
        assertTrue(handle.requests.all { it.maxBitmapSidePx == 2_048 })
    }

    @Test
    fun `visible zoom render debounces rapid scale changes to latest request`() = runTest(testDispatcher) {
        val pdf = tmpFolder.newFile("zoom-debounce.pdf").apply { writeText("pdf") }
        val handle = FakePdfDocumentBitmapHandle(pageCount = 3)
        val viewModel = buildViewModel(
            records = listOf(scanRecord(filepath = pdf.absolutePath, pageCount = 3)),
            renderer = FakePdfPageBitmapRenderer(handle)
        )

        advanceUntilIdle()
        viewModel.onVisiblePagesChanged(pageIndexes = listOf(1), targetWidthPx = 320)
        advanceUntilIdle()
        handle.requests.clear()

        viewModel.requestVisibleZoomRender(viewportWidthPx = 320, zoomScale = 3f)
        viewModel.requestVisibleZoomRender(viewportWidthPx = 320, zoomScale = 1.2f)
        advanceUntilIdle()

        assertEquals(listOf(0, 1, 2), handle.requests.map { it.pageIndex }.sorted())
        assertTrue(handle.requests.all { it.targetWidthPx == 480 })
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
    fun `search finds pages and emits wrapped navigation requests`() = runTest(testDispatcher) {
        val pdf = tmpFolder.newFile("search.pdf").apply { writeText("pdf") }
        val viewModel = buildViewModel(
            records = listOf(
                scanRecord(
                    filepath = pdf.absolutePath,
                    pageCount = 3,
                    pageTexts = listOf("Alpha", "Beta invoice", "BETA paid")
                )
            ),
            renderer = FakePdfPageBitmapRenderer(FakePdfDocumentBitmapHandle(pageCount = 3))
        )
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.pageSearchAvailable)

        viewModel.openSearch()
        val firstRequest = async(start = CoroutineStart.UNDISPATCHED) {
            viewModel.scrollToPageRequests.first()
        }
        viewModel.updateSearchQuery("beta")
        advanceUntilIdle()

        assertEquals(listOf(1, 2), viewModel.uiState.value.searchMatches.map { it.pageIndex })
        assertEquals(0, viewModel.uiState.value.searchCurrentIndex)
        assertEquals(1, firstRequest.await())

        val nextRequest = async(start = CoroutineStart.UNDISPATCHED) {
            viewModel.scrollToPageRequests.first()
        }
        viewModel.goToNextMatch()
        assertEquals(2, nextRequest.await())
        assertEquals(1, viewModel.uiState.value.searchCurrentIndex)

        val wrappedRequest = async(start = CoroutineStart.UNDISPATCHED) {
            viewModel.scrollToPageRequests.first()
        }
        viewModel.goToNextMatch()
        assertEquals(1, wrappedRequest.await())
        assertEquals(0, viewModel.uiState.value.searchCurrentIndex)

        viewModel.closeSearch()
        assertFalse(viewModel.uiState.value.searchActive)
        assertEquals("", viewModel.uiState.value.searchQuery)
        assertEquals(emptyList<info.meuse24.pdf_scanner.domain.common.PdfSearchMatch>(), viewModel.uiState.value.searchMatches)
    }

    @Test
    fun `search stays unavailable for misaligned legacy page text`() = runTest(testDispatcher) {
        val pdf = tmpFolder.newFile("legacy-search.pdf").apply { writeText("pdf") }
        val viewModel = buildViewModel(
            records = listOf(
                scanRecord(
                    filepath = pdf.absolutePath,
                    pageCount = 3,
                    pageTexts = listOf("First", "Third")
                )
            ),
            renderer = FakePdfPageBitmapRenderer(FakePdfDocumentBitmapHandle(pageCount = 3))
        )

        advanceUntilIdle()
        viewModel.openSearch()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.pageSearchAvailable)
        assertFalse(viewModel.uiState.value.searchActive)
    }

    @Test
    fun `native PDF text takes precedence and preserves individual matches`() = runTest(testDispatcher) {
        val pdf = tmpFolder.newFile("native-search.pdf").apply { writeText("pdf") }
        val viewModel = buildViewModel(
            records = listOf(
                scanRecord(
                    filepath = pdf.absolutePath,
                    pageCount = 2,
                    pageTexts = listOf("OCR fallback", "OCR fallback")
                )
            ),
            renderer = FakePdfPageBitmapRenderer(FakePdfDocumentBitmapHandle(pageCount = 2)),
            pdfTextOps = FakePdfTextOps(
                listOf(
                    PdfPageTextContent(0, "Native needle and needle"),
                    PdfPageTextContent(1, "")
                )
            )
        )

        advanceUntilIdle()
        viewModel.openSearch()
        viewModel.updateSearchQuery("needle")
        advanceUntilIdle()

        assertEquals(listOf(0, 0), viewModel.uiState.value.searchMatches.map { it.pageIndex })
        assertTrue(viewModel.uiState.value.pageSearchAvailable)
    }

    @Test
    fun `database updates without content changes keep native text search available`() = runTest(testDispatcher) {
        val pdf = tmpFolder.newFile("native-update.pdf").apply { writeText("pdf") }
        val records = MutableStateFlow(listOf(scanRecord(filepath = pdf.absolutePath, pageCount = 1)))
        val viewModel = buildViewModel(
            recordsFlow = records,
            renderer = FakePdfPageBitmapRenderer(FakePdfDocumentBitmapHandle(pageCount = 1)),
            pdfTextOps = FakePdfTextOps(listOf(PdfPageTextContent(0, "Native text")))
        )

        advanceUntilIdle()
        viewModel.openSearch()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.pageSearchAvailable)

        records.value = listOf(scanRecord(filepath = pdf.absolutePath, pageCount = 1, isFavorite = true))
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.pageSearchAvailable)
        viewModel.openSearch()
        assertTrue(viewModel.uiState.value.searchActive)
    }

    @Test
    fun `cancelled extraction cannot clear replacement extraction state`() = runTest(testDispatcher) {
        val pdf = tmpFolder.newFile("replacement-extraction.pdf").apply { writeText("pdf") }
        val records = MutableStateFlow(listOf(scanRecord(filepath = pdf.absolutePath, pageCount = 1)))
        val viewModel = buildViewModel(
            recordsFlow = records,
            renderer = FakePdfPageBitmapRenderer(FakePdfDocumentBitmapHandle(pageCount = 1)),
            pdfTextOps = HangingPdfTextOps()
        )

        advanceUntilIdle()
        viewModel.openSearch()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.searchExtractionRunning)

        records.value = listOf(
            scanRecord(filepath = pdf.absolutePath, pageCount = 1, pageTexts = listOf("OCR fallback"))
        )
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.searchExtractionRunning)
        assertTrue(viewModel.uiState.value.pageSearchAvailable)
    }

    @Test
    fun `moving between matches on the same page emits no scroll request`() = runTest(testDispatcher) {
        val pdf = tmpFolder.newFile("same-page.pdf").apply { writeText("pdf") }
        val viewModel = buildViewModel(
            records = listOf(scanRecord(filepath = pdf.absolutePath, pageTexts = listOf("needle needle"))),
            renderer = FakePdfPageBitmapRenderer(FakePdfDocumentBitmapHandle(pageCount = 1))
        )

        advanceUntilIdle()
        viewModel.openSearch()
        viewModel.updateSearchQuery("needle")
        advanceUntilIdle()
        val request = async(start = CoroutineStart.UNDISPATCHED) {
            withTimeoutOrNull(1) { viewModel.scrollToPageRequests.first() }
        }

        viewModel.goToNextMatch()
        advanceUntilIdle()

        assertEquals(null, request.await())
        assertEquals(1, viewModel.uiState.value.searchCurrentIndex)
    }

    @Test
    fun `loading record detects smart document entities`() = runTest(testDispatcher) {
        val pdf = tmpFolder.newFile("entities.pdf").apply { writeText("pdf") }
        val viewModel = buildViewModel(
            records = listOf(
                scanRecord(
                    filepath = pdf.absolutePath,
                    extractedText = "Zahlbar bis 15.07.2026, Betrag 42,50 EUR, IBAN DE89 3704 0044 0532 0130 00"
                )
            ),
            renderer = FakePdfPageBitmapRenderer(FakePdfDocumentBitmapHandle(pageCount = 1))
        )

        advanceUntilIdle()

        val entities = viewModel.uiState.value.detectedEntities
        assertEquals(listOf("42,50 EUR"), entities.amounts)
        assertEquals(listOf("DE89 3704 0044 0532 0130 00"), entities.ibans)
        assertEquals(1, entities.dates.size)
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
        pdfMetadataOps: PdfMetadataOps = testPdfMetadataOps { PdfPageSizeCategory.UNIFORM_STANDARD },
        formCapability: AcroFormCapability = AcroFormCapability.NONE,
        pdfTextOps: PdfTextOps = FakePdfTextOps()
    ): PdfViewerViewModel = buildViewModel(
        recordsFlow = flowOf(records),
        renderer = renderer,
        savedStateHandle = savedStateHandle,
        pdfMetadataOps = pdfMetadataOps,
        formCapability = formCapability,
        pdfTextOps = pdfTextOps
    )

    private fun buildViewModel(
        recordsFlow: Flow<List<Document>>,
        renderer: PdfPageBitmapRenderer,
        scanId: Long = 1L,
        savedStateHandle: SavedStateHandle = SavedStateHandle(mapOf("scanId" to scanId)),
        pdfMetadataOps: PdfMetadataOps = testPdfMetadataOps { PdfPageSizeCategory.UNIFORM_STANDARD },
        formCapability: AcroFormCapability = AcroFormCapability.NONE,
        pdfTextOps: PdfTextOps = FakePdfTextOps()
    ): PdfViewerViewModel {
        val repository = mock(ScanRepository::class.java)
        `when`(repository.getAllScans()).thenReturn(recordsFlow)

        return PdfViewerViewModel(
            repository = repository,
            pageBitmapRenderer = renderer,
            exportScanUseCase = mock(ExportScanUseCase::class.java),
            exportAsJpgUseCase = mock(ExportAsJpgUseCase::class.java),
            exportDocxUseCase = mock(ExportDocxUseCase::class.java),
            exportOcrTextUseCase = mock(ExportOcrTextUseCase::class.java),
            checkPrintPageSizeWarningUseCase = CheckPrintPageSizeWarningUseCase(
                pdfMetadataOps = pdfMetadataOps,
                dispatcherProvider = TestDispatcherProvider(testDispatcher)
            ),
            resourceProvider = resourceProvider,
            dispatcherProvider = TestDispatcherProvider(testDispatcher),
            pdfFormOps = object : info.meuse24.pdf_scanner.domain.pdf.PdfFormOps {
                override fun detectFormCapability(file: File) =
                    formCapability

                override fun readFormFields(file: File) =
                    emptyList<info.meuse24.pdf_scanner.domain.model.FormField>()

                override fun fillFormFields(
                    input: File,
                    outputDir: File,
                    values: Map<String, info.meuse24.pdf_scanner.domain.model.FormFieldValue>,
                    flatten: Boolean
                ): File = input
            },
            pdfTextOps = pdfTextOps,
            savedStateHandle = savedStateHandle
        )
    }

    private fun testPdfMetadataOps(classifier: () -> PdfPageSizeCategory): PdfMetadataOps =
        object : PdfMetadataOps {
            override fun addPageNumbers(
                input: File,
                outputDir: File,
                settings: info.meuse24.pdf_scanner.domain.model.PageNumberSettings
            ): File = input
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
        isEncrypted: Boolean = false,
        extractedText: String? = null,
        pageTexts: List<String> = emptyList(),
        isFavorite: Boolean = false
    ): Document = Document(
        id = 1L,
        filename = "scan",
        filepath = filepath,
        timestamp = 0L,
        pageCount = pageCount,
        fileSize = File(filepath).length(),
        isEncrypted = isEncrypted,
        extractedText = extractedText,
        pageTexts = pageTexts,
        isFavorite = isFavorite,
        hasStoredOcrText = !extractedText.isNullOrBlank() || pageTexts.any { it.isNotBlank() }
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

private class FakePdfTextOps(
    private val pages: List<PdfPageTextContent> = emptyList()
) : PdfTextOps {
    override fun removeTextLayer(input: File, outputDir: File): File = input
    override fun extractTextLines(file: File, pageIndex: Int) = emptyList<info.meuse24.pdf_scanner.domain.usecase.TextLine>()
    override fun extractSearchText(file: File): Flow<PdfPageTextContent> = flowOf(*pages.toTypedArray())
    override fun extractPageGlyphBoxes(file: File, pageIndex: Int): List<NormalizedBox?> = emptyList()
}

private class HangingPdfTextOps : PdfTextOps {
    override fun removeTextLayer(input: File, outputDir: File): File = input
    override fun extractTextLines(file: File, pageIndex: Int) = emptyList<info.meuse24.pdf_scanner.domain.usecase.TextLine>()
    override fun extractSearchText(file: File): Flow<PdfPageTextContent> = flow { awaitCancellation() }
    override fun extractPageGlyphBoxes(file: File, pageIndex: Int): List<NormalizedBox?> = emptyList()
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
