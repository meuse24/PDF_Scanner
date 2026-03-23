package info.meuse24.pdf_scanner.domain.workflow

import info.meuse24.pdf_scanner.data.local.ScanRecord
import info.meuse24.pdf_scanner.domain.usecase.MakeSearchableUseCase
import java.io.File
import java.io.IOException
import javax.inject.Inject

data class MakeSearchableWorkflowResult(
    val processedCount: Int,
    val firstFilename: String
)

class MakeSearchableWorkflow @Inject constructor(
    private val makeSearchableUseCase: MakeSearchableUseCase
) {
    suspend operator fun invoke(
        records: List<ScanRecord>,
        languageCode: String,
        onProgress: (Int, Int) -> Unit = { _, _ -> }
    ): WorkflowResult<MakeSearchableWorkflowResult> {
        if (records.isEmpty()) {
            return WorkflowResult.Failure(ScanWorkflowError.NothingSelected)
        }

        val pending = records.filter { !it.isSearchable }
        if (pending.isEmpty()) {
            return WorkflowResult.Failure(ScanWorkflowError.NoEligibleScans)
        }

        val missingFiles = pending
            .filterNot { File(it.filepath).exists() }
            .map { it.filename }
        if (missingFiles.isNotEmpty()) {
            return WorkflowResult.Failure(ScanWorkflowError.MissingFiles(missingFiles))
        }

        return try {
            val processedCount = makeSearchableUseCase(pending, languageCode, onProgress)
            WorkflowResult.Success(
                MakeSearchableWorkflowResult(
                    processedCount = processedCount,
                    firstFilename = pending.first().filename
                )
            )
        } catch (e: IOException) {
            WorkflowResult.Failure(ScanWorkflowError.StorageWriteFailed(e))
        } catch (t: Throwable) {
            WorkflowResult.Failure(ScanWorkflowError.OcrFailed(t))
        }
    }
}
