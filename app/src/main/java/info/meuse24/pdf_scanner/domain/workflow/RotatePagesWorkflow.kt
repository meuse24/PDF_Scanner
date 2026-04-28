package info.meuse24.pdf_scanner.domain.workflow

import info.meuse24.pdf_scanner.domain.model.Document
import info.meuse24.pdf_scanner.domain.usecase.RotatePagesUseCase
import info.meuse24.pdf_scanner.domain.common.normalizePageIndexes
import java.io.File
import javax.inject.Inject

class RotatePagesWorkflow @Inject constructor(
    private val rotatePagesUseCase: RotatePagesUseCase,
    private val workflowGuard: DocumentWorkflowGuard
) {
    suspend operator fun invoke(
        record: Document,
        pageIndexes: List<Int>,
        rotationDegrees: Int,
        saveAsCopy: Boolean,
        scansDir: File
    ): WorkflowResult<Unit> {
        val normalized = normalizePageIndexes(record.pageCount, pageIndexes)
        return workflowGuard.run(
            record = record,
            failureMapper = ScanWorkflowError::RotateFailed,
            validate = {
                if (normalized.size != pageIndexes.distinct().size || pageIndexes.isEmpty() || rotationDegrees % 90 != 0) {
                    ScanWorkflowError.InvalidPageSelection
                } else {
                    null
                }
            }
        ) {
            rotatePagesUseCase(record, pageIndexes, rotationDegrees, saveAsCopy, scansDir)
            Unit
        }
    }
}

