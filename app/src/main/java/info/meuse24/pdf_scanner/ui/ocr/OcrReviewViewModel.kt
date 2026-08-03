package info.meuse24.pdf_scanner.ui.ocr

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import info.meuse24.pdf_scanner.R
import info.meuse24.pdf_scanner.domain.model.Document
import info.meuse24.pdf_scanner.domain.model.AiChatbotTarget
import info.meuse24.pdf_scanner.domain.model.defaultAiChatbotTargets
import info.meuse24.pdf_scanner.ui.home.AiPromptCopyRequest
import info.meuse24.pdf_scanner.domain.repository.DocumentRepository
import info.meuse24.pdf_scanner.domain.usecase.ExportOcrTextUseCase
import info.meuse24.pdf_scanner.domain.usecase.ExtractTextUseCase
import info.meuse24.pdf_scanner.domain.usecase.OcrNoTextException
import info.meuse24.pdf_scanner.domain.gateway.DispatcherProvider
import info.meuse24.pdf_scanner.domain.model.OcrModelInstallException
import info.meuse24.pdf_scanner.domain.model.OcrQuality
import info.meuse24.pdf_scanner.domain.gateway.ResourceProvider
import info.meuse24.pdf_scanner.domain.common.OcrAiPromptBuilder
import info.meuse24.pdf_scanner.domain.common.OcrAiPromptPurpose
import info.meuse24.pdf_scanner.domain.repository.AppSettingsRepository
import info.meuse24.pdf_scanner.domain.model.toQuality
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OcrReviewPage(
    val pageIndex: Int,
    val text: String,
    val canCopyAiPrompt: Boolean = false
)

