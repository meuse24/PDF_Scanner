package info.meuse24.pdf_scanner.domain.workflow

import info.meuse24.pdf_scanner.data.local.ScanRecord
import info.meuse24.pdf_scanner.domain.usecase.HighlightPdfUseCase
import info.meuse24.pdf_scanner.domain.usecase.HighlightRect
import info.meuse24.pdf_scanner.domain.usecase.HighlightStroke
import info.meuse24.pdf_scanner.util.PdfEditor
import java.io.File
import java.io.IOException
import javax.inject.Inject

data class HighlightPdfWorkflowResult(val outputFilename: String)

class HighlightPdfWorkflow @Inject constructor(
    private val highlightPdfUseCase: HighlightPdfUseCase,
    private val pdfEditor: PdfEditor
) {
    suspend operator fun invoke(
        record: ScanRecord,
        strokes: List<HighlightStroke>,
        rects: List<HighlightRect>,
        scansDir: File
    ): WorkflowResult<HighlightPdfWorkflowResult> {
        val input = File(record.filepath)
        if (!input.exists()) {
            return WorkflowResult.Failure(ScanWorkflowError.MissingFiles(listOf(record.filename)))
        }
        if (strokes.isEmpty() && rects.isEmpty()) {
            return WorkflowResult.Failure(ScanWorkflowError.NoHighlightStrokes)
        }
        if (pdfEditor.isPdfEncrypted(input)) {
            return WorkflowResult.Failure(ScanWorkflowError.ProtectedPdfUnsupported)
        }

        return try {
            WorkflowResult.Success(
                HighlightPdfWorkflowResult(
                    outputFilename = highlightPdfUseCase(record, strokes, rects, scansDir)
                )
            )
        } catch (e: IOException) {
            WorkflowResult.Failure(ScanWorkflowError.StorageWriteFailed(e))
        } catch (t: Throwable) {
            WorkflowResult.Failure(ScanWorkflowError.HighlightFailed(t))
        }
    }
}
