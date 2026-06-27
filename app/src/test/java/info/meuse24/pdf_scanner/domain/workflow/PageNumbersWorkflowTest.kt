package info.meuse24.pdf_scanner.domain.workflow

import info.meuse24.pdf_scanner.domain.model.Document
import info.meuse24.pdf_scanner.domain.model.AppSettings
import info.meuse24.pdf_scanner.domain.model.PageNumberHorizontalPosition
import info.meuse24.pdf_scanner.domain.model.PageNumberSettings
import info.meuse24.pdf_scanner.domain.model.PageNumberVerticalPosition
import info.meuse24.pdf_scanner.data.repository.ScanRepository
import info.meuse24.pdf_scanner.domain.usecase.AddPageNumbersUseCase
import info.meuse24.pdf_scanner.domain.usecase.FakeScanDao
import info.meuse24.pdf_scanner.util.PdfEditor
import info.meuse24.pdf_scanner.testutil.FakeSettingsRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

class PageNumbersWorkflowTest {

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

    private fun workflow(
        pdfEditor: PdfEditor,
        pageNumberSettings: PageNumberSettings = PageNumberSettings()
    ): Pair<PageNumbersWorkflow, FakeScanDao> {
        val dao = FakeScanDao()
        val repository = ScanRepository(dao)
        val useCase = AddPageNumbersUseCase(
            pdfEditor,
            info.meuse24.pdf_scanner.domain.service.ScanArtifactPersister(pdfEditor, repository),
            FakeSettingsRepository(AppSettings(pageNumberSettings = pageNumberSettings))
        )
        return PageNumbersWorkflow(useCase, DocumentWorkflowGuard(pdfEditor)) to dao
    }

    @Test
    fun `fehlende Datei liefert MissingFiles`() = runTest {
        val (workflow, _) = workflow(FakePageNumbersPdfEditor())

        val result = workflow(record(1L, exists = false), tmpFolder.root)

        assertTrue(result is WorkflowResult.Failure)
        assertEquals(
            ScanWorkflowError.MissingFiles(listOf("scan_1")),
            (result as WorkflowResult.Failure).error
        )
    }

    @Test
    fun `io Fehler werden als StorageWriteFailed gemappt`() = runTest {
        val (workflow, _) = workflow(
            FakePageNumbersPdfEditor(onAdd = { _, _ -> throw IOException("locked") })
        )

        val result = workflow(record(2L), tmpFolder.root)

        assertTrue(result is WorkflowResult.Failure)
        assertTrue((result as WorkflowResult.Failure).error is ScanWorkflowError.StorageWriteFailed)
    }

    @Test
    fun `erfolgreicher Ablauf speichert neuen Record`() = runTest {
        val (workflow, dao) = workflow(FakePageNumbersPdfEditor())

        val result = workflow(record(3L), tmpFolder.root)

        assertTrue(result is WorkflowResult.Success)
        assertEquals(1, dao.inserted.size)
    }

    @Test
    fun `Seitennummern Einstellungen werden an PDF Editor uebergeben`() = runTest {
        val settings = PageNumberSettings(
            horizontalPosition = PageNumberHorizontalPosition.RIGHT,
            verticalPosition = PageNumberVerticalPosition.TOP,
            prefix = "Seite",
            includeTotalPageCount = true
        )
        val editor = FakePageNumbersPdfEditor()
        val (workflow, _) = workflow(editor, settings)

        workflow(record(4L), tmpFolder.root)

        assertEquals(settings, editor.receivedSettings)
    }
}

private class FakePageNumbersPdfEditor(
    private val onAdd: (File, File) -> File = { input, outputDir ->
        File(outputDir, "${input.nameWithoutExtension}_Nummeriert.pdf").apply { writeText("copy") }
    }
) : PdfEditor() {
    var receivedSettings: PageNumberSettings? = null
        private set

    override fun addPageNumbers(
        input: File,
        outputDir: File,
        settings: PageNumberSettings
    ): File {
        receivedSettings = settings
        return onAdd(input, outputDir)
    }

    override fun generateThumbnail(pdfFile: File, outputFile: File): Boolean {
        outputFile.writeText("thumb")
        return true
    }
}


