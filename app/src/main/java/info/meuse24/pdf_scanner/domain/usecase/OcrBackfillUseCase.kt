package info.meuse24.pdf_scanner.domain.usecase

import info.meuse24.pdf_scanner.domain.repository.AppSettingsRepository
import info.meuse24.pdf_scanner.domain.repository.DocumentRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import javax.inject.Inject

class OcrBackfillUseCase @Inject constructor(
    private val repository: DocumentRepository,
    private val settingsRepository: AppSettingsRepository,
    private val extractTextUseCase: ExtractTextUseCase,
    private val autoTagUseCase: AutoTagUseCase
) {
    suspend operator fun invoke(
        languageCode: String,
        limit: Int = 10,
        timeoutMillis: Long = 10_000L,
        onBackfilled: (String) -> Unit = {},
        onFailure: (String, Throwable) -> Unit = { _, _ -> }
    ) {
        val autoTaggingEnabled = settingsRepository.settings.first().autoTaggingEnabled
        val allScans = withTimeoutOrNull(timeoutMillis) {
            repository.getAllScans().first()
        } ?: return

        val candidates = allScans
            .filter { it.isSearchable && it.extractedText == null && File(it.filepath).exists() }
            .take(limit)
        if (candidates.isEmpty()) return

        for (record in candidates) {
            runCatching {
                val result = extractTextUseCase(listOf(record), languageCode).singleOrNull()
                if (result != null && result.fullText.isNotBlank()) {
                    repository.markSearchableWithContent(
                        id = record.id,
                        fileSize = File(record.filepath).length(),
                        text = result.fullText,
                        tags = if (autoTaggingEnabled) autoTagUseCase.extractTags(result.fullText) else null,
                        confidence = result.stats?.confidence,
                        language = result.stats?.recognizedLanguage,
                        pageTexts = result.pageTexts
                    )
                    onBackfilled(record.filename)
                }
            }.onFailure { exception ->
                onFailure(record.filename, exception)
            }
        }
    }
}
