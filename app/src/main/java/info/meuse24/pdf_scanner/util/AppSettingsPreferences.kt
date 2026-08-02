package info.meuse24.pdf_scanner.util

import android.content.Context
import androidx.core.content.edit
import info.meuse24.pdf_scanner.domain.model.AppSettings
import info.meuse24.pdf_scanner.domain.model.AppSortOrder
import info.meuse24.pdf_scanner.domain.model.LocalSyncTimeout
import info.meuse24.pdf_scanner.domain.model.PdfMarginPreset
import info.meuse24.pdf_scanner.domain.model.PdfPageOrientation
import info.meuse24.pdf_scanner.domain.model.PdfPageSetup
import info.meuse24.pdf_scanner.domain.model.PdfPageSizePreset
import info.meuse24.pdf_scanner.domain.model.PageNumberHorizontalPosition
import info.meuse24.pdf_scanner.domain.model.PageNumberSettings
import info.meuse24.pdf_scanner.domain.model.PageNumberVerticalPosition
import info.meuse24.pdf_scanner.domain.model.ThemeMode

object AppSettingsPreferences {
    private const val PREFS_NAME = "app_settings"

    private const val KEY_THEME_MODE = "theme_mode"
    private const val KEY_DYNAMIC_COLOR_ENABLED = "dynamic_color_enabled"
    private const val KEY_M24_ANIMATION_ENABLED = "m24_animation_enabled"
    private const val KEY_KEEP_SCREEN_ON_DURING_SCAN = "keep_screen_on_during_scan"
    private const val KEY_DEFAULT_MAKE_SEARCHABLE = "default_make_searchable"
    private const val KEY_AUTO_TAGGING_ENABLED = "auto_tagging_enabled"
    private const val KEY_DEFAULT_OCR_LANGUAGE = "default_ocr_language"
    private const val KEY_DEFAULT_SORT_ORDER = "default_sort_order"
    private const val KEY_TRASH_UNDO_SNACKBAR_SECONDS = "trash_undo_snackbar_seconds"
    private const val KEY_APP_LOCK_ENABLED = "app_lock_enabled"
    private const val KEY_APP_LOCK_TIMEOUT_SECONDS = "app_lock_timeout_seconds"
    private const val KEY_LOCAL_SYNC_IDLE_TIMEOUT_MINUTES = "local_sync_idle_timeout_minutes"
    private const val KEY_IMG_PDF_SIZE_PRESET = "img_pdf_size_preset"
    private const val KEY_IMG_PDF_ORIENTATION = "img_pdf_orientation"
    private const val KEY_IMG_PDF_MARGIN_PRESET = "img_pdf_margin_preset"
    private const val KEY_PAGE_NUMBER_HORIZONTAL_POSITION = "page_number_horizontal_position"
    private const val KEY_PAGE_NUMBER_VERTICAL_POSITION = "page_number_vertical_position"
    private const val KEY_PAGE_NUMBER_PREFIX = "page_number_prefix"
    private const val KEY_PAGE_NUMBER_INCLUDE_TOTAL = "page_number_include_total"

