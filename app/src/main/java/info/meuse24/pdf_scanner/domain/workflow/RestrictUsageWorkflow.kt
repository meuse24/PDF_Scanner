package info.meuse24.pdf_scanner.domain.workflow

import info.meuse24.pdf_scanner.domain.model.Document
import info.meuse24.pdf_scanner.domain.pdf.PdfPasswordRequiredException
import info.meuse24.pdf_scanner.domain.usecase.RestrictUsageUseCase
import java.io.File
import javax.inject.Inject

data class RestrictUsageWorkflowResult(val outputFilename: String)

class RestrictUsageWorkflow @Inject constructor(
    private val restrictUsageUseCase: RestrictUsageUseCase,
    private val workflowGuard: DocumentWorkflowGuard
) {
    suspend operator fun invoke(
        record: Document,
        scansDir: File,
        ownerPassword: String,
        canPrint: Boolean,
        canCopy: Boolean,
        canEdit: Boolean
    ): WorkflowResult<RestrictUsageWorkflowResult> =
        workflowGuard.run(
            record = record,
            failureMapper = ScanWorkflowError::UsageRestrictionFailed,
            exceptionMapper = { throwable ->
                when (throwable) {
                    is PdfPasswordRequiredException -> ScanWorkflowError.PasswordRequiredToRemove
                    else -> null
                }
            }
        ) {
            RestrictUsageWorkflowResult(
                outputFilename = restrictUsageUseCase(
                    record, scansDir, ownerPassword, canPrint, canCopy, canEdit
                )
            )
        }
}

