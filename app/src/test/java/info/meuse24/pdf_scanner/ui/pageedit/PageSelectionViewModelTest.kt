package info.meuse24.pdf_scanner.ui.pageedit

import androidx.lifecycle.SavedStateHandle
import info.meuse24.pdf_scanner.data.local.ScanDao
import info.meuse24.pdf_scanner.data.local.ScanRecord
import info.meuse24.pdf_scanner.data.repository.ScanRepository
import info.meuse24.pdf_scanner.domain.usecase.RotatePagesUseCase
import info.meuse24.pdf_scanner.domain.workflow.DeletePagesWorkflow
import info.meuse24.pdf_scanner.domain.workflow.DuplicatePagesWorkflow
import info.meuse24.pdf_scanner.domain.workflow.ExtractPagesWorkflow
import info.meuse24.pdf_scanner.domain.workflow.RotatePagesWorkflow
import info.meuse24.pdf_scanner.domain.workflow.WorkflowErrorMapper
import info.meuse24.pdf_scanner.testutil.FakeResourceProvider
import info.meuse24.pdf_scanner.testutil.TestDispatcherProvider
import info.meuse24.pdf_scanner.util.StorageProvider
import info.meuse24.pdf_scanner.util.PdfEditor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Mockito.mock
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class PageSelectionViewModelTest {

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

    @Test
    fun `rotatePages uses storage provider scans dir`() = runTest(testDispatcher) {
        val scansDir = File(tmpFolder.root, "customScans").apply { mkdirs() }
        val pdfFile = tmpFolder.newFile("scan_1.pdf").apply { writeText("pdf") }
        val record = ScanRecord(
            id = 1L,
            filename = "scan_1",
            filepath = pdfFile.absolutePath,
            timestamp = 0L,
            pageCount = 2,
            fileSize = pdfFile.length()
        )
        val dao = TestScanDao(listOf(record))
        val repository = ScanRepository(dao)
        val pdfEditor = RotatingPageSelectionPdfEditor(scansDir)
        val workflow = RotatePagesWorkflow(RotatePagesUseCase(pdfEditor, repository))

        val viewModel = PageSelectionViewModel(
            repository = repository,
            pdfEditor = pdfEditor,
            rotatePagesWorkflow = workflow,
            deletePagesWorkflow = mock(DeletePagesWorkflow::class.java),
            extractPagesWorkflow = mock(ExtractPagesWorkflow::class.java),
            duplicatePagesWorkflow = mock(DuplicatePagesWorkflow::class.java),
            errorMapper = WorkflowErrorMapper(FakeResourceProvider(fallback = "err")),
            storageProvider = object : StorageProvider {
                override fun scansDir(): File = scansDir
                override fun tempDir(): File = File(tmpFolder.root, "temp").apply { mkdirs() }
            },
            dispatcherProvider = TestDispatcherProvider(testDispatcher),
            savedStateHandle = SavedStateHandle(mapOf("scanId" to 1L))
        )

        viewModel.togglePage(0)
        viewModel.rotatePages(90)

        assertEquals(1, dao.inserted.size)
        assertEquals(listOf(0), pdfEditor.rotatedPages)
        assertEquals(90, pdfEditor.rotationDegrees)
        assertEquals(File(scansDir, "scan_1_rotated.jpg").absolutePath, dao.inserted.single().thumbnailPath)
        assertTrue(viewModel.success.value)
    }
}

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
    override suspend fun delete(record: ScanRecord) = Unit
    override fun searchScansFlow(query: String): Flow<List<ScanRecord>> = flowOf(emptyList())
    override suspend fun markSearchableWithContent(id: Long, fileSize: Long, text: String?, tags: String?) = Unit
    override suspend fun markSearchable(id: Long, fileSize: Long) = Unit
    override suspend fun updateFileSize(id: Long, fileSize: Long) = Unit
    override suspend fun updatePageMetrics(id: Long, pageCount: Int, fileSize: Long) = Unit
    override suspend fun invalidateAfterAppend(id: Long, fileSize: Long, pageCount: Int) = Unit
    override suspend fun updateFilenameAndPath(id: Long, filename: String, filepath: String, thumbnailPath: String?) = Unit
}

private class RotatingPageSelectionPdfEditor(
    private val outputDir: File
) : PdfEditor() {
    var rotatedPages: List<Int> = emptyList()
    var rotationDegrees: Int? = null

    override fun renderPageThumbnail(pdfFile: File, pageIndex: Int, maxSizePx: Int) = null

    override fun rotatePages(
        input: File,
        pageIndexes: List<Int>,
        rotationDegrees: Int,
        saveAsCopy: Boolean
    ): File {
        rotatedPages = pageIndexes
        this.rotationDegrees = rotationDegrees
        return File(outputDir, "${input.nameWithoutExtension}_rotated.pdf").apply { writeText("rotated") }
    }

    override fun generateThumbnail(pdfFile: File, outputFile: File): Boolean {
        outputFile.writeText("thumb")
        return true
    }
}
