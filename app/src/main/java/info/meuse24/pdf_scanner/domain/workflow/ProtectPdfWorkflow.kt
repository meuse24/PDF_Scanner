package info.meuse24.pdf_scanner.domain.workflow

import info.meuse24.pdf_scanner.data.local.ScanRecord
import info.meuse24.pdf_scanner.domain.usecase.ProtectPdfUseCase
import info.meuse24.pdf_scanner.util.PdfEditor
import java.io.File
import java.io.IOException
import javax.inject.Inject

data class ProtectPdfWorkflowResult(
    val outputFilename: String
)

class ProtectPdfWorkflow @Inject constructor(
    private val protectPdfUseCase: ProtectPdfUseCase,
    private val pdfEditor: PdfEditor
) {
    suspend operator fun invoke(
        record: ScanRecord,
        password: String,
        scansDir: File
    ): WorkflowResult<ProtectPdfWorkflowResult> {
        val input = File(record.filepath)
        if (!input.exists()) {
            return WorkflowResult.Failure(ScanWorkflowError.MissingFiles(listOf(record.filename)))
        }
        if (password.isBlank()) {
            return WorkflowResult.Failure(ScanWorkflowError.PasswordRequired)
        }
        if (pdfEditor.isPdfEncrypted(input)) {
            return WorkflowResult.Failure(ScanWorkflowError.AlreadyProtected)
        }

        return try {
            WorkflowResult.Success(
                ProtectPdfWorkflowResult(
                    outputFilename = protectPdfUseCase(record, password.trim(), scansDir)
                )
            )
        } catch (e: IOException) {
            WorkflowResult.Failure(ScanWorkflowError.StorageWriteFailed(e))
        } catch (t: Throwable) {
            WorkflowResult.Failure(ScanWorkflowError.ProtectFailed(t))
        }
    }
}
