package info.meuse24.pdf_scanner.domain.workflow

import info.meuse24.pdf_scanner.domain.model.Document
import info.meuse24.pdf_scanner.domain.usecase.RemoveTextLayerUseCase
import java.io.File
import javax.inject.Inject

data class RemoveTextLayerWorkflowResult(val outputFilename: String)

class RemoveTextLayerWorkflow @Inject constructor(
    private val removeTextLayerUseCase: RemoveTextLayerUseCase,
    private val workflowGuard: DocumentWorkflowGuard
) {
    suspend operator fun invoke(
        record: Document,
        scansDir: File
    ): WorkflowResult<RemoveTextLayerWorkflowResult> =
        workflowGuard.run(
            record = record,
            failureMapper = ScanWorkflowError::RemoveTextLayerFailed,
            validate = {
                if (!record.isSearchable) {
                    ScanWorkflowError.NotSearchable
                } else {
                    null
                }
            }
        ) {
            RemoveTextLayerWorkflowResult(
                outputFilename = removeTextLayerUseCase(record, scansDir)
            )
        }
}

