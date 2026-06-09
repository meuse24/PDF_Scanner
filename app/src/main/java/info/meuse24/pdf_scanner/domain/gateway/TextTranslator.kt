package info.meuse24.pdf_scanner.domain.gateway

interface TextTranslator {
    suspend fun translate(
        pageTexts: List<String>,
        sourceLanguage: String,
        targetLanguage: String,
        onProgress: (current: Int, total: Int) -> Unit = { _, _ -> }
    ): List<String>
}
