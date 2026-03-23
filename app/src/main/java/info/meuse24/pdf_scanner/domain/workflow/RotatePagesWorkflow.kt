package info.meuse24.pdf_scanner.domain.workflow

import info.meuse24.pdf_scanner.data.local.ScanRecord
import info.meuse24.pdf_scanner.domain.usecase.RotatePagesUseCase
import info.meuse24.pdf_scanner.util.normalizePageIndexes
import java.io.File
import java.io.IOException
import javax.inject.Inject

class RotatePagesWorkflow @Inject constructor(
    private val rotatePagesUseCase: RotatePagesUseCase
) {
    suspend operator fun invoke(
        record: ScanRecord,
        pageIndexes: List<Int>,
        rotationDegrees: Int,
        saveAsCopy: Boolean,
        scansDir: File
    ): WorkflowResult<Unit> {
        if (!File(record.filepath).exists()) {
            return WorkflowResult.Failure(ScanWorkflowError.MissingFiles(listOf(record.filename)))
        }
        if (normalizePageIndexes(record.pageCount, pageIndexes).size != pageIndexes.distinct().size) {
            return WorkflowResult.Failure(ScanWorkflowError.InvalidPageSelection)
        }
        if (pageIndexes.isEmpty() || rotationDegrees % 90 != 0) {
            return WorkflowResult.Failure(ScanWorkflowError.InvalidPageSelection)
        }

        return try {
            rotatePagesUseCase(record, pageIndexes, rotationDegrees, saveAsCopy, scansDir)
            WorkflowResult.Success(Unit)
        } catch (e: IOException) {
            WorkflowResult.Failure(ScanWorkflowError.StorageWriteFailed(e))
        } catch (t: Throwable) {
            WorkflowResult.Failure(ScanWorkflowError.RotateFailed(t))
        }
    }
}
