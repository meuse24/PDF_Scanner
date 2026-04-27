package info.meuse24.pdf_scanner.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import info.meuse24.pdf_scanner.R
import info.meuse24.pdf_scanner.domain.repository.AppSettingsRepository
import info.meuse24.pdf_scanner.domain.usecase.RetroTagUseCase
import info.meuse24.pdf_scanner.ui.theme.ThemeMode
import info.meuse24.pdf_scanner.util.AppSortOrder
import info.meuse24.pdf_scanner.util.AppSettings
import info.meuse24.pdf_scanner.util.ResourceProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: AppSettingsRepository,
    private val retroTagUseCase: RetroTagUseCase,
    private val resourceProvider: ResourceProvider
) : ViewModel() {
    private val _error = MutableStateFlow<String?>(null)

    val settings: StateFlow<AppSettings> = settingsRepository.settings
    val error: StateFlow<String?> = _error.asStateFlow()

    fun setThemeMode(mode: ThemeMode) {
        settingsRepository.updateThemeMode(mode)
    }

    fun setM24AnimationEnabled(enabled: Boolean) {
        settingsRepository.updateM24AnimationEnabled(enabled)
    }

    fun setDefaultMakeSearchable(enabled: Boolean) {
        settingsRepository.updateDefaultMakeSearchable(enabled)
    }

    fun setDefaultOcrLanguage(languageCode: String) {
        settingsRepository.updateDefaultOcrLanguage(languageCode)
    }

    fun retroTagDocuments() {
        viewModelScope.launch {
            try {
                val count = retroTagUseCase()
                _error.value = resourceProvider.getString(R.string.settings_retro_tag_done, count)
            } catch (_: Exception) {
                _error.value = resourceProvider.getString(R.string.ocr_export_error)
            }
        }
    }

    fun setDefaultSortOrder(sortOrder: AppSortOrder) {
        settingsRepository.updateDefaultSortOrder(sortOrder)
    }

    fun setTrashUndoSnackbarSeconds(seconds: Int) {
        settingsRepository.updateTrashUndoSnackbarSeconds(seconds)
    }

    fun setAppLockEnabled(enabled: Boolean) {
        settingsRepository.updateAppLockEnabled(enabled)
    }

    fun setAppLockTimeoutSeconds(seconds: Int) {
        settingsRepository.updateAppLockTimeoutSeconds(seconds)
    }

    fun reportError(message: String) {
        _error.value = message
    }

    fun clearError() {
        _error.value = null
    }
}

