package info.meuse24.pdf_scanner.domain.workflow

import info.meuse24.pdf_scanner.data.local.ScanRecord
import info.meuse24.pdf_scanner.domain.usecase.DeleteScansUseCase
import info.meuse24.pdf_scanner.domain.usecase.RedactPdfUseCase
import info.meuse24.pdf_scanner.domain.usecase.RedactionRect
import info.meuse24.pdf_scanner.util.PdfEditor
import java.io.File
import java.io.IOException
import javax.inject.Inject

data class RedactPdfWorkflowResult(val outputFilename: String)

class RedactPdfWorkflow @Inject constructor(
    private val redactPdfUseCase: RedactPdfUseCase,
    private val makeSearchableWorkflow: MakeSearchableWorkflow,
    private val deleteScansUseCase: DeleteScansUseCase,
    private val pdfEditor: PdfEditor
) {
    suspend operator fun invoke(
        record: ScanRecord,
        rects: List<RedactionRect>,
        scansDir: File,
        makeSearchable: Boolean = false,
        languageCode: String = "en"
    ): WorkflowResult<RedactPdfWorkflowResult> {
        val input = File(record.filepath)
        if (!input.exists()) {
            return WorkflowResult.Failure(ScanWorkflowError.MissingFiles(listOf(record.filename)))
        }
        if (rects.isEmpty()) {
            return WorkflowResult.Failure(ScanWorkflowError.NoRedactionAreas)
        }
        if (pdfEditor.isPdfEncrypted(input)) {
            return WorkflowResult.Failure(ScanWorkflowError.ProtectedPdfUnsupported)
        }

        return try {
            val outputRecord = redactPdfUseCase(record, rects, scansDir)
            if (makeSearchable) {
                when (val searchableResult = makeSearchableWorkflow(
                    records = listOf(outputRecord),
                    languageCode = languageCode,
                    force = false
                )) {
                    is WorkflowResult.Success -> Unit
                    is WorkflowResult.Failure -> {
                        runCatching { deleteScansUseCase(listOf(outputRecord)) }
                        return WorkflowResult.Failure(mapSearchableFollowUpError(searchableResult.error))
                    }
                }
            }
            WorkflowResult.Success(
                RedactPdfWorkflowResult(
                    outputFilename = outputRecord.filename
                )
            )
        } catch (e: IOException) {
            WorkflowResult.Failure(ScanWorkflowError.StorageWriteFailed(e))
        } catch (t: Throwable) {
            WorkflowResult.Failure(ScanWorkflowError.RedactionFailed(t))
        }
    }
}

internal fun mapSearchableFollowUpError(error: ScanWorkflowError): ScanWorkflowError {
    return when (error) {
        is ScanWorkflowError.OcrFailed,
        is ScanWorkflowError.StorageWriteFailed -> error
        else -> ScanWorkflowError.RedactionFailed(
            IllegalStateException("Unexpected searchable follow-up failure after redaction: $error", error.cause)
        )
    }
}
