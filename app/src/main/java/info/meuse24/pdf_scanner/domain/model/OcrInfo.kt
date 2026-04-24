package info.meuse24.pdf_scanner.domain.model

data class OcrInfo(
    val extractedText: String? = null,
    val confidence: Float? = null,
    val language: String? = null,
    val pageTexts: List<String> = emptyList()
)
