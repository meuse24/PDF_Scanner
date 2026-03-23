package info.meuse24.pdf_scanner.domain.workflow

import info.meuse24.pdf_scanner.data.local.ScanRecord
import info.meuse24.pdf_scanner.domain.usecase.ExtractPagesUseCase
import info.meuse24.pdf_scanner.util.normalizePageIndexes
import java.io.File
import java.io.IOException
import javax.inject.Inject

data class ExtractPagesWorkflowResult(
    val outputFilename: String
)

class ExtractPagesWorkflow @Inject constructor(
    private val extractPagesUseCase: ExtractPagesUseCase
) {
    suspend operator fun invoke(
        record: ScanRecord,
        pageIndexes: List<Int>,
        scansDir: File
    ): WorkflowResult<ExtractPagesWorkflowResult> {
        if (!File(record.filepath).exists()) {
            return WorkflowResult.Failure(ScanWorkflowError.MissingFiles(listOf(record.filename)))
        }
        val normalized = normalizePageIndexes(record.pageCount, pageIndexes)
        if (normalized.isEmpty() || normalized.size != pageIndexes.distinct().size) {
            return WorkflowResult.Failure(ScanWorkflowError.InvalidPageSelection)
        }

        return try {
            WorkflowResult.Success(
                ExtractPagesWorkflowResult(
                    outputFilename = extractPagesUseCase(record, normalized, scansDir)
                )
            )
        } catch (e: IOException) {
            WorkflowResult.Failure(ScanWorkflowError.StorageWriteFailed(e))
        } catch (t: Throwable) {
            WorkflowResult.Failure(ScanWorkflowError.ExtractPagesFailed(t))
        }
    }
}
