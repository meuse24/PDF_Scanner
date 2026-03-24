package info.meuse24.pdf_scanner.ui.split

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import info.meuse24.pdf_scanner.data.local.ScanDao
import info.meuse24.pdf_scanner.data.local.ScanRecord
import info.meuse24.pdf_scanner.data.repository.ScanRepository
import info.meuse24.pdf_scanner.domain.usecase.SplitPdfUseCase
import info.meuse24.pdf_scanner.domain.workflow.SplitPdfWorkflow
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
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.io.File
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class SplitViewModelTest {

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
        val ctx = mock(Context::class.java)
        `when`(ctx.getString(org.mockito.ArgumentMatchers.anyInt())).thenReturn(msg)
        return WorkflowErrorMapper(ctx)
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
        val context = mock(Context::class.java)
        `when`(context.filesDir).thenReturn(tmpFolder.root)
        val savedState = SavedStateHandle(mapOf("scanId" to 1L))
        return SplitViewModel(
            repository = repository,
            pdfEditor = pdfEditor,
            splitPdfWorkflow = workflow,
            errorMapper = errorMapper,
            context = context,
            savedStateHandle = savedState
        )
    }

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    fun `editLoading ist true direkt nach splitPdf-Aufruf`() = runTest(testDispatcher) {
        val file = pdfFile()
        val rec = record(file)
        val vm = buildVm(NoOpPdfEditor(), rec)

        vm.splitPdf(listOf(1))

        assertTrue(vm.editLoading.value)
    }

    @Test
    fun `splitPdf success setzt success und beendet editLoading`() = runTest(testDispatcher) {
        val file = pdfFile()
        val rec = record(file)
        val vm = buildVm(SuccessSplitPdfEditor(tmpFolder.root), rec)

        vm.splitPdf(listOf(1))
        vm.editLoading.first { !it }

        assertTrue(vm.success.value)
        assertFalse(vm.editLoading.value)
        assertNull(vm.error.value)
    }

    @Test
    fun `splitPdf failure setzt error und beendet editLoading`() = runTest(testDispatcher) {
        val file = pdfFile()
        val rec = record(file)
        val vm = buildVm(FailSplitPdfEditor(), rec)

        vm.splitPdf(listOf(1))
        vm.editLoading.first { !it }

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
        val context = mock(Context::class.java)
        `when`(context.filesDir).thenReturn(tmpFolder.root)
        val savedState = SavedStateHandle(mapOf("scanId" to 1L))
        val vm = SplitViewModel(
            repository = repository,
            pdfEditor = SuccessSplitPdfEditor(tmpFolder.root),
            splitPdfWorkflow = workflow,
            errorMapper = stubMapper(),
            context = context,
            savedStateHandle = savedState
        )

        vm.splitPdf(listOf(1))
        // second call should be ignored because editLoading is already true
        vm.splitPdf(listOf(1))
        vm.editLoading.first { !it }

        assertTrue(vm.success.value)
    }

    @Test
    fun `clearError loescht Fehlerzustand`() = runTest(testDispatcher) {
        val file = pdfFile()
        val rec = record(file)
        val vm = buildVm(FailSplitPdfEditor(), rec)

        vm.splitPdf(listOf(1))
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
    override suspend fun markSearchable(id: Long, fileSize: Long) {}
    override suspend fun updateFileSize(id: Long, fileSize: Long) {}
    override suspend fun updatePageMetrics(id: Long, pageCount: Int, fileSize: Long) {}
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
