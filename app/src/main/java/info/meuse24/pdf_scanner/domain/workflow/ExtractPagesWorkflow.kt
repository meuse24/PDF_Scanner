package info.meuse24.pdf_scanner.domain.workflow

import info.meuse24.pdf_scanner.domain.model.Document
import info.meuse24.pdf_scanner.domain.usecase.ExtractPagesUseCase
import info.meuse24.pdf_scanner.util.normalizePageIndexes
import java.io.File
import javax.inject.Inject

data class ExtractPagesWorkflowResult(
    val outputFilename: String
)

class ExtractPagesWorkflow @Inject constructor(
    private val extractPagesUseCase: ExtractPagesUseCase,
    private val workflowGuard: DocumentWorkflowGuard
) {
    suspend operator fun invoke(
        record: Document,
        pageIndexes: List<Int>,
        scansDir: File
    ): WorkflowResult<ExtractPagesWorkflowResult> {
        val normalized = normalizePageIndexes(record.pageCount, pageIndexes)
        return workflowGuard.run(
            record = record,
            failureMapper = ScanWorkflowError::ExtractPagesFailed,
            validate = {
                if (normalized.isEmpty() || normalized.size != pageIndexes.distinct().size) {
                    ScanWorkflowError.InvalidPageSelection
                } else {
                    null
                }
            }
        ) {
            ExtractPagesWorkflowResult(
                outputFilename = extractPagesUseCase(record, normalized, scansDir)
            )
        }
    }
}

