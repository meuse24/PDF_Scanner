package info.meuse24.pdf_scanner.domain.model

data class TranslationResult(
    val sourceLanguage: String,
    val targetLanguage: String,
    val pageTranslations: List<String>,
    val sourcePageIndices: List<Int> = pageTranslations.indices.toList()
) {
    val fullText: String get() = pageTranslations.joinToString("\n\n")
}
