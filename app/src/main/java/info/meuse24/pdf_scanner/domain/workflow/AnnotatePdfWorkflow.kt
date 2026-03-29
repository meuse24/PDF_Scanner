package info.meuse24.pdf_scanner.domain.workflow

import info.meuse24.pdf_scanner.data.local.ScanRecord
import info.meuse24.pdf_scanner.domain.usecase.AnnotatePdfUseCase
import info.meuse24.pdf_scanner.domain.usecase.AnnotationOval
import info.meuse24.pdf_scanner.domain.usecase.AnnotationRect
import info.meuse24.pdf_scanner.domain.usecase.AnnotationStroke
import info.meuse24.pdf_scanner.domain.usecase.AnnotationText
import info.meuse24.pdf_scanner.util.PdfEditor
import java.io.File
import java.io.IOException
import javax.inject.Inject

data class AnnotatePdfWorkflowResult(val outputFilename: String)

class AnnotatePdfWorkflow @Inject constructor(
    private val annotatePdfUseCase: AnnotatePdfUseCase,
    private val pdfEditor: PdfEditor
) {
    suspend operator fun invoke(
        record: ScanRecord,
        strokes: List<AnnotationStroke>,
        rects: List<AnnotationRect>,
        ovals: List<AnnotationOval>,
        comments: List<AnnotationText>,
        scansDir: File
    ): WorkflowResult<AnnotatePdfWorkflowResult> {
        val input = File(record.filepath)
        if (!input.exists()) {
            return WorkflowResult.Failure(ScanWorkflowError.MissingFiles(listOf(record.filename)))
        }
        if (strokes.isEmpty() && rects.isEmpty() && ovals.isEmpty() && comments.isEmpty()) {
            return WorkflowResult.Failure(ScanWorkflowError.NoAnnotations)
        }
        if (pdfEditor.isPdfEncrypted(input)) {
            return WorkflowResult.Failure(ScanWorkflowError.ProtectedPdfUnsupported)
        }

        return try {
            WorkflowResult.Success(
                AnnotatePdfWorkflowResult(
                    outputFilename = annotatePdfUseCase(record, strokes, rects, ovals, comments, scansDir)
                )
            )
        } catch (e: IOException) {
            WorkflowResult.Failure(ScanWorkflowError.StorageWriteFailed(e))
        } catch (t: Throwable) {
            WorkflowResult.Failure(ScanWorkflowError.AnnotateFailed(t))
        }
    }
}
