package info.meuse24.pdf_scanner.ui.documentaction

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import info.meuse24.pdf_scanner.data.local.ScanDao
import info.meuse24.pdf_scanner.data.local.ScanRecord
import info.meuse24.pdf_scanner.data.repository.ScanRepository
import info.meuse24.pdf_scanner.domain.usecase.AddPageNumbersUseCase
import info.meuse24.pdf_scanner.domain.workflow.CompressPdfWorkflow
import info.meuse24.pdf_scanner.domain.workflow.PageNumbersWorkflow
import info.meuse24.pdf_scanner.domain.workflow.ProtectPdfWorkflow
import info.meuse24.pdf_scanner.domain.workflow.RemovePasswordWorkflow
import info.meuse24.pdf_scanner.domain.workflow.RemoveTextLayerWorkflow
import info.meuse24.pdf_scanner.domain.workflow.RestrictUsageWorkflow
import info.meuse24.pdf_scanner.domain.workflow.SignatureStampWorkflow
import info.meuse24.pdf_scanner.domain.workflow.TextWatermarkWorkflow
import info.meuse24.pdf_scanner.domain.workflow.UnlockPdfWorkflow
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
class DocumentEditViewModelTest {

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

    private fun record(file: File): ScanRecord = ScanRecord(
        id = 1L,
        filename = file.nameWithoutExtension,
        filepath = file.absolutePath,
        timestamp = 0L,
        pageCount = 1,
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
    ): DocumentEditViewModel {
        val dao = TestScanDao(listOf(scanRecord))
        val repository = ScanRepository(dao)
        val addPageNumbersUseCase = AddPageNumbersUseCase(pdfEditor, repository)
        val pageNumbersWorkflow = PageNumbersWorkflow(addPageNumbersUseCase)

        // The other workflows are never invoked in these tests — mock them.
        val textWatermarkWorkflow    = mock(TextWatermarkWorkflow::class.java)
        val compressPdfWorkflow      = mock(CompressPdfWorkflow::class.java)
        val protectPdfWorkflow       = mock(ProtectPdfWorkflow::class.java)
        val unlockPdfWorkflow        = mock(UnlockPdfWorkflow::class.java)
        val signatureStampWorkflow   = mock(SignatureStampWorkflow::class.java)
        val removeTextLayerWorkflow  = mock(RemoveTextLayerWorkflow::class.java)
        val removePasswordWorkflow   = mock(RemovePasswordWorkflow::class.java)
        val restrictUsageWorkflow    = mock(RestrictUsageWorkflow::class.java)

        val context = mock(Context::class.java)
        `when`(context.filesDir).thenReturn(tmpFolder.root)
        val savedState = SavedStateHandle(mapOf("scanId" to 1L))

        return DocumentEditViewModel(
            repository = repository,
            pageNumbersWorkflow = pageNumbersWorkflow,
            textWatermarkWorkflow = textWatermarkWorkflow,
            compressPdfWorkflow = compressPdfWorkflow,
            protectPdfWorkflow = protectPdfWorkflow,
            unlockPdfWorkflow = unlockPdfWorkflow,
            signatureStampWorkflow = signatureStampWorkflow,
            removeTextLayerWorkflow = removeTextLayerWorkflow,
            removePasswordWorkflow = removePasswordWorkflow,
            restrictUsageWorkflow = restrictUsageWorkflow,
            errorMapper = errorMapper,
            context = context,
            savedStateHandle = savedState
        )
    }

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    fun `addPageNumbers success setzt success und beendet editLoading`() = runTest(testDispatcher) {
        val file = pdfFile()
        val rec = record(file)
        val vm = buildVm(SuccessPageNumbersPdfEditor(tmpFolder.root), rec)

        vm.addPageNumbers()
        vm.editLoading.first { !it }

        assertTrue(vm.success.value)
        assertFalse(vm.editLoading.value)
        assertNull(vm.error.value)
    }

    @Test
    fun `addPageNumbers failure setzt error und beendet editLoading`() = runTest(testDispatcher) {
        val file = pdfFile()
        val rec = record(file)
        val vm = buildVm(FailPageNumbersPdfEditor(), rec)

        vm.addPageNumbers()
        vm.editLoading.first { !it }

        assertNotNull(vm.error.value)
        assertFalse(vm.success.value)
        assertFalse(vm.editLoading.value)
    }

    @Test
    fun `editLoading ist true direkt nach addPageNumbers-Aufruf`() = runTest(testDispatcher) {
        val file = pdfFile()
        val rec = record(file)
        val vm = buildVm(NoOpPageNumbersPdfEditor(), rec)

        vm.addPageNumbers()

        assertTrue(vm.editLoading.value)
    }

    @Test
    fun `clearError loescht Fehlerzustand`() = runTest(testDispatcher) {
        val file = pdfFile()
        val rec = record(file)
        val vm = buildVm(FailPageNumbersPdfEditor(), rec)

        vm.addPageNumbers()
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

/** Does nothing — only used to keep editLoading == true while the coroutine is "running". */
private class NoOpPageNumbersPdfEditor : PdfEditor() {
    override fun addPageNumbers(input: File, outputDir: File): File = input
    override fun renderPageThumbnail(pdfFile: File, pageIndex: Int, maxSizePx: Int) = null
}

/**
 * addPageNumbers writes a new file and returns it;
 * generateThumbnail writes text; renderPageThumbnail returns null.
 */
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

/** addPageNumbers throws IOException to simulate a disk-full error. */
private class FailPageNumbersPdfEditor : PdfEditor() {
    override fun addPageNumbers(input: File, outputDir: File): File =
        throw IOException("disk full")

    override fun renderPageThumbnail(pdfFile: File, pageIndex: Int, maxSizePx: Int) = null
}
