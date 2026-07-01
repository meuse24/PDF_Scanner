package info.meuse24.pdf_scanner.domain.usecase

import info.meuse24.pdf_scanner.domain.gateway.SearchablePdfGenerator
import info.meuse24.pdf_scanner.domain.model.Document
import info.meuse24.pdf_scanner.domain.model.hasAlignedOcrPageTexts
import info.meuse24.pdf_scanner.domain.repository.AppSettingsRepository
import info.meuse24.pdf_scanner.domain.repository.DocumentRepository
import info.meuse24.pdf_scanner.domain.model.OcrPipelineStatus
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
    private val searchablePdfBuilder: SearchablePdfGenerator,
    private val repository:           DocumentRepository,
    private val autoTagUseCase:       AutoTagUseCase,
    private val settingsRepository:   AppSettingsRepository,
    private val extractTextUseCase:   ExtractTextUseCase
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
        val pending = records.filter {
            force ||
                !it.isSearchable ||
                it.extractedText.isNullOrBlank() ||
                !it.hasAlignedOcrPageTexts()
        }
        val autoTaggingEnabled = settingsRepository.settings.first().autoTaggingEnabled
        var blankOcrCount = 0
        for (record in pending) {
            val pdfFile = File(record.filepath)
            if (!pdfFile.exists()) continue
            val extracted = if (record.isSearchable && !force) {
                try {
                    extractTextUseCase(listOf(record), languageCode, onStatus).single()
                } catch (_: OcrNoTextException) {
                    blankOcrCount++
                    continue
                }
            } else {
                searchablePdfBuilder.makeSearchable(pdfFile, languageCode, onProgress, onStatus)
                    .let { result ->
                        OcrDocumentResult(
                            recordId = record.id,
                            fullText = result.extractedText,
                            pageTexts = result.pageTexts,
                            stats = result.stats
                        )
                    }
            }
            if (extracted.fullText.isBlank()) blankOcrCount++
            repository.markSearchableWithContent(
                id = record.id,
                fileSize = pdfFile.length(),
                text = extracted.fullText.ifBlank { null },
                tags = if (autoTaggingEnabled) {
                    extracted.fullText.ifBlank { null }?.let(autoTagUseCase::extractTags)
                } else {
                    null
                },
                confidence = extracted.stats?.confidence,
                language = extracted.stats?.recognizedLanguage,
                pageTexts = extracted.pageTexts
            )
        }
        return pending.size to blankOcrCount
    }
}

