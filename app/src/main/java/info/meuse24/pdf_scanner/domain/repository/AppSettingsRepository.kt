package info.meuse24.pdf_scanner.domain.repository

import info.meuse24.pdf_scanner.domain.model.PdfPageSetup
import info.meuse24.pdf_scanner.util.AppSettings
import info.meuse24.pdf_scanner.util.AppSortOrder
import info.meuse24.pdf_scanner.ui.theme.ThemeMode
import kotlinx.coroutines.flow.StateFlow

interface AppSettingsRepository {
    val settings: StateFlow<AppSettings>

    fun updateThemeMode(mode: ThemeMode)
    fun updateM24AnimationEnabled(enabled: Boolean)
    fun updateDefaultMakeSearchable(enabled: Boolean)
    fun updateAutoTaggingEnabled(enabled: Boolean)
    fun updateDefaultOcrLanguage(languageCode: String)
    fun updateDefaultSortOrder(sortOrder: AppSortOrder)
    fun updateTrashUndoSnackbarSeconds(seconds: Int)
    fun updateAppLockEnabled(enabled: Boolean)
    fun updateAppLockTimeoutSeconds(seconds: Int)
    fun updateDefaultImagePdfPageSetup(setup: PdfPageSetup)
}
