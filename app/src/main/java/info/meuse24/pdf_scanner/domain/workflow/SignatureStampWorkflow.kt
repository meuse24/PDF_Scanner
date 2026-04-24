package info.meuse24.pdf_scanner.domain.workflow

import android.graphics.Bitmap
import info.meuse24.pdf_scanner.domain.model.Document
import info.meuse24.pdf_scanner.domain.usecase.ApplySignatureStampUseCase
import java.io.File
import javax.inject.Inject

data class SignatureStampWorkflowResult(
    val outputFilename: String
)

class SignatureStampWorkflow @Inject constructor(
    private val applySignatureStampUseCase: ApplySignatureStampUseCase,
    private val workflowGuard: DocumentWorkflowGuard
) {
    suspend operator fun invoke(
        record: Document,
        signatureBitmap: Bitmap?,
        pageIndex: Int,
        scaleFraction: Float,
        scansDir: File
    ): WorkflowResult<SignatureStampWorkflowResult> =
        workflowGuard.run(
            record = record,
            requireUnencrypted = true,
            failureMapper = ScanWorkflowError::SignatureFailed,
            validate = {
                when {
                    signatureBitmap == null -> ScanWorkflowError.SignatureRequired
                    pageIndex !in 0 until record.pageCount -> ScanWorkflowError.InvalidPageSelection
                    scaleFraction <= 0f -> ScanWorkflowError.InvalidSignatureScale
                    else -> null
                }
            }
        ) {
            val requiredSignature = requireNotNull(signatureBitmap)
            SignatureStampWorkflowResult(
                outputFilename = applySignatureStampUseCase(
                    record = record,
                    signatureBitmap = requiredSignature,
                    pageIndex = pageIndex,
                    scaleFraction = scaleFraction,
                    scansDir = scansDir
                )
            )
        }
}

