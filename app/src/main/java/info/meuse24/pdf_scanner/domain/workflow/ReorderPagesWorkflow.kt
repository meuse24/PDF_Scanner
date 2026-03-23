package info.meuse24.pdf_scanner.domain.workflow

import info.meuse24.pdf_scanner.data.local.ScanRecord
import info.meuse24.pdf_scanner.domain.usecase.ReorderPagesUseCase
import java.io.File
import java.io.IOException
import javax.inject.Inject

data class ReorderPagesWorkflowResult(
    val savedAsCopy: Boolean
)

class ReorderPagesWorkflow @Inject constructor(
    private val reorderPagesUseCase: ReorderPagesUseCase
) {
    suspend operator fun invoke(
        record: ScanRecord,
        newOrder: List<Int>,
        saveAsCopy: Boolean,
        scansDir: File
    ): WorkflowResult<ReorderPagesWorkflowResult> {
        if (!File(record.filepath).exists()) {
            return WorkflowResult.Failure(ScanWorkflowError.MissingFiles(listOf(record.filename)))
        }

        val expectedOrder = (0 until record.pageCount).toList()
        if (newOrder.size != record.pageCount || newOrder.sorted() != expectedOrder) {
            return WorkflowResult.Failure(ScanWorkflowError.InvalidPageOrder)
        }

        return try {
            reorderPagesUseCase(record, newOrder, saveAsCopy, scansDir)
            WorkflowResult.Success(ReorderPagesWorkflowResult(savedAsCopy = saveAsCopy))
        } catch (e: IOException) {
            WorkflowResult.Failure(ScanWorkflowError.StorageWriteFailed(e))
        } catch (t: Throwable) {
            WorkflowResult.Failure(ScanWorkflowError.ReorderFailed(t))
        }
    }
}
