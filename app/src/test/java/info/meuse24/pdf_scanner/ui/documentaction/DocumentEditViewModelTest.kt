package info.meuse24.pdf_scanner.ui.documentaction

import androidx.lifecycle.SavedStateHandle
import info.meuse24.pdf_scanner.data.local.ScanDao
import info.meuse24.pdf_scanner.data.local.ScanRecord
import info.meuse24.pdf_scanner.data.mapper.toDomain
import info.meuse24.pdf_scanner.data.mapper.toEntity
import info.meuse24.pdf_scanner.data.repository.ScanRepository
import info.meuse24.pdf_scanner.domain.model.Document
import info.meuse24.pdf_scanner.domain.usecase.AddPageNumbersUseCase
import info.meuse24.pdf_scanner.domain.usecase.AnnotationOval
import info.meuse24.pdf_scanner.domain.usecase.AnnotationRect
import info.meuse24.pdf_scanner.domain.usecase.AnnotationShapeStyle
import info.meuse24.pdf_scanner.domain.usecase.AnnotationStroke
import info.meuse24.pdf_scanner.domain.usecase.AnnotationText
import info.meuse24.pdf_scanner.domain.workflow.AnnotatePdfWorkflow
import info.meuse24.pdf_scanner.domain.workflow.AnnotatePdfWorkflowResult
import info.meuse24.pdf_scanner.domain.workflow.CompressPdfWorkflow
import info.meuse24.pdf_scanner.domain.workflow.ConvertToGrayscaleWorkflow
import info.meuse24.pdf_scanner.domain.workflow.DocumentWorkflowGuard
import info.meuse24.pdf_scanner.domain.workflow.HighlightPdfWorkflow
import info.meuse24.pdf_scanner.domain.workflow.PageNumbersWorkflow
import info.meuse24.pdf_scanner.domain.workflow.ProtectPdfWorkflow
import info.meuse24.pdf_scanner.domain.workflow.RedactPdfWorkflowResult
import info.meuse24.pdf_scanner.domain.workflow.RedactPdfWorkflow
import info.meuse24.pdf_scanner.domain.workflow.RemovePasswordWorkflow
import info.meuse24.pdf_scanner.domain.workflow.RemoveTextLayerWorkflow
import info.meuse24.pdf_scanner.domain.workflow.ScanWorkflowError
import info.meuse24.pdf_scanner.domain.workflow.RestrictUsageWorkflow
import info.meuse24.pdf_scanner.domain.workflow.SignatureStampWorkflow
import info.meuse24.pdf_scanner.domain.workflow.TextWatermarkWorkflow
import info.meuse24.pdf_scanner.domain.workflow.UnlockPdfWorkflow
import info.meuse24.pdf_scanner.domain.workflow.UpdatePdfMetadataWorkflow
import info.meuse24.pdf_scanner.domain.workflow.WorkflowErrorMapper
import info.meuse24.pdf_scanner.domain.workflow.WorkflowResult
import info.meuse24.pdf_scanner.domain.usecase.RedactionRect
import info.meuse24.pdf_scanner.testutil.FakeResourceProvider
import info.meuse24.pdf_scanner.testutil.TestDispatcherProvider
import info.meuse24.pdf_scanner.testutil.TestStorageProvider
import info.meuse24.pdf_scanner.util.PdfEditor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
class DocumentEditViewModelTest {

    @get:Rule
    val tmpFolder = TemporaryFolder()

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun pdfFile(name: String = "scan_1.pdf"): File =
        tmpFolder.newFile(name).apply { writeText("pdf") }

    private fun record(file: File): Document = Document(
        id = 1L,
        filename = file.nameWithoutExtension,
        filepath = file.absolutePath,
        timestamp = 0L,
        pageCount = 1,
        fileSize = file.length()
    )

    private fun stubMapper(msg: String = "err"): WorkflowErrorMapper {
        return WorkflowErrorMapper(FakeResourceProvider(fallback = msg))
    }

