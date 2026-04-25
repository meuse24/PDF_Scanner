package info.meuse24.pdf_scanner.domain.workflow

import info.meuse24.pdf_scanner.domain.model.Document
import info.meuse24.pdf_scanner.domain.pdf.PdfSecurityOps
import kotlinx.coroutines.CancellationException
import java.io.File
import java.io.IOException
import javax.inject.Inject

class DocumentWorkflowGuard @Inject constructor(
    private val pdfSecurityOps: PdfSecurityOps
) {
    suspend fun <T> run(
        record: Document,
        requireUnencrypted: Boolean = false,
        failureMapper: (Throwable) -> ScanWorkflowError,
        validate: (File) -> ScanWorkflowError? = { null },
        exceptionMapper: (Throwable) -> ScanWorkflowError? = { null },
        block: suspend () -> T
    ): WorkflowResult<T> {
        val input = File(record.filepath)
        if (!input.exists()) {
            return WorkflowResult.Failure(ScanWorkflowError.MissingFiles(listOf(record.filename)))
        }

        validate(input)?.let { error ->
            return WorkflowResult.Failure(error)
        }

        if (requireUnencrypted && pdfSecurityOps.isPdfEncrypted(input)) {
            return WorkflowResult.Failure(ScanWorkflowError.ProtectedPdfUnsupported)
        }

        return try {
            WorkflowResult.Success(block())
        } catch (exception: CancellationException) {
            throw exception
        } catch (throwable: Throwable) {
            exceptionMapper(throwable)?.let { error ->
                return WorkflowResult.Failure(error)
            }
            when (throwable) {
                is IOException -> WorkflowResult.Failure(ScanWorkflowError.StorageWriteFailed(throwable))
                else -> WorkflowResult.Failure(failureMapper(throwable))
            }
        }
    }
}
