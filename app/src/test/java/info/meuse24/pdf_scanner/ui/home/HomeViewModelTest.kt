package info.meuse24.pdf_scanner.ui.home

import info.meuse24.pdf_scanner.R
import info.meuse24.pdf_scanner.data.local.ScanRecord
import info.meuse24.pdf_scanner.data.repository.ScanRepository
import info.meuse24.pdf_scanner.data.repository.SettingsRepository
import info.meuse24.pdf_scanner.domain.usecase.DeleteScansUseCase
import info.meuse24.pdf_scanner.domain.usecase.ExportAsJpgUseCase
import info.meuse24.pdf_scanner.domain.usecase.ExportScanUseCase
import info.meuse24.pdf_scanner.domain.usecase.ExtractTextUseCase
import info.meuse24.pdf_scanner.domain.usecase.ImportFileUseCase
import info.meuse24.pdf_scanner.domain.usecase.ImportScanUseCase
import info.meuse24.pdf_scanner.domain.usecase.OcrNoTextException
import info.meuse24.pdf_scanner.ui.ocr.OCR_LANGUAGE_AUTO
import info.meuse24.pdf_scanner.domain.workflow.MakeSearchableWorkflow
import info.meuse24.pdf_scanner.domain.workflow.MergePdfsWorkflow
import info.meuse24.pdf_scanner.domain.workflow.WorkflowErrorMapper
import info.meuse24.pdf_scanner.testutil.FakeResourceProvider
import info.meuse24.pdf_scanner.testutil.TestDispatcherProvider
import info.meuse24.pdf_scanner.testutil.TestStorageProvider
import info.meuse24.pdf_scanner.util.OcrModelInstallException
import info.meuse24.pdf_scanner.util.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    @get:Rule
    val tmpFolder = TemporaryFolder()

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var repository: ScanRepository
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var importScanUseCase: ImportScanUseCase
    private lateinit var importFileUseCase: ImportFileUseCase
    private lateinit var exportScanUseCase: ExportScanUseCase
    private lateinit var exportAsJpgUseCase: ExportAsJpgUseCase
    private lateinit var deleteScansUseCase: DeleteScansUseCase
    private lateinit var extractTextUseCase: ExtractTextUseCase
    private lateinit var makeSearchableWorkflow: MakeSearchableWorkflow
    private lateinit var mergePdfsWorkflow: MergePdfsWorkflow
    private lateinit var resourceProvider: FakeResourceProvider
    private lateinit var storageProvider: TestStorageProvider

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        repository = mock(ScanRepository::class.java)
        settingsRepository = mock(SettingsRepository::class.java)
        importScanUseCase = mock(ImportScanUseCase::class.java)
        importFileUseCase = mock(ImportFileUseCase::class.java)
        exportScanUseCase = mock(ExportScanUseCase::class.java)
        exportAsJpgUseCase = mock(ExportAsJpgUseCase::class.java)
        deleteScansUseCase = mock(DeleteScansUseCase::class.java)
        extractTextUseCase = mock(ExtractTextUseCase::class.java)
        makeSearchableWorkflow = mock(MakeSearchableWorkflow::class.java)
        mergePdfsWorkflow = mock(MergePdfsWorkflow::class.java)

        `when`(repository.getAllScans()).thenReturn(flowOf(emptyList()))
        `when`(settingsRepository.settings).thenReturn(MutableStateFlow(AppSettings()))

        resourceProvider = FakeResourceProvider(
            strings = mapOf(
                R.string.ocr_no_image         to "No image available",
                R.string.ocr_no_text_auto_hint to "No text: try Hindi manually",
                R.string.ocr_no_text_found    to "No text found",
                R.string.ocr_auto_detection_uncertain to "Automatic detection uncertain",
                R.string.ocr_model_download_failed to "Model download failed",
                R.string.ocr_failed           to "OCR failed",
                R.string.rename_error_exists  to "Filename already exists",
                R.string.rename_error_failed  to "Rename failed",
                R.string.rename_success       to "Renamed to %s"
            )
        )
        storageProvider = TestStorageProvider(tmpFolder.root)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `extractTexts reports missing image before invoking use case`() = runTest(testDispatcher) {
        val viewModel = buildViewModel()
        val missing = ScanRecord(
            id = 1L,
            filename = "missing",
            filepath = File(tmpFolder.root, "missing.pdf").absolutePath,
            timestamp = 0L,
            pageCount = 1,
            fileSize = 0L
        )

        viewModel.extractTexts(listOf(missing), "de")
        advanceUntilIdle()

        assertEquals("No image available", viewModel.error.value)
        assertFalse(viewModel.ocrLoading.value)
        assertNull(viewModel.ocrText.value)
    }

    @Test
    fun `extractTexts shows auto hint when OcrNoTextException in automatic mode`() = runTest(testDispatcher) {
        val pdf = File(tmpFolder.root, "doc.pdf").apply { writeText("pdf") }
        val record = ScanRecord(id = 1L, filename = "doc", filepath = pdf.absolutePath,
            timestamp = 0L, pageCount = 1, fileSize = 0L)

        val viewModel = buildViewModel(extractTextUseCase = fakeExtract { _, _ -> throw OcrNoTextException() })
        viewModel.extractTexts(listOf(record), OCR_LANGUAGE_AUTO)
        advanceUntilIdle()

        assertEquals("No text: try Hindi manually", viewModel.error.value)
        assertNull(viewModel.ocrText.value)
        assertFalse(viewModel.ocrLoading.value)
    }

    @Test
    fun `extractTexts shows low confidence warning when confidence is below 30 percent`() = runTest(testDispatcher) {
        val pdf = File(tmpFolder.root, "low_quality.pdf").apply { writeText("pdf") }
        val record = ScanRecord(id = 1L, filename = "low_quality", filepath = pdf.absolutePath,
            timestamp = 0L, pageCount = 1, fileSize = 0L)

        resourceProvider.strings += R.string.ocr_low_confidence_warning to "Low confidence: %d%%"

        val stats = info.meuse24.pdf_scanner.util.OcrResultStats(0.25f, "en", 0f)
        val viewModel = buildViewModel(extractTextUseCase = fakeExtract { _, _ -> "Extracted with errors" to stats })
        viewModel.extractTexts(listOf(record), "en")
        advanceUntilIdle()

        assertEquals("Extracted with errors", viewModel.ocrText.value)
        assertEquals("Low confidence: 25%", viewModel.error.value)
    }

    @Test
    fun `extractTexts shows no-text-found when OcrNoTextException in manual mode`() = runTest(testDispatcher) {
        val pdf = File(tmpFolder.root, "doc2.pdf").apply { writeText("pdf") }
        val record = ScanRecord(id = 2L, filename = "doc2", filepath = pdf.absolutePath,
            timestamp = 0L, pageCount = 1, fileSize = 0L)

        val viewModel = buildViewModel(extractTextUseCase = fakeExtract { _, _ -> throw OcrNoTextException() })
        viewModel.extractTexts(listOf(record), "hi")
        advanceUntilIdle()

        assertEquals("No text found", viewModel.error.value)
        assertNull(viewModel.ocrText.value)
        assertFalse(viewModel.ocrLoading.value)
    }

    @Test
    fun `extractTexts shows generic error on unexpected exception`() = runTest(testDispatcher) {
        val pdf = File(tmpFolder.root, "doc3.pdf").apply { writeText("pdf") }
        val record = ScanRecord(id = 3L, filename = "doc3", filepath = pdf.absolutePath,
            timestamp = 0L, pageCount = 1, fileSize = 0L)

        val viewModel = buildViewModel(extractTextUseCase = fakeExtract { _, _ -> throw RuntimeException("render crash") })
        viewModel.extractTexts(listOf(record), OCR_LANGUAGE_AUTO)
        advanceUntilIdle()

        assertEquals("OCR failed", viewModel.error.value)
        assertNull(viewModel.ocrText.value)
        assertFalse(viewModel.ocrLoading.value)
    }

    @Test
    fun `extractTexts shows uncertain warning when auto mode has weak language signal`() = runTest(testDispatcher) {
        val pdf = File(tmpFolder.root, "doc4.pdf").apply { writeText("pdf") }
        val record = ScanRecord(id = 4L, filename = "doc4", filepath = pdf.absolutePath,
            timestamp = 0L, pageCount = 1, fileSize = 0L)

        val stats = info.meuse24.pdf_scanner.util.OcrResultStats(0.55f, null, 0f)
        val viewModel = buildViewModel(extractTextUseCase = fakeExtract { _, _ -> "Ambiguous text" to stats })
        viewModel.extractTexts(listOf(record), OCR_LANGUAGE_AUTO)
        advanceUntilIdle()

        assertEquals("Ambiguous text", viewModel.ocrText.value)
        assertEquals("Automatic detection uncertain", viewModel.error.value)
    }

    @Test
    fun `extractTexts reports model download failure explicitly`() = runTest(testDispatcher) {
        val pdf = File(tmpFolder.root, "doc5.pdf").apply { writeText("pdf") }
        val record = ScanRecord(id = 5L, filename = "doc5", filepath = pdf.absolutePath,
            timestamp = 0L, pageCount = 1, fileSize = 0L)

        val viewModel = buildViewModel(
            extractTextUseCase = fakeExtract { _, _ ->
                throw OcrModelInstallException("install failed")
            }
        )
        viewModel.extractTexts(listOf(record), OCR_LANGUAGE_AUTO)
        advanceUntilIdle()

        assertEquals("Model download failed", viewModel.error.value)
        assertNull(viewModel.ocrText.value)
    }

    @Test
    fun `renameScan uses storage provider directory and updates repository`() = runTest(testDispatcher) {
        val viewModel = buildViewModel()
        val scansDir = storageProvider.scansDir()
        val originalFile = File(scansDir, "original.pdf").apply { writeText("pdf") }
        val originalThumb = File(scansDir, "original.jpg").apply { writeText("thumb") }
        val record = ScanRecord(
            id = 7L,
            filename = "original",
            filepath = originalFile.absolutePath,
            timestamp = 10L,
            pageCount = 1,
            fileSize = originalFile.length(),
            thumbnailPath = originalThumb.absolutePath
        )

        viewModel.renameScan(record, "renamed")
        advanceUntilIdle()

        val renamedFile = File(scansDir, "renamed.pdf")
        val renamedThumb = File(scansDir, "renamed.jpg")
        assertTrue(renamedFile.exists())
        assertTrue(renamedThumb.exists())
        assertFalse(originalFile.exists())
        assertFalse(originalThumb.exists())
        assertEquals("Renamed to renamed", viewModel.success.value)
        assertNull(viewModel.error.value)
        verify(repository).updateFilenameAndPath(
            7L,
            "renamed",
            renamedFile.absolutePath,
            renamedThumb.absolutePath
        )
    }

    private fun buildViewModel(
        extractTextUseCase: ExtractTextUseCase = this.extractTextUseCase
    ): HomeViewModel {
        return HomeViewModel(
            repository = repository,
            settingsRepository = settingsRepository,
            importScanUseCase = importScanUseCase,
            importFileUseCase = importFileUseCase,
            exportScanUseCase = exportScanUseCase,
            exportAsJpgUseCase = exportAsJpgUseCase,
            deleteScansUseCase = deleteScansUseCase,
            extractTextUseCase = extractTextUseCase,
            makeSearchableWorkflow = makeSearchableWorkflow,
            mergePdfsWorkflow = mergePdfsWorkflow,
            workflowErrorMapper = WorkflowErrorMapper(resourceProvider),
            resourceProvider = resourceProvider,
            storageProvider = storageProvider,
            dispatcherProvider = TestDispatcherProvider(testDispatcher)
        )
    }

    /** Erstellt eine anonyme ExtractTextUseCase-Subklasse, die den Block als invoke-Body nutzt. */
    private fun fakeExtract(block: suspend (List<ScanRecord>, String) -> Pair<String, info.meuse24.pdf_scanner.util.OcrResultStats?>): ExtractTextUseCase =
        object : ExtractTextUseCase(
            ocrPipeline = mock(info.meuse24.pdf_scanner.util.OcrPipeline::class.java),
            inputImageLoader = mock(info.meuse24.pdf_scanner.util.OcrInputImageLoader::class.java),
            pdfPageInputImageLoader = mock(info.meuse24.pdf_scanner.util.PdfPageInputImageLoader::class.java),
            dispatcherProvider = TestDispatcherProvider(testDispatcher),
            textRecognizerRunner = mock(info.meuse24.pdf_scanner.util.TextRecognizerRunner::class.java)
        ) {
            override suspend fun invoke(
                records: List<ScanRecord>,
                languageCode: String,
                onStatus: (info.meuse24.pdf_scanner.util.OcrPipelineStatus) -> Unit
            ): Pair<String, info.meuse24.pdf_scanner.util.OcrResultStats?> =
                block(records, languageCode)
        }
}
