package info.meuse24.pdf_scanner.domain.workflow

import info.meuse24.pdf_scanner.domain.model.Document
import info.meuse24.pdf_scanner.domain.usecase.AddPageNumbersUseCase
import java.io.File
import javax.inject.Inject

data class PageNumbersWorkflowResult(
    val outputFilename: String
)

class PageNumbersWorkflow @Inject constructor(
    private val addPageNumbersUseCase: AddPageNumbersUseCase,
    private val workflowGuard: DocumentWorkflowGuard
) {
    suspend operator fun invoke(
        record: Document,
        scansDir: File
    ): WorkflowResult<PageNumbersWorkflowResult> =
        workflowGuard.run(
            record = record,
            failureMapper = ScanWorkflowError::PageNumbersFailed
        ) {
            PageNumbersWorkflowResult(
                outputFilename = addPageNumbersUseCase(record, scansDir)
            )
        }
}

