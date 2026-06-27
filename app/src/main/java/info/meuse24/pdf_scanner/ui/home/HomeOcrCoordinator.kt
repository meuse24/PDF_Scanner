package info.meuse24.pdf_scanner.ui.home

import info.meuse24.pdf_scanner.domain.model.Document
import info.meuse24.pdf_scanner.domain.model.OcrPipelineStatus
import info.meuse24.pdf_scanner.domain.repository.DocumentRepository
import info.meuse24.pdf_scanner.domain.usecase.ExtractTextUseCase
import info.meuse24.pdf_scanner.domain.usecase.FindOcrExtractableDocumentsUseCase
import info.meuse24.pdf_scanner.domain.usecase.OcrBackfillUseCase
import info.meuse24.pdf_scanner.domain.usecase.OcrDocumentResult
import info.meuse24.pdf_scanner.domain.workflow.MakeSearchableWorkflow
import info.meuse24.pdf_scanner.domain.workflow.MakeSearchableWorkflowResult
import info.meuse24.pdf_scanner.domain.workflow.WorkflowResult
import javax.inject.Inject

class HomeOcrCoordinator @Inject constructor(
    private val repository: DocumentRepository,
    private val extractTextUseCase: ExtractTextUseCase,
    private val findOcrExtractableDocumentsUseCase: FindOcrExtractableDocumentsUseCase,
    private val ocrBackfillUseCase: OcrBackfillUseCase,
    private val makeSearchableWorkflow: MakeSearchableWorkflow
) {
    fun findExtractable(records: List<Document>): List<Document> =
        findOcrExtractableDocumentsUseCase(records)

    suspend fun extractAndPersist(
        records: List<Document>,
        languageCode: String,
        onStatus: (OcrPipelineStatus) -> Unit
    ): List<OcrDocumentResult> {
        val results = extractTextUseCase(records, languageCode, onStatus)
        results.forEach { document ->
            repository.updateExtractedTextAndOcrStats(
                id = document.recordId,
                text = document.fullText.ifBlank { null },
                confidence = document.stats?.confidence,
                language = document.stats?.recognizedLanguage,
                pageTexts = document.pageTexts
            )
        }
        return results
    }

    suspend fun makeSearchable(
        records: List<Document>,
        languageCode: String,
        force: Boolean,
        onProgress: (Int, Int) -> Unit,
        onStatus: (OcrPipelineStatus) -> Unit
    ): WorkflowResult<MakeSearchableWorkflowResult> = makeSearchableWorkflow(
        records = records,
        languageCode = languageCode,
        force = force,
        onProgress = onProgress,
        onStatus = onStatus
    )

    suspend fun backfill(
        languageCode: String,
        onBackfilled: (String) -> Unit,
        onFailure: (String, Throwable) -> Unit
    ) {
        ocrBackfillUseCase(
            languageCode = languageCode,
            onBackfilled = onBackfilled,
            onFailure = onFailure
        )
    }
}
