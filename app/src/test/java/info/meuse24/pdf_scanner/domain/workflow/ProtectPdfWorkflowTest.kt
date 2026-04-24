package info.meuse24.pdf_scanner.domain.workflow

import info.meuse24.pdf_scanner.domain.model.Document
import info.meuse24.pdf_scanner.data.repository.ScanRepository
import info.meuse24.pdf_scanner.domain.usecase.FakeScanDao
import info.meuse24.pdf_scanner.domain.usecase.ProtectPdfUseCase
import info.meuse24.pdf_scanner.util.PdfEditor
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ProtectPdfWorkflowTest {

    @get:Rule val tmpFolder = TemporaryFolder()

    private fun record(id: Long, encrypted: Boolean = false): Document {
        val file = tmpFolder.newFile("scan_$id.pdf").apply { writeText("pdf") }
        return Document(
            id = id,
            filename = "scan_$id",
            filepath = file.absolutePath,
            timestamp = 0L,
            pageCount = 2,
            fileSize = file.length(),
            isSearchable = false
        ).also {
            if (encrypted) {
                // state handled via fake editor
            }
        }
    }

    private fun workflow(pdfEditor: PdfEditor): Pair<ProtectPdfWorkflow, FakeScanDao> {
        val dao = FakeScanDao()
        val repository = ScanRepository(dao)
        val useCase = ProtectPdfUseCase(pdfEditor, info.meuse24.pdf_scanner.domain.service.ScanArtifactPersister(pdfEditor, repository))
        return ProtectPdfWorkflow(useCase, pdfEditor, DocumentWorkflowGuard(pdfEditor)) to dao
    }

    @Test
    fun `leeres Passwort liefert PasswordRequired`() = runTest {
        val (workflow, _) = workflow(FakeProtectPdfEditor())

        val result = workflow(record(1L), "   ", tmpFolder.root)

        assertTrue(result is WorkflowResult.Failure)
        assertEquals(ScanWorkflowError.PasswordRequired, (result as WorkflowResult.Failure).error)
    }

    @Test
    fun `bereits geschuetzte pdf liefert AlreadyProtected`() = runTest {
        val (workflow, _) = workflow(FakeProtectPdfEditor(encrypted = true))

        val result = workflow(record(2L), "secret", tmpFolder.root)

        assertTrue(result is WorkflowResult.Failure)
        assertEquals(ScanWorkflowError.AlreadyProtected, (result as WorkflowResult.Failure).error)
    }

    @Test
    fun `erfolgreicher Ablauf speichert neuen Record`() = runTest {
        val (workflow, dao) = workflow(FakeProtectPdfEditor())

        val result = workflow(record(3L), "secret", tmpFolder.root)

        assertTrue(result is WorkflowResult.Success)
        assertEquals(1, dao.inserted.size)
    }
}

private class FakeProtectPdfEditor(
    private val encrypted: Boolean = false
) : PdfEditor() {
    override fun isPdfEncrypted(input: File): Boolean = encrypted

    override fun protectPdf(input: File, outputDir: File, password: String): File {
        return File(outputDir, "${input.nameWithoutExtension}_Geschuetzt.pdf").apply { writeText("copy") }
    }
}


