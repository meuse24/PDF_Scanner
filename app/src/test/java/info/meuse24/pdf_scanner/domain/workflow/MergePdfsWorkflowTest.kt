package info.meuse24.pdf_scanner.domain.workflow

import info.meuse24.pdf_scanner.data.local.ScanRecord
import info.meuse24.pdf_scanner.data.repository.ScanRepository
import info.meuse24.pdf_scanner.domain.usecase.FakeScanDao
import info.meuse24.pdf_scanner.domain.usecase.MergePdfsUseCase
import info.meuse24.pdf_scanner.util.PdfEditor
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

class MergePdfsWorkflowTest {

    @get:Rule val tmpFolder = TemporaryFolder()

    private fun record(id: Long, exists: Boolean = true): ScanRecord {
        val file = if (exists) {
            tmpFolder.newFile("scan_$id.pdf")
        } else {
            File(tmpFolder.root, "missing_$id.pdf")
        }
        return ScanRecord(
            id = id,
            filename = "scan_$id",
            filepath = file.absolutePath,
            timestamp = 0L,
            pageCount = 1,
            fileSize = 0L
        )
    }

    private fun workflow(pdfEditor: PdfEditor): MergePdfsWorkflow {
        val repository = ScanRepository(FakeScanDao())
        val useCase = MergePdfsUseCase(pdfEditor, repository)
        return MergePdfsWorkflow(useCase)
    }

    @Test
    fun `weniger als zwei Scans liefert NotEnoughScans`() = runTest {
        val result = workflow(FakePdfEditor())(
            records = listOf(record(1L)),
            outputFilename = "merged",
            scansDir = tmpFolder.root
        )

        assertTrue(result is WorkflowResult.Failure)
        assertEquals(ScanWorkflowError.NotEnoughScans, (result as WorkflowResult.Failure).error)
    }

    @Test
    fun `fehlende Dateien liefern MissingFiles`() = runTest {
        val result = workflow(FakePdfEditor())(
            records = listOf(record(1L), record(2L, exists = false)),
            outputFilename = "merged",
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
        val result = workflow(FakePdfEditor(onMerge = { _, _ -> throw IOException("locked") }))(
            records = listOf(record(1L), record(2L)),
            outputFilename = "merged",
            scansDir = tmpFolder.root
        )

        assertTrue(result is WorkflowResult.Failure)
        assertTrue((result as WorkflowResult.Failure).error is ScanWorkflowError.StorageWriteFailed)
    }

    @Test
    fun `erfolgreicher Ablauf liefert Ausgabename`() = runTest {
        val result = workflow(FakePdfEditor())(
            records = listOf(record(1L), record(2L)),
            outputFilename = "merged",
            scansDir = tmpFolder.root
        )

        assertTrue(result is WorkflowResult.Success)
        assertEquals("merged", (result as WorkflowResult.Success).value.outputFilename)
    }
}

private class FakePdfEditor(
    private val onMerge: (List<File>, File) -> Unit = { _, output ->
        output.writeText("pdf")
    }
) : PdfEditor() {
    override fun mergePdfs(inputs: List<File>, output: File) {
        onMerge(inputs, output)
    }

    override fun generateThumbnail(pdfFile: File, outputFile: File): Boolean {
        outputFile.writeText("thumb")
        return true
    }
}
