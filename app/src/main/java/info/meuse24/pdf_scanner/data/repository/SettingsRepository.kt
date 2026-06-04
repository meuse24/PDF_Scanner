package info.meuse24.pdf_scanner.data.repository

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import info.meuse24.pdf_scanner.domain.model.PdfPageSetup
import info.meuse24.pdf_scanner.domain.repository.AppSettingsRepository
import info.meuse24.pdf_scanner.domain.model.ThemeMode
import info.meuse24.pdf_scanner.domain.model.AppSortOrder
import info.meuse24.pdf_scanner.domain.model.AppSettings
import info.meuse24.pdf_scanner.util.AppSettingsPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepository @Inject constructor(
    @param:ApplicationContext private val context: Context
) : AppSettingsRepository {
    private val _settings = MutableStateFlow(AppSettingsPreferences.load(context))
    override val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    override fun updateThemeMode(mode: ThemeMode) {
        val current = _settings.value
        if (current.themeMode == mode) return
        val updated = current.copy(themeMode = mode)
        AppSettingsPreferences.save(context, updated)
        _settings.value = updated
    }

    override fun updateDynamicColorEnabled(enabled: Boolean) {
        val current = _settings.value
        if (current.dynamicColorEnabled == enabled) return
        val updated = current.copy(dynamicColorEnabled = enabled)
        AppSettingsPreferences.save(context, updated)
        _settings.value = updated
    }

    override fun updateM24AnimationEnabled(enabled: Boolean) {
        val current = _settings.value
        if (current.m24AnimationEnabled == enabled) return
        val updated = current.copy(m24AnimationEnabled = enabled)
        AppSettingsPreferences.save(context, updated)
        _settings.value = updated
    }

    override fun updateDefaultMakeSearchable(enabled: Boolean) {
        val current = _settings.value
        if (current.defaultMakeSearchable == enabled) return
        val updated = current.copy(defaultMakeSearchable = enabled)
        AppSettingsPreferences.save(context, updated)
        _settings.value = updated
    }

    override fun updateAutoTaggingEnabled(enabled: Boolean) {
        val current = _settings.value
        if (current.autoTaggingEnabled == enabled) return
        val updated = current.copy(autoTaggingEnabled = enabled)
        AppSettingsPreferences.save(context, updated)
        _settings.value = updated
    }

    override fun updateDefaultOcrLanguage(languageCode: String) {
        val current = _settings.value
        if (current.defaultOcrLanguage == languageCode) return
        val updated = current.copy(defaultOcrLanguage = languageCode)
        AppSettingsPreferences.save(context, updated)
        _settings.value = updated
    }

    override fun updateDefaultSortOrder(sortOrder: AppSortOrder) {
        val current = _settings.value
        if (current.defaultSortOrder == sortOrder) return
        val updated = current.copy(defaultSortOrder = sortOrder)
        AppSettingsPreferences.save(context, updated)
        _settings.value = updated
    }

    override fun updateTrashUndoSnackbarSeconds(seconds: Int) {
        val normalized = seconds.coerceAtLeast(1)
        val current = _settings.value
        if (current.trashUndoSnackbarSeconds == normalized) return
        val updated = current.copy(trashUndoSnackbarSeconds = normalized)
        AppSettingsPreferences.save(context, updated)
        _settings.value = updated
    }

    override fun updateAppLockEnabled(enabled: Boolean) {
        val current = _settings.value
        if (current.appLockEnabled == enabled) return
        val updated = current.copy(appLockEnabled = enabled)
        AppSettingsPreferences.save(context, updated)
        _settings.value = updated
    }

    override fun updateAppLockTimeoutSeconds(seconds: Int) {
        val current = _settings.value
        if (current.appLockTimeoutSeconds == seconds) return
        val updated = current.copy(appLockTimeoutSeconds = seconds.coerceAtLeast(0))
        AppSettingsPreferences.save(context, updated)
        _settings.value = updated
    }

    override fun updateDefaultImagePdfPageSetup(setup: PdfPageSetup) {
        val current = _settings.value
        if (current.defaultImagePdfPageSetup == setup) return
        val updated = current.copy(defaultImagePdfPageSetup = setup)
        AppSettingsPreferences.save(context, updated)
        _settings.value = updated
    }
}
