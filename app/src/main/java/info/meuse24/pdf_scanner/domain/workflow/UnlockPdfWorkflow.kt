package info.meuse24.pdf_scanner.domain.workflow

import info.meuse24.pdf_scanner.domain.model.Document
import info.meuse24.pdf_scanner.domain.pdf.PdfSecurityOps
import info.meuse24.pdf_scanner.domain.pdf.PdfWrongPasswordException
import info.meuse24.pdf_scanner.domain.usecase.UnlockPdfUseCase
import java.io.File
import javax.inject.Inject

data class UnlockPdfWorkflowResult(
    val outputFilename: String
)

class UnlockPdfWorkflow @Inject constructor(
    private val unlockPdfUseCase: UnlockPdfUseCase,
    private val pdfEditor: PdfSecurityOps,
    private val workflowGuard: DocumentWorkflowGuard
) {
    suspend operator fun invoke(
        record: Document,
        password: String,
        scansDir: File
    ): WorkflowResult<UnlockPdfWorkflowResult> =
        workflowGuard.run(
            record = record,
            failureMapper = ScanWorkflowError::UnlockFailed,
            validate = { input ->
                when {
                    !pdfEditor.isPdfEncrypted(input) -> ScanWorkflowError.NotProtected
                    password.isBlank() -> ScanWorkflowError.PasswordRequired
                    else -> null
                }
            },
            exceptionMapper = { throwable ->
                when (throwable) {
                    is PdfWrongPasswordException -> ScanWorkflowError.WrongPassword
                    else -> null
                }
            }
        ) {
            UnlockPdfWorkflowResult(
                outputFilename = unlockPdfUseCase(record, password.trim(), scansDir)
            )
        }
}

