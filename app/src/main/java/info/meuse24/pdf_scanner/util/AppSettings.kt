package info.meuse24.pdf_scanner.util

import info.meuse24.pdf_scanner.domain.model.PdfPageSetup
import info.meuse24.pdf_scanner.ui.theme.ThemeMode

/**
 * Zentrale, persistente App-Einstellungen.
 * Neue Settings können hier ergänzt werden, ohne die UI sofort zu erweitern.
 */
data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val keepScreenOnDuringScan: Boolean = false,
    val defaultMakeSearchable: Boolean = false,
    val defaultOcrLanguage: String = OCR_LANGUAGE_AUTO,
    val defaultSortOrder: AppSortOrder = AppSortOrder.BY_DATE,
    val appLockEnabled: Boolean = false,
    val appLockTimeoutSeconds: Int = 30,
    val defaultImagePdfPageSetup: PdfPageSetup = PdfPageSetup()
) {
    companion object {
        const val OCR_LANGUAGE_AUTO = "auto"
    }
}
