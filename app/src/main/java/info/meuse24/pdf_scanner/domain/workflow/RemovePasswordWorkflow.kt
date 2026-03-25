package info.meuse24.pdf_scanner.domain.workflow

import info.meuse24.pdf_scanner.data.local.ScanRecord
import info.meuse24.pdf_scanner.domain.usecase.RemovePasswordUseCase
import info.meuse24.pdf_scanner.util.PdfEditor
import java.io.File
import java.io.IOException
import javax.inject.Inject

data class RemovePasswordWorkflowResult(val outputFilename: String)

class RemovePasswordWorkflow @Inject constructor(
    private val removePasswordUseCase: RemovePasswordUseCase,
    private val pdfEditor: PdfEditor
) {
    suspend operator fun invoke(
        record: ScanRecord,
        scansDir: File
    ): WorkflowResult<RemovePasswordWorkflowResult> {
        if (!File(record.filepath).exists()) {
            return WorkflowResult.Failure(ScanWorkflowError.MissingFiles(listOf(record.filename)))
        }
        if (!pdfEditor.isPdfEncrypted(File(record.filepath))) {
            return WorkflowResult.Failure(ScanWorkflowError.NotProtected)
        }
        return try {
            WorkflowResult.Success(
                RemovePasswordWorkflowResult(
                    outputFilename = removePasswordUseCase(record, scansDir)
                )
            )
        } catch (e: PdfEditor.PasswordRequiredException) {
            WorkflowResult.Failure(ScanWorkflowError.PasswordRequiredToRemove)
        } catch (e: IOException) {
            WorkflowResult.Failure(ScanWorkflowError.StorageWriteFailed(e))
        } catch (t: Throwable) {
            WorkflowResult.Failure(ScanWorkflowError.RemovePasswordFailed(t))
        }
    }
}
