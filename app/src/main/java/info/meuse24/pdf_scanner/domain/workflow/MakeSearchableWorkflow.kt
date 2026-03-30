package info.meuse24.pdf_scanner.domain.workflow

import info.meuse24.pdf_scanner.data.local.ScanRecord
import info.meuse24.pdf_scanner.domain.usecase.MakeSearchableUseCase
import info.meuse24.pdf_scanner.util.OcrPipelineStatus
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
        records: List<ScanRecord>,
        languageCode: String,
        force: Boolean = false,
        onProgress: (Int, Int) -> Unit = { _, _ -> },
        onStatus: (OcrPipelineStatus) -> Unit = {}
    ): WorkflowResult<MakeSearchableWorkflowResult> {
        if (records.isEmpty()) {
            return WorkflowResult.Failure(ScanWorkflowError.NothingSelected)
        }

        // CJK-Schriften können nicht zuverlässig per PdfBox in einen Textlayer eingebettet werden.
        // Durchsuchbares PDF für zh/ja/ko wird daher nicht unterstützt.
        // Nutzer können stattdessen „Text extrahieren" verwenden, um den Text für die App-Suche zu indexieren.
        if (languageCode in setOf("zh", "ja", "ko")) {
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
        } catch (e: IOException) {
            WorkflowResult.Failure(ScanWorkflowError.StorageWriteFailed(e))
        } catch (t: Throwable) {
            WorkflowResult.Failure(ScanWorkflowError.OcrFailed(t))
        }
    }
}
