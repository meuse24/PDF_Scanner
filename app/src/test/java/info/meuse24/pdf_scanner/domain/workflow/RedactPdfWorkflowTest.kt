package info.meuse24.pdf_scanner.domain.workflow

import info.meuse24.pdf_scanner.data.local.ScanRecord
import info.meuse24.pdf_scanner.data.repository.ScanRepository
import info.meuse24.pdf_scanner.domain.usecase.FakeScanDao
import info.meuse24.pdf_scanner.domain.usecase.RedactPdfUseCase
import info.meuse24.pdf_scanner.domain.usecase.RedactionRect
import info.meuse24.pdf_scanner.util.PdfEditor
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

class RedactPdfWorkflowTest {

    @get:Rule val tmpFolder = TemporaryFolder()

    private fun record(
        id: Long,
        exists: Boolean = true,
        isSearchable: Boolean = true,
        extractedText: String? = "secret text",
        tags: String? = "invoice,bank"
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
            pageCount = 2,
            fileSize = file.length(),
            isSearchable = isSearchable,
            extractedText = extractedText,
            tags = tags
        )
    }

    private fun rect(page: Int = 0) = RedactionRect(
        left = 0.10f,
        top = 0.20f,
        right = 0.60f,
        bottom = 0.35f,
        pageIndex = page
    )

    private fun workflow(pdfEditor: PdfEditor): Pair<RedactPdfWorkflow, FakeScanDao> {
        val dao = FakeScanDao()
        val repository = ScanRepository(dao)
        val useCase = RedactPdfUseCase(pdfEditor, repository)
        return RedactPdfWorkflow(useCase, pdfEditor) to dao
    }

    @Test
    fun `leere Bereiche liefern NoRedactionAreas`() = runTest {
        val (workflow, _) = workflow(FakeRedactionPdfEditor())

        val result = workflow(record(1L), emptyList(), tmpFolder.root)

        assertTrue(result is WorkflowResult.Failure)
        assertEquals(
            ScanWorkflowError.NoRedactionAreas,
            (result as WorkflowResult.Failure).error
        )
    }

    @Test
    fun `fehlende Datei liefert MissingFiles`() = runTest {
        val (workflow, _) = workflow(FakeRedactionPdfEditor())

        val result = workflow(record(2L, exists = false), listOf(rect()), tmpFolder.root)

        assertTrue(result is WorkflowResult.Failure)
        assertEquals(
            ScanWorkflowError.MissingFiles(listOf("scan_2")),
            (result as WorkflowResult.Failure).error
        )
    }

    @Test
    fun `geschuetzte pdf liefert ProtectedPdfUnsupported`() = runTest {
        val (workflow, _) = workflow(FakeRedactionPdfEditor(encrypted = true))

        val result = workflow(record(3L), listOf(rect()), tmpFolder.root)

        assertTrue(result is WorkflowResult.Failure)
        assertEquals(
            ScanWorkflowError.ProtectedPdfUnsupported,
            (result as WorkflowResult.Failure).error
        )
    }

    @Test
    fun `io Fehler werden als StorageWriteFailed gemappt`() = runTest {
        val (workflow, _) = workflow(
            FakeRedactionPdfEditor(onApply = { _, _, _ -> throw IOException("disk full") })
        )

        val result = workflow(record(4L), listOf(rect()), tmpFolder.root)

        assertTrue(result is WorkflowResult.Failure)
        assertTrue((result as WorkflowResult.Failure).error is ScanWorkflowError.StorageWriteFailed)
    }

    @Test
    fun `unbekannter Fehler wird als RedactionFailed gemappt`() = runTest {
        val (workflow, _) = workflow(
            FakeRedactionPdfEditor(onApply = { _, _, _ -> throw RuntimeException("unexpected") })
        )

        val result = workflow(record(5L), listOf(rect()), tmpFolder.root)

        assertTrue(result is WorkflowResult.Failure)
        assertTrue((result as WorkflowResult.Failure).error is ScanWorkflowError.RedactionFailed)
    }

    @Test
    fun `erfolgreicher Ablauf speichert nicht mehr suchbaren Record ohne Text und Tags`() = runTest {
        val (workflow, dao) = workflow(FakeRedactionPdfEditor())

        val result = workflow(record(6L), listOf(rect(0), rect(1)), tmpFolder.root)

        assertTrue(result is WorkflowResult.Success)
        assertEquals(1, dao.inserted.size)
        val saved = dao.inserted.single()
        assertEquals(false, saved.isSearchable)
        assertNull(saved.extractedText)
        assertNull(saved.tags)
    }
}

private class FakeRedactionPdfEditor(
    private val encrypted: Boolean = false,
    private val onApply: (File, File, List<RedactionRect>) -> File = { input, outputDir, _ ->
        File(outputDir, "${input.nameWithoutExtension}_Geschwaerzt.pdf").apply { writeText("copy") }
    }
) : PdfEditor() {
    override fun isPdfEncrypted(input: File): Boolean = encrypted

    override fun applySecureRedaction(
        input: File,
        outputDir: File,
        rects: List<RedactionRect>
    ): File = onApply(input, outputDir, rects)

    override fun generateThumbnail(pdfFile: File, outputFile: File): Boolean {
        outputFile.writeText("thumb")
        return true
    }
}
