package info.meuse24.pdf_scanner.domain.workflow

import info.meuse24.pdf_scanner.domain.model.Document
import info.meuse24.pdf_scanner.domain.usecase.ReorderPagesUseCase
import java.io.File
import javax.inject.Inject

data class ReorderPagesWorkflowResult(
    val savedAsCopy: Boolean
)

class ReorderPagesWorkflow @Inject constructor(
    private val reorderPagesUseCase: ReorderPagesUseCase,
    private val workflowGuard: DocumentWorkflowGuard
) {
    suspend operator fun invoke(
        record: Document,
        newOrder: List<Int>,
        saveAsCopy: Boolean,
        scansDir: File
    ): WorkflowResult<ReorderPagesWorkflowResult> {
        val expectedOrder = (0 until record.pageCount).toList()
        return workflowGuard.run(
            record = record,
            failureMapper = ScanWorkflowError::ReorderFailed,
            validate = {
                if (newOrder.size != record.pageCount || newOrder.sorted() != expectedOrder) {
                    ScanWorkflowError.InvalidPageOrder
                } else {
                    null
                }
            }
        ) {
            reorderPagesUseCase(record, newOrder, saveAsCopy, scansDir)
            ReorderPagesWorkflowResult(savedAsCopy = saveAsCopy)
        }
    }
}

