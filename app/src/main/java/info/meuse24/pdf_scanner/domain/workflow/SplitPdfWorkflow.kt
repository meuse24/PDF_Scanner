package info.meuse24.pdf_scanner.domain.workflow

import info.meuse24.pdf_scanner.data.local.ScanRecord
import info.meuse24.pdf_scanner.domain.usecase.SplitPdfUseCase
import info.meuse24.pdf_scanner.util.normalizeSplitPoints
import java.io.File
import java.io.IOException
import javax.inject.Inject

data class SplitPdfWorkflowResult(
    val partsCount: Int
)

class SplitPdfWorkflow @Inject constructor(
    private val splitPdfUseCase: SplitPdfUseCase
) {
    suspend operator fun invoke(
        record: ScanRecord,
        splitAtPages: List<Int>,
        scansDir: File
    ): WorkflowResult<SplitPdfWorkflowResult> {
        if (!File(record.filepath).exists()) {
            return WorkflowResult.Failure(ScanWorkflowError.MissingFiles(listOf(record.filename)))
        }

        val normalizedSplitPoints = normalizeSplitPoints(record.pageCount, splitAtPages)
        if (normalizedSplitPoints.isEmpty() || normalizedSplitPoints.size != splitAtPages.size) {
            return WorkflowResult.Failure(ScanWorkflowError.InvalidSplitSelection)
        }

        return try {
            WorkflowResult.Success(
                SplitPdfWorkflowResult(
                    partsCount = splitPdfUseCase(record, normalizedSplitPoints, scansDir)
                )
            )
        } catch (e: IOException) {
            WorkflowResult.Failure(ScanWorkflowError.StorageWriteFailed(e))
        } catch (t: Throwable) {
            WorkflowResult.Failure(ScanWorkflowError.SplitFailed(t))
        }
    }
}
