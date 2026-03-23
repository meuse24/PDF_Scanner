package info.meuse24.pdf_scanner.domain.workflow

import info.meuse24.pdf_scanner.data.local.ScanRecord
import info.meuse24.pdf_scanner.data.repository.ScanRepository
import info.meuse24.pdf_scanner.domain.usecase.DeletePdfPagesUseCase
import info.meuse24.pdf_scanner.domain.usecase.FakeScanDao
import info.meuse24.pdf_scanner.util.PdfEditor
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

class DeletePagesWorkflowTest {

    @get:Rule val tmpFolder = TemporaryFolder()

    private fun record(
        id: Long,
        pageCount: Int = 4,
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

    private fun workflow(pdfEditor: PdfEditor): Pair<DeletePagesWorkflow, FakeScanDao> {
        val dao = FakeScanDao()
        val repository = ScanRepository(dao)
        val useCase = DeletePdfPagesUseCase(pdfEditor, repository)
        return DeletePagesWorkflow(useCase) to dao
    }

    @Test
    fun `loeschen aller Seiten liefert CannotDeleteAllPages`() = runTest {
        val (workflow, _) = workflow(FakeDeletePdfEditor())

        val result = workflow(
            record = record(1L),
            pageIndexes = listOf(0, 1, 2, 3),
            saveAsCopy = false,
            scansDir = tmpFolder.root
        )

        assertTrue(result is WorkflowResult.Failure)
        assertEquals(
            ScanWorkflowError.CannotDeleteAllPages,
            (result as WorkflowResult.Failure).error
        )
    }

    @Test
    fun `fehlende Datei liefert MissingFiles`() = runTest {
        val (workflow, _) = workflow(FakeDeletePdfEditor())

        val result = workflow(
            record = record(2L, exists = false),
            pageIndexes = listOf(0),
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
            FakeDeletePdfEditor(onDelete = { _, _, _ -> throw IOException("locked") })
        )

        val result = workflow(
            record = record(3L),
            pageIndexes = listOf(1),
            saveAsCopy = true,
            scansDir = tmpFolder.root
        )

        assertTrue(result is WorkflowResult.Failure)
        assertTrue((result as WorkflowResult.Failure).error is ScanWorkflowError.StorageWriteFailed)
    }

    @Test
    fun `overwrite aktualisiert Seitenmetadaten`() = runTest {
        val (workflow, dao) = workflow(FakeDeletePdfEditor())

        val result = workflow(
            record = record(4L),
            pageIndexes = listOf(1),
            saveAsCopy = false,
            scansDir = tmpFolder.root
        )

        assertTrue(result is WorkflowResult.Success)
        assertEquals(1, dao.pageMetricUpdates.size)
        assertEquals(3, (result as WorkflowResult.Success).value.remainingPages)
    }
}

private class FakeDeletePdfEditor(
    private val onDelete: (File, List<Int>, Boolean) -> File = { input, _, saveAsCopy ->
        if (saveAsCopy) {
            File(input.parentFile, "${input.nameWithoutExtension}_Gekuerzt.pdf").apply {
                writeText("copy")
            }
        } else {
            input.apply { writeText("updated") }
        }
    }
) : PdfEditor() {
    override fun deletePages(input: File, pageIndexes: List<Int>, saveAsCopy: Boolean): File {
        return onDelete(input, pageIndexes, saveAsCopy)
    }

    override fun getPageCount(pdfFile: File): Int = 3

    override fun generateThumbnail(pdfFile: File, outputFile: File): Boolean {
        outputFile.writeText("thumb")
        return true
    }
}
