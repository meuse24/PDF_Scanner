package info.meuse24.pdf_scanner.domain.workflow

import info.meuse24.pdf_scanner.domain.model.Document
import info.meuse24.pdf_scanner.domain.usecase.DuplicatePagesUseCase
import info.meuse24.pdf_scanner.util.normalizePageIndexes
import java.io.File
import javax.inject.Inject

data class DuplicatePagesWorkflowResult(
    val outputFilename: String
)

class DuplicatePagesWorkflow @Inject constructor(
    private val duplicatePagesUseCase: DuplicatePagesUseCase,
    private val workflowGuard: DocumentWorkflowGuard
) {
    suspend operator fun invoke(
        record: Document,
        pageIndexes: List<Int>,
        scansDir: File
    ): WorkflowResult<DuplicatePagesWorkflowResult> {
        val normalized = normalizePageIndexes(record.pageCount, pageIndexes)
        return workflowGuard.run(
            record = record,
            failureMapper = ScanWorkflowError::DuplicatePagesFailed,
            validate = {
                if (normalized.isEmpty() || normalized.size != pageIndexes.distinct().size) {
                    ScanWorkflowError.InvalidPageSelection
                } else {
                    null
                }
            }
        ) {
            DuplicatePagesWorkflowResult(
                outputFilename = duplicatePagesUseCase(record, normalized, scansDir)
            )
        }
    }
}

