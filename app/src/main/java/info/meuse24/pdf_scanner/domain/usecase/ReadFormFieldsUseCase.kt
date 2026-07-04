package info.meuse24.pdf_scanner.domain.usecase

import info.meuse24.pdf_scanner.domain.model.AcroFormCapability
import info.meuse24.pdf_scanner.domain.model.FormField
import info.meuse24.pdf_scanner.domain.pdf.PdfFormOps
import java.io.File
import javax.inject.Inject

data class FormFieldsResult(
    val capability: AcroFormCapability,
    val fields: List<FormField>
)

class ReadFormFieldsUseCase @Inject constructor(
    private val pdfFormOps: PdfFormOps
) {
    operator fun invoke(file: File): FormFieldsResult {
        val capability = pdfFormOps.detectFormCapability(file)
        val fields = if (capability == AcroFormCapability.FILLABLE) {
            pdfFormOps.readFormFields(file)
        } else {
            emptyList()
        }
        return FormFieldsResult(capability, fields)
    }
}
