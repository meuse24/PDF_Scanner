package info.meuse24.pdf_scanner.domain.workflow

import info.meuse24.pdf_scanner.domain.model.Document
import info.meuse24.pdf_scanner.domain.usecase.AppendResult
import info.meuse24.pdf_scanner.domain.usecase.AppendSourceEncryptedException
import info.meuse24.pdf_scanner.domain.usecase.AppendSource
import info.meuse24.pdf_scanner.domain.usecase.AppendTargetMissingException
import info.meuse24.pdf_scanner.domain.usecase.AppendTargetEncryptedException
import info.meuse24.pdf_scanner.domain.usecase.AppendToPdfUseCase
import java.io.File
import java.io.IOException
import javax.inject.Inject

data class AppendToPdfWorkflowResult(
    val pageCount: Int,
    val fileSize: Long,
    val appendedPageCount: Int
)

class AppendToPdfWorkflow @Inject constructor(
    private val appendToPdfUseCase: AppendToPdfUseCase
) {
    suspend operator fun invoke(
        target: Document,
        source: AppendSource
    ): WorkflowResult<AppendToPdfWorkflowResult> {
        if (!File(target.filepath).exists()) {
            return WorkflowResult.Failure(
                ScanWorkflowError.AppendFailed(AppendTargetMissingException())
            )
        }
        if (target.isEncrypted) {
            return WorkflowResult.Failure(ScanWorkflowError.AppendTargetEncrypted)
        }

        return try {
            val result: AppendResult = appendToPdfUseCase(target, source)
            WorkflowResult.Success(
                AppendToPdfWorkflowResult(
                    pageCount = result.pageCount,
                    fileSize = result.fileSize,
                    appendedPageCount = result.appendedPageCount
                )
            )
        } catch (_: AppendTargetEncryptedException) {
            WorkflowResult.Failure(ScanWorkflowError.AppendTargetEncrypted)
        } catch (_: AppendSourceEncryptedException) {
            WorkflowResult.Failure(ScanWorkflowError.AppendSourceEncrypted)
        } catch (exception: IOException) {
            WorkflowResult.Failure(ScanWorkflowError.StorageWriteFailed(exception))
        } catch (throwable: Throwable) {
            WorkflowResult.Failure(ScanWorkflowError.AppendFailed(throwable))
        }
    }
}

