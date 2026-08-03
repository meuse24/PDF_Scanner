package info.meuse24.pdf_scanner.domain.repository

import info.meuse24.pdf_scanner.domain.model.PdfPageSetup
import info.meuse24.pdf_scanner.domain.model.PageNumberSettings
import info.meuse24.pdf_scanner.domain.model.AppSettings
import info.meuse24.pdf_scanner.domain.model.AppSortOrder
import info.meuse24.pdf_scanner.domain.model.ThemeMode
import info.meuse24.pdf_scanner.domain.model.AiChatbotTarget
import kotlinx.coroutines.flow.StateFlow

interface AppSettingsRepository {
    val settings: StateFlow<AppSettings>

    fun updateThemeMode(mode: ThemeMode)
    fun updateDynamicColorEnabled(enabled: Boolean)
    fun updateM24AnimationEnabled(enabled: Boolean)
    fun updateDefaultMakeSearchable(enabled: Boolean)
    fun updateAutoTaggingEnabled(enabled: Boolean)
    fun updateDefaultOcrLanguage(languageCode: String)
    fun updateDefaultSortOrder(sortOrder: AppSortOrder)
    fun updateTrashUndoSnackbarSeconds(seconds: Int)
    fun updateAppLockEnabled(enabled: Boolean)
    fun updateAppLockTimeoutSeconds(seconds: Int)
    fun updateLocalSyncIdleTimeoutMinutes(minutes: Int)
    fun updateAiPromptNoticeAccepted(accepted: Boolean)
    fun updateCustomAiChatbotTargets(targets: List<AiChatbotTarget>)
    fun updateDefaultImagePdfPageSetup(setup: PdfPageSetup)
    fun updatePageNumberSettings(settings: PageNumberSettings)
}
