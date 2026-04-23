package info.meuse24.pdf_scanner.ui.split

import androidx.lifecycle.SavedStateHandle
import info.meuse24.pdf_scanner.data.local.ScanDao
import info.meuse24.pdf_scanner.data.local.ScanRecord
import info.meuse24.pdf_scanner.data.repository.ScanRepository
import info.meuse24.pdf_scanner.domain.usecase.SplitPdfUseCase
import info.meuse24.pdf_scanner.domain.workflow.SplitPdfWorkflow
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
class SplitViewModelTest {

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

    private fun pdfFile(name: String = "scan_1.pdf"): File =
        tmpFolder.newFile(name).apply { writeText("pdf") }

    private fun record(file: File, pageCount: Int = 4): ScanRecord = ScanRecord(
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
        scanRecord: ScanRecord,
        errorMapper: WorkflowErrorMapper = stubMapper()
    ): SplitViewModel {
        val dao = TestScanDao(listOf(scanRecord))
        val repository = ScanRepository(dao)
        val useCase = SplitPdfUseCase(pdfEditor, repository)
        val workflow = SplitPdfWorkflow(useCase)
        val savedState = SavedStateHandle(mapOf("scanId" to 1L))
        return SplitViewModel(
            repository = repository,
            pdfEditor = pdfEditor,
            splitPdfWorkflow = workflow,
            errorMapper = errorMapper,
            storageProvider = TestStorageProvider(tmpFolder.root),
            dispatcherProvider = TestDispatcherProvider(testDispatcher),
            savedStateHandle = savedState
        )
    }

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    fun `editLoading ist true direkt nach splitPdf-Aufruf`() = runTest(testDispatcher) {
        val file = pdfFile()
        val rec = record(file)
        val vm = buildVm(NoOpPdfEditor(), rec)
        advanceUntilIdle()

        vm.splitPdf(listOf(1))

        assertTrue(vm.editLoading.value)
    }

    @Test
    fun `splitPdf success setzt success und beendet editLoading`() = runTest(testDispatcher) {
        val file = pdfFile()
        val rec = record(file)
        val vm = buildVm(SuccessSplitPdfEditor(tmpFolder.root), rec)
        advanceUntilIdle()

        vm.splitPdf(listOf(1))
        advanceUntilIdle()

        assertTrue(vm.success.value)
        assertFalse(vm.editLoading.value)
        assertNull(vm.error.value)
    }

    @Test
    fun `splitPdf failure setzt error und beendet editLoading`() = runTest(testDispatcher) {
        val file = pdfFile()
        val rec = record(file)
        val vm = buildVm(FailSplitPdfEditor(), rec)
        advanceUntilIdle()

        vm.splitPdf(listOf(1))
        advanceUntilIdle()

        assertNotNull(vm.error.value)
        assertFalse(vm.success.value)
        assertFalse(vm.editLoading.value)
    }

    @Test
    fun `zweiter Aufruf waehrend editLoading wird ignoriert`() = runTest(testDispatcher) {
        val file = pdfFile()
        val rec = record(file)
        val dao = TestScanDao(listOf(rec))
        val repository = ScanRepository(dao)
        val useCase = SplitPdfUseCase(SuccessSplitPdfEditor(tmpFolder.root), repository)
        val workflow = SplitPdfWorkflow(useCase)
        val savedState = SavedStateHandle(mapOf("scanId" to 1L))
        val vm = SplitViewModel(
            repository = repository,
            pdfEditor = SuccessSplitPdfEditor(tmpFolder.root),
            splitPdfWorkflow = workflow,
            errorMapper = stubMapper(),
            storageProvider = TestStorageProvider(tmpFolder.root),
            dispatcherProvider = TestDispatcherProvider(testDispatcher),
            savedStateHandle = savedState
        )
        advanceUntilIdle()

        vm.splitPdf(listOf(1))
        // second call should be ignored because editLoading is already true
        vm.splitPdf(listOf(1))
        advanceUntilIdle()

        assertTrue(vm.success.value)
    }

    @Test
    fun `clearError loescht Fehlerzustand`() = runTest(testDispatcher) {
        val file = pdfFile()
        val rec = record(file)
        val vm = buildVm(FailSplitPdfEditor(), rec)
        advanceUntilIdle()

        vm.splitPdf(listOf(1))
        advanceUntilIdle()
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
    override suspend fun insert(record: ScanRecord): Long {
        inserted.add(record)
        return inserted.size.toLong()
    }
    override suspend fun insertAll(records: List<ScanRecord>) { inserted.addAll(records) }
    override suspend fun delete(record: ScanRecord) {}
    override fun searchScansFlow(query: String): Flow<List<ScanRecord>> = flowOf(emptyList())
    override suspend fun markSearchableWithContent(id: Long, fileSize: Long, text: String?, tags: String?) {}
    override suspend fun markSearchable(id: Long, fileSize: Long) {}
    override suspend fun updateFileSize(id: Long, fileSize: Long) {}
    override suspend fun updatePageMetrics(id: Long, pageCount: Int, fileSize: Long) {}
    override suspend fun invalidateAfterAppend(id: Long, fileSize: Long, pageCount: Int) {}
    override suspend fun updateFilenameAndPath(id: Long, filename: String, filepath: String, thumbnailPath: String?) {}
}

// ── Fake PdfEditors ───────────────────────────────────────────────────────────

/** Does nothing — renderPageThumbnail returns null. */
private class NoOpPdfEditor : PdfEditor() {
    override fun renderPageThumbnail(pdfFile: File, pageIndex: Int, maxSizePx: Int) = null
    override fun splitPdf(input: File, outputDir: File, splitAtPages: List<Int>): List<File> =
        emptyList()
}

/** splitPdf creates real files in tmpFolder; generateThumbnail writes text; getPageCount = 1. */
private class SuccessSplitPdfEditor(private val outputDir: File) : PdfEditor() {
    override fun splitPdf(input: File, outputDir: File, splitAtPages: List<Int>): List<File> =
        List(splitAtPages.size + 1) { index ->
            File(outputDir, "${input.nameWithoutExtension}_part_${index + 1}.pdf")
                .apply { writeText("part") }
        }

    override fun generateThumbnail(pdfFile: File, outputFile: File): Boolean {
        outputFile.writeText("thumb")
        return true
    }

    override fun getPageCount(pdfFile: File): Int = 1

    override fun renderPageThumbnail(pdfFile: File, pageIndex: Int, maxSizePx: Int) = null
}

/** splitPdf throws IOException to simulate a disk-full error. */
private class FailSplitPdfEditor : PdfEditor() {
    override fun splitPdf(input: File, outputDir: File, splitAtPages: List<Int>): List<File> =
        throw IOException("disk full")

    override fun renderPageThumbnail(pdfFile: File, pageIndex: Int, maxSizePx: Int) = null
}
