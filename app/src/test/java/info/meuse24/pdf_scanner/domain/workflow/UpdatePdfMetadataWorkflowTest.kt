package info.meuse24.pdf_scanner.domain.workflow

import info.meuse24.pdf_scanner.data.local.ScanRecord
import info.meuse24.pdf_scanner.data.repository.ScanRepository
import info.meuse24.pdf_scanner.domain.usecase.FakeScanDao
import info.meuse24.pdf_scanner.domain.usecase.UpdatePdfMetadataUseCase
import info.meuse24.pdf_scanner.util.PdfEditor
import info.meuse24.pdf_scanner.util.PdfMetadata
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.util.Calendar

class UpdatePdfMetadataWorkflowTest {

    @get:Rule val tmpFolder = TemporaryFolder()

    private fun record(id: Long, exists: Boolean = true, isEncrypted: Boolean = false): ScanRecord {
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
            pageCount = 1,
            fileSize = file.length(),
            isEncrypted = isEncrypted
        )
    }

    private fun metadata() = PdfMetadata(
        title = "Title",
        author = "Author",
        creator = "Creator",
        subject = "Subject",
        keywords = "one, two",
        creationDate = Calendar.getInstance(),
        modificationDate = null
    )

    private fun workflow(pdfEditor: PdfEditor): Pair<UpdatePdfMetadataWorkflow, FakeScanDao> {
        val dao = FakeScanDao()
        val repository = ScanRepository(dao)
        val useCase = UpdatePdfMetadataUseCase(pdfEditor, repository)
        return UpdatePdfMetadataWorkflow(useCase) to dao
    }

    @Test
    fun `fehlende Datei liefert MissingFiles`() = runTest {
        val (workflow, _) = workflow(FakeMetadataPdfEditor())

        val result = workflow(record(1L, exists = false), metadata())

        assertTrue(result is WorkflowResult.Failure)
        assertEquals(
            ScanWorkflowError.MissingFiles(listOf("scan_1")),
            (result as WorkflowResult.Failure).error
        )
    }

    @Test
    fun `verschluesselte Datei liefert ProtectedPdfUnsupported`() = runTest {
        val (workflow, _) = workflow(FakeMetadataPdfEditor())

        val result = workflow(record(2L, isEncrypted = true), metadata())

        assertTrue(result is WorkflowResult.Failure)
        assertEquals(
            ScanWorkflowError.ProtectedPdfUnsupported,
            (result as WorkflowResult.Failure).error
        )
    }

    @Test
    fun `io Fehler werden als StorageWriteFailed gemappt`() = runTest {
        val (workflow, _) = workflow(
            FakeMetadataPdfEditor(onUpdate = { _, _ -> throw IOException("locked") })
        )

        val result = workflow(record(3L), metadata())

        assertTrue(result is WorkflowResult.Failure)
        assertTrue((result as WorkflowResult.Failure).error is ScanWorkflowError.StorageWriteFailed)
    }

    @Test
    fun `erfolgreicher Ablauf aktualisiert Dateigroesse`() = runTest {
        val (workflow, dao) = workflow(FakeMetadataPdfEditor())

        val result = workflow(record(4L), metadata())

        assertTrue(result is WorkflowResult.Success)
        assertEquals(listOf(4L to 12L), dao.fileSizeUpdates)
    }
}

private class FakeMetadataPdfEditor(
    private val onUpdate: (File, PdfMetadata) -> File = { input, _ ->
        input.writeText("updated-meta")
        input
    }
) : PdfEditor() {
    override fun updateMetadata(input: File, metadata: PdfMetadata): File = onUpdate(input, metadata)
}
