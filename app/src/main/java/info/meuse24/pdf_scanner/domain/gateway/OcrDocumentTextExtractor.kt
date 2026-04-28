package info.meuse24.pdf_scanner.domain.gateway

import info.meuse24.pdf_scanner.domain.model.Document
import info.meuse24.pdf_scanner.domain.model.OcrPipelineStatus
import info.meuse24.pdf_scanner.domain.usecase.OcrDocumentResult

interface OcrDocumentTextExtractor {
    suspend fun extract(
        records: List<Document>,
        languageCode: String,
        onStatus: (OcrPipelineStatus) -> Unit = {}
    ): List<OcrDocumentResult>
}
