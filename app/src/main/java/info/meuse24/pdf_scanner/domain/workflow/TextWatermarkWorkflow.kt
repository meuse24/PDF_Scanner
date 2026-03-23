package info.meuse24.pdf_scanner.domain.workflow

import info.meuse24.pdf_scanner.data.local.ScanRecord
import info.meuse24.pdf_scanner.domain.usecase.ApplyTextWatermarkUseCase
import java.io.File
import java.io.IOException
import javax.inject.Inject

data class TextWatermarkWorkflowResult(
    val outputFilename: String
)

class TextWatermarkWorkflow @Inject constructor(
    private val applyTextWatermarkUseCase: ApplyTextWatermarkUseCase
) {
    suspend operator fun invoke(
        record: ScanRecord,
        text: String,
        scansDir: File
    ): WorkflowResult<TextWatermarkWorkflowResult> {
        if (!File(record.filepath).exists()) {
            return WorkflowResult.Failure(ScanWorkflowError.MissingFiles(listOf(record.filename)))
        }
        if (text.isBlank()) {
            return WorkflowResult.Failure(ScanWorkflowError.InvalidWatermarkText)
        }

        return try {
            WorkflowResult.Success(
                TextWatermarkWorkflowResult(
                    outputFilename = applyTextWatermarkUseCase(record, text.trim(), scansDir)
                )
            )
        } catch (e: IOException) {
            WorkflowResult.Failure(ScanWorkflowError.StorageWriteFailed(e))
        } catch (t: Throwable) {
            WorkflowResult.Failure(ScanWorkflowError.TextWatermarkFailed(t))
        }
    }
}
