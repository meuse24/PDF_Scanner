package info.meuse24.pdf_scanner.ui.reorder

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import info.meuse24.pdf_scanner.data.local.ScanDao
import info.meuse24.pdf_scanner.data.local.ScanRecord
import info.meuse24.pdf_scanner.data.repository.ScanRepository
import info.meuse24.pdf_scanner.domain.usecase.ReorderPagesUseCase
import info.meuse24.pdf_scanner.domain.workflow.ReorderPagesWorkflow
import info.meuse24.pdf_scanner.domain.workflow.WorkflowErrorMapper
import info.meuse24.pdf_scanner.util.PdfEditor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.io.File
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class ReorderViewModelTest {

    @get:Rule
    val tmpFolder = TemporaryFolder()

    private val testDispatcher = UnconfinedTestDispatcher()

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
    private fun record(file: File, pageCount: Int = 1): ScanRecord = ScanRecord(
        id = 1L,
        filename = file.nameWithoutExtension,
        filepath = file.absolutePath,
        timestamp = 0L,
        pageCount = pageCount,
        fileSize = file.length()
    )

    /** A WorkflowErrorMapper that always returns a fixed string without needing a real Context. */
    private fun stubMapper(msg: String = "err"): WorkflowErrorMapper {
        val ctx = mock(Context::class.java)
        `when`(ctx.getString(anyInt())).thenReturn(msg)
        return WorkflowErrorMapper(ctx)
    }

    private fun buildVm(
        pdfEditor: PdfEditor,
        scanRecord: ScanRecord,
        errorMapper: WorkflowErrorMapper = stubMapper()
    ): ReorderViewModel {
        val dao = TestScanDao(listOf(scanRecord))
        val repository = ScanRepository(dao)
        val useCase = ReorderPagesUseCase(pdfEditor, repository)
        val workflow = ReorderPagesWorkflow(useCase)
        val context = mock(Context::class.java)
        `when`(context.filesDir).thenReturn(tmpFolder.root)
        val savedState = SavedStateHandle(mapOf("scanId" to 1L))
        return ReorderViewModel(
            repository = repository,
            pdfEditor = pdfEditor,
            reorderPagesWorkflow = workflow,
            errorMapper = errorMapper,
            context = context,
            savedStateHandle = savedState
        )
    }

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    fun `editLoading ist true direkt nach reorderPages-Aufruf`() = runTest(testDispatcher) {
        val file = pdfFile()
        val rec = record(file)
        val vm = buildVm(NoOpReorderPdfEditor(), rec)

        vm.reorderPages()

        assertTrue(vm.editLoading.value)
    }

    @Test
    fun `reorderPages success setzt success und beendet editLoading`() = runTest(testDispatcher) {
        val file = pdfFile()
        val rec = record(file)
        val vm = buildVm(SuccessReorderPdfEditor(tmpFolder.root), rec)

        vm.reorderPages()
        vm.editLoading.first { !it }

        assertTrue(vm.success.value)
        assertFalse(vm.editLoading.value)
        assertNull(vm.error.value)
    }

    @Test
    fun `reorderPages failure setzt error und beendet editLoading`() = runTest(testDispatcher) {
        val file = pdfFile()
        val rec = record(file)
        val vm = buildVm(FailReorderPdfEditor(), rec)

        vm.reorderPages()
        vm.editLoading.first { !it }

        assertNotNull(vm.error.value)
        assertFalse(vm.success.value)
        assertFalse(vm.editLoading.value)
    }

    @Test
    fun `clearError loescht Fehlerzustand`() = runTest(testDispatcher) {
        val file = pdfFile()
        val rec = record(file)
        val vm = buildVm(FailReorderPdfEditor(), rec)

        vm.reorderPages()
        vm.editLoading.first { !it }
        assertNotNull(vm.error.value)

        vm.clearError()

        assertNull(vm.error.value)
    }
}

// ── Fake DAO ──────────────────────────────────────────────────────────────────

private class TestScanDao(
    private val initialRecords: List<ScanRecord> = emptyList()
) : ScanDao {
    val inserted = mutableListOf<ScanRecord>()
    override fun getAllScans(): Flow<List<ScanRecord>> = flowOf(initialRecords)
    override suspend fun insert(record: ScanRecord) { inserted.add(record) }
    override suspend fun insertAll(records: List<ScanRecord>) { inserted.addAll(records) }
    override suspend fun delete(record: ScanRecord) {}
    override fun searchScansFlow(query: String): Flow<List<ScanRecord>> = flowOf(emptyList())
    override suspend fun markSearchableWithContent(id: Long, fileSize: Long, text: String?, tags: String?) {}
    override suspend fun markSearchable(id: Long, fileSize: Long) {}
    override suspend fun updateFileSize(id: Long, fileSize: Long) {}
    override suspend fun updatePageMetrics(id: Long, pageCount: Int, fileSize: Long) {}
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
