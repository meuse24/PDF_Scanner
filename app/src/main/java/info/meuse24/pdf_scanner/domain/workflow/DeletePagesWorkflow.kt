package info.meuse24.pdf_scanner.domain.workflow

import info.meuse24.pdf_scanner.domain.model.Document
import info.meuse24.pdf_scanner.domain.usecase.DeletePdfPagesUseCase
import info.meuse24.pdf_scanner.util.normalizePageIndexes
import java.io.File
import javax.inject.Inject

data class DeletePagesWorkflowResult(
    val remainingPages: Int
)

class DeletePagesWorkflow @Inject constructor(
    private val deletePdfPagesUseCase: DeletePdfPagesUseCase,
    private val workflowGuard: DocumentWorkflowGuard
) {
    suspend operator fun invoke(
        record: Document,
        pageIndexes: List<Int>,
        saveAsCopy: Boolean,
        scansDir: File
    ): WorkflowResult<DeletePagesWorkflowResult> {
        val normalized = normalizePageIndexes(record.pageCount, pageIndexes)
        return workflowGuard.run(
            record = record,
            failureMapper = ScanWorkflowError::DeletePagesFailed,
            validate = {
                when {
                    normalized.isEmpty() || normalized.size != pageIndexes.distinct().size ->
                        ScanWorkflowError.InvalidPageSelection
                    normalized.size >= record.pageCount ->
                        ScanWorkflowError.CannotDeleteAllPages
                    else -> null
                }
            }
        ) {
            DeletePagesWorkflowResult(
                remainingPages = deletePdfPagesUseCase(record, normalized, saveAsCopy, scansDir)
            )
        }
    }
}

