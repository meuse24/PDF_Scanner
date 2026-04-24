package info.meuse24.pdf_scanner.domain.workflow

import info.meuse24.pdf_scanner.domain.model.Document
import info.meuse24.pdf_scanner.domain.usecase.ConvertToGrayscaleUseCase
import java.io.File
import javax.inject.Inject

data class ConvertToGrayscaleWorkflowResult(val outputFilename: String)

class ConvertToGrayscaleWorkflow @Inject constructor(
    private val convertToGrayscaleUseCase: ConvertToGrayscaleUseCase,
    private val workflowGuard: DocumentWorkflowGuard
) {
    suspend operator fun invoke(
        record: Document,
        scansDir: File
    ): WorkflowResult<ConvertToGrayscaleWorkflowResult> =
        workflowGuard.run(
            record = record,
            requireUnencrypted = true,
            failureMapper = ScanWorkflowError::GrayscaleFailed
        ) {
            ConvertToGrayscaleWorkflowResult(
                outputFilename = convertToGrayscaleUseCase(record, scansDir)
            )
        }
}

