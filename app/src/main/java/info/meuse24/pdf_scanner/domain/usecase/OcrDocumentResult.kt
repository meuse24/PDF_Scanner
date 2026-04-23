package info.meuse24.pdf_scanner.domain.usecase

import info.meuse24.pdf_scanner.util.OcrResultStats

data class OcrDocumentResult(
    val recordId: Long,
    val fullText: String,
    val pageTexts: List<String>,
    val stats: OcrResultStats?
)

data class SearchableResult(
    val extractedText: String,
    val pageTexts: List<String>,
    val stats: OcrResultStats?
)
