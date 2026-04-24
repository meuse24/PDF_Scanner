package info.meuse24.pdf_scanner.domain.workflow

import info.meuse24.pdf_scanner.domain.model.Document
import info.meuse24.pdf_scanner.domain.usecase.MergePdfsUseCase
import java.io.File
import java.io.IOException
import javax.inject.Inject

data class MergePdfsWorkflowResult(
    val outputFilename: String
)

class MergePdfsWorkflow @Inject constructor(
    private val mergePdfsUseCase: MergePdfsUseCase
) {
    suspend operator fun invoke(
        records: List<Document>,
        outputFilename: String,
        scansDir: File
    ): WorkflowResult<MergePdfsWorkflowResult> {
        if (records.isEmpty()) {
            return WorkflowResult.Failure(ScanWorkflowError.NothingSelected)
        }
        if (records.size < 2) {
            return WorkflowResult.Failure(ScanWorkflowError.NotEnoughScans)
        }

        val trimmedName = outputFilename.trim()
        if (trimmedName.isBlank()) {
            return WorkflowResult.Failure(ScanWorkflowError.NotEnoughScans)
        }

        val missingFiles = records
            .filterNot { File(it.filepath).exists() }
            .map { it.filename }
        if (missingFiles.isNotEmpty()) {
            return WorkflowResult.Failure(ScanWorkflowError.MissingFiles(missingFiles))
        }

        return try {
            WorkflowResult.Success(
                MergePdfsWorkflowResult(
                    outputFilename = mergePdfsUseCase(records, trimmedName, scansDir)
                )
            )
        } catch (e: IOException) {
            WorkflowResult.Failure(ScanWorkflowError.StorageWriteFailed(e))
        } catch (t: Throwable) {
            WorkflowResult.Failure(ScanWorkflowError.MergeFailed(t))
        }
    }
}

