package info.meuse24.pdf_scanner.domain.workflow

import info.meuse24.pdf_scanner.data.local.ScanRecord
import info.meuse24.pdf_scanner.domain.usecase.RemoveTextLayerUseCase
import java.io.File
import java.io.IOException
import javax.inject.Inject

data class RemoveTextLayerWorkflowResult(val outputFilename: String)

class RemoveTextLayerWorkflow @Inject constructor(
    private val removeTextLayerUseCase: RemoveTextLayerUseCase
) {
    suspend operator fun invoke(
        record: ScanRecord,
        scansDir: File
    ): WorkflowResult<RemoveTextLayerWorkflowResult> {
        if (!File(record.filepath).exists()) {
            return WorkflowResult.Failure(ScanWorkflowError.MissingFiles(listOf(record.filename)))
        }
        if (!record.isSearchable) {
            return WorkflowResult.Failure(ScanWorkflowError.NotSearchable)
        }
        return try {
            WorkflowResult.Success(
                RemoveTextLayerWorkflowResult(
                    outputFilename = removeTextLayerUseCase(record, scansDir)
                )
            )
        } catch (e: IOException) {
            WorkflowResult.Failure(ScanWorkflowError.StorageWriteFailed(e))
        } catch (t: Throwable) {
            WorkflowResult.Failure(ScanWorkflowError.RemoveTextLayerFailed(t))
        }
    }
}
