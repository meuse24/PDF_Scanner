package info.meuse24.pdf_scanner.domain.workflow

import info.meuse24.pdf_scanner.domain.model.Document
import info.meuse24.pdf_scanner.data.repository.ScanRepository
import info.meuse24.pdf_scanner.domain.usecase.FakeScanDao
import info.meuse24.pdf_scanner.domain.usecase.RotatePagesUseCase
import info.meuse24.pdf_scanner.util.PdfEditor
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

class RotatePagesWorkflowTest {

    @get:Rule val tmpFolder = TemporaryFolder()

    private fun record(
        id: Long,
        pageCount: Int = 3,
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
            fileSize = file.length(),
            isSearchable = true
        )
    }

    private fun workflow(pdfEditor: PdfEditor): Pair<RotatePagesWorkflow, FakeScanDao> {
        val dao = FakeScanDao()
        val repository = ScanRepository(dao)
        val useCase = RotatePagesUseCase(pdfEditor, info.meuse24.pdf_scanner.domain.service.ScanArtifactPersister(pdfEditor, repository), repository)
        return RotatePagesWorkflow(useCase, DocumentWorkflowGuard(pdfEditor)) to dao
    }

    @Test
    fun `ungueltige Seitenauswahl liefert InvalidPageSelection`() = runTest {
        val (workflow, _) = workflow(FakeRotatePdfEditor())

        val result = workflow(
            record = record(1L),
            pageIndexes = listOf(9),
            rotationDegrees = 90,
            saveAsCopy = false,
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
        val (workflow, _) = workflow(FakeRotatePdfEditor())

        val result = workflow(
            record = record(2L, exists = false),
            pageIndexes = listOf(0),
            rotationDegrees = 90,
            saveAsCopy = false,
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
            FakeRotatePdfEditor(onRotate = { _, _, _, _ -> throw IOException("locked") })
        )

        val result = workflow(
            record = record(3L),
            pageIndexes = listOf(0, 1),
            rotationDegrees = 180,
            saveAsCopy = true,
            scansDir = tmpFolder.root
        )

        assertTrue(result is WorkflowResult.Failure)
        assertTrue((result as WorkflowResult.Failure).error is ScanWorkflowError.StorageWriteFailed)
    }

    @Test
    fun `erfolgreicher Copy-Ablauf speichert neuen Record`() = runTest {
        val (workflow, dao) = workflow(FakeRotatePdfEditor())

        val result = workflow(
            record = record(4L),
            pageIndexes = listOf(0, 2),
            rotationDegrees = 270,
            saveAsCopy = true,
            scansDir = tmpFolder.root
        )

        assertTrue(result is WorkflowResult.Success)
        assertEquals(1, dao.inserted.size)
    }
}

private class FakeRotatePdfEditor(
    private val onRotate: (File, List<Int>, Int, Boolean) -> File = { input, _, _, saveAsCopy ->
        if (saveAsCopy) {
            File(input.parentFile, "${input.nameWithoutExtension}_Gedreht.pdf").apply {
                writeText("copy")
            }
        } else {
            input.apply { writeText("updated") }
        }
    }
) : PdfEditor() {
    override fun rotatePages(
        input: File,
        pageIndexes: List<Int>,
        rotationDegrees: Int,
        saveAsCopy: Boolean
    ): File = onRotate(input, pageIndexes, rotationDegrees, saveAsCopy)

    override fun generateThumbnail(pdfFile: File, outputFile: File): Boolean {
        outputFile.writeText("thumb")
        return true
    }
}


