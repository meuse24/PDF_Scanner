package info.meuse24.pdf_scanner.domain.workflow

import info.meuse24.pdf_scanner.data.local.ScanRecord
import info.meuse24.pdf_scanner.data.repository.ScanRepository
import info.meuse24.pdf_scanner.domain.usecase.ExtractPagesUseCase
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

class ExtractPagesWorkflowTest {

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
            fileSize = file.length()
        )
    }

    private fun workflow(pdfEditor: PdfEditor): Pair<ExtractPagesWorkflow, FakeScanDao> {
        val dao = FakeScanDao()
        val repository = ScanRepository(dao)
        val useCase = ExtractPagesUseCase(pdfEditor, repository)
        return ExtractPagesWorkflow(useCase) to dao
    }

    @Test
    fun `ungueltige Seitenauswahl liefert InvalidPageSelection`() = runTest {
        val (workflow, _) = workflow(FakeExtractPdfEditor())

        val result = workflow(
            record = record(1L),
            pageIndexes = listOf(8),
            scansDir = tmpFolder.root
        )

        assertTrue(result is WorkflowResult.Failure)
        assertEquals(
            ScanWorkflowError.InvalidPageSelection,
            (result as WorkflowResult.Failure).error
        )
    }

    @Test
    fun `fehlende Datei liefert MissingFiles`() = runTest {
        val (workflow, _) = workflow(FakeExtractPdfEditor())

        val result = workflow(
            record = record(2L, exists = false),
            pageIndexes = listOf(0),
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
            FakeExtractPdfEditor(onExtract = { _, _, _ -> throw IOException("locked") })
        )

        val result = workflow(
            record = record(3L),
            pageIndexes = listOf(0, 2),
            scansDir = tmpFolder.root
        )

        assertTrue(result is WorkflowResult.Failure)
        assertTrue((result as WorkflowResult.Failure).error is ScanWorkflowError.StorageWriteFailed)
    }

    @Test
    fun `erfolgreicher Ablauf liefert Dateinamen und speichert neuen Record`() = runTest {
        val (workflow, dao) = workflow(FakeExtractPdfEditor())

        val result = workflow(
            record = record(4L),
            pageIndexes = listOf(1, 2),
            scansDir = tmpFolder.root
        )

        assertTrue(result is WorkflowResult.Success)
        assertEquals("scan_4_Extrahiert", (result as WorkflowResult.Success).value.outputFilename)
        assertEquals(1, dao.inserted.size)
    }
}

private class FakeExtractPdfEditor(
    private val onExtract: (File, File, List<Int>) -> File = { input, outputDir, _ ->
        File(outputDir, "${input.nameWithoutExtension}_Extrahiert.pdf").apply { writeText("extract") }
    }
) : PdfEditor() {
    override fun extractPages(input: File, outputDir: File, pageIndexes: List<Int>): File {
        return onExtract(input, outputDir, pageIndexes)
    }

    override fun getPageCount(pdfFile: File): Int = 2

    override fun generateThumbnail(pdfFile: File, outputFile: File): Boolean {
        outputFile.writeText("thumb")
        return true
    }
}
