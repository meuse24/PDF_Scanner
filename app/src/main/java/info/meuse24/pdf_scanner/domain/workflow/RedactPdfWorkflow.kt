package info.meuse24.pdf_scanner.domain.workflow

import info.meuse24.pdf_scanner.data.local.ScanRecord
import info.meuse24.pdf_scanner.domain.usecase.HighlightRect
import info.meuse24.pdf_scanner.domain.usecase.RedactPdfUseCase
import info.meuse24.pdf_scanner.util.PdfEditor
import java.io.File
import java.io.IOException
import javax.inject.Inject

data class RedactPdfWorkflowResult(val outputFilename: String)

class RedactPdfWorkflow @Inject constructor(
    private val redactPdfUseCase: RedactPdfUseCase,
    private val pdfEditor: PdfEditor
) {
    suspend operator fun invoke(
        record: ScanRecord,
        rects: List<HighlightRect>,
        scansDir: File
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
            WorkflowResult.Success(
                RedactPdfWorkflowResult(
                    outputFilename = redactPdfUseCase(record, rects, scansDir)
                )
            )
        } catch (e: IOException) {
            WorkflowResult.Failure(ScanWorkflowError.StorageWriteFailed(e))
        } catch (t: Throwable) {
            WorkflowResult.Failure(ScanWorkflowError.RedactionFailed(t))
        }
    }
}
