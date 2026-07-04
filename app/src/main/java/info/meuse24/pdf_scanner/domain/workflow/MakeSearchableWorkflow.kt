package info.meuse24.pdf_scanner.domain.workflow

import info.meuse24.pdf_scanner.domain.model.Document
import info.meuse24.pdf_scanner.domain.model.OcrPipelineStatus
import info.meuse24.pdf_scanner.domain.model.supportsSearchablePdfTextLayer
import info.meuse24.pdf_scanner.domain.usecase.MakeSearchableUseCase
import kotlinx.coroutines.CancellationException
import java.io.File
import java.io.IOException
import javax.inject.Inject

data class MakeSearchableWorkflowResult(
    val processedCount: Int,
    val firstFilename: String,
    val blankOcrCount: Int = 0
)

class MakeSearchableWorkflow @Inject constructor(
    private val makeSearchableUseCase: MakeSearchableUseCase
) {
    suspend operator fun invoke(
        records: List<Document>,
        languageCode: String,
        force: Boolean = false,
        onProgress: (Int, Int) -> Unit = { _, _ -> },
        onStatus: (OcrPipelineStatus) -> Unit = {}
    ): WorkflowResult<MakeSearchableWorkflowResult> {
        if (records.isEmpty()) {
            return WorkflowResult.Failure(ScanWorkflowError.NothingSelected)
        }

        // Arabische OCR ist mit dem Latin-Fallback nicht zuverlässig genug für einen Textlayer.
        // CJK-Schriften können nicht zuverlässig per PdfBox eingebettet werden.
        // Nutzer können stattdessen „Text extrahieren" verwenden, um den Text für die App-Suche zu indexieren.
        if (!languageCode.supportsSearchablePdfTextLayer()) {
            return WorkflowResult.Failure(ScanWorkflowError.SearchableUnsupportedForScript)
        }

        val pending = if (force) records else records.filter { !it.isSearchable || it.extractedText == null }
        if (pending.isEmpty()) {
            return WorkflowResult.Failure(ScanWorkflowError.NoEligibleScans)
        }

        val missingFiles = pending
            .filterNot { File(it.filepath).exists() }
            .map { it.filename }
        if (missingFiles.isNotEmpty()) {
            return WorkflowResult.Failure(ScanWorkflowError.MissingFiles(missingFiles))
        }

        return try {
            val (processedCount, blankOcrCount) = makeSearchableUseCase(pending, languageCode, force, onProgress, onStatus)
            WorkflowResult.Success(
                MakeSearchableWorkflowResult(
                    processedCount = processedCount,
                    firstFilename  = pending.first().filename,
                    blankOcrCount  = blankOcrCount
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            WorkflowResult.Failure(ScanWorkflowError.StorageWriteFailed(e))
        } catch (t: Throwable) {
            WorkflowResult.Failure(ScanWorkflowError.OcrFailed(t))
        }
    }
}

