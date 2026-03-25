package info.meuse24.pdf_scanner.domain.workflow

import info.meuse24.pdf_scanner.data.local.ScanRecord
import info.meuse24.pdf_scanner.domain.usecase.RestrictUsageUseCase
import info.meuse24.pdf_scanner.util.PdfEditor
import java.io.File
import java.io.IOException
import javax.inject.Inject

data class RestrictUsageWorkflowResult(val outputFilename: String)

class RestrictUsageWorkflow @Inject constructor(
    private val restrictUsageUseCase: RestrictUsageUseCase,
    private val pdfEditor: PdfEditor
) {
    suspend operator fun invoke(
        record: ScanRecord,
        scansDir: File,
        ownerPassword: String,
        canPrint: Boolean,
        canCopy: Boolean,
        canEdit: Boolean
    ): WorkflowResult<RestrictUsageWorkflowResult> {
        if (!File(record.filepath).exists()) {
            return WorkflowResult.Failure(ScanWorkflowError.MissingFiles(listOf(record.filename)))
        }
        return try {
            WorkflowResult.Success(
                RestrictUsageWorkflowResult(
                    outputFilename = restrictUsageUseCase(
                        record, scansDir, ownerPassword, canPrint, canCopy, canEdit
                    )
                )
            )
        } catch (e: PdfEditor.PasswordRequiredException) {
            WorkflowResult.Failure(ScanWorkflowError.PasswordRequiredToRemove)
        } catch (e: IOException) {
            WorkflowResult.Failure(ScanWorkflowError.StorageWriteFailed(e))
        } catch (t: Throwable) {
            WorkflowResult.Failure(ScanWorkflowError.UsageRestrictionFailed(t))
        }
    }
}
