package info.meuse24.pdf_scanner.domain.workflow

import info.meuse24.pdf_scanner.data.local.ScanRecord
import info.meuse24.pdf_scanner.domain.usecase.DeletePdfPagesUseCase
import info.meuse24.pdf_scanner.util.normalizePageIndexes
import java.io.File
import java.io.IOException
import javax.inject.Inject

data class DeletePagesWorkflowResult(
    val remainingPages: Int
)

class DeletePagesWorkflow @Inject constructor(
    private val deletePdfPagesUseCase: DeletePdfPagesUseCase
) {
    suspend operator fun invoke(
        record: ScanRecord,
        pageIndexes: List<Int>,
        saveAsCopy: Boolean,
        scansDir: File
    ): WorkflowResult<DeletePagesWorkflowResult> {
        if (!File(record.filepath).exists()) {
            return WorkflowResult.Failure(ScanWorkflowError.MissingFiles(listOf(record.filename)))
        }
        val normalized = normalizePageIndexes(record.pageCount, pageIndexes)
        if (normalized.isEmpty() || normalized.size != pageIndexes.distinct().size) {
            return WorkflowResult.Failure(ScanWorkflowError.InvalidPageSelection)
        }
        if (normalized.size >= record.pageCount) {
            return WorkflowResult.Failure(ScanWorkflowError.CannotDeleteAllPages)
        }

        return try {
            WorkflowResult.Success(
                DeletePagesWorkflowResult(
                    remainingPages = deletePdfPagesUseCase(record, normalized, saveAsCopy, scansDir)
                )
            )
        } catch (e: IOException) {
            WorkflowResult.Failure(ScanWorkflowError.StorageWriteFailed(e))
        } catch (t: Throwable) {
            WorkflowResult.Failure(ScanWorkflowError.DeletePagesFailed(t))
        }
    }
}
