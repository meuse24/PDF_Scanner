package info.meuse24.pdf_scanner.domain.usecase

import info.meuse24.pdf_scanner.domain.model.BusinessCard
import info.meuse24.pdf_scanner.domain.model.Document
import info.meuse24.pdf_scanner.domain.service.BusinessCardParser
import info.meuse24.pdf_scanner.domain.model.AppSettings
import javax.inject.Inject

class ScanBusinessCardUseCase @Inject constructor(
    private val extractTextUseCase: ExtractTextUseCase,
    private val businessCardParser: BusinessCardParser
) {
    suspend operator fun invoke(record: Document): BusinessCard {
        val preferredText = record.pageTexts.firstOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: record.extractedText?.takeIf { it.isNotBlank() }

        val text = preferredText ?: runCatching {
            extractTextUseCase(
                records = listOf(record),
                languageCode = AppSettings.OCR_LANGUAGE_AUTO
            ).firstOrNull()?.pageTexts?.firstOrNull().orEmpty()
        }.getOrDefault("")

        return businessCardParser.parse(text)
    }
}
