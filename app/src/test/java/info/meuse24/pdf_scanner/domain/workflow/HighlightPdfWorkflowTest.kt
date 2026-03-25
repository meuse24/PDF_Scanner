package info.meuse24.pdf_scanner.domain.workflow

import info.meuse24.pdf_scanner.data.local.ScanRecord
import info.meuse24.pdf_scanner.data.repository.ScanRepository
import info.meuse24.pdf_scanner.domain.usecase.FakeScanDao
import info.meuse24.pdf_scanner.domain.usecase.HighlightPdfUseCase
import info.meuse24.pdf_scanner.domain.usecase.HighlightStroke
import info.meuse24.pdf_scanner.util.PdfEditor
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

class HighlightPdfWorkflowTest {

    @get:Rule val tmpFolder = TemporaryFolder()

    private fun record(id: Long, exists: Boolean = true): ScanRecord {
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
            pageCount = 3,
            fileSize = file.length(),
            isSearchable = false
        )
    }

    private fun stroke(page: Int = 0) = HighlightStroke(
        points = listOf(Pair(0.1f, 0.5f), Pair(0.5f, 0.5f)),
        pageIndex = page
    )

    private fun workflow(pdfEditor: PdfEditor): Pair<HighlightPdfWorkflow, FakeScanDao> {
        val dao = FakeScanDao()
        val repository = ScanRepository(dao)
        val useCase = HighlightPdfUseCase(pdfEditor, repository)
        return HighlightPdfWorkflow(useCase, pdfEditor) to dao
    }

    @Test
    fun `leere Strokes liefern NoHighlightStrokes`() = runTest {
        val (workflow, _) = workflow(FakeHighlightPdfEditor())

        val result = workflow(record(1L), emptyList(), tmpFolder.root)

        assertTrue(result is WorkflowResult.Failure)
        assertEquals(
            ScanWorkflowError.NoHighlightStrokes,
            (result as WorkflowResult.Failure).error
        )
    }

    @Test
    fun `fehlende Datei liefert MissingFiles`() = runTest {
        val (workflow, _) = workflow(FakeHighlightPdfEditor())

        val result = workflow(record(2L, exists = false), listOf(stroke()), tmpFolder.root)

        assertTrue(result is WorkflowResult.Failure)
        assertEquals(
            ScanWorkflowError.MissingFiles(listOf("scan_2")),
            (result as WorkflowResult.Failure).error
        )
    }

    @Test
    fun `geschuetzte pdf liefert ProtectedPdfUnsupported`() = runTest {
        val (workflow, _) = workflow(FakeHighlightPdfEditor(encrypted = true))

        val result = workflow(record(3L), listOf(stroke()), tmpFolder.root)

        assertTrue(result is WorkflowResult.Failure)
        assertEquals(
            ScanWorkflowError.ProtectedPdfUnsupported,
            (result as WorkflowResult.Failure).error
        )
    }

    @Test
    fun `io Fehler werden als StorageWriteFailed gemappt`() = runTest {
        val (workflow, _) = workflow(
            FakeHighlightPdfEditor(onApply = { _, _, _ -> throw IOException("disk full") })
        )

        val result = workflow(record(4L), listOf(stroke()), tmpFolder.root)

        assertTrue(result is WorkflowResult.Failure)
        assertTrue((result as WorkflowResult.Failure).error is ScanWorkflowError.StorageWriteFailed)
    }

    @Test
    fun `unbekannter Fehler wird als HighlightFailed gemappt`() = runTest {
        val (workflow, _) = workflow(
            FakeHighlightPdfEditor(onApply = { _, _, _ -> throw RuntimeException("unexpected") })
        )

        val result = workflow(record(5L), listOf(stroke()), tmpFolder.root)

        assertTrue(result is WorkflowResult.Failure)
        assertTrue((result as WorkflowResult.Failure).error is ScanWorkflowError.HighlightFailed)
    }

    @Test
    fun `erfolgreicher Ablauf speichert neuen Record`() = runTest {
        val (workflow, dao) = workflow(FakeHighlightPdfEditor())

        val result = workflow(record(6L), listOf(stroke(0), stroke(1)), tmpFolder.root)

        assertTrue(result is WorkflowResult.Success)
        assertEquals(1, dao.inserted.size)
    }
}

private class FakeHighlightPdfEditor(
    private val encrypted: Boolean = false,
    private val onApply: (File, File, List<HighlightStroke>) -> File = { input, outputDir, _ ->
        File(outputDir, "${input.nameWithoutExtension}_Markiert.pdf").apply { writeText("copy") }
    }
) : PdfEditor() {
    override fun isPdfEncrypted(input: File): Boolean = encrypted

    override fun applyHighlight(
        input: File,
        outputDir: File,
        strokes: List<HighlightStroke>
    ): File = onApply(input, outputDir, strokes)

    override fun generateThumbnail(pdfFile: File, outputFile: File): Boolean {
        outputFile.writeText("thumb")
        return true
    }
}
