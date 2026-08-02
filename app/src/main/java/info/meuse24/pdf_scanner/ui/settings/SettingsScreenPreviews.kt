package info.meuse24.pdf_scanner.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import info.meuse24.pdf_scanner.ui.theme.PDF_ScannerTheme
import info.meuse24.pdf_scanner.domain.model.ThemeMode
import info.meuse24.pdf_scanner.domain.model.AppSettings

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun SettingsScreenPreview() {
    PDF_ScannerTheme(dynamicColor = false) {
        SettingsScreen(
            settings = AppSettings(),
            onThemeModeChange = {},
            onDynamicColorEnabledChange = {},
            onM24AnimationEnabledChange = {},
            onDefaultMakeSearchableChange = {},
            onAutoTaggingEnabledChange = {},
            onDefaultOcrLanguageChange = {},
            onRetroTagDocuments = {},
            onDefaultSortOrderChange = {},
            onTrashUndoSnackbarSecondsChange = {},
            onAppLockEnabledChange = {},
            onAppLockTimeoutSecondsChange = {},
            onLocalSyncIdleTimeoutMinutesChange = {},
            onPageNumberSettingsChange = {},
            transientSuccess = null,
            transientError = null,
            onTransientSuccessConsumed = {},
            onTransientErrorConsumed = {}
        )
    }
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun SettingsScreenDarkPreview() {
    PDF_ScannerTheme(themeMode = ThemeMode.DARK, dynamicColor = false) {
        SettingsScreen(
            settings = AppSettings(themeMode = ThemeMode.DARK, appLockEnabled = true),
            onThemeModeChange = {},
            onDynamicColorEnabledChange = {},
            onM24AnimationEnabledChange = {},
            onDefaultMakeSearchableChange = {},
            onAutoTaggingEnabledChange = {},
            onDefaultOcrLanguageChange = {},
            onRetroTagDocuments = {},
            onDefaultSortOrderChange = {},
            onTrashUndoSnackbarSecondsChange = {},
            onAppLockEnabledChange = {},
            onAppLockTimeoutSecondsChange = {},
            onLocalSyncIdleTimeoutMinutesChange = {},
            onPageNumberSettingsChange = {},
            transientSuccess = null,
            transientError = null,
            onTransientSuccessConsumed = {},
            onTransientErrorConsumed = {}
        )
    }
}
