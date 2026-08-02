package info.meuse24.pdf_scanner.domain.model

/**
 * Zentrale, persistente App-Einstellungen.
 * Neue Settings koennen hier ergaenzt werden, ohne die UI sofort zu erweitern.
 */
data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColorEnabled: Boolean = true,
    val m24AnimationEnabled: Boolean = true,
    val keepScreenOnDuringScan: Boolean = false,
    val defaultMakeSearchable: Boolean = true,
    val autoTaggingEnabled: Boolean = true,
    val defaultOcrLanguage: String = OCR_LANGUAGE_AUTO,
    val defaultSortOrder: AppSortOrder = AppSortOrder.BY_DATE,
    val trashUndoSnackbarSeconds: Int = DEFAULT_TRASH_UNDO_SNACKBAR_SECONDS,
    val appLockEnabled: Boolean = false,
    val appLockTimeoutSeconds: Int = 30,
    val localSyncIdleTimeoutMinutes: Int = LocalSyncTimeout.DEFAULT_MINUTES,
    val defaultImagePdfPageSetup: PdfPageSetup = PdfPageSetup(),
    val pageNumberSettings: PageNumberSettings = PageNumberSettings()
) {
    companion object {
        const val OCR_LANGUAGE_AUTO = "auto"
        const val DEFAULT_TRASH_UNDO_SNACKBAR_SECONDS = 5
    }
}
