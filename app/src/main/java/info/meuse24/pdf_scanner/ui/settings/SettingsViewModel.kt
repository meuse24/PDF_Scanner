package info.meuse24.pdf_scanner.ui.settings

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import info.meuse24.pdf_scanner.data.repository.SettingsRepository
import info.meuse24.pdf_scanner.ui.theme.ThemeMode
import info.meuse24.pdf_scanner.util.AppSortOrder
import info.meuse24.pdf_scanner.util.AppSettings
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    val settings: StateFlow<AppSettings> = settingsRepository.settings

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
}