    private fun buildVm(
        pdfEditor: PdfEditor,
        scanRecord: Document,
        errorMapper: WorkflowErrorMapper = stubMapper(),
        redactPdfWorkflow: RedactPdfWorkflow = mock(RedactPdfWorkflow::class.java),
        annotatePdfWorkflow: AnnotatePdfWorkflow = mock(AnnotatePdfWorkflow::class.java)
    ): DocumentEditViewModel {
        val dao = TestScanDao(listOf(scanRecord))
        val repository = ScanRepository(dao)
        val addPageNumbersUseCase = AddPageNumbersUseCase(
            pdfEditor,
            info.meuse24.pdf_scanner.domain.service.ScanArtifactPersister(pdfEditor, repository)
        )
        val pageNumbersWorkflow = PageNumbersWorkflow(addPageNumbersUseCase, DocumentWorkflowGuard(pdfEditor))

        val textWatermarkWorkflow = mock(TextWatermarkWorkflow::class.java)
        val compressPdfWorkflow = mock(CompressPdfWorkflow::class.java)
        val protectPdfWorkflow = mock(ProtectPdfWorkflow::class.java)
        val unlockPdfWorkflow = mock(UnlockPdfWorkflow::class.java)
        val signatureStampWorkflow = mock(SignatureStampWorkflow::class.java)
        val removeTextLayerWorkflow = mock(RemoveTextLayerWorkflow::class.java)
        val removePasswordWorkflow = mock(RemovePasswordWorkflow::class.java)
        val restrictUsageWorkflow = mock(RestrictUsageWorkflow::class.java)
        val highlightPdfWorkflow = mock(HighlightPdfWorkflow::class.java)
        val convertToGrayscaleWorkflow = mock(ConvertToGrayscaleWorkflow::class.java)
        val updatePdfMetadataWorkflow = mock(UpdatePdfMetadataWorkflow::class.java)

        val savedState = SavedStateHandle(mapOf("scanId" to 1L))

        return DocumentEditViewModel(
            repository = repository,
            pageNumbersWorkflow = pageNumbersWorkflow,
            textWatermarkWorkflow = textWatermarkWorkflow,
            compressPdfWorkflow = compressPdfWorkflow,
            protectPdfWorkflow = protectPdfWorkflow,
            unlockPdfWorkflow = unlockPdfWorkflow,
            signatureStampWorkflow = signatureStampWorkflow,
            redactPdfWorkflow = redactPdfWorkflow,
            removeTextLayerWorkflow = removeTextLayerWorkflow,
            removePasswordWorkflow = removePasswordWorkflow,
            restrictUsageWorkflow = restrictUsageWorkflow,
            highlightPdfWorkflow = highlightPdfWorkflow,
            annotatePdfWorkflow = annotatePdfWorkflow,
            convertToGrayscaleWorkflow = convertToGrayscaleWorkflow,
            updatePdfMetadataWorkflow = updatePdfMetadataWorkflow,
            pdfEditor = pdfEditor,
            errorMapper = errorMapper,
            resourceProvider = FakeResourceProvider(),
            storageProvider = TestStorageProvider(tmpFolder.root),
            dispatcherProvider = TestDispatcherProvider(testDispatcher),
            savedStateHandle = savedState
        )
    }

