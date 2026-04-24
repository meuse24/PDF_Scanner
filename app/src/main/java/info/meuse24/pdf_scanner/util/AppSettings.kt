package info.meuse24.pdf_scanner.util

import info.meuse24.pdf_scanner.domain.model.PdfPageSetup
import info.meuse24.pdf_scanner.ui.theme.ThemeMode

/**
 * Zentrale, persistente App-Einstellungen.
 * Neue Settings können hier ergänzt werden, ohne die UI sofort zu erweitern.
 */
data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val m24AnimationEnabled: Boolean = true,
    val keepScreenOnDuringScan: Boolean = false,
    val defaultMakeSearchable: Boolean = false,
    val defaultOcrLanguage: String = OCR_LANGUAGE_AUTO,
    val defaultSortOrder: AppSortOrder = AppSortOrder.BY_DATE,
    val trashUndoSnackbarSeconds: Int = DEFAULT_TRASH_UNDO_SNACKBAR_SECONDS,
    val appLockEnabled: Boolean = false,
    val appLockTimeoutSeconds: Int = 30,
    val defaultImagePdfPageSetup: PdfPageSetup = PdfPageSetup()
) {
    companion object {
        const val OCR_LANGUAGE_AUTO = "auto"
        const val DEFAULT_TRASH_UNDO_SNACKBAR_SECONDS = 10
    }
}
