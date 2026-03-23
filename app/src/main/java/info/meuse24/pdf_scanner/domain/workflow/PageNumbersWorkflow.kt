package info.meuse24.pdf_scanner.domain.workflow

import info.meuse24.pdf_scanner.data.local.ScanRecord
import info.meuse24.pdf_scanner.domain.usecase.AddPageNumbersUseCase
import java.io.File
import java.io.IOException
import javax.inject.Inject

data class PageNumbersWorkflowResult(
    val outputFilename: String
)

class PageNumbersWorkflow @Inject constructor(
    private val addPageNumbersUseCase: AddPageNumbersUseCase
) {
    suspend operator fun invoke(
        record: ScanRecord,
        scansDir: File
    ): WorkflowResult<PageNumbersWorkflowResult> {
        if (!File(record.filepath).exists()) {
            return WorkflowResult.Failure(ScanWorkflowError.MissingFiles(listOf(record.filename)))
        }

        return try {
            WorkflowResult.Success(
                PageNumbersWorkflowResult(
                    outputFilename = addPageNumbersUseCase(record, scansDir)
                )
            )
        } catch (e: IOException) {
            WorkflowResult.Failure(ScanWorkflowError.StorageWriteFailed(e))
        } catch (t: Throwable) {
            WorkflowResult.Failure(ScanWorkflowError.PageNumbersFailed(t))
        }
    }
}
