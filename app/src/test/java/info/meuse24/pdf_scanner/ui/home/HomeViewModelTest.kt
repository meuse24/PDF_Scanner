package info.meuse24.pdf_scanner.ui.home

import info.meuse24.pdf_scanner.R
import info.meuse24.pdf_scanner.data.local.ScanRecord
import info.meuse24.pdf_scanner.data.mapper.toEntity
import info.meuse24.pdf_scanner.domain.model.Document
import info.meuse24.pdf_scanner.domain.model.Folder
import info.meuse24.pdf_scanner.domain.model.PdfMetadata
import info.meuse24.pdf_scanner.domain.model.PdfPageSizeCategory
import info.meuse24.pdf_scanner.domain.pdf.PdfMetadataOps
import info.meuse24.pdf_scanner.data.local.TrashDao
import info.meuse24.pdf_scanner.data.repository.ScanRepository
import info.meuse24.pdf_scanner.data.repository.TrashRepository
import info.meuse24.pdf_scanner.data.repository.SettingsRepository
import info.meuse24.pdf_scanner.domain.repository.FolderRepository
import info.meuse24.pdf_scanner.domain.gateway.ReviewPromptPolicy
import info.meuse24.pdf_scanner.domain.usecase.AutoTagUseCase
import info.meuse24.pdf_scanner.domain.usecase.BuildScanSearchQueryUseCase
import info.meuse24.pdf_scanner.domain.usecase.CheckPrintPageSizeWarningUseCase
import info.meuse24.pdf_scanner.domain.usecase.ExportAsJpgUseCase
import info.meuse24.pdf_scanner.domain.usecase.ExportDocxUseCase
import info.meuse24.pdf_scanner.domain.usecase.ExportOcrTextUseCase
import info.meuse24.pdf_scanner.domain.usecase.ExportScanUseCase
import info.meuse24.pdf_scanner.domain.usecase.ExtractTextUseCase
import info.meuse24.pdf_scanner.domain.usecase.FindOcrExtractableDocumentsUseCase
import info.meuse24.pdf_scanner.domain.usecase.ImportFileUseCase
import info.meuse24.pdf_scanner.domain.usecase.ImportScanUseCase
import info.meuse24.pdf_scanner.domain.usecase.JpgExportResult
import info.meuse24.pdf_scanner.domain.usecase.MoveDocumentsUseCase
import info.meuse24.pdf_scanner.domain.usecase.OcrBackfillUseCase
import info.meuse24.pdf_scanner.domain.usecase.OcrDocumentResult
import info.meuse24.pdf_scanner.domain.usecase.OcrNoTextException
import info.meuse24.pdf_scanner.domain.usecase.RecordReviewPromptActionUseCase
import info.meuse24.pdf_scanner.domain.usecase.RenameDocumentUseCase
import info.meuse24.pdf_scanner.domain.usecase.RestoreScansUseCase
import info.meuse24.pdf_scanner.domain.usecase.ToggleFavoriteUseCase
import info.meuse24.pdf_scanner.domain.usecase.TrashScansUseCase
import info.meuse24.pdf_scanner.ui.ocr.OCR_LANGUAGE_AUTO
import info.meuse24.pdf_scanner.domain.workflow.MakeSearchableWorkflow
import info.meuse24.pdf_scanner.domain.workflow.MergePdfsWorkflow
import info.meuse24.pdf_scanner.domain.workflow.WorkflowErrorMapper
import info.meuse24.pdf_scanner.testutil.FakeResourceProvider
import info.meuse24.pdf_scanner.testutil.TestDispatcherProvider
import info.meuse24.pdf_scanner.testutil.TestStorageProvider
import info.meuse24.pdf_scanner.domain.model.OcrModelInstallException
import info.meuse24.pdf_scanner.domain.model.AppSettings
import info.meuse24.pdf_scanner.domain.gateway.DownloadEntry
import info.meuse24.pdf_scanner.domain.gateway.DownloadsStorage
import info.meuse24.pdf_scanner.util.PlayReviewPromptManager
import kotlinx.coroutines.async
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
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
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.io.File
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    @get:Rule
    val tmpFolder = TemporaryFolder()

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var repository: ScanRepository
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var folderRepository: FolderRepository
    private lateinit var importScanUseCase: ImportScanUseCase
    private lateinit var importFileUseCase: ImportFileUseCase
    private lateinit var exportScanUseCase: ExportScanUseCase
    private lateinit var exportAsJpgUseCase: ExportAsJpgUseCase
    private lateinit var trashScansUseCase: TrashScansUseCase
    private lateinit var restoreScansUseCase: RestoreScansUseCase
    private lateinit var moveDocumentsUseCase: MoveDocumentsUseCase
    private lateinit var toggleFavoriteUseCase: ToggleFavoriteUseCase
    private lateinit var extractTextUseCase: ExtractTextUseCase
    private lateinit var makeSearchableWorkflow: MakeSearchableWorkflow
    private lateinit var mergePdfsWorkflow: MergePdfsWorkflow
    private lateinit var resourceProvider: FakeResourceProvider
    private lateinit var storageProvider: TestStorageProvider
    private lateinit var playReviewPromptManager: PlayReviewPromptManager

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        repository = mock(ScanRepository::class.java)
        settingsRepository = mock(SettingsRepository::class.java)
        folderRepository = mock(FolderRepository::class.java)
        importScanUseCase = mock(ImportScanUseCase::class.java)
        importFileUseCase = mock(ImportFileUseCase::class.java)
        exportScanUseCase = mock(ExportScanUseCase::class.java)
        exportAsJpgUseCase = mock(ExportAsJpgUseCase::class.java)
        trashScansUseCase = mock(TrashScansUseCase::class.java)
        restoreScansUseCase = mock(RestoreScansUseCase::class.java)
        moveDocumentsUseCase = mock(MoveDocumentsUseCase::class.java)
        toggleFavoriteUseCase = mock(ToggleFavoriteUseCase::class.java)
        extractTextUseCase = mock(ExtractTextUseCase::class.java)
        makeSearchableWorkflow = mock(MakeSearchableWorkflow::class.java)
        mergePdfsWorkflow = mock(MergePdfsWorkflow::class.java)
        playReviewPromptManager = mock(PlayReviewPromptManager::class.java)

        `when`(repository.getAllScans()).thenReturn(flowOf(emptyList()))
        `when`(repository.getFavoriteScans()).thenReturn(flowOf(emptyList()))
        `when`(repository.getAllScansForList()).thenReturn(flowOf(emptyList()))
        `when`(repository.getFavoriteScansForList()).thenReturn(flowOf(emptyList()))
        `when`(repository.getScansInFolderForList(anyLong())).thenReturn(flowOf(emptyList()))
        `when`(repository.searchScansForList(anyString())).thenReturn(flowOf(emptyList()))
        `when`(repository.searchScansInFolderForList(anyString(), anyLong())).thenReturn(flowOf(emptyList()))
        `when`(repository.searchFavoriteScansForList(anyString())).thenReturn(flowOf(emptyList()))
        `when`(settingsRepository.settings).thenReturn(MutableStateFlow(AppSettings()))
        `when`(folderRepository.observeFolders()).thenReturn(flowOf(emptyList()))

        resourceProvider = FakeResourceProvider(
            strings = mapOf(
                R.string.ocr_no_image         to "No image available",
                R.string.ocr_no_text_auto_hint to "No text: try Hindi manually",
                R.string.ocr_no_text_found    to "No text found",
                R.string.ocr_auto_detection_uncertain to "Automatic detection uncertain",
                R.string.ocr_model_download_failed to "Model download failed",
                R.string.ocr_failed           to "OCR failed",
                R.string.error_delete_failed  to "Delete failed",
                R.string.error_restore_missing_file to "Restore missing file",
                R.string.error_restore_failed to "Restore failed",
                R.string.rename_error_exists  to "Filename already exists",
                R.string.rename_error_failed  to "Rename failed",
                R.string.rename_success       to "Renamed to %s",
                R.string.docx_export_success  to "DOCX exported: %s",
                R.string.docx_export_nothing_to_export to "No OCR text for DOCX",
                R.string.docx_export_error    to "DOCX export failed",
                R.string.docx_export_ocr_busy to "OCR is already running",
                R.string.export_pages_folder_success_single to "Saved as Downloads/%s/page_1.jpg",
                R.string.export_pages_folder_success_multi to "%d pages saved in Downloads/%s/",
                R.string.error_export_pages_folder_failed to "JPG folder export failed"
            ),
            plurals = mapOf(
                R.plurals.trash_moved to "%d moved to trash",
                R.plurals.trash_restored to "%d restored"
            )
        )
        storageProvider = TestStorageProvider(tmpFolder.root)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `buildFtsQuery tokenizes and adds prefix operators`() {
        val useCase = BuildScanSearchQueryUseCase()
        assertEquals("foo* bar*", useCase("foo bar"))
        assertEquals("invoice* 2026* final*", useCase("invoice:2026/final"))
        assertEquals("", useCase(" - "))
    }

    @Test
    fun `exportAsJpg reports target folder and page count`() = runTest(testDispatcher) {
        val record = Document(
            id = 21L,
            filename = "Invoice.pdf",
            filepath = File(tmpFolder.root, "Invoice.pdf").absolutePath,
            timestamp = 0L,
            pageCount = 3,
            fileSize = 1L
        )
        `when`(exportAsJpgUseCase(record)).thenReturn(
            JpgExportResult(folderName = "Invoice", pageCount = 3)
        )
        val viewModel = buildViewModel()

        viewModel.exportAsJpg(record)
        advanceUntilIdle()

        assertEquals(
            "3 pages saved in Downloads/Invoice/",
            viewModel.messageUiState.value.success
        )
        assertNull(viewModel.messageUiState.value.error)
        verify(exportAsJpgUseCase).invoke(record)
    }

    @Test
    fun `exportAsJpg reports localized error when export fails`() = runTest(testDispatcher) {
        val record = Document(
            id = 22L,
            filename = "Broken.pdf",
            filepath = File(tmpFolder.root, "Broken.pdf").absolutePath,
            timestamp = 0L,
            pageCount = 1,
            fileSize = 1L
        )
        `when`(exportAsJpgUseCase(record)).thenThrow(IllegalStateException("synthetic failure"))
        val viewModel = buildViewModel()

        viewModel.exportAsJpg(record)
        advanceUntilIdle()

        assertEquals("JPG folder export failed", viewModel.messageUiState.value.error)
        assertNull(viewModel.messageUiState.value.success)
    }

    @Test
    fun `folder search uses context specific repository query`() = runTest(testDispatcher) {
        val archiveFilterStore = ArchiveFilterStore().apply { showFolder(42L) }
        val matchingDocument = Document(
            id = 42L,
            filename = "alpha",
            filepath = File(tmpFolder.root, "alpha.pdf").absolutePath,
            timestamp = 0L,
            pageCount = 1,
            fileSize = 0L,
            folderId = 42L
        )
        `when`(repository.searchScansInFolderForList("alpha*", 42L)).thenReturn(flowOf(listOf(matchingDocument)))

        val viewModel = buildViewModel(archiveFilterStore = archiveFilterStore)
        viewModel.updateSearchQuery("alpha")
        advanceUntilIdle()

        assertEquals(listOf(matchingDocument), viewModel.archiveUiState.value.filteredScans)
        verify(repository).searchScansInFolderForList("alpha*", 42L)
    }

    @Test
    fun `extractTexts reports missing image before invoking use case`() = runTest(testDispatcher) {
        val viewModel = buildViewModel()
        val missing = Document(
            id = 1L,
            filename = "missing",
            filepath = File(tmpFolder.root, "missing.pdf").absolutePath,
            timestamp = 0L,
            pageCount = 1,
            fileSize = 0L
        )

        viewModel.extractTexts(listOf(missing), "de")
        advanceUntilIdle()

        assertEquals("No image available", viewModel.messageUiState.value.error)
        assertFalse(viewModel.operationUiState.value.ocrLoading)
        assertNull(viewModel.operationUiState.value.ocrText)
    }

    @Test
    fun `exportDocx without OCR text prompts OCR instead of showing error`() = runTest(testDispatcher) {
        val pdf = File(tmpFolder.root, "no-ocr.pdf").apply { writeText("pdf") }
        val record = Document(
            id = 31L,
            filename = "no-ocr",
            filepath = pdf.absolutePath,
            timestamp = 0L,
            pageCount = 1,
            fileSize = pdf.length()
        )
        `when`(repository.getScansByIds(listOf(31L))).thenReturn(listOf(record))

        val viewModel = buildViewModel()
        viewModel.exportDocx(record)
        advanceUntilIdle()

        assertEquals(listOf(31L), viewModel.operationUiState.value.docxOcrPrompt?.documentIds)
        assertNull(viewModel.messageUiState.value.error)
    }

    @Test
    fun `startDocxOcrPrompt exports Word after successful OCR`() = runTest(testDispatcher) {
        val pdf = File(tmpFolder.root, "needs-ocr.pdf").apply { writeText("pdf") }
        val record = Document(
            id = 32L,
            filename = "needs-ocr",
            filepath = pdf.absolutePath,
            timestamp = 0L,
            pageCount = 1,
            fileSize = pdf.length()
        )
        val recordWithText = record.copy(
            extractedText = "Recognized text",
            pageTexts = listOf("Recognized text"),
            hasStoredOcrText = true
        )
        `when`(repository.getScansByIds(listOf(32L))).thenReturn(listOf(record), listOf(record), listOf(recordWithText))

        val viewModel = buildViewModel(
            extractTextUseCase = fakeExtract { records, _ ->
                listOf(
                    OcrDocumentResult(
                        recordId = records.single().id,
                        fullText = "Recognized text",
                        pageTexts = listOf("Recognized text"),
                        stats = null
                    )
                )
            }
        )

        viewModel.exportDocx(record)
        advanceUntilIdle()
        viewModel.startDocxOcrPrompt("de")
        advanceUntilIdle()

        assertNull(viewModel.operationUiState.value.docxOcrPrompt)
        assertNull(viewModel.operationUiState.value.ocrReviewRequestId)
        assertEquals("DOCX exported: needs-ocr.docx", viewModel.messageUiState.value.success)
        assertNull(viewModel.messageUiState.value.error)
    }

    @Test
    fun `exportDocxs with mixed OCR prompts for missing text and exports original selection after OCR`() = runTest(testDispatcher) {
        val readyPdf = File(tmpFolder.root, "ready.pdf").apply { writeText("pdf") }
        val missingPdf = File(tmpFolder.root, "missing.pdf").apply { writeText("pdf") }
        val ready = Document(
            id = 41L,
            filename = "ready",
            filepath = readyPdf.absolutePath,
            timestamp = 0L,
            pageCount = 1,
            fileSize = readyPdf.length(),
            extractedText = "Already recognized",
            pageTexts = listOf("Already recognized"),
            hasStoredOcrText = true
        )
        val missing = Document(
            id = 42L,
            filename = "missing",
            filepath = missingPdf.absolutePath,
            timestamp = 0L,
            pageCount = 1,
            fileSize = missingPdf.length()
        )
        val missingWithText = missing.copy(
            extractedText = "Newly recognized",
            pageTexts = listOf("Newly recognized"),
            hasStoredOcrText = true
        )
        `when`(repository.getScansByIds(listOf(41L, 42L)))
            .thenReturn(listOf(ready, missing), listOf(ready, missingWithText))
        `when`(repository.getScansByIds(listOf(42L))).thenReturn(listOf(missing))

        val viewModel = buildViewModel(
            extractTextUseCase = fakeExtract { records, _ ->
                listOf(
                    OcrDocumentResult(
                        recordId = records.single().id,
                        fullText = "Newly recognized",
                        pageTexts = listOf("Newly recognized"),
                        stats = null
                    )
                )
            }
        )

        viewModel.exportDocxs(listOf(ready, missing))
        advanceUntilIdle()

        assertEquals(listOf(42L), viewModel.operationUiState.value.docxOcrPrompt?.documentIds)

        viewModel.startDocxOcrPrompt("de")
        advanceUntilIdle()

        assertNull(viewModel.operationUiState.value.docxOcrPrompt)
        assertTrue(viewModel.messageUiState.value.success?.startsWith("DOCX exported: docx_export_") == true)
        assertNull(viewModel.messageUiState.value.error)
    }

    @Test
    fun `extractTexts shows auto hint when OcrNoTextException in automatic mode`() = runTest(testDispatcher) {
        val pdf = File(tmpFolder.root, "doc.pdf").apply { writeText("pdf") }
        val record = Document(id = 1L, filename = "doc", filepath = pdf.absolutePath,
            timestamp = 0L, pageCount = 1, fileSize = 0L)

        val viewModel = buildViewModel(extractTextUseCase = fakeExtract { _, _ -> throw OcrNoTextException() })
        viewModel.extractTexts(listOf(record), OCR_LANGUAGE_AUTO)
        advanceUntilIdle()

        assertEquals("No text: try Hindi manually", viewModel.messageUiState.value.error)
        assertNull(viewModel.operationUiState.value.ocrText)
        assertFalse(viewModel.operationUiState.value.ocrLoading)
    }

    @Test
    fun `extractTexts navigates to review for single result and keeps toast quiet on low confidence`() = runTest(testDispatcher) {
        val pdf = File(tmpFolder.root, "low_quality.pdf").apply { writeText("pdf") }
        val record = Document(id = 1L, filename = "low_quality", filepath = pdf.absolutePath,
            timestamp = 0L, pageCount = 1, fileSize = 0L)

        val stats = info.meuse24.pdf_scanner.domain.model.OcrResultStats(0.25f, "en", 0f)
        val viewModel = buildViewModel(
            extractTextUseCase = fakeExtract { records, _ ->
                listOf(
                    OcrDocumentResult(
                        recordId = records.single().id,
                        fullText = "Extracted with errors",
                        pageTexts = listOf("Extracted with errors"),
                        stats = stats
                    )
                )
            }
        )
        viewModel.extractTexts(listOf(record), "en")
        advanceUntilIdle()

        assertNull(viewModel.operationUiState.value.ocrText)
        assertEquals(1L, viewModel.operationUiState.value.ocrReviewRequestId)
        assertNull(viewModel.messageUiState.value.error)
    }

    @Test
    fun `extractTexts shows no-text-found when OcrNoTextException in manual mode`() = runTest(testDispatcher) {
        val pdf = File(tmpFolder.root, "doc2.pdf").apply { writeText("pdf") }
        val record = Document(id = 2L, filename = "doc2", filepath = pdf.absolutePath,
            timestamp = 0L, pageCount = 1, fileSize = 0L)

        val viewModel = buildViewModel(extractTextUseCase = fakeExtract { _, _ -> throw OcrNoTextException() })
        viewModel.extractTexts(listOf(record), "hi")
        advanceUntilIdle()

        assertEquals("No text found", viewModel.messageUiState.value.error)
        assertNull(viewModel.operationUiState.value.ocrText)
        assertFalse(viewModel.operationUiState.value.ocrLoading)
    }

    @Test
    fun `extractTexts shows generic error on unexpected exception`() = runTest(testDispatcher) {
        val pdf = File(tmpFolder.root, "doc3.pdf").apply { writeText("pdf") }
        val record = Document(id = 3L, filename = "doc3", filepath = pdf.absolutePath,
            timestamp = 0L, pageCount = 1, fileSize = 0L)

        val viewModel = buildViewModel(extractTextUseCase = fakeExtract { _, _ -> throw RuntimeException("render crash") })
        viewModel.extractTexts(listOf(record), OCR_LANGUAGE_AUTO)
        advanceUntilIdle()

        assertEquals("OCR failed", viewModel.messageUiState.value.error)
        assertNull(viewModel.operationUiState.value.ocrText)
        assertFalse(viewModel.operationUiState.value.ocrLoading)
    }

    @Test
    fun `extractTexts shows uncertain warning when auto mode has weak language signal`() = runTest(testDispatcher) {
        val pdf = File(tmpFolder.root, "doc4.pdf").apply { writeText("pdf") }
        val record = Document(id = 4L, filename = "doc4", filepath = pdf.absolutePath,
            timestamp = 0L, pageCount = 1, fileSize = 0L)

        val stats = info.meuse24.pdf_scanner.domain.model.OcrResultStats(0.55f, null, 0f)
        val viewModel = buildViewModel(
            extractTextUseCase = fakeExtract { records, _ ->
                listOf(
                    OcrDocumentResult(
                        recordId = records.single().id,
                        fullText = "Ambiguous text",
                        pageTexts = listOf("Ambiguous text"),
                        stats = stats
                    )
                )
            }
        )
        viewModel.extractTexts(listOf(record), OCR_LANGUAGE_AUTO)
        advanceUntilIdle()

        assertNull(viewModel.operationUiState.value.ocrText)
        assertEquals(4L, viewModel.operationUiState.value.ocrReviewRequestId)
        assertEquals("Automatic detection uncertain", viewModel.messageUiState.value.error)
    }

    @Test
    fun `extractTexts reports model download failure explicitly`() = runTest(testDispatcher) {
        val pdf = File(tmpFolder.root, "doc5.pdf").apply { writeText("pdf") }
        val record = Document(id = 5L, filename = "doc5", filepath = pdf.absolutePath,
            timestamp = 0L, pageCount = 1, fileSize = 0L)

        val viewModel = buildViewModel(
            extractTextUseCase = fakeExtract { _, _ ->
                throw OcrModelInstallException("install failed")
            }
        )
        viewModel.extractTexts(listOf(record), OCR_LANGUAGE_AUTO)
        advanceUntilIdle()

        assertEquals("Model download failed", viewModel.messageUiState.value.error)
        assertNull(viewModel.operationUiState.value.ocrText)
    }

    @Test
    fun `renameScan uses storage provider directory and updates repository`() = runTest(testDispatcher) {
        val viewModel = buildViewModel()
        val scansDir = storageProvider.scansDir()
        val originalFile = File(scansDir, "original.pdf").apply { writeText("pdf") }
        val originalThumb = File(scansDir, "original.jpg").apply { writeText("thumb") }
        val record = Document(
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
        assertEquals("Renamed to renamed", viewModel.messageUiState.value.success)
        assertNull(viewModel.messageUiState.value.error)
        verify(repository).updateFilenameAndPath(
            7L,
            "renamed",
            renamedFile.absolutePath,
            renamedThumb.absolutePath
        )
    }

    @Test
    fun `restoreLastTrashed clears ids after successful restore`() = runTest(testDispatcher) {
        val file = File(tmpFolder.root, "restore-ok.pdf").apply { writeText("pdf") }
        val record = Document(
            id = 11L,
            filename = "restore-ok",
            filepath = file.absolutePath,
            timestamp = 0L,
            pageCount = 1,
            fileSize = file.length()
        )
        val trashRepository = TrashRepository(HomeFakeTrashDao(listOf(record)))
        val viewModel = buildViewModel(
            trashScansUseCase = TrashScansUseCase(trashRepository),
            restoreScansUseCase = RestoreScansUseCase(
                trashRepository,
                repository,
                HomeTestFolderRepository()
            )
        )

        viewModel.deleteScans(listOf(record))
        advanceUntilIdle()
        viewModel.restoreLastTrashed()
        advanceUntilIdle()

        assertEquals(emptyList<Long>(), viewModel.messageUiState.value.lastTrashed)
        assertEquals("1 restored", viewModel.messageUiState.value.success)
        assertNull(viewModel.messageUiState.value.error)
    }

    @Test
    fun `restoreLastTrashed keeps ids when restore fails`() = runTest(testDispatcher) {
        val missingFile = File(tmpFolder.root, "restore-missing.pdf")
        val record = Document(
            id = 12L,
            filename = "restore-missing",
            filepath = missingFile.absolutePath,
            timestamp = 0L,
            pageCount = 1,
            fileSize = 0L
        )
        val trashRepository = TrashRepository(HomeFakeTrashDao(listOf(record)))
        val viewModel = buildViewModel(
            trashScansUseCase = TrashScansUseCase(trashRepository),
            restoreScansUseCase = RestoreScansUseCase(
                trashRepository,
                repository,
                HomeTestFolderRepository()
            )
        )

        viewModel.deleteScans(listOf(record))
        advanceUntilIdle()
        viewModel.restoreLastTrashed()
        advanceUntilIdle()

        assertEquals(listOf(12L), viewModel.messageUiState.value.lastTrashed)
        assertEquals("Restore missing file", viewModel.messageUiState.value.error)
        assertNull(viewModel.messageUiState.value.success)
    }

    @Test
    fun `requestPrint emits print request for standard page sizes`() = runTest(testDispatcher) {
        val viewModel = buildViewModel(
            pdfMetadataOps = testPdfMetadataOps { PdfPageSizeCategory.UNIFORM_STANDARD }
        )
        val record = printRecord("standard.pdf")
        val printEvent = async { viewModel.printRequests.first() }

        viewModel.requestPrint(record)
        advanceUntilIdle()

        assertEquals(record, printEvent.await())
        assertNull(viewModel.pendingPrintDocument.value)
    }

    @Test
    fun `requestPrint stores pending warning for custom page sizes`() = runTest(testDispatcher) {
        val viewModel = buildViewModel(
            pdfMetadataOps = testPdfMetadataOps { PdfPageSizeCategory.UNIFORM_CUSTOM }
        )
        val record = printRecord("custom.pdf")

        viewModel.requestPrint(record)
        advanceUntilIdle()

        assertEquals(record, viewModel.pendingPrintDocument.value)
    }

    @Test
    fun `requestPrint emits print request when page size classification fails`() = runTest(testDispatcher) {
        val viewModel = buildViewModel(
            pdfMetadataOps = testPdfMetadataOps { throw IOException("missing") }
        )
        val record = printRecord("missing.pdf")
        val printEvent = async { viewModel.printRequests.first() }

        viewModel.requestPrint(record)
        advanceUntilIdle()

        assertEquals(record, printEvent.await())
        assertNull(viewModel.pendingPrintDocument.value)
    }

    private fun buildViewModel(
        extractTextUseCase: ExtractTextUseCase = this.extractTextUseCase,
        exportDocxUseCase: ExportDocxUseCase = testExportDocxUseCase(),
        trashScansUseCase: TrashScansUseCase = this.trashScansUseCase,
        restoreScansUseCase: RestoreScansUseCase = this.restoreScansUseCase,
        archiveFilterStore: ArchiveFilterStore = ArchiveFilterStore(),
        pdfMetadataOps: PdfMetadataOps = testPdfMetadataOps { PdfPageSizeCategory.UNIFORM_STANDARD }
    ): HomeViewModel {
        val importCoordinator = HomeImportCoordinator(
            importScanUseCase = importScanUseCase,
            importFileUseCase = importFileUseCase,
            recordReviewPromptActionUseCase = RecordReviewPromptActionUseCase(
                object : ReviewPromptPolicy {
                    override fun recordSuccessfulDocumentActionAndCheckEligibility(): Boolean = false
                }
            ),
            playReviewPromptManager = playReviewPromptManager
        )
        val exportCoordinator = HomeExportCoordinator(
            exportScanUseCase = exportScanUseCase,
            exportAsJpgUseCase = exportAsJpgUseCase,
            exportDocxUseCase = exportDocxUseCase,
            exportOcrTextUseCase = testExportOcrTextUseCase(),
            checkPrintPageSizeWarningUseCase = CheckPrintPageSizeWarningUseCase(
                pdfMetadataOps = pdfMetadataOps,
                dispatcherProvider = TestDispatcherProvider(testDispatcher)
            )
        )
        val ocrCoordinator = HomeOcrCoordinator(
            repository = repository,
            extractTextUseCase = extractTextUseCase,
            findOcrExtractableDocumentsUseCase = FindOcrExtractableDocumentsUseCase(
                object : info.meuse24.pdf_scanner.domain.gateway.DocumentFileStore {
                    override fun savePdf(source: Any, filename: String): File = error("not used")
                    override fun saveThumbnail(source: Any, filename: String): File? = error("not used")
                    override fun copyToTemp(source: Any, suffix: String): File = error("not used")
                    override fun exists(path: String): Boolean = File(path).exists()
                }
            ),
            ocrBackfillUseCase = OcrBackfillUseCase(
                repository = repository,
                settingsRepository = settingsRepository,
                extractTextUseCase = extractTextUseCase,
                autoTagUseCase = AutoTagUseCase()
            ),
            makeSearchableWorkflow = makeSearchableWorkflow
        )
        val archiveCoordinator = HomeArchiveCoordinator(
            repository = repository,
            folderRepository = folderRepository,
            settingsRepository = settingsRepository,
            trashScansUseCase = trashScansUseCase,
            restoreScansUseCase = restoreScansUseCase,
            moveDocumentsUseCase = moveDocumentsUseCase,
            toggleFavoriteUseCase = toggleFavoriteUseCase,
            renameDocumentUseCase = RenameDocumentUseCase(repository, storageProvider),
            buildScanSearchQueryUseCase = BuildScanSearchQueryUseCase(),
            mergePdfsWorkflow = mergePdfsWorkflow,
            storageProvider = storageProvider,
            archiveFilterStore = archiveFilterStore
        )
        return HomeViewModel(
            importCoordinator = importCoordinator,
            exportCoordinator = exportCoordinator,
            ocrCoordinator = ocrCoordinator,
            archiveCoordinator = archiveCoordinator,
            workflowErrorMapper = WorkflowErrorMapper(resourceProvider),
            resourceProvider = resourceProvider,
            dispatcherProvider = TestDispatcherProvider(testDispatcher)
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

    private fun testExportDocxUseCase() = ExportDocxUseCase(
        downloadsStorage = object : DownloadsStorage {
            override fun writeDownload(
                displayName: String,
                mimeType: String,
                writer: (java.io.OutputStream) -> Unit
            ): DownloadEntry {
                writer(java.io.ByteArrayOutputStream())
                return object : DownloadEntry {
                    override val displayName: String = displayName
                    override fun delete() = Unit
                }
            }
        },
        docxBuilder = object : info.meuse24.pdf_scanner.domain.gateway.DocxBuilder {
            override fun writeDocx(
                doc: info.meuse24.pdf_scanner.domain.gateway.DocxDocument,
                out: java.io.OutputStream
            ) = Unit
        }
    )

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

    private fun printRecord(filename: String): Document {
        val file = File(tmpFolder.root, filename)
        return Document(
            id = filename.hashCode().toLong(),
            filename = filename.removeSuffix(".pdf"),
            filepath = file.absolutePath,
            timestamp = 0L,
            pageCount = 1,
            fileSize = 0L
        )
    }

    /** Erstellt eine anonyme ExtractTextUseCase-Subklasse, die den Block als invoke-Body nutzt. */
    private fun fakeExtract(block: suspend (List<Document>, String) -> List<OcrDocumentResult>): ExtractTextUseCase =
        object : ExtractTextUseCase(
            ocrDocumentTextExtractor = object : info.meuse24.pdf_scanner.domain.gateway.OcrDocumentTextExtractor {
                override suspend fun extract(
                    records: List<Document>,
                    languageCode: String,
                    onStatus: (info.meuse24.pdf_scanner.domain.model.OcrPipelineStatus) -> Unit
                ): List<OcrDocumentResult> = emptyList()
            }
        ) {
            override suspend fun invoke(
                records: List<Document>,
                languageCode: String,
                onStatus: (info.meuse24.pdf_scanner.domain.model.OcrPipelineStatus) -> Unit
            ): List<OcrDocumentResult> =
                block(records, languageCode)
        }
}

private class HomeFakeTrashDao(
    initialRecords: List<Document>
) : TrashDao {
    private val records = initialRecords.associateBy { it.id }.toMutableMap()

    override fun getTrashedScans(): Flow<List<ScanRecord>> = flowOf(
        records.values.filter { it.deletedAt != null }.map { it.toEntity() }
    )

    override suspend fun getScansByIds(ids: List<Long>): List<ScanRecord> =
        ids.mapNotNull(records::get).map { it.toEntity() }

    override suspend fun softDelete(ids: List<Long>, timestamp: Long) {
        ids.forEach { id ->
            val record = records[id] ?: return@forEach
            records[id] = record.copy(deletedAt = timestamp)
        }
    }

    override suspend fun restore(ids: List<Long>) {
        ids.forEach { id ->
            val record = records[id] ?: return@forEach
            records[id] = record.copy(deletedAt = null)
        }
    }

    override suspend fun findExpiredTrash(threshold: Long): List<ScanRecord> = emptyList()
}

private class HomeTestFolderRepository : FolderRepository {
    override fun observeFolders(): Flow<List<Folder>> = flowOf(emptyList())
    override suspend fun createFolder(folder: Folder): Long = 0L
    override suspend fun renameFolder(id: Long, name: String) = Unit
    override suspend fun deleteFolder(id: Long) = Unit
    override suspend fun folderExists(id: Long): Boolean = false
}


