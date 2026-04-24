package info.meuse24.pdf_scanner.domain.workflow

import info.meuse24.pdf_scanner.domain.model.Document
import info.meuse24.pdf_scanner.domain.pdf.PdfSecurityOps
import info.meuse24.pdf_scanner.domain.usecase.ProtectPdfUseCase
import java.io.File
import javax.inject.Inject

data class ProtectPdfWorkflowResult(
    val outputFilename: String
)

class ProtectPdfWorkflow @Inject constructor(
    private val protectPdfUseCase: ProtectPdfUseCase,
    private val pdfEditor: PdfSecurityOps,
    private val workflowGuard: DocumentWorkflowGuard
) {
    suspend operator fun invoke(
        record: Document,
        password: String,
        scansDir: File
    ): WorkflowResult<ProtectPdfWorkflowResult> =
        workflowGuard.run(
            record = record,
            failureMapper = ScanWorkflowError::ProtectFailed,
            validate = { input ->
                when {
                    password.isBlank() -> ScanWorkflowError.PasswordRequired
                    pdfEditor.isPdfEncrypted(input) -> ScanWorkflowError.AlreadyProtected
                    else -> null
                }
            }
        ) {
            ProtectPdfWorkflowResult(
                outputFilename = protectPdfUseCase(record, password.trim(), scansDir)
            )
        }
}