    @Test
    fun `addPageNumbers success setzt success und beendet editLoading`() = runTest(testDispatcher) {
        val file = pdfFile()
        val rec = record(file)
        val vm = buildVm(SuccessPageNumbersPdfEditor(tmpFolder.root), rec)
        advanceUntilIdle()

        vm.addPageNumbers()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.success)
        assertFalse(vm.uiState.value.editLoading)
        assertNull(vm.uiState.value.error)
    }

    @Test
    fun `addPageNumbers failure setzt error und beendet editLoading`() = runTest(testDispatcher) {
        val file = pdfFile()
        val rec = record(file)
        val vm = buildVm(FailPageNumbersPdfEditor(), rec)
        advanceUntilIdle()

        vm.addPageNumbers()
        advanceUntilIdle()

        assertNotNull(vm.uiState.value.error)
        assertFalse(vm.uiState.value.success)
        assertFalse(vm.uiState.value.editLoading)
    }

    @Test
    fun `editLoading ist true direkt nach addPageNumbers-Aufruf`() = runTest(testDispatcher) {
        val file = pdfFile()
        val rec = record(file)
        val vm = buildVm(NoOpPageNumbersPdfEditor(), rec)
        advanceUntilIdle()

        val loadingStates = mutableListOf<Boolean>()
        val job = backgroundScope.launch { vm.uiState.collect { loadingStates += it.editLoading } }
        vm.addPageNumbers()
        advanceUntilIdle()
        job.cancel()

        assertTrue(loadingStates.contains(true))
    }

    @Test
    fun `clearError loescht Fehlerzustand`() = runTest(testDispatcher) {
        val file = pdfFile()
        val rec = record(file)
        val vm = buildVm(FailPageNumbersPdfEditor(), rec)
        advanceUntilIdle()

        vm.addPageNumbers()
        advanceUntilIdle()
        assertNotNull(vm.uiState.value.error)

        vm.clearError()
        runCurrent()

        assertNull(vm.uiState.value.error)
    }

    @Test
    fun `applyRedactions success setzt success und beendet editLoading`() = runTest(testDispatcher) {
        val file = pdfFile()
        val rec = record(file)
        val redactWorkflow = mock(RedactPdfWorkflow::class.java)
        val rects = listOf(redactionRect())
        val scansDir = File(tmpFolder.root, "scans").apply { mkdirs() }
        `when`(redactWorkflow.invoke(rec, rects, scansDir, true, "de")).thenReturn(
            WorkflowResult.Success(RedactPdfWorkflowResult(outputFilename = "scan_redacted"))
        )
        val vm = buildVm(NoOpPageNumbersPdfEditor(), rec, redactPdfWorkflow = redactWorkflow)
        advanceUntilIdle()

        vm.applyRedactions(rects, makeSearchable = true, languageCode = "de")
        advanceUntilIdle()

        assertTrue(vm.uiState.value.success)
        assertFalse(vm.uiState.value.editLoading)
        assertNull(vm.uiState.value.error)
    }

    @Test
    fun `applyRedactions failure setzt error und beendet editLoading`() = runTest(testDispatcher) {
        val file = pdfFile()
        val rec = record(file)
        val redactWorkflow = mock(RedactPdfWorkflow::class.java)
        val rects = listOf(redactionRect())
        val scansDir = File(tmpFolder.root, "scans").apply { mkdirs() }
        `when`(redactWorkflow.invoke(rec, rects, scansDir, false, "en")).thenReturn(
            WorkflowResult.Failure(ScanWorkflowError.NoRedactionAreas)
        )
        val vm = buildVm(NoOpPageNumbersPdfEditor(), rec, redactPdfWorkflow = redactWorkflow)
        advanceUntilIdle()

        vm.applyRedactions(rects)
        advanceUntilIdle()

        assertNotNull(vm.uiState.value.error)
        assertFalse(vm.uiState.value.success)
        assertFalse(vm.uiState.value.editLoading)
    }

    @Test
    fun `applyAnnotations success setzt success und beendet editLoading`() = runTest(testDispatcher) {
        val file = pdfFile()
        val rec = record(file)
        val annotateWorkflow = mock(AnnotatePdfWorkflow::class.java)
        val scansDir = File(tmpFolder.root, "scans").apply { mkdirs() }
        val strokes = listOf(
            AnnotationStroke(
                points = listOf(0.1f to 0.2f, 0.4f to 0.2f),
                pageIndex = 0
            )
        )
        val rects = listOf(
            AnnotationRect(
                left = 0.2f,
                top = 0.2f,
                right = 0.5f,
                bottom = 0.35f,
                pageIndex = 0,
                style = AnnotationShapeStyle.FILLED
            )
        )
        val ovals = listOf(
            AnnotationOval(
                left = 0.3f,
                top = 0.3f,
                right = 0.6f,
                bottom = 0.5f,
                pageIndex = 0,
                style = AnnotationShapeStyle.FRAME
            )
        )
        val comments = listOf(
            AnnotationText(
                pageIndex = 0,
                anchorX = 0.25f,
                anchorY = 0.4f,
                text = "note",
                fontSizeFraction = 0.03f
            )
        )
        `when`(annotateWorkflow.invoke(rec, strokes, rects, ovals, comments, scansDir)).thenReturn(
            WorkflowResult.Success(AnnotatePdfWorkflowResult(outputFilename = "scan_annotated"))
        )
        val vm = buildVm(NoOpPageNumbersPdfEditor(), rec, annotatePdfWorkflow = annotateWorkflow)
        advanceUntilIdle()

        vm.applyAnnotations(strokes, rects, ovals, comments)
        advanceUntilIdle()

        assertTrue(vm.uiState.value.success)
        assertFalse(vm.uiState.value.editLoading)
        assertNull(vm.uiState.value.error)
    }
}

