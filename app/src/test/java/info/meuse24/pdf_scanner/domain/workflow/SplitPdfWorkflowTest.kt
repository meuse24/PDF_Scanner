package info.meuse24.pdf_scanner.domain.workflow

import info.meuse24.pdf_scanner.domain.model.Document
import info.meuse24.pdf_scanner.data.repository.ScanRepository
import info.meuse24.pdf_scanner.domain.usecase.FakeScanDao
import info.meuse24.pdf_scanner.domain.usecase.SplitPdfUseCase
import info.meuse24.pdf_scanner.util.PdfEditor
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

class SplitPdfWorkflowTest {

    @get:Rule val tmpFolder = TemporaryFolder()

    private fun record(
        id: Long,
        pageCount: Int = 4,
        exists: Boolean = true
    ): Document {
        val file = if (exists) {
            tmpFolder.newFile("scan_$id.pdf").apply { writeText("pdf") }
        } else {
            File(tmpFolder.root, "missing_$id.pdf")
        }
        return Document(
            id = id,
            filename = "scan_$id",
            filepath = file.absolutePath,
            timestamp = 0L,
            pageCount = pageCount,
            fileSize = file.length()
        )
    }

    private fun workflow(pdfEditor: PdfEditor): Pair<SplitPdfWorkflow, FakeScanDao> {
        val dao = FakeScanDao()
        val repository = ScanRepository(dao)
        val useCase = SplitPdfUseCase(pdfEditor, info.meuse24.pdf_scanner.domain.service.ScanArtifactPersister(pdfEditor, repository))
        return SplitPdfWorkflow(useCase, DocumentWorkflowGuard(pdfEditor)) to dao
    }

    @Test
    fun `ungueltige Split-Punkte liefern InvalidSplitSelection`() = runTest {
        val (workflow, _) = workflow(FakeSplitPdfEditor())

        val result = workflow(
            record = record(1L),
            splitAtPages = emptyList(),
            scansDir = tmpFolder.root
        )

        assertTrue(result is WorkflowResult.Failure)
        assertEquals(
            ScanWorkflowError.InvalidSplitSelection,
            (result as WorkflowResult.Failure).error
        )
    }

    @Test
    fun `fehlende Datei liefert MissingFiles`() = runTest {
        val (workflow, _) = workflow(FakeSplitPdfEditor())

        val result = workflow(
            record = record(2L, exists = false),
            splitAtPages = listOf(1),
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
            FakeSplitPdfEditor(onSplit = { _, _, _ -> throw IOException("locked") })
        )

        val result = workflow(
            record = record(3L),
            splitAtPages = listOf(1),
            scansDir = tmpFolder.root
        )

        assertTrue(result is WorkflowResult.Failure)
        assertTrue((result as WorkflowResult.Failure).error is ScanWorkflowError.StorageWriteFailed)
    }

    @Test
    fun `erfolgreicher Ablauf liefert Part-Anzahl und speichert neue Records`() = runTest {
        val (workflow, dao) = workflow(FakeSplitPdfEditor())

        val result = workflow(
            record = record(4L),
            splitAtPages = listOf(0, 2),
            scansDir = tmpFolder.root
        )

        assertTrue(result is WorkflowResult.Success)
        assertEquals(3, (result as WorkflowResult.Success).value.partsCount)
        assertEquals(3, dao.inserted.size)
    }
}

private class FakeSplitPdfEditor(
    private val onSplit: (File, File, List<Int>) -> List<File> = { input, outputDir, splitAtPages ->
        List(splitAtPages.size + 1) { index ->
            File(outputDir, "${input.nameWithoutExtension}_part_${index + 1}.pdf").apply {
                writeText("part")
            }
        }
    }
) : PdfEditor() {
    override fun splitPdf(input: File, outputDir: File, splitAtPages: List<Int>): List<File> {
        return onSplit(input, outputDir, splitAtPages)
    }

    override fun generateThumbnail(pdfFile: File, outputFile: File): Boolean {
        outputFile.writeText("thumb")
        return true
    }

    override fun getPageCount(pdfFile: File): Int = 1
}


