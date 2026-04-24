package info.meuse24.pdf_scanner.domain.workflow

import info.meuse24.pdf_scanner.domain.model.Document
import info.meuse24.pdf_scanner.domain.pdf.PdfPasswordRequiredException
import info.meuse24.pdf_scanner.domain.pdf.PdfSecurityOps
import info.meuse24.pdf_scanner.domain.usecase.RemovePasswordUseCase
import java.io.File
import javax.inject.Inject

data class RemovePasswordWorkflowResult(val outputFilename: String)

class RemovePasswordWorkflow @Inject constructor(
    private val removePasswordUseCase: RemovePasswordUseCase,
    private val pdfEditor: PdfSecurityOps,
    private val workflowGuard: DocumentWorkflowGuard
) {
    suspend operator fun invoke(
        record: Document,
        scansDir: File
    ): WorkflowResult<RemovePasswordWorkflowResult> =
        workflowGuard.run(
            record = record,
            failureMapper = ScanWorkflowError::RemovePasswordFailed,
            validate = { input ->
                if (!pdfEditor.isPdfEncrypted(input)) {
                    ScanWorkflowError.NotProtected
                } else {
                    null
                }
            },
            exceptionMapper = { throwable ->
                when (throwable) {
                    is PdfPasswordRequiredException -> ScanWorkflowError.PasswordRequiredToRemove
                    else -> null
                }
            }
        ) {
            RemovePasswordWorkflowResult(
                outputFilename = removePasswordUseCase(record, scansDir)
            )
        }
}

