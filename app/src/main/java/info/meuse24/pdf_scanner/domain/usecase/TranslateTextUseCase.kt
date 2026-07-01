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
        val sourcePages = document.effectiveSourcePages()
        if (sourcePages.isEmpty()) throw TranslationNoTextException()

        val translations = textTranslator.translate(
            pageTexts = sourcePages.map { it.text },
            sourceLanguage = sourceLanguage,
            targetLanguage = targetLanguage,
            onProgress = onProgress
        )

        return TranslationResult(
            sourceLanguage = sourceLanguage,
            targetLanguage = targetLanguage,
            pageTranslations = translations,
            sourcePageIndices = sourcePages.map { it.index }
        )
    }
}

private data class SourcePage(val index: Int, val text: String)

private fun Document.effectiveSourcePages(): List<SourcePage> {
    val nonBlankPages = pageTexts.mapIndexedNotNull { index, text ->
        text.trim().takeIf { it.isNotBlank() }?.let { SourcePage(index, it) }
    }
    if (nonBlankPages.isNotEmpty()) return nonBlankPages
    val full = extractedText?.trim().orEmpty()
    if (full.isBlank()) return emptyList()
    return listOf(SourcePage(index = 0, text = full))
}
