package info.meuse24.pdf_scanner.domain.usecase

import info.meuse24.pdf_scanner.domain.gateway.TextTranslator
import info.meuse24.pdf_scanner.domain.model.Document
import info.meuse24.pdf_scanner.domain.model.TranslationResult
import javax.inject.Inject

class TranslationNoTextException : Exception()

open class TranslateTextUseCase @Inject constructor(
    private val textTranslator: TextTranslator
) {
    open suspend operator fun invoke(
        document: Document,
        sourceLanguage: String,
        targetLanguage: String,
        onProgress: (current: Int, total: Int) -> Unit = { _, _ -> }
    ): TranslationResult {
        val pageTexts = document.effectivePageTexts()
        if (pageTexts.isEmpty()) throw TranslationNoTextException()

        val translations = textTranslator.translate(
            pageTexts = pageTexts,
            sourceLanguage = sourceLanguage,
            targetLanguage = targetLanguage,
            onProgress = onProgress
        )

        return TranslationResult(
            sourceLanguage = sourceLanguage,
            targetLanguage = targetLanguage,
            pageTranslations = translations
        )
    }
}

private fun Document.effectivePageTexts(): List<String> {
    val filtered = pageTexts.map { it.trim() }.filter { it.isNotBlank() }
    if (filtered.isNotEmpty()) return filtered
    val full = extractedText?.trim().orEmpty()
    if (full.isBlank()) return emptyList()
    return listOf(full)
}
