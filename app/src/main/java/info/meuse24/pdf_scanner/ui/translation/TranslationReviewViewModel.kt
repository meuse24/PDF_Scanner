package info.meuse24.pdf_scanner.ui.translation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import info.meuse24.pdf_scanner.R
import info.meuse24.pdf_scanner.domain.gateway.DispatcherProvider
import info.meuse24.pdf_scanner.domain.gateway.ResourceProvider
import info.meuse24.pdf_scanner.domain.model.Document
import info.meuse24.pdf_scanner.domain.model.TranslationResult
import info.meuse24.pdf_scanner.domain.repository.DocumentRepository
import info.meuse24.pdf_scanner.domain.usecase.TranslateTextUseCase
import info.meuse24.pdf_scanner.domain.usecase.TranslationNoTextException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TranslationReviewViewModel @Inject constructor(
    private val repository: DocumentRepository,
    private val translateTextUseCase: TranslateTextUseCase,
    private val resourceProvider: ResourceProvider,
    private val dispatcherProvider: DispatcherProvider,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    data class UiState(
        val record: Document? = null,
        val result: TranslationResult? = null,
        val isLoading: Boolean = false,
        val progress: Pair<Int, Int>? = null,
        val error: String? = null
    )

    companion object {
        const val DEFAULT_SOURCE = "en"
        const val DEFAULT_TARGET = "de"
    }

    private val scanId: Long = checkNotNull(savedStateHandle["scanId"])

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.getAllScans()
                .map { scans -> scans.find { it.id == scanId } }
                .collect { doc -> _uiState.update { it.copy(record = doc) } }
        }
    }

    fun translate(sourceLanguage: String, targetLanguage: String) {
        val doc = _uiState.value.record ?: return
        if (_uiState.value.isLoading) return

        _uiState.update { it.copy(isLoading = true, error = null, progress = null) }

        viewModelScope.launch(dispatcherProvider.io) {
            try {
                val result = translateTextUseCase(
                    document = doc,
                    sourceLanguage = sourceLanguage,
                    targetLanguage = targetLanguage,
                    onProgress = { cur, total ->
                        _uiState.update { it.copy(progress = cur to total) }
                    }
                )
                _uiState.update { it.copy(result = result, isLoading = false, progress = null) }
            } catch (_: TranslationNoTextException) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        progress = null,
                        error = resourceProvider.getString(R.string.translate_error_no_text)
                    )
                }
            } catch (_: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        progress = null,
                        error = resourceProvider.getString(R.string.translate_error_failed)
                    )
                }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
