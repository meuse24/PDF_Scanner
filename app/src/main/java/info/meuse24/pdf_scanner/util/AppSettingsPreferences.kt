package info.meuse24.pdf_scanner.util

import android.content.Context
import info.meuse24.pdf_scanner.ui.theme.ThemeMode

object AppSettingsPreferences {
    private const val PREFS_NAME = "app_settings"

    private const val KEY_THEME_MODE = "theme_mode"
    private const val KEY_KEEP_SCREEN_ON_DURING_SCAN = "keep_screen_on_during_scan"
    private const val KEY_DEFAULT_MAKE_SEARCHABLE = "default_make_searchable"
    private const val KEY_DEFAULT_OCR_LANGUAGE = "default_ocr_language"
    private const val KEY_DEFAULT_SORT_ORDER = "default_sort_order"

    fun load(context: Context): AppSettings {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return AppSettings(
            themeMode = ThemeMode.fromStorageValue(prefs.getString(KEY_THEME_MODE, null)),
            keepScreenOnDuringScan = prefs.getBoolean(KEY_KEEP_SCREEN_ON_DURING_SCAN, false),
            defaultMakeSearchable = prefs.getBoolean(KEY_DEFAULT_MAKE_SEARCHABLE, false),
            defaultOcrLanguage = prefs.getString(
                KEY_DEFAULT_OCR_LANGUAGE,
                AppSettings.OCR_LANGUAGE_AUTO
            ) ?: AppSettings.OCR_LANGUAGE_AUTO,
            defaultSortOrder = AppSortOrder.fromStorageValue(
                prefs.getString(KEY_DEFAULT_SORT_ORDER, null)
            )
        )
    }

    fun save(context: Context, settings: AppSettings) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_THEME_MODE, settings.themeMode.storageValue)
            .putBoolean(KEY_KEEP_SCREEN_ON_DURING_SCAN, settings.keepScreenOnDuringScan)
            .putBoolean(KEY_DEFAULT_MAKE_SEARCHABLE, settings.defaultMakeSearchable)
            .putString(KEY_DEFAULT_OCR_LANGUAGE, settings.defaultOcrLanguage)
            .putString(KEY_DEFAULT_SORT_ORDER, settings.defaultSortOrder.storageValue)
            .apply()
    }

    fun updateThemeMode(context: Context, mode: ThemeMode): AppSettings {
        val current = load(context)
        val updated = current.copy(themeMode = mode)
        save(context, updated)
        return updated
    }
}
