package info.meuse24.pdf_scanner.domain.workflow

import info.meuse24.pdf_scanner.data.local.ScanRecord
import info.meuse24.pdf_scanner.domain.usecase.UnlockPdfUseCase
import info.meuse24.pdf_scanner.util.PdfEditor
import java.io.File
import java.io.IOException
import javax.inject.Inject

data class UnlockPdfWorkflowResult(
    val outputFilename: String
)

class UnlockPdfWorkflow @Inject constructor(
    private val unlockPdfUseCase: UnlockPdfUseCase,
    private val pdfEditor: PdfEditor
) {
    suspend operator fun invoke(
        record: ScanRecord,
        password: String,
        scansDir: File
    ): WorkflowResult<UnlockPdfWorkflowResult> {
        val input = File(record.filepath)
        if (!input.exists()) {
            return WorkflowResult.Failure(ScanWorkflowError.MissingFiles(listOf(record.filename)))
        }
        if (!pdfEditor.isPdfEncrypted(input)) {
            return WorkflowResult.Failure(ScanWorkflowError.NotProtected)
        }
        if (password.isBlank()) {
            return WorkflowResult.Failure(ScanWorkflowError.PasswordRequired)
        }

        return try {
            WorkflowResult.Success(
                UnlockPdfWorkflowResult(
                    outputFilename = unlockPdfUseCase(record, password.trim(), scansDir)
                )
            )
        } catch (e: PdfEditor.WrongPasswordException) {
            WorkflowResult.Failure(ScanWorkflowError.WrongPassword)
        } catch (e: IOException) {
            WorkflowResult.Failure(ScanWorkflowError.StorageWriteFailed(e))
        } catch (t: Throwable) {
            WorkflowResult.Failure(ScanWorkflowError.UnlockFailed(t))
        }
    }
}
