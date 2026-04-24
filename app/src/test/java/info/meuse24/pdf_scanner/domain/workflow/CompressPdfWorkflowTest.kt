package info.meuse24.pdf_scanner.domain.workflow

import info.meuse24.pdf_scanner.domain.model.Document
import info.meuse24.pdf_scanner.data.repository.ScanRepository
import info.meuse24.pdf_scanner.domain.usecase.CompressPdfUseCase
import info.meuse24.pdf_scanner.domain.usecase.FakeScanDao
import info.meuse24.pdf_scanner.domain.usecase.PdfCompressionPreset
import info.meuse24.pdf_scanner.util.PdfEditor
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class CompressPdfWorkflowTest {

    @get:Rule val tmpFolder = TemporaryFolder()

    private fun record(
        id: Long,
        searchable: Boolean = false,
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
            pageCount = 2,
            fileSize = file.length(),
            isSearchable = searchable
        )
    }

    private fun workflow(pdfEditor: PdfEditor): Pair<CompressPdfWorkflow, FakeScanDao> {
        val dao = FakeScanDao()
        val repository = ScanRepository(dao)
        val useCase = CompressPdfUseCase(pdfEditor, info.meuse24.pdf_scanner.domain.service.ScanArtifactPersister(pdfEditor, repository))
        return CompressPdfWorkflow(useCase, DocumentWorkflowGuard(pdfEditor)) to dao
    }

    @Test
    fun `searchable pdf liefert CompressionUnsupportedForSearchablePdf`() = runTest {
        val (workflow, _) = workflow(FakeCompressPdfEditor())

        val result = workflow(record(1L, searchable = true), PdfCompressionPreset.MEDIUM, tmpFolder.root)

        assertTrue(result is WorkflowResult.Failure)
        assertEquals(
            ScanWorkflowError.CompressionUnsupportedForSearchablePdf,
            (result as WorkflowResult.Failure).error
        )
    }

    @Test
    fun `geschuetzte pdf liefert ProtectedPdfUnsupported`() = runTest {
        val (workflow, _) = workflow(FakeCompressPdfEditor(encrypted = true))

        val result = workflow(record(2L), PdfCompressionPreset.MEDIUM, tmpFolder.root)

        assertTrue(result is WorkflowResult.Failure)
        assertEquals(
            ScanWorkflowError.ProtectedPdfUnsupported,
            (result as WorkflowResult.Failure).error
        )
    }

    @Test
    fun `erfolgreicher Ablauf speichert neuen Record`() = runTest {
        val (workflow, dao) = workflow(FakeCompressPdfEditor())

        val result = workflow(record(3L), PdfCompressionPreset.LOW, tmpFolder.root)

        assertTrue(result is WorkflowResult.Success)
        assertEquals(1, dao.inserted.size)
    }
}

private class FakeCompressPdfEditor(
    private val encrypted: Boolean = false
) : PdfEditor() {
    override fun isPdfEncrypted(input: File): Boolean = encrypted

    override fun compressPdf(
        input: File,
        outputDir: File,
        preset: PdfCompressionPreset
    ): File {
        return File(outputDir, "${input.nameWithoutExtension}_Komprimiert.pdf").apply { writeText("copy") }
    }

    override fun generateThumbnail(pdfFile: File, outputFile: File): Boolean {
        outputFile.writeText("thumb")
        return true
    }
}


