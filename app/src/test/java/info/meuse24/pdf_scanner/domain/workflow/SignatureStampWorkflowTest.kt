package info.meuse24.pdf_scanner.domain.workflow

import android.graphics.Bitmap
import info.meuse24.pdf_scanner.domain.model.Document
import info.meuse24.pdf_scanner.data.repository.ScanRepository
import info.meuse24.pdf_scanner.domain.usecase.ApplySignatureStampUseCase
import info.meuse24.pdf_scanner.domain.usecase.FakeScanDao
import info.meuse24.pdf_scanner.util.PdfEditor
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Mockito
import java.io.File
import java.io.IOException

class SignatureStampWorkflowTest {

    @get:Rule val tmpFolder = TemporaryFolder()

    private fun record(id: Long, exists: Boolean = true): Document {
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
            pageCount = 3,
            fileSize = file.length(),
            isSearchable = true
        )
    }

    private fun workflow(pdfEditor: PdfEditor): Pair<SignatureStampWorkflow, FakeScanDao> {
        val dao = FakeScanDao()
        val repository = ScanRepository(dao)
        val useCase = ApplySignatureStampUseCase(pdfEditor, info.meuse24.pdf_scanner.domain.service.ScanArtifactPersister(pdfEditor, repository))
        return SignatureStampWorkflow(useCase, DocumentWorkflowGuard(pdfEditor)) to dao
    }

    @Test
    fun `null Signatur liefert SignatureRequired`() = runTest {
        val (workflow, _) = workflow(FakeSignaturePdfEditor())

        val result = workflow(record(1L), null, 0, 0.24f, tmpFolder.root)

        assertTrue(result is WorkflowResult.Failure)
        assertEquals(
            ScanWorkflowError.SignatureRequired,
            (result as WorkflowResult.Failure).error
        )
    }

    @Test
    fun `fehlende Datei liefert MissingFiles`() = runTest {
        val (workflow, _) = workflow(FakeSignaturePdfEditor())

        val result = workflow(record(2L, exists = false), fakeBitmap(), 0, 0.24f, tmpFolder.root)

        assertTrue(result is WorkflowResult.Failure)
        assertEquals(
            ScanWorkflowError.MissingFiles(listOf("scan_2")),
            (result as WorkflowResult.Failure).error
        )
    }

    @Test
    fun `ungueltige Seite liefert InvalidPageSelection`() = runTest {
        val (workflow, _) = workflow(FakeSignaturePdfEditor())

        val result = workflow(record(3L), fakeBitmap(), 9, 0.24f, tmpFolder.root)

        assertTrue(result is WorkflowResult.Failure)
        assertEquals(
            ScanWorkflowError.InvalidPageSelection,
            (result as WorkflowResult.Failure).error
        )
    }

    @Test
    fun `ungueltige Skalierung liefert InvalidSignatureScale`() = runTest {
        val (workflow, _) = workflow(FakeSignaturePdfEditor())

        val result = workflow(record(31L), fakeBitmap(), 0, 0f, tmpFolder.root)

        assertTrue(result is WorkflowResult.Failure)
        assertEquals(
            ScanWorkflowError.InvalidSignatureScale,
            (result as WorkflowResult.Failure).error
        )
    }

    @Test
    fun `geschuetzte pdf liefert ProtectedPdfUnsupported`() = runTest {
        val (workflow, _) = workflow(FakeSignaturePdfEditor(encrypted = true))

        val result = workflow(record(4L), fakeBitmap(), 0, 0.24f, tmpFolder.root)

        assertTrue(result is WorkflowResult.Failure)
        assertEquals(
            ScanWorkflowError.ProtectedPdfUnsupported,
            (result as WorkflowResult.Failure).error
        )
    }

    @Test
    fun `io Fehler werden als StorageWriteFailed gemappt`() = runTest {
        val (workflow, _) = workflow(
            FakeSignaturePdfEditor(onApply = { _, _, _, _, _ -> throw IOException("locked") })
        )

        val result = workflow(record(5L), fakeBitmap(), 0, 0.24f, tmpFolder.root)

        assertTrue(result is WorkflowResult.Failure)
        assertTrue((result as WorkflowResult.Failure).error is ScanWorkflowError.StorageWriteFailed)
    }

    @Test
    fun `erfolgreicher Ablauf speichert neuen Record`() = runTest {
        val (workflow, dao) = workflow(FakeSignaturePdfEditor())

        val result = workflow(record(6L), fakeBitmap(), 1, 0.24f, tmpFolder.root)

        assertTrue(result is WorkflowResult.Success)
        assertEquals(1, dao.inserted.size)
    }

    private fun fakeBitmap(): Bitmap = Mockito.mock(Bitmap::class.java)
}

private class FakeSignaturePdfEditor(
    private val encrypted: Boolean = false,
    private val onApply: (File, File, Bitmap, Int, Float) -> File = { input, outputDir, _, _, _ ->
        File(outputDir, "${input.nameWithoutExtension}_Signiert.pdf").apply { writeText("copy") }
    }
) : PdfEditor() {
    override fun isPdfEncrypted(input: File): Boolean = encrypted

    override fun applySignatureStamp(
        input: File,
        outputDir: File,
        signatureBitmap: Bitmap,
        pageIndex: Int,
        scaleFraction: Float
    ): File = onApply(input, outputDir, signatureBitmap, pageIndex, scaleFraction)

    override fun generateThumbnail(pdfFile: File, outputFile: File): Boolean {
        outputFile.writeText("thumb")
        return true
    }
}


