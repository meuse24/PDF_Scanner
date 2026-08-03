package info.meuse24.pdf_scanner.testutil

import info.meuse24.pdf_scanner.domain.model.LocalSyncTimeout
import info.meuse24.pdf_scanner.domain.model.PdfPageSetup
import info.meuse24.pdf_scanner.domain.model.PageNumberSettings
import info.meuse24.pdf_scanner.domain.repository.AppSettingsRepository
import info.meuse24.pdf_scanner.domain.model.ThemeMode
import info.meuse24.pdf_scanner.domain.model.AppSettings
import info.meuse24.pdf_scanner.domain.model.AppSortOrder
import info.meuse24.pdf_scanner.domain.model.AiChatbotTarget
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeSettingsRepository(
    initial: AppSettings = AppSettings()
) : AppSettingsRepository {
    private val _settings = MutableStateFlow(initial)
    override val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    override fun updateThemeMode(mode: ThemeMode) {
        _settings.value = _settings.value.copy(themeMode = mode)
    }

    override fun updateDynamicColorEnabled(enabled: Boolean) {
        _settings.value = _settings.value.copy(dynamicColorEnabled = enabled)
    }

    override fun updateM24AnimationEnabled(enabled: Boolean) {
        _settings.value = _settings.value.copy(m24AnimationEnabled = enabled)
    }

    override fun updateDefaultMakeSearchable(enabled: Boolean) {
        _settings.value = _settings.value.copy(defaultMakeSearchable = enabled)
    }

    override fun updateAutoTaggingEnabled(enabled: Boolean) {
        _settings.value = _settings.value.copy(autoTaggingEnabled = enabled)
    }

    override fun updateDefaultOcrLanguage(languageCode: String) {
        _settings.value = _settings.value.copy(defaultOcrLanguage = languageCode)
    }

    override fun updateDefaultSortOrder(sortOrder: AppSortOrder) {
        _settings.value = _settings.value.copy(defaultSortOrder = sortOrder)
    }

    override fun updateTrashUndoSnackbarSeconds(seconds: Int) {
        _settings.value = _settings.value.copy(trashUndoSnackbarSeconds = seconds)
    }

    override fun updateAppLockEnabled(enabled: Boolean) {
        _settings.value = _settings.value.copy(appLockEnabled = enabled)
    }

    override fun updateAppLockTimeoutSeconds(seconds: Int) {
        _settings.value = _settings.value.copy(appLockTimeoutSeconds = seconds)
    }

    override fun updateLocalSyncIdleTimeoutMinutes(minutes: Int) {
        _settings.value = _settings.value.copy(
            localSyncIdleTimeoutMinutes = LocalSyncTimeout.normalize(minutes)
        )
    }

    override fun updateAiPromptNoticeAccepted(accepted: Boolean) {
        _settings.value = _settings.value.copy(aiPromptNoticeAccepted = accepted)
    }
    override fun updateCustomAiChatbotTargets(targets: List<AiChatbotTarget>) {
        _settings.value = _settings.value.copy(customAiChatbotTargets = targets)
    }

    override fun updateDefaultImagePdfPageSetup(setup: PdfPageSetup) {
        _settings.value = _settings.value.copy(defaultImagePdfPageSetup = setup)
    }

    override fun updatePageNumberSettings(settings: PageNumberSettings) {
        _settings.value = _settings.value.copy(pageNumberSettings = settings)
    }
}
