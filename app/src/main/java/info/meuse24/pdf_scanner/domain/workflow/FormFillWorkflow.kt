package info.meuse24.pdf_scanner.domain.workflow

import info.meuse24.pdf_scanner.domain.model.AcroFormCapability
import info.meuse24.pdf_scanner.domain.model.Document
import info.meuse24.pdf_scanner.domain.model.FormField
import info.meuse24.pdf_scanner.domain.model.FormFieldType
import info.meuse24.pdf_scanner.domain.model.FormFieldValue
import info.meuse24.pdf_scanner.domain.pdf.PdfFormOps
import info.meuse24.pdf_scanner.domain.usecase.FillFormUseCase
import java.io.File
import javax.inject.Inject

data class FormFillWorkflowResult(
    val outputFilename: String
)

class FormFillWorkflow @Inject constructor(
    private val fillFormUseCase: FillFormUseCase,
    private val pdfFormOps: PdfFormOps,
    private val workflowGuard: DocumentWorkflowGuard
) {
    suspend operator fun invoke(
        record: Document,
        fields: List<FormField>,
        values: Map<String, FormFieldValue>,
        flatten: Boolean,
        scansDir: File
    ): WorkflowResult<FormFillWorkflowResult> =
        workflowGuard.run(
            record = record,
            requireUnencrypted = true,
            failureMapper = ScanWorkflowError::FormFillFailed,
            validate = { input ->
                runCatching {
                    when (pdfFormOps.detectFormCapability(input)) {
                        AcroFormCapability.FILLABLE ->
                            validateRequiredFields(fields, values)
                        AcroFormCapability.XFA_UNSUPPORTED ->
                            ScanWorkflowError.FormXfaUnsupported
                        AcroFormCapability.SIGNED_LOCKED ->
                            ScanWorkflowError.FormSignedLocked
                        AcroFormCapability.NONE ->
                            ScanWorkflowError.FormNotFillable
                    }
                }.getOrNull()
            }
        ) {
            FormFillWorkflowResult(
                outputFilename = fillFormUseCase(record, values, flatten, scansDir)
            )
        }

    private fun validateRequiredFields(
        fields: List<FormField>,
        values: Map<String, FormFieldValue>
    ): ScanWorkflowError? {
        val missing = fields
            .asSequence()
            .filter { it.required && !it.readOnly }
            .distinctBy(FormField::fullyQualifiedName)
            .filter { field -> isMissing(field, values[field.fullyQualifiedName] ?: field.value) }
            .map(FormField::label)
            .toList()
        return missing.takeIf { it.isNotEmpty() }?.let(ScanWorkflowError::RequiredFormFieldsMissing)
    }

    private fun isMissing(field: FormField, value: FormFieldValue): Boolean = when (field.type) {
        FormFieldType.CHECKBOX,
        FormFieldType.RADIO_GROUP ->
            value.values.isEmpty() || value.singleValue == "Off"
        FormFieldType.TEXT,
        FormFieldType.MULTILINE_TEXT,
        FormFieldType.COMBO_BOX,
        FormFieldType.LIST_BOX ->
            value.values.isEmpty() || value.values.all(String::isBlank)
        FormFieldType.UNSUPPORTED -> false
    }
}