private fun redactionRect() = RedactionRect(
    pageIndex = 0,
    left = 0.1f,
    top = 0.1f,
    right = 0.4f,
    bottom = 0.3f
)

private class TestScanDao(
    private val initialRecords: List<Document> = emptyList()
) : ScanDao {
    val inserted = mutableListOf<Document>()
    override fun getAllScans(): Flow<List<ScanRecord>> = flowOf(initialRecords.map { it.toEntity() })
    override suspend fun insert(record: ScanRecord): Long {
        inserted.add(record.toDomain())
        return inserted.size.toLong()
    }
    override suspend fun insertAll(records: List<ScanRecord>) { inserted.addAll(records.map { it.toDomain() }) }
    override suspend fun delete(record: ScanRecord) {}
    override fun searchScansFlow(query: String): Flow<List<ScanRecord>> = flowOf(emptyList())
    override suspend fun markSearchableWithContent(
        id: Long,
        fileSize: Long,
        text: String?,
        tags: String?,
        confidence: Float?,
        language: String?,
        pageTextJson: String?
    ) {}
    override suspend fun updateExtractedTextAndOcrStats(
        id: Long,
        text: String?,
        confidence: Float?,
        language: String?,
        pageTextJson: String?
    ) {}
    override suspend fun markSearchable(id: Long, fileSize: Long) {}
    override suspend fun updateFileSize(id: Long, fileSize: Long) {}
    override suspend fun updatePageMetrics(id: Long, pageCount: Int, fileSize: Long) {}
    override suspend fun invalidateAfterAppend(id: Long, fileSize: Long, pageCount: Int) {}
    override suspend fun updateFilenameAndPath(id: Long, filename: String, filepath: String, thumbnailPath: String?) {}
    override fun getScansInFolder(folderId: Long): Flow<List<ScanRecord>> = flowOf(emptyList())
    override fun getFavoriteScans(): Flow<List<ScanRecord>> = flowOf(emptyList())
    override suspend fun moveScans(ids: List<Long>, folderId: Long?) {}
    override suspend fun setFavorite(ids: List<Long>, favorite: Boolean) {}
}

private class NoOpPageNumbersPdfEditor : PdfEditor() {
    override fun addPageNumbers(input: File, outputDir: File): File = input
    override fun renderPageThumbnail(pdfFile: File, pageIndex: Int, maxSizePx: Int) = null
}

private class SuccessPageNumbersPdfEditor(private val outputDir: File) : PdfEditor() {
    override fun addPageNumbers(input: File, outputDir: File): File =
        File(outputDir, "${input.nameWithoutExtension}_numbered.pdf")
            .apply { writeText("numbered") }

    override fun generateThumbnail(pdfFile: File, outputFile: File): Boolean {
        outputFile.writeText("thumb")
        return true
    }

    override fun renderPageThumbnail(pdfFile: File, pageIndex: Int, maxSizePx: Int) = null
}

private class FailPageNumbersPdfEditor : PdfEditor() {
    override fun addPageNumbers(input: File, outputDir: File): File =
        throw IOException("disk full")

    override fun renderPageThumbnail(pdfFile: File, pageIndex: Int, maxSizePx: Int) = null
}

