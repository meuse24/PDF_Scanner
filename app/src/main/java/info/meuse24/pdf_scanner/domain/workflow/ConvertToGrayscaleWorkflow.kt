package info.meuse24.pdf_scanner.domain.workflow

import info.meuse24.pdf_scanner.data.local.ScanRecord
import info.meuse24.pdf_scanner.domain.usecase.ConvertToGrayscaleUseCase
import java.io.File
import java.io.IOException
import javax.inject.Inject

data class ConvertToGrayscaleWorkflowResult(val outputFilename: String)

class ConvertToGrayscaleWorkflow @Inject constructor(
    private val convertToGrayscaleUseCase: ConvertToGrayscaleUseCase
) {
    suspend operator fun invoke(
        record: ScanRecord,
        scansDir: File
    ): WorkflowResult<ConvertToGrayscaleWorkflowResult> {
        if (!File(record.filepath).exists()) {
            return WorkflowResult.Failure(ScanWorkflowError.MissingFiles(listOf(record.filename)))
        }
        if (record.isEncrypted) {
            return WorkflowResult.Failure(ScanWorkflowError.ProtectedPdfUnsupported)
        }
        return try {
            WorkflowResult.Success(
                ConvertToGrayscaleWorkflowResult(
                    outputFilename = convertToGrayscaleUseCase(record, scansDir)
                )
            )
        } catch (e: IOException) {
            WorkflowResult.Failure(ScanWorkflowError.StorageWriteFailed(e))
        } catch (t: Throwable) {
            WorkflowResult.Failure(ScanWorkflowError.GrayscaleFailed(t))
        }
    }
}
