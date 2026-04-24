package info.meuse24.pdf_scanner.domain.workflow

import info.meuse24.pdf_scanner.domain.model.Document
import info.meuse24.pdf_scanner.domain.usecase.HighlightPdfUseCase
import info.meuse24.pdf_scanner.domain.usecase.HighlightRect
import info.meuse24.pdf_scanner.domain.usecase.HighlightStroke
import java.io.File
import javax.inject.Inject

data class HighlightPdfWorkflowResult(val outputFilename: String)

class HighlightPdfWorkflow @Inject constructor(
    private val highlightPdfUseCase: HighlightPdfUseCase,
    private val workflowGuard: DocumentWorkflowGuard
) {
    suspend operator fun invoke(
        record: Document,
        strokes: List<HighlightStroke>,
        rects: List<HighlightRect>,
        scansDir: File
    ): WorkflowResult<HighlightPdfWorkflowResult> =
        workflowGuard.run(
            record = record,
            requireUnencrypted = true,
            failureMapper = ScanWorkflowError::HighlightFailed,
            validate = {
                if (strokes.isEmpty() && rects.isEmpty()) {
                    ScanWorkflowError.NoHighlightStrokes
                } else {
                    null
                }
            }
        ) {
            HighlightPdfWorkflowResult(
                outputFilename = highlightPdfUseCase(record, strokes, rects, scansDir)
            )
        }
}

