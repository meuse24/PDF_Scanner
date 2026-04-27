package info.meuse24.pdf_scanner.domain.usecase

import info.meuse24.pdf_scanner.domain.model.Document
import info.meuse24.pdf_scanner.domain.repository.AppSettingsRepository
import info.meuse24.pdf_scanner.domain.repository.DocumentRepository
import info.meuse24.pdf_scanner.util.OcrPipelineStatus
import info.meuse24.pdf_scanner.util.SearchablePdfBuilder
import kotlinx.coroutines.flow.first
import java.io.File
import javax.inject.Inject

/**
 * Macht eine Liste von Scans durchsuchbar (OCR-Textlayer einfügen).
 * Bereits durchsuchbare Records werden übersprungen (Idempotenz).
 * Speichert den extrahierten Text in der Datenbank.
 * @return Anzahl der tatsächlich verarbeiteten Records
 */
class MakeSearchableUseCase @Inject constructor(
    private val searchablePdfBuilder: SearchablePdfBuilder,
    private val repository:           DocumentRepository,
    private val autoTagUseCase:       AutoTagUseCase,
    private val settingsRepository:   AppSettingsRepository
) {
    /**
     * @return Pair(processedCount, blankOcrCount) — blankOcrCount zählt Dokumente, bei denen
     *         OCR keinen Text erkannt hat (möglicher Hinweis auf falsche Sprachauswahl).
     */
    suspend operator fun invoke(
        records:      List<Document>,
        languageCode: String,
        force:        Boolean = false,
        onProgress:   (Int, Int) -> Unit = { _, _ -> },
        onStatus:     (OcrPipelineStatus) -> Unit = {}
    ): Pair<Int, Int> {
        val pending = records.filter { force || !it.isSearchable || it.extractedText == null }
        val autoTaggingEnabled = settingsRepository.settings.first().autoTaggingEnabled
        var blankOcrCount = 0
        for (record in pending) {
            val pdfFile = File(record.filepath)
            if (!pdfFile.exists()) continue
            val searchableResult = searchablePdfBuilder.makeSearchable(pdfFile, languageCode, onProgress, onStatus)
            if (searchableResult.extractedText.isBlank()) blankOcrCount++
            repository.markSearchableWithContent(
                id = record.id,
                fileSize = pdfFile.length(),
                text = searchableResult.extractedText.ifBlank { null },
                tags = if (autoTaggingEnabled) {
                    searchableResult.extractedText.ifBlank { null }?.let(autoTagUseCase::extractTags)
                } else {
                    null
                },
                confidence = searchableResult.stats?.confidence,
                language = searchableResult.stats?.recognizedLanguage,
                pageTexts = searchableResult.pageTexts
            )
        }
        return pending.size to blankOcrCount
    }
}

