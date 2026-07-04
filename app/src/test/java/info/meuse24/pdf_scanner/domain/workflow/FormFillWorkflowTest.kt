package info.meuse24.pdf_scanner.domain.workflow

import info.meuse24.pdf_scanner.data.repository.ScanRepository
import info.meuse24.pdf_scanner.domain.model.AcroFormCapability
import info.meuse24.pdf_scanner.domain.model.Document
import info.meuse24.pdf_scanner.domain.model.FormField
import info.meuse24.pdf_scanner.domain.model.FormFieldType
import info.meuse24.pdf_scanner.domain.model.FormFieldValue
import info.meuse24.pdf_scanner.domain.model.NormalizedFormRect
import info.meuse24.pdf_scanner.domain.pdf.PdfFormOps
import info.meuse24.pdf_scanner.domain.service.ScanArtifactPersister
import info.meuse24.pdf_scanner.domain.usecase.FakeScanDao
import info.meuse24.pdf_scanner.domain.usecase.FillFormUseCase
import info.meuse24.pdf_scanner.util.PdfEditor
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class FormFillWorkflowTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `missing required field is rejected`() = runTest {
        val (workflow, _) = workflow(AcroFormCapability.FILLABLE)
        val field = requiredTextField()

        val result = workflow(
            record = record(),
            fields = listOf(field),
            values = mapOf(field.fullyQualifiedName to FormFieldValue()),
            flatten = false,
            scansDir = temporaryFolder.root
        )

        assertTrue(result is WorkflowResult.Failure)
        assertEquals(
            ScanWorkflowError.RequiredFormFieldsMissing(listOf("Name")),
            (result as WorkflowResult.Failure).error
        )
    }

    @Test
    fun `XFA form is rejected explicitly`() = runTest {
        val (workflow, _) = workflow(AcroFormCapability.XFA_UNSUPPORTED)

        val result = workflow(
            record = record(),
            fields = emptyList(),
            values = emptyMap(),
            flatten = false,
            scansDir = temporaryFolder.root
        )

        assertEquals(
            ScanWorkflowError.FormXfaUnsupported,
            (result as WorkflowResult.Failure).error
        )
    }

    @Test
    fun `valid form creates derived document`() = runTest {
        val (workflow, dao) = workflow(AcroFormCapability.FILLABLE)
        val field = requiredTextField()

        val result = workflow(
            record = record(),
            fields = listOf(field),
            values = mapOf(field.fullyQualifiedName to FormFieldValue.single("Ada")),
            flatten = true,
            scansDir = temporaryFolder.root
        )

        assertTrue(result is WorkflowResult.Success)
        assertEquals(1, dao.inserted.size)
    }

    private fun workflow(
        capability: AcroFormCapability
    ): Pair<FormFillWorkflow, FakeScanDao> {
        val formOps = FakeFormOps(capability)
        val dao = FakeScanDao()
        val pdfEditor = FakeThumbnailPdfEditor()
        val useCase = FillFormUseCase(
            formOps,
            ScanArtifactPersister(pdfEditor, ScanRepository(dao))
        )
        return FormFillWorkflow(
            fillFormUseCase = useCase,
            pdfFormOps = formOps,
            workflowGuard = DocumentWorkflowGuard(pdfEditor)
        ) to dao
    }

    private fun record(): Document {
        val file = File(temporaryFolder.root, "form.pdf").apply {
            if (!exists()) writeText("pdf")
        }
        return Document(
            id = 1L,
            filename = "form",
            filepath = file.absolutePath,
            timestamp = 0L,
            pageCount = 1,
            fileSize = file.length()
        )
    }

    private fun requiredTextField() = FormField(
        id = "name#0",
        fullyQualifiedName = "name",
        label = "Name",
        type = FormFieldType.TEXT,
        pageIndex = 0,
        rectNormalized = NormalizedFormRect(0.1f, 0.1f, 0.5f, 0.15f),
        value = FormFieldValue(),
        required = true
    )
}

private class FakeFormOps(
    private val capability: AcroFormCapability
) : PdfFormOps {
    override fun detectFormCapability(file: File): AcroFormCapability = capability

    override fun readFormFields(file: File): List<FormField> = emptyList()

    override fun fillFormFields(
        input: File,
        outputDir: File,
        values: Map<String, FormFieldValue>,
        flatten: Boolean
    ): File = File(outputDir, "form_filled.pdf").apply { writeText("filled") }
}

private class FakeThumbnailPdfEditor : PdfEditor() {
    override fun isPdfEncrypted(input: File): Boolean = false

    override fun generateThumbnail(pdfFile: File, outputFile: File): Boolean {
        outputFile.writeText("thumbnail")
        return true
    }
}