    fun load(context: Context): AppSettings {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return AppSettings(
            themeMode = ThemeMode.fromStorageValue(prefs.getString(KEY_THEME_MODE, null)),
            dynamicColorEnabled = prefs.getBoolean(KEY_DYNAMIC_COLOR_ENABLED, true),
            m24AnimationEnabled = prefs.getBoolean(KEY_M24_ANIMATION_ENABLED, true),
            keepScreenOnDuringScan = prefs.getBoolean(KEY_KEEP_SCREEN_ON_DURING_SCAN, false),
            defaultMakeSearchable = prefs.getBoolean(KEY_DEFAULT_MAKE_SEARCHABLE, true),
            autoTaggingEnabled = prefs.getBoolean(KEY_AUTO_TAGGING_ENABLED, true),
            defaultOcrLanguage = prefs.getString(
                KEY_DEFAULT_OCR_LANGUAGE,
                AppSettings.OCR_LANGUAGE_AUTO
            ) ?: AppSettings.OCR_LANGUAGE_AUTO,
            defaultSortOrder = AppSortOrder.fromStorageValue(
                prefs.getString(KEY_DEFAULT_SORT_ORDER, null)
            ),
            trashUndoSnackbarSeconds = prefs.getInt(
                KEY_TRASH_UNDO_SNACKBAR_SECONDS,
                AppSettings.DEFAULT_TRASH_UNDO_SNACKBAR_SECONDS
            ).coerceAtLeast(1),
            appLockEnabled = prefs.getBoolean(KEY_APP_LOCK_ENABLED, false),
            appLockTimeoutSeconds = prefs.getInt(KEY_APP_LOCK_TIMEOUT_SECONDS, 30),
            localSyncIdleTimeoutMinutes = LocalSyncTimeout.normalize(
                prefs.getInt(
                    KEY_LOCAL_SYNC_IDLE_TIMEOUT_MINUTES,
                    LocalSyncTimeout.DEFAULT_MINUTES
                )
            ),
            defaultImagePdfPageSetup = PdfPageSetup(
                sizePreset = enumPreference(
                    prefs.getString(KEY_IMG_PDF_SIZE_PRESET, null),
                    PdfPageSizePreset.ISO_A4
                ),
                orientation = enumPreference(
                    prefs.getString(KEY_IMG_PDF_ORIENTATION, null),
                    PdfPageOrientation.PORTRAIT
                ),
                marginPreset = enumPreference(
                    prefs.getString(KEY_IMG_PDF_MARGIN_PRESET, null),
                    PdfMarginPreset.MEDIUM
                )
            ),
            pageNumberSettings = PageNumberSettings(
                horizontalPosition = enumPreference(
                    prefs.getString(KEY_PAGE_NUMBER_HORIZONTAL_POSITION, null),
                    PageNumberHorizontalPosition.CENTER
                ),
                verticalPosition = enumPreference(
                    prefs.getString(KEY_PAGE_NUMBER_VERTICAL_POSITION, null),
                    PageNumberVerticalPosition.BOTTOM
                ),
                prefix = prefs.getString(KEY_PAGE_NUMBER_PREFIX, null)
                    .orEmpty()
                    .take(PageNumberSettings.MAX_PREFIX_LENGTH),
                includeTotalPageCount = prefs.getBoolean(KEY_PAGE_NUMBER_INCLUDE_TOTAL, false)
            )
        )
    }

    fun save(context: Context, settings: AppSettings) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit {
            putString(KEY_THEME_MODE, settings.themeMode.storageValue)
            putBoolean(KEY_DYNAMIC_COLOR_ENABLED, settings.dynamicColorEnabled)
            putBoolean(KEY_M24_ANIMATION_ENABLED, settings.m24AnimationEnabled)
            putBoolean(KEY_KEEP_SCREEN_ON_DURING_SCAN, settings.keepScreenOnDuringScan)
            putBoolean(KEY_DEFAULT_MAKE_SEARCHABLE, settings.defaultMakeSearchable)
            putBoolean(KEY_AUTO_TAGGING_ENABLED, settings.autoTaggingEnabled)
            putString(KEY_DEFAULT_OCR_LANGUAGE, settings.defaultOcrLanguage)
            putString(KEY_DEFAULT_SORT_ORDER, settings.defaultSortOrder.storageValue)
            putInt(KEY_TRASH_UNDO_SNACKBAR_SECONDS, settings.trashUndoSnackbarSeconds.coerceAtLeast(1))
            putBoolean(KEY_APP_LOCK_ENABLED, settings.appLockEnabled)
            putInt(KEY_APP_LOCK_TIMEOUT_SECONDS, settings.appLockTimeoutSeconds)
            putInt(
                KEY_LOCAL_SYNC_IDLE_TIMEOUT_MINUTES,
                LocalSyncTimeout.normalize(settings.localSyncIdleTimeoutMinutes)
            )
            putString(KEY_IMG_PDF_SIZE_PRESET, settings.defaultImagePdfPageSetup.sizePreset.name)
            putString(KEY_IMG_PDF_ORIENTATION, settings.defaultImagePdfPageSetup.orientation.name)
            putString(KEY_IMG_PDF_MARGIN_PRESET, settings.defaultImagePdfPageSetup.marginPreset.name)
            putString(
                KEY_PAGE_NUMBER_HORIZONTAL_POSITION,
                settings.pageNumberSettings.horizontalPosition.name
            )
            putString(
                KEY_PAGE_NUMBER_VERTICAL_POSITION,
                settings.pageNumberSettings.verticalPosition.name
            )
            putString(
                KEY_PAGE_NUMBER_PREFIX,
                settings.pageNumberSettings.prefix
            )
            putBoolean(
                KEY_PAGE_NUMBER_INCLUDE_TOTAL,
                settings.pageNumberSettings.includeTotalPageCount
            )
        }
    }

    fun updateThemeMode(context: Context, mode: ThemeMode): AppSettings {
        val current = load(context)
        val updated = current.copy(themeMode = mode)
        save(context, updated)
        return updated
    }

    private inline fun <reified T : Enum<T>> enumPreference(
        value: String?,
        default: T
    ): T {
        val storedValue = value ?: return default
        return runCatching { enumValueOf<T>(storedValue) }.getOrDefault(default)
    }
}
