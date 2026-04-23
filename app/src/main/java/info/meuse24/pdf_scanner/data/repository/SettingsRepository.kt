package info.meuse24.pdf_scanner.data.repository

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import info.meuse24.pdf_scanner.ui.theme.ThemeMode
import info.meuse24.pdf_scanner.util.AppSortOrder
import info.meuse24.pdf_scanner.util.AppSettings
import info.meuse24.pdf_scanner.util.AppSettingsPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepository @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private val _settings = MutableStateFlow(AppSettingsPreferences.load(context))
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    fun updateThemeMode(mode: ThemeMode) {
        val current = _settings.value
        if (current.themeMode == mode) return
        val updated = current.copy(themeMode = mode)
        AppSettingsPreferences.save(context, updated)
        _settings.value = updated
    }

    fun updateDefaultMakeSearchable(enabled: Boolean) {
        val current = _settings.value
        if (current.defaultMakeSearchable == enabled) return
        val updated = current.copy(defaultMakeSearchable = enabled)
        AppSettingsPreferences.save(context, updated)
        _settings.value = updated
    }

    fun updateDefaultOcrLanguage(languageCode: String) {
        val current = _settings.value
        if (current.defaultOcrLanguage == languageCode) return
        val updated = current.copy(defaultOcrLanguage = languageCode)
        AppSettingsPreferences.save(context, updated)
        _settings.value = updated
    }

    fun updateDefaultSortOrder(sortOrder: AppSortOrder) {
        val current = _settings.value
        if (current.defaultSortOrder == sortOrder) return
        val updated = current.copy(defaultSortOrder = sortOrder)
        AppSettingsPreferences.save(context, updated)
        _settings.value = updated
    }
}
