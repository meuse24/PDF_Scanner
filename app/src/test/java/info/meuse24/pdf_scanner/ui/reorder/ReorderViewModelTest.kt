package info.meuse24.pdf_scanner.ui.reorder

import androidx.lifecycle.SavedStateHandle
import info.meuse24.pdf_scanner.data.local.ScanDao
import info.meuse24.pdf_scanner.data.local.ScanRecord
import info.meuse24.pdf_scanner.data.mapper.toDomain
import info.meuse24.pdf_scanner.data.mapper.toEntity
import info.meuse24.pdf_scanner.domain.model.Document
import info.meuse24.pdf_scanner.data.repository.ScanRepository
import info.meuse24.pdf_scanner.domain.usecase.ReorderPagesUseCase
import info.meuse24.pdf_scanner.domain.workflow.DocumentWorkflowGuard
import info.meuse24.pdf_scanner.domain.workflow.ReorderPagesWorkflow
import info.meuse24.pdf_scanner.domain.workflow.WorkflowErrorMapper
import info.meuse24.pdf_scanner.testutil.FakeResourceProvider
import info.meuse24.pdf_scanner.testutil.TestDispatcherProvider
import info.meuse24.pdf_scanner.testutil.TestStorageProvider
import info.meuse24.pdf_scanner.util.PdfEditor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
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
import java.io.File
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class ReorderViewModelTest {

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

    // ── Helpers ──────────────────────────────────────────────────────────────

    /** Creates a real PDF file in tmpFolder so File.exists() returns true. */
    private fun pdfFile(name: String = "scan_1.pdf"): File =
        tmpFolder.newFile(name).apply { writeText("pdf") }

    /**
     * pageCount=1 so that getCurrentOrder()=[0] and the workflow's
     * expectedOrder check ([0].sorted() == [0]) passes.
     */
    private fun record(file: File, pageCount: Int = 1): Document = Document(
        id = 1L,
        filename = file.nameWithoutExtension,
        filepath = file.absolutePath,
        timestamp = 0L,
        pageCount = pageCount,
        fileSize = file.length()
    )

    /** A WorkflowErrorMapper that always returns a fixed string without needing a real Context. */
    private fun stubMapper(msg: String = "err"): WorkflowErrorMapper {
        return WorkflowErrorMapper(FakeResourceProvider(fallback = msg))
    }

    private fun buildVm(
        pdfEditor: PdfEditor,
        scanRecord: Document,
        errorMapper: WorkflowErrorMapper = stubMapper()
    ): ReorderViewModel {
        val dao = TestScanDao(listOf(scanRecord))
        val repository = ScanRepository(dao)
        val useCase = ReorderPagesUseCase(
            pdfEditor,
            info.meuse24.pdf_scanner.domain.service.ScanArtifactPersister(pdfEditor, repository),
            repository
        )
        val workflow = ReorderPagesWorkflow(useCase, DocumentWorkflowGuard(pdfEditor))
        val savedState = SavedStateHandle(mapOf("scanId" to 1L))
        return ReorderViewModel(
            repository = repository,
            pdfEditor = pdfEditor,
            reorderPagesWorkflow = workflow,
            errorMapper = errorMapper,
            storageProvider = TestStorageProvider(tmpFolder.root),
            dispatcherProvider = TestDispatcherProvider(testDispatcher),
            savedStateHandle = savedState
        )
    }

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    fun `editLoading ist true direkt nach reorderPages-Aufruf`() = runTest(testDispatcher) {
        val file = pdfFile()
        val rec = record(file)
        val vm = buildVm(NoOpReorderPdfEditor(), rec)
        advanceUntilIdle()

        vm.reorderPages()

        assertTrue(vm.editLoading.value)
    }

    @Test
    fun `reorderPages success setzt success und beendet editLoading`() = runTest(testDispatcher) {
        val file = pdfFile()
        val rec = record(file)
        val vm = buildVm(SuccessReorderPdfEditor(tmpFolder.root), rec)
        advanceUntilIdle()

        vm.reorderPages()
        advanceUntilIdle()

        assertTrue(vm.success.value)
        assertFalse(vm.editLoading.value)
        assertNull(vm.error.value)
    }

    @Test
    fun `reorderPages failure setzt error und beendet editLoading`() = runTest(testDispatcher) {
        val file = pdfFile()
        val rec = record(file)
        val vm = buildVm(FailReorderPdfEditor(), rec)
        advanceUntilIdle()

        vm.reorderPages()
        advanceUntilIdle()

        assertNotNull(vm.error.value)
        assertFalse(vm.success.value)
        assertFalse(vm.editLoading.value)
    }

    @Test
    fun `clearError loescht Fehlerzustand`() = runTest(testDispatcher) {
        val file = pdfFile()
        val rec = record(file)
        val vm = buildVm(FailReorderPdfEditor(), rec)
        advanceUntilIdle()

        vm.reorderPages()
        advanceUntilIdle()
        assertNotNull(vm.error.value)

        vm.clearError()

        assertNull(vm.error.value)
    }
}

// ── Fake DAO ──────────────────────────────────────────────────────────────────

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
    override fun getScansWithTag(tagKey: String): kotlinx.coroutines.flow.Flow<List<info.meuse24.pdf_scanner.data.local.ScanRecord>> = kotlinx.coroutines.flow.flowOf(emptyList())
    override suspend fun getAllSearchableWithoutTags(): List<info.meuse24.pdf_scanner.data.local.ScanRecord> = emptyList()
    override suspend fun updateTags(id: Long, tags: String) = Unit
}

// ── Fake PdfEditors ───────────────────────────────────────────────────────────

/** Does nothing — renderPageThumbnail returns null. */
private class NoOpReorderPdfEditor : PdfEditor() {
    override fun renderPageThumbnail(pdfFile: File, pageIndex: Int, maxSizePx: Int) = null
    override fun reorderPages(input: File, newOrder: List<Int>, saveAsCopy: Boolean): File =
        input
}

/**
 * reorderPages writes a new file in outputDir and returns it;
 * generateThumbnail writes text; renderPageThumbnail returns null.
 */
private class SuccessReorderPdfEditor(private val outputDir: File) : PdfEditor() {
    override fun reorderPages(input: File, newOrder: List<Int>, saveAsCopy: Boolean): File =
        File(outputDir, "${input.nameWithoutExtension}_Sortiert.pdf")
            .apply { writeText("reordered") }

    override fun generateThumbnail(pdfFile: File, outputFile: File): Boolean {
        outputFile.writeText("thumb")
        return true
    }

    override fun renderPageThumbnail(pdfFile: File, pageIndex: Int, maxSizePx: Int) = null
}

/** reorderPages throws IOException to simulate a disk-full error. */
private class FailReorderPdfEditor : PdfEditor() {
    override fun reorderPages(input: File, newOrder: List<Int>, saveAsCopy: Boolean): File =
        throw IOException("disk full")

    override fun renderPageThumbnail(pdfFile: File, pageIndex: Int, maxSizePx: Int) = null
}


