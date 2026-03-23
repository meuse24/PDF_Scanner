package info.meuse24.pdf_scanner.domain.workflow

import info.meuse24.pdf_scanner.data.local.ScanRecord
import info.meuse24.pdf_scanner.data.repository.ScanRepository
import info.meuse24.pdf_scanner.domain.usecase.FakeScanDao
import info.meuse24.pdf_scanner.domain.usecase.ReorderPagesUseCase
import info.meuse24.pdf_scanner.util.PdfEditor
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

class ReorderPagesWorkflowTest {

    @get:Rule val tmpFolder = TemporaryFolder()

    private fun record(
        id: Long,
        pageCount: Int = 3,
        exists: Boolean = true
    ): ScanRecord {
        val file = if (exists) {
            tmpFolder.newFile("scan_$id.pdf").apply { writeText("pdf") }
        } else {
            File(tmpFolder.root, "missing_$id.pdf")
        }
        return ScanRecord(
            id = id,
            filename = "scan_$id",
            filepath = file.absolutePath,
            timestamp = 0L,
            pageCount = pageCount,
            fileSize = file.length(),
            isSearchable = true
        )
    }

    private fun workflow(pdfEditor: PdfEditor): Pair<ReorderPagesWorkflow, FakeScanDao> {
        val dao = FakeScanDao()
        val repository = ScanRepository(dao)
        val useCase = ReorderPagesUseCase(pdfEditor, repository)
        return ReorderPagesWorkflow(useCase) to dao
    }

    @Test
    fun `ungueltige Reihenfolge liefert InvalidPageOrder`() = runTest {
        val (workflow, _) = workflow(FakeReorderPdfEditor())

        val result = workflow(
            record = record(1L),
            newOrder = listOf(0, 0, 2),
            saveAsCopy = false,
            scansDir = tmpFolder.root
        )

        assertTrue(result is WorkflowResult.Failure)
        assertEquals(
            ScanWorkflowError.InvalidPageOrder,
            (result as WorkflowResult.Failure).error
        )
    }

    @Test
    fun `fehlende Datei liefert MissingFiles`() = runTest {
        val (workflow, _) = workflow(FakeReorderPdfEditor())

        val result = workflow(
            record = record(2L, exists = false),
            newOrder = listOf(0, 1, 2),
            saveAsCopy = false,
            scansDir = tmpFolder.root
        )

        assertTrue(result is WorkflowResult.Failure)
        assertEquals(
            ScanWorkflowError.MissingFiles(listOf("scan_2")),
            (result as WorkflowResult.Failure).error
        )
    }

    @Test
    fun `io Fehler werden als StorageWriteFailed gemappt`() = runTest {
        val (workflow, _) = workflow(
            FakeReorderPdfEditor(onReorder = { _, _, _ -> throw IOException("locked") })
        )

        val result = workflow(
            record = record(3L),
            newOrder = listOf(2, 1, 0),
            saveAsCopy = true,
            scansDir = tmpFolder.root
        )

        assertTrue(result is WorkflowResult.Failure)
        assertTrue((result as WorkflowResult.Failure).error is ScanWorkflowError.StorageWriteFailed)
    }

    @Test
    fun `erfolgreicher Copy-Ablauf liefert Success und speichert neuen Record`() = runTest {
        val (workflow, dao) = workflow(FakeReorderPdfEditor())

        val result = workflow(
            record = record(4L),
            newOrder = listOf(2, 1, 0),
            saveAsCopy = true,
            scansDir = tmpFolder.root
        )

        assertTrue(result is WorkflowResult.Success)
        assertTrue((result as WorkflowResult.Success).value.savedAsCopy)
        assertEquals(1, dao.inserted.size)
    }
}

private class FakeReorderPdfEditor(
    private val onReorder: (File, List<Int>, Boolean) -> File = { input, _, saveAsCopy ->
        if (saveAsCopy) {
            File(input.parentFile, "${input.nameWithoutExtension}_Sortiert.pdf").apply {
                writeText("copy")
            }
        } else {
            input.apply { writeText("updated") }
        }
    }
) : PdfEditor() {
    override fun reorderPages(input: File, newOrder: List<Int>, saveAsCopy: Boolean): File {
        return onReorder(input, newOrder, saveAsCopy)
    }

    override fun generateThumbnail(pdfFile: File, outputFile: File): Boolean {
        outputFile.writeText("thumb")
        return true
    }
}
