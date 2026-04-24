package info.meuse24.pdf_scanner.domain.workflow

import info.meuse24.pdf_scanner.domain.model.Document
import info.meuse24.pdf_scanner.domain.usecase.SplitPdfUseCase
import info.meuse24.pdf_scanner.util.normalizeSplitPoints
import java.io.File
import javax.inject.Inject

data class SplitPdfWorkflowResult(
    val partsCount: Int
)

class SplitPdfWorkflow @Inject constructor(
    private val splitPdfUseCase: SplitPdfUseCase,
    private val workflowGuard: DocumentWorkflowGuard
) {
    suspend operator fun invoke(
        record: Document,
        splitAtPages: List<Int>,
        scansDir: File
    ): WorkflowResult<SplitPdfWorkflowResult> {
        val normalizedSplitPoints = normalizeSplitPoints(record.pageCount, splitAtPages)
        return workflowGuard.run(
            record = record,
            failureMapper = ScanWorkflowError::SplitFailed,
            validate = {
                if (normalizedSplitPoints.isEmpty() || normalizedSplitPoints.size != splitAtPages.size) {
                    ScanWorkflowError.InvalidSplitSelection
                } else {
                    null
                }
            }
        ) {
            SplitPdfWorkflowResult(
                partsCount = splitPdfUseCase(record, normalizedSplitPoints, scansDir)
            )
        }
    }
}