@HiltViewModel
class OcrReviewViewModel @Inject constructor(
    private val repository: DocumentRepository,
    private val extractTextUseCase: ExtractTextUseCase,
    private val exportOcrTextUseCase: ExportOcrTextUseCase,
    private val settingsRepository: AppSettingsRepository,
    private val resourceProvider: ResourceProvider,
    private val dispatcherProvider: DispatcherProvider,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    data class UiState(
        val record: Document? = null,
        val text: String? = null,
        val pageTexts: List<String> = emptyList(),
        val displayPages: List<OcrReviewPage> = emptyList(),
        val confidence: Float? = null,
        val recognizedLanguage: String? = null,
        val quality: OcrQuality = OcrQuality.UNKNOWN,
        val loading: Boolean = false,
        val exporting: Boolean = false,
        val canCopyAiPrompt: Boolean = false,
        val canCopyAiSummaryPrompt: Boolean = false,
        val isAiPromptTooLong: Boolean = false,
        val success: String? = null,
        val error: String? = null
    )

    private val scanId: Long = checkNotNull(savedStateHandle["scanId"])
    private val record = repository.getAllScans()
        .map { records -> records.find { it.id == scanId } }

    private val loading = MutableStateFlow(false)
    private val exporting = MutableStateFlow(false)
    private val success = MutableStateFlow<String?>(null)
    private val error = MutableStateFlow<String?>(null)
    private val _pendingAiPrompt = MutableStateFlow<String?>(null)
    val pendingAiPrompt: StateFlow<String?> = _pendingAiPrompt
    private val _aiPromptToCopy = MutableStateFlow<AiPromptCopyRequest?>(null)
    val aiPromptToCopy: StateFlow<AiPromptCopyRequest?> = _aiPromptToCopy
    val aiChatbotTargets: StateFlow<List<AiChatbotTarget>> = settingsRepository.settings
        .map { it.customAiChatbotTargets }
        .stateIn(viewModelScope, SharingStarted.Eagerly, defaultAiChatbotTargets)
    val aiPromptNoticeAccepted: StateFlow<Boolean> = settingsRepository.settings
        .map { it.aiPromptNoticeAccepted }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)
    private var initialLoadRequested = false

    val uiState: StateFlow<UiState> = combine(record, loading, exporting, success, error) {
            record, loading, exporting, success, error ->
        val pageTexts = record.pageTexts()
        val instruction = resourceProvider.getString(R.string.ocr_ai_prompt_instruction)
        val summaryInstruction = resourceProvider.getString(R.string.ocr_ai_prompt_summary_instruction)
        val languageRule = resourceProvider.getString(R.string.ocr_ai_prompt_output_language_rule)
        val fullPrompt = OcrAiPromptBuilder.build(
            instruction,
            null,
            record?.extractedText.orEmpty(),
            languageRule
        )
        val fullSummaryPrompt = OcrAiPromptBuilder.build(
            summaryInstruction,
            null,
            record?.extractedText.orEmpty(),
            languageRule
        )
        UiState(
            record = record,
            text = record?.extractedText?.takeIf { it.isNotBlank() },
            pageTexts = pageTexts,
            displayPages = pageTexts.mapIndexedNotNull { index, text ->
                text.takeIf { it.isNotBlank() }?.let {
                    OcrReviewPage(
                        pageIndex = index,
                        text = it,
                        canCopyAiPrompt = OcrAiPromptBuilder.build(
                            instruction,
                            pageHint(record, pageTexts, index),
                            it,
                            languageRule
                        ).prompt != null
                    )
                }
            },
            confidence = record?.ocrConfidence,
            recognizedLanguage = record?.ocrLanguage,
            quality = record?.ocrConfidence.toQuality(),
            loading = loading,
            exporting = exporting,
            canCopyAiPrompt = fullPrompt.prompt != null,
            canCopyAiSummaryPrompt = fullSummaryPrompt.prompt != null,
            isAiPromptTooLong = fullPrompt.tooLong || fullSummaryPrompt.tooLong,
            success = success,
            error = error
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState(loading = true))

    init {
        viewModelScope.launch {
            record.collect { current ->
                if (
                    current != null &&
                    current.extractedText.isNullOrBlank() &&
                    !initialLoadRequested &&
                    !loading.value
                ) {
                    initialLoadRequested = true
                    runExtract(
                        record = current,
                        languageCode = current.ocrLanguage?.takeIf { it.isNotBlank() } ?: OCR_LANGUAGE_AUTO
                    )
                }
            }
        }
    }

    fun reExtract(languageCode: String) {
        val current = uiState.value.record ?: return
        runExtract(current, languageCode)
    }

    private fun runExtract(record: Document, languageCode: String) {
        if (loading.value) return
        loading.value = true
        error.value = null
        viewModelScope.launch(dispatcherProvider.io) {
            try {
                val result = extractTextUseCase(listOf(record), languageCode).single()
                repository.updateExtractedTextAndOcrStats(
                    id = record.id,
                    text = result.fullText.ifBlank { null },
                    confidence = result.stats?.confidence,
                    language = result.stats?.recognizedLanguage,
                    pageTexts = result.pageTexts
                )
            } catch (_: OcrNoTextException) {
                error.value = resourceProvider.getString(R.string.ocr_review_no_text)
            } catch (_: OcrModelInstallException) {
                error.value = resourceProvider.getString(R.string.ocr_model_download_failed)
            } catch (_: Exception) {
                error.value = resourceProvider.getString(
                    if (record.extractedText.isNullOrBlank()) {
                        R.string.ocr_review_load_failed
                    } else {
                        R.string.ocr_review_reextract_failed
                    }
                )
            } finally {
                loading.value = false
            }
        }
    }

    fun clearError() {
        error.value = null
    }

    fun clearSuccess() {
        success.value = null
    }

    fun exportAsText() {
        val current = uiState.value.record ?: return
        if (current.extractedText.isNullOrBlank() || exporting.value) return

        exporting.value = true
        error.value = null
        success.value = null
        viewModelScope.launch(dispatcherProvider.io) {
            try {
                val filename = exportOcrTextUseCase(listOf(current))
                success.value = resourceProvider.getString(R.string.ocr_export_success, filename)
            } catch (_: Exception) {
                error.value = resourceProvider.getString(R.string.ocr_export_error)
            } finally {
                exporting.value = false
            }
        }
    }

    private fun pageHint(record: Document?, pageTexts: List<String>, pageIndex: Int): String {
        return if (record != null && pageTexts.size == record.pageCount) {
            resourceProvider.getString(R.string.ocr_ai_prompt_page_hint, pageIndex + 1, record.pageCount)
        } else {
            resourceProvider.getString(R.string.ocr_ai_prompt_page_hint_short, pageIndex + 1)
        }
    }

    fun requestAiPrompt(
        pageIndex: Int? = null,
        purpose: OcrAiPromptPurpose = OcrAiPromptPurpose.CORRECTION
    ) {
        val current = uiState.value.record ?: return
        val pageTexts = current.pageTexts()
        val text = if (pageIndex == null) {
            current.extractedText.orEmpty()
        } else {
            pageTexts.getOrNull(pageIndex).orEmpty()
        }
        val pageHint = pageIndex?.let { pageHint(current, pageTexts, it) }
        val result = OcrAiPromptBuilder.build(
            instruction = resourceProvider.getString(
                when (purpose) {
                    OcrAiPromptPurpose.CORRECTION -> R.string.ocr_ai_prompt_instruction
                    OcrAiPromptPurpose.SUMMARY -> R.string.ocr_ai_prompt_summary_instruction
                }
            ),
            pageHint = pageHint,
            ocrText = text,
            languageRule = resourceProvider.getString(R.string.ocr_ai_prompt_output_language_rule)
        )
        when {
            result.empty -> error.value = resourceProvider.getString(R.string.ocr_review_no_text)
            result.tooLong -> Unit
            else -> _pendingAiPrompt.value = result.prompt
        }
    }

    fun confirmAiPrompt(target: AiChatbotTarget, consentAccepted: Boolean) {
        val prompt = _pendingAiPrompt.value ?: return
        if (!settingsRepository.settings.value.aiPromptNoticeAccepted && !consentAccepted) return
        settingsRepository.updateAiPromptNoticeAccepted(true)
        _pendingAiPrompt.value = null
        _aiPromptToCopy.value = AiPromptCopyRequest(prompt, target.url)
    }

    fun dismissAiPrompt() {
        _pendingAiPrompt.value = null
    }

    fun onAiPromptCopied() {
        _aiPromptToCopy.value = null
    }
}

private fun Document?.pageTexts(): List<String> {
    if (this == null) return emptyList()
    if (pageTexts.isNotEmpty()) return pageTexts
    val fullText = extractedText?.trim().orEmpty()
    if (fullText.isBlank()) return emptyList()
    return listOf(fullText)
}


