package info.meuse24.pdf_scanner.ui.ocr

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import info.meuse24.pdf_scanner.R
import info.meuse24.pdf_scanner.data.local.ScanRecord
import info.meuse24.pdf_scanner.data.repository.ScanRepository
import info.meuse24.pdf_scanner.domain.usecase.ExtractTextUseCase
import info.meuse24.pdf_scanner.domain.usecase.OcrNoTextException
import info.meuse24.pdf_scanner.util.DispatcherProvider
import info.meuse24.pdf_scanner.util.OcrModelInstallException
import info.meuse24.pdf_scanner.util.OcrQuality
import info.meuse24.pdf_scanner.util.ResourceProvider
import info.meuse24.pdf_scanner.util.fromOcrPageTextJson
import info.meuse24.pdf_scanner.util.toOcrPageTextJson
import info.meuse24.pdf_scanner.util.toQuality
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OcrReviewViewModel @Inject constructor(
    private val repository: ScanRepository,
    private val extractTextUseCase: ExtractTextUseCase,
    private val resourceProvider: ResourceProvider,
    private val dispatcherProvider: DispatcherProvider,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    data class UiState(
        val record: ScanRecord? = null,
        val text: String? = null,
        val pageTexts: List<String> = emptyList(),
        val confidence: Float? = null,
        val recognizedLanguage: String? = null,
        val quality: OcrQuality = OcrQuality.UNKNOWN,
        val loading: Boolean = false,
        val error: String? = null
    )

    private val scanId: Long = checkNotNull(savedStateHandle["scanId"])
    private val record = repository.getAllScans()
        .map { records -> records.find { it.id == scanId } }

    private val loading = MutableStateFlow(false)
    private val error = MutableStateFlow<String?>(null)
    private var initialLoadRequested = false

    val uiState: StateFlow<UiState> = combine(record, loading, error) { record, loading, error ->
        val pageTexts = record.pageTexts()
        UiState(
            record = record,
            text = record?.extractedText?.takeIf { it.isNotBlank() },
            pageTexts = pageTexts,
            confidence = record?.ocrConfidence,
            recognizedLanguage = record?.ocrLanguage,
            quality = record?.ocrConfidence.toQuality(),
            loading = loading,
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

    private fun runExtract(record: ScanRecord, languageCode: String) {
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
                    pageTextJson = result.pageTexts.toOcrPageTextJson()
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
}

private fun ScanRecord?.pageTexts(): List<String> {
    if (this == null) return emptyList()
    val cachedPages = ocrPageTextJson.fromOcrPageTextJson()
    if (cachedPages.isNotEmpty()) return cachedPages
    val fullText = extractedText?.trim().orEmpty()
    if (fullText.isBlank()) return emptyList()
    return listOf(fullText)
}
