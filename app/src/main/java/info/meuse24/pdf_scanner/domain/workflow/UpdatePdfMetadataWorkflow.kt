package info.meuse24.pdf_scanner.domain.workflow

import info.meuse24.pdf_scanner.data.local.ScanRecord
import info.meuse24.pdf_scanner.domain.usecase.UpdatePdfMetadataUseCase
import info.meuse24.pdf_scanner.util.PdfMetadata
import java.io.File
import java.io.IOException
import javax.inject.Inject

class UpdatePdfMetadataWorkflow @Inject constructor(
    private val updatePdfMetadataUseCase: UpdatePdfMetadataUseCase
) {
    suspend operator fun invoke(
        record: ScanRecord,
        metadata: PdfMetadata
    ): WorkflowResult<Unit> {
        if (!File(record.filepath).exists()) {
            return WorkflowResult.Failure(ScanWorkflowError.MissingFiles(listOf(record.filename)))
        }
        if (record.isEncrypted) {
            return WorkflowResult.Failure(ScanWorkflowError.ProtectedPdfUnsupported)
        }

        return try {
            updatePdfMetadataUseCase(record, metadata)
            WorkflowResult.Success(Unit)
        } catch (e: IOException) {
            WorkflowResult.Failure(ScanWorkflowError.StorageWriteFailed(e))
        } catch (t: Throwable) {
            WorkflowResult.Failure(ScanWorkflowError.PdfMetadataFailed(t))
        }
    }
}
