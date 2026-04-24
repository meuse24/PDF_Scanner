package info.meuse24.pdf_scanner.domain.workflow

import info.meuse24.pdf_scanner.domain.model.Document
import info.meuse24.pdf_scanner.domain.usecase.AnnotatePdfUseCase
import info.meuse24.pdf_scanner.domain.usecase.AnnotationOval
import info.meuse24.pdf_scanner.domain.usecase.AnnotationRect
import info.meuse24.pdf_scanner.domain.usecase.AnnotationStroke
import info.meuse24.pdf_scanner.domain.usecase.AnnotationText
import java.io.File
import javax.inject.Inject

data class AnnotatePdfWorkflowResult(val outputFilename: String)

class AnnotatePdfWorkflow @Inject constructor(
    private val annotatePdfUseCase: AnnotatePdfUseCase,
    private val workflowGuard: DocumentWorkflowGuard
) {
    suspend operator fun invoke(
        record: Document,
        strokes: List<AnnotationStroke>,
        rects: List<AnnotationRect>,
        ovals: List<AnnotationOval>,
        comments: List<AnnotationText>,
        scansDir: File
    ): WorkflowResult<AnnotatePdfWorkflowResult> =
        workflowGuard.run(
            record = record,
            requireUnencrypted = true,
            failureMapper = ScanWorkflowError::AnnotateFailed,
            validate = {
                if (strokes.isEmpty() && rects.isEmpty() && ovals.isEmpty() && comments.isEmpty()) {
                    ScanWorkflowError.NoAnnotations
                } else {
                    null
                }
            }
        ) {
            AnnotatePdfWorkflowResult(
                outputFilename = annotatePdfUseCase(record, strokes, rects, ovals, comments, scansDir)
            )
        }
}

