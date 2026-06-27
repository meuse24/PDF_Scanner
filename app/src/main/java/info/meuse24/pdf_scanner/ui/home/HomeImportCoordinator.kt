package info.meuse24.pdf_scanner.ui.home

import android.app.Activity
import android.net.Uri
import info.meuse24.pdf_scanner.domain.model.Document
import info.meuse24.pdf_scanner.domain.model.OcrPipelineStatus
import info.meuse24.pdf_scanner.domain.usecase.ImportFileUseCase
import info.meuse24.pdf_scanner.domain.usecase.ImportScanUseCase
import info.meuse24.pdf_scanner.domain.usecase.RecordReviewPromptActionUseCase
import info.meuse24.pdf_scanner.util.PlayReviewPromptManager
import javax.inject.Inject

class HomeImportCoordinator @Inject constructor(
    private val importScanUseCase: ImportScanUseCase,
    private val importFileUseCase: ImportFileUseCase,
    private val recordReviewPromptActionUseCase: RecordReviewPromptActionUseCase,
    private val playReviewPromptManager: PlayReviewPromptManager
) {
    suspend fun saveScan(
        pdfUri: Uri,
        pageCount: Int,
        filename: String,
        thumbnailUri: Uri?,
        makeSearchable: Boolean,
        languageCode: String,
        onProgress: (Int, Int) -> Unit,
        onStatus: (OcrPipelineStatus) -> Unit
    ): Document = importScanUseCase(
        pdfUri = pdfUri,
        pageCount = pageCount,
        filename = filename,
        thumbnailUri = thumbnailUri,
        makeSearchable = makeSearchable,
        languageCode = languageCode,
        onProgress = onProgress,
        onStatus = onStatus
    )

    suspend fun importFile(pdfUri: Uri, filename: String): Document =
        importFileUseCase(pdfUri, filename)

    fun shouldRequestReview(): Boolean = recordReviewPromptActionUseCase()

    fun launchReview(activity: Activity, onComplete: () -> Unit) {
        playReviewPromptManager.launchReviewFlow(activity, onComplete)
    }
}
