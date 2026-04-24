package info.meuse24.pdf_scanner.domain.workflow

import info.meuse24.pdf_scanner.domain.model.Document
import info.meuse24.pdf_scanner.domain.usecase.ApplyTextWatermarkUseCase
import java.io.File
import javax.inject.Inject

data class TextWatermarkWorkflowResult(
    val outputFilename: String
)

class TextWatermarkWorkflow @Inject constructor(
    private val applyTextWatermarkUseCase: ApplyTextWatermarkUseCase,
    private val workflowGuard: DocumentWorkflowGuard
) {
    suspend operator fun invoke(
        record: Document,
        text: String,
        scansDir: File
    ): WorkflowResult<TextWatermarkWorkflowResult> =
        workflowGuard.run(
            record = record,
            failureMapper = ScanWorkflowError::TextWatermarkFailed,
            validate = {
                if (text.isBlank()) {
                    ScanWorkflowError.InvalidWatermarkText
                } else {
                    null
                }
            }
        ) {
            TextWatermarkWorkflowResult(
                outputFilename = applyTextWatermarkUseCase(record, text.trim(), scansDir)
            )
        }
}

