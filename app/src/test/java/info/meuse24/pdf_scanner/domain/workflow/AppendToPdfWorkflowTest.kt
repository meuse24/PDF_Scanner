package info.meuse24.pdf_scanner.domain.workflow

import info.meuse24.pdf_scanner.domain.model.Document
import info.meuse24.pdf_scanner.domain.usecase.AppendResult
import info.meuse24.pdf_scanner.domain.usecase.AppendSource
import info.meuse24.pdf_scanner.domain.usecase.AppendSourceEncryptedException
import info.meuse24.pdf_scanner.domain.usecase.AppendTargetMissingException
import info.meuse24.pdf_scanner.domain.usecase.AppendToPdfUseCase
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Mockito.mock
import java.io.File

class AppendToPdfWorkflowTest {

    @get:Rule
    val tmpFolder = TemporaryFolder()

    @Test
    fun `missing target file returns AppendFailed`() = runTest {
        val workflow = AppendToPdfWorkflow(StubAppendToPdfUseCase(AppendResult(2, 20L, 1)))
        val record = record(File(tmpFolder.root, "missing.pdf"), exists = false)

        val result = workflow(record, AppendSource.Pdf(mock(android.net.Uri::class.java)))

        assertTrue(result is WorkflowResult.Failure)
        val error = (result as WorkflowResult.Failure).error
        assertTrue(error is ScanWorkflowError.AppendFailed)
        assertTrue(error.cause is AppendTargetMissingException)
    }

    @Test
    fun `encrypted target returns AppendTargetEncrypted`() = runTest {
        val file = tmpFolder.newFile("encrypted.pdf").apply { writeText("pdf") }
        val workflow = AppendToPdfWorkflow(StubAppendToPdfUseCase(AppendResult(2, 20L, 1)))

        val result = workflow(record(file, isEncrypted = true), AppendSource.Pdf(mock(android.net.Uri::class.java)))

        assertTrue(result is WorkflowResult.Failure)
        assertEquals(ScanWorkflowError.AppendTargetEncrypted, (result as WorkflowResult.Failure).error)
    }

    @Test
    fun `success returns appended page stats`() = runTest {
        val file = tmpFolder.newFile("target.pdf").apply { writeText("pdf") }
        val workflow = AppendToPdfWorkflow(StubAppendToPdfUseCase(AppendResult(5, 128L, 2)))

        val result = workflow(record(file), AppendSource.Pdf(mock(android.net.Uri::class.java)))

        assertTrue(result is WorkflowResult.Success)
        assertEquals(2, (result as WorkflowResult.Success).value.appendedPageCount)
        assertEquals(5, result.value.pageCount)
    }

    @Test
    fun `encrypted source returns AppendSourceEncrypted`() = runTest {
        val file = tmpFolder.newFile("target_source_encrypted.pdf").apply { writeText("pdf") }
        val workflow = AppendToPdfWorkflow(SourceEncryptedAppendToPdfUseCase())

        val result = workflow(record(file), AppendSource.Pdf(mock(android.net.Uri::class.java)))

        assertTrue(result is WorkflowResult.Failure)
        assertEquals(ScanWorkflowError.AppendSourceEncrypted, (result as WorkflowResult.Failure).error)
    }
}

private class StubAppendToPdfUseCase(
    private val result: AppendResult
) : AppendToPdfUseCase(
    fileUtil = mock(info.meuse24.pdf_scanner.util.FileUtil::class.java),
    pdfEditor = mock(info.meuse24.pdf_scanner.util.PdfEditor::class.java),
    repository = mock(info.meuse24.pdf_scanner.data.repository.ScanRepository::class.java),
    imagePdfBuilder = mock(info.meuse24.pdf_scanner.domain.usecase.ImagePdfBuilder::class.java)
) {
    override suspend fun invoke(target: Document, source: AppendSource): AppendResult = result
}

private class SourceEncryptedAppendToPdfUseCase : AppendToPdfUseCase(
    fileUtil = mock(info.meuse24.pdf_scanner.util.FileUtil::class.java),
    pdfEditor = mock(info.meuse24.pdf_scanner.util.PdfEditor::class.java),
    repository = mock(info.meuse24.pdf_scanner.data.repository.ScanRepository::class.java),
    imagePdfBuilder = mock(info.meuse24.pdf_scanner.domain.usecase.ImagePdfBuilder::class.java)
) {
    override suspend fun invoke(target: Document, source: AppendSource): AppendResult {
        throw AppendSourceEncryptedException()
    }
}

private fun record(
    file: File,
    exists: Boolean = true,
    isEncrypted: Boolean = false
) = Document(
    id = 1L,
    filename = file.nameWithoutExtension,
    filepath = file.absolutePath,
    timestamp = 0L,
    pageCount = 3,
    fileSize = if (exists) file.length() else 0L,
    isEncrypted = isEncrypted
)

