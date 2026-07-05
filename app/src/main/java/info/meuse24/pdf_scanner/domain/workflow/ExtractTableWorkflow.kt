package info.meuse24.pdf_scanner.domain.workflow

import info.meuse24.pdf_scanner.domain.model.Document
import info.meuse24.pdf_scanner.domain.model.OcrPipelineStatus
import info.meuse24.pdf_scanner.domain.model.TableExtractionResult
import info.meuse24.pdf_scanner.domain.usecase.ExtractTableUseCase
import info.meuse24.pdf_scanner.domain.usecase.NoTableFoundException
import java.io.IOException
import javax.inject.Inject

class ExtractTableWorkflow @Inject constructor(
    private val extractTableUseCase: ExtractTableUseCase,
    private val workflowGuard: DocumentWorkflowGuard
) {
    suspend operator fun invoke(
        record: Document,
        languageCode: String,
        onStatus: (OcrPipelineStatus) -> Unit = {},
        onProgress: (current: Int, total: Int) -> Unit = { _, _ -> }
    ): WorkflowResult<TableExtractionResult> =
        workflowGuard.run(
            record = record,
            requireUnencrypted = true,
            failureMapper = ScanWorkflowError::TableExtractionFailed,
            exceptionMapper = { throwable ->
                when (throwable) {
                    is NoTableFoundException -> ScanWorkflowError.NoTableFound
                    // Extraktion schreibt nichts; eine IOException (z. B. beim PDF-Rendering
                    // oder OCR) ist hier kein Speicherschreibfehler wie beim generischen
                    // DocumentWorkflowGuard-Default (StorageWriteFailed), sondern ein
                    // Tabellenextraktionsfehler.
                    is IOException -> ScanWorkflowError.TableExtractionFailed(throwable)
                    else -> null
                }
            }
        ) {
            extractTableUseCase(record, languageCode, onStatus, onProgress)
        }
}
