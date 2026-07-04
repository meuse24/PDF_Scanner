package info.meuse24.pdf_scanner.domain.pdf

import info.meuse24.pdf_scanner.domain.model.AcroFormCapability
import info.meuse24.pdf_scanner.domain.model.FormField
import info.meuse24.pdf_scanner.domain.model.FormFieldValue
import java.io.File

interface PdfFormOps {
    fun detectFormCapability(file: File): AcroFormCapability

    fun readFormFields(file: File): List<FormField>

    fun fillFormFields(
        input: File,
        outputDir: File,
        values: Map<String, FormFieldValue>,
        flatten: Boolean
    ): File
}
