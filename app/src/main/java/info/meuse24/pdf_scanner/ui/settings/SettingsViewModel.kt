package info.meuse24.pdf_scanner.ui.settings

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import info.meuse24.pdf_scanner.domain.repository.AppSettingsRepository
import info.meuse24.pdf_scanner.ui.theme.ThemeMode
import info.meuse24.pdf_scanner.util.AppSortOrder
import info.meuse24.pdf_scanner.util.AppSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: AppSettingsRepository
) : ViewModel() {
    private val _error = MutableStateFlow<String?>(null)

    val settings: StateFlow<AppSettings> = settingsRepository.settings
    val error: StateFlow<String?> = _error.asStateFlow()

    fun setThemeMode(mode: ThemeMode) {
        settingsRepository.updateThemeMode(mode)
    }

    fun setDefaultMakeSearchable(enabled: Boolean) {
        settingsRepository.updateDefaultMakeSearchable(enabled)
    }

    fun setDefaultOcrLanguage(languageCode: String) {
        settingsRepository.updateDefaultOcrLanguage(languageCode)
    }

    fun setDefaultSortOrder(sortOrder: AppSortOrder) {
        settingsRepository.updateDefaultSortOrder(sortOrder)
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

