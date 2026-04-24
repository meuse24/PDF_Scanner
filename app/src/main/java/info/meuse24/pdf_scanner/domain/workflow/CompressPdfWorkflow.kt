package info.meuse24.pdf_scanner.domain.workflow

import info.meuse24.pdf_scanner.domain.model.Document
import info.meuse24.pdf_scanner.domain.usecase.CompressPdfUseCase
import info.meuse24.pdf_scanner.domain.usecase.PdfCompressionPreset
import java.io.File
import javax.inject.Inject

data class CompressPdfWorkflowResult(
    val outputFilename: String
)

class CompressPdfWorkflow @Inject constructor(
    private val compressPdfUseCase: CompressPdfUseCase,
    private val workflowGuard: DocumentWorkflowGuard
) {
    suspend operator fun invoke(
        record: Document,
        preset: PdfCompressionPreset,
        scansDir: File
    ): WorkflowResult<CompressPdfWorkflowResult> =
        workflowGuard.run(
            record = record,
            requireUnencrypted = true,
            failureMapper = ScanWorkflowError::CompressionFailed,
            validate = {
                if (record.isSearchable) {
                    ScanWorkflowError.CompressionUnsupportedForSearchablePdf
                } else {
                    null
                }
            }
        ) {
            CompressPdfWorkflowResult(
                outputFilename = compressPdfUseCase(record, preset, scansDir)
            )
        }
}

