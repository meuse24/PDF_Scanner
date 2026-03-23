package info.meuse24.pdf_scanner.domain.workflow

import android.graphics.Bitmap
import info.meuse24.pdf_scanner.data.local.ScanRecord
import info.meuse24.pdf_scanner.domain.usecase.ApplySignatureStampUseCase
import info.meuse24.pdf_scanner.util.PdfEditor
import java.io.File
import java.io.IOException
import javax.inject.Inject

data class SignatureStampWorkflowResult(
    val outputFilename: String
)

class SignatureStampWorkflow @Inject constructor(
    private val applySignatureStampUseCase: ApplySignatureStampUseCase,
    private val pdfEditor: PdfEditor
) {
    suspend operator fun invoke(
        record: ScanRecord,
        signatureBitmap: Bitmap?,
        pageIndex: Int,
        scaleFraction: Float,
        scansDir: File
    ): WorkflowResult<SignatureStampWorkflowResult> {
        val input = File(record.filepath)
        if (!input.exists()) {
            return WorkflowResult.Failure(ScanWorkflowError.MissingFiles(listOf(record.filename)))
        }
        if (signatureBitmap == null) {
            return WorkflowResult.Failure(ScanWorkflowError.SignatureRequired)
        }
        if (pageIndex !in 0 until record.pageCount) {
            return WorkflowResult.Failure(ScanWorkflowError.InvalidPageSelection)
        }
        if (scaleFraction <= 0f) {
            return WorkflowResult.Failure(ScanWorkflowError.SignatureRequired)
        }
        if (pdfEditor.isPdfEncrypted(input)) {
            return WorkflowResult.Failure(ScanWorkflowError.ProtectedPdfUnsupported)
        }

        return try {
            WorkflowResult.Success(
                SignatureStampWorkflowResult(
                    outputFilename = applySignatureStampUseCase(
                        record = record,
                        signatureBitmap = signatureBitmap,
                        pageIndex = pageIndex,
                        scaleFraction = scaleFraction,
                        scansDir = scansDir
                    )
                )
            )
        } catch (e: IOException) {
            WorkflowResult.Failure(ScanWorkflowError.StorageWriteFailed(e))
        } catch (t: Throwable) {
            WorkflowResult.Failure(ScanWorkflowError.SignatureFailed(t))
        }
    }
}
