package info.meuse24.pdf_scanner.domain.workflow

import info.meuse24.pdf_scanner.domain.model.Document
import info.meuse24.pdf_scanner.domain.model.PdfMetadata
import info.meuse24.pdf_scanner.domain.usecase.UpdatePdfMetadataUseCase
import javax.inject.Inject

class UpdatePdfMetadataWorkflow @Inject constructor(
    private val updatePdfMetadataUseCase: UpdatePdfMetadataUseCase,
    private val workflowGuard: DocumentWorkflowGuard
) {
    suspend operator fun invoke(
        record: Document,
        metadata: PdfMetadata
    ): WorkflowResult<Unit> =
        workflowGuard.run(
            record = record,
            requireUnencrypted = true,
            failureMapper = ScanWorkflowError::PdfMetadataFailed
        ) {
            updatePdfMetadataUseCase(record, metadata)
            Unit
        }
}

