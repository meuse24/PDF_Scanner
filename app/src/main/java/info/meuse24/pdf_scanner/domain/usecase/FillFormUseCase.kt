package info.meuse24.pdf_scanner.domain.usecase

import info.meuse24.pdf_scanner.domain.model.Document
import info.meuse24.pdf_scanner.domain.model.FormFieldValue
import info.meuse24.pdf_scanner.domain.pdf.PdfFormOps
import info.meuse24.pdf_scanner.domain.service.ScanArtifactPersister
import java.io.File
import javax.inject.Inject

class FillFormUseCase @Inject constructor(
    private val pdfFormOps: PdfFormOps,
    private val persister: ScanArtifactPersister
) {
    suspend operator fun invoke(
        record: Document,
        values: Map<String, FormFieldValue>,
        flatten: Boolean,
        scansDir: File
    ): String {
        val resultFile = pdfFormOps.fillFormFields(
            input = File(record.filepath),
            outputDir = scansDir,
            values = values,
            flatten = flatten
        )
        persister.persistDerivedFrom(record, resultFile, scansDir)
        return resultFile.nameWithoutExtension
    }
}
