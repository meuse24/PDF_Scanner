package info.meuse24.pdf_scanner.domain.workflow

import info.meuse24.pdf_scanner.data.local.ScanRecord
import info.meuse24.pdf_scanner.data.repository.ScanRepository
import info.meuse24.pdf_scanner.domain.usecase.FakeScanDao
import info.meuse24.pdf_scanner.domain.usecase.UnlockPdfUseCase
import info.meuse24.pdf_scanner.util.PdfEditor
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class UnlockPdfWorkflowTest {

    @get:Rule val tmpFolder = TemporaryFolder()

    private fun record(id: Long): ScanRecord {
        val file = tmpFolder.newFile("scan_$id.pdf").apply { writeText("pdf") }
        return ScanRecord(
            id = id,
            filename = "scan_$id",
            filepath = file.absolutePath,
            timestamp = 0L,
            pageCount = 2,
            fileSize = file.length(),
            isSearchable = false
        )
    }

    private fun workflow(pdfEditor: PdfEditor): Pair<UnlockPdfWorkflow, FakeScanDao> {
        val dao = FakeScanDao()
        val repository = ScanRepository(dao)
        val useCase = UnlockPdfUseCase(pdfEditor, repository)
        return UnlockPdfWorkflow(useCase, pdfEditor) to dao
    }

    @Test
    fun `nicht geschuetzte pdf liefert NotProtected`() = runTest {
        val (workflow, _) = workflow(FakeUnlockPdfEditor(encrypted = false))

        val result = workflow(record(1L), "secret", tmpFolder.root)

        assertTrue(result is WorkflowResult.Failure)
        assertEquals(ScanWorkflowError.NotProtected, (result as WorkflowResult.Failure).error)
    }

    @Test
    fun `leeres Passwort liefert PasswordRequired`() = runTest {
        val (workflow, _) = workflow(FakeUnlockPdfEditor(encrypted = true))

        val result = workflow(record(2L), " ", tmpFolder.root)

        assertTrue(result is WorkflowResult.Failure)
        assertEquals(ScanWorkflowError.PasswordRequired, (result as WorkflowResult.Failure).error)
    }

    @Test
    fun `falsches Passwort liefert WrongPassword`() = runTest {
        val (workflow, _) = workflow(FakeUnlockPdfEditor(encrypted = true, wrongPassword = true))

        val result = workflow(record(3L), "secret", tmpFolder.root)

        assertTrue(result is WorkflowResult.Failure)
        assertEquals(ScanWorkflowError.WrongPassword, (result as WorkflowResult.Failure).error)
    }

    @Test
    fun `erfolgreicher Ablauf speichert neuen Record`() = runTest {
        val (workflow, dao) = workflow(FakeUnlockPdfEditor(encrypted = true))

        val result = workflow(record(4L), "secret", tmpFolder.root)

        assertTrue(result is WorkflowResult.Success)
        assertEquals(1, dao.inserted.size)
    }
}

private class FakeUnlockPdfEditor(
    private val encrypted: Boolean,
    private val wrongPassword: Boolean = false
) : PdfEditor() {
    override fun isPdfEncrypted(input: File): Boolean = encrypted

    override fun unlockPdf(input: File, outputDir: File, password: String): File {
        if (wrongPassword) throw PdfEditor.WrongPasswordException()
        return File(outputDir, "${input.nameWithoutExtension}_Entsperrt.pdf").apply { writeText("copy") }
    }

    override fun generateThumbnail(pdfFile: File, outputFile: File): Boolean {
        outputFile.writeText("thumb")
        return true
    }
}
