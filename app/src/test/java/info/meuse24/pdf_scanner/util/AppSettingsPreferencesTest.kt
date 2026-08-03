package info.meuse24.pdf_scanner.util

import android.content.Context
import android.content.SharedPreferences
import info.meuse24.pdf_scanner.domain.model.AiChatbotTarget
import info.meuse24.pdf_scanner.domain.model.AppSettings
import info.meuse24.pdf_scanner.domain.model.LocalSyncTimeout
import info.meuse24.pdf_scanner.domain.model.PdfMarginPreset
import info.meuse24.pdf_scanner.domain.model.PdfPageOrientation
import info.meuse24.pdf_scanner.domain.model.PdfPageSizePreset
import info.meuse24.pdf_scanner.domain.model.PageNumberHorizontalPosition
import info.meuse24.pdf_scanner.domain.model.PageNumberSettings
import info.meuse24.pdf_scanner.domain.model.PageNumberVerticalPosition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class AppSettingsPreferencesTest {

    @Test
    fun `load falls back to default image pdf setup for unknown enum values`() {
        val context = mock(Context::class.java)
        val prefs = mock(SharedPreferences::class.java)
        `when`(context.applicationContext).thenReturn(context)
        `when`(context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)).thenReturn(prefs)
        `when`(prefs.getString("theme_mode", null)).thenReturn(null)
        `when`(prefs.getBoolean("dynamic_color_enabled", true)).thenReturn(true)
        `when`(prefs.getString("default_sort_order", null)).thenReturn(null)
        `when`(prefs.getString("default_ocr_language", AppSettings.OCR_LANGUAGE_AUTO))
            .thenReturn(AppSettings.OCR_LANGUAGE_AUTO)
        `when`(
            prefs.getInt(
                "trash_undo_snackbar_seconds",
                AppSettings.DEFAULT_TRASH_UNDO_SNACKBAR_SECONDS
            )
        ).thenReturn(AppSettings.DEFAULT_TRASH_UNDO_SNACKBAR_SECONDS)
        `when`(prefs.getString("img_pdf_size_preset", null)).thenReturn("ISO_A6")
        `when`(prefs.getString("img_pdf_orientation", null)).thenReturn("AUTO")
        `when`(prefs.getString("img_pdf_margin_preset", null)).thenReturn("HUGE")

        val settings = AppSettingsPreferences.load(context)

        assertEquals(PdfPageSizePreset.ISO_A4, settings.defaultImagePdfPageSetup.sizePreset)
        assertEquals(PdfPageOrientation.PORTRAIT, settings.defaultImagePdfPageSetup.orientation)
        assertEquals(PdfMarginPreset.MEDIUM, settings.defaultImagePdfPageSetup.marginPreset)
        assertTrue(settings.dynamicColorEnabled)
    }

    @Test
    fun `load falls back to five second trash undo snackbar duration`() {
        val context = mock(Context::class.java)
        val prefs = mock(SharedPreferences::class.java)
        `when`(context.applicationContext).thenReturn(context)
        `when`(context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)).thenReturn(prefs)
        `when`(prefs.getString("theme_mode", null)).thenReturn(null)
        `when`(prefs.getBoolean("dynamic_color_enabled", true)).thenReturn(true)
        `when`(prefs.getString("default_sort_order", null)).thenReturn(null)
        `when`(prefs.getString("default_ocr_language", AppSettings.OCR_LANGUAGE_AUTO))
            .thenReturn(AppSettings.OCR_LANGUAGE_AUTO)
        `when`(
            prefs.getInt(
                "trash_undo_snackbar_seconds",
                AppSettings.DEFAULT_TRASH_UNDO_SNACKBAR_SECONDS
            )
        ).thenReturn(AppSettings.DEFAULT_TRASH_UNDO_SNACKBAR_SECONDS)

        val settings = AppSettingsPreferences.load(context)

        assertEquals(5, settings.trashUndoSnackbarSeconds)
    }

    @Test
    fun `load restores page number settings`() {
        val context = mock(Context::class.java)
        val prefs = mock(SharedPreferences::class.java)
        `when`(context.applicationContext).thenReturn(context)
        `when`(context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)).thenReturn(prefs)
        `when`(prefs.getString("page_number_horizontal_position", null)).thenReturn("RIGHT")
        `when`(prefs.getString("page_number_vertical_position", null)).thenReturn("TOP")
        `when`(prefs.getString("page_number_prefix", null)).thenReturn("Seite")
        `when`(prefs.getBoolean("page_number_include_total", false)).thenReturn(true)

        val settings = AppSettingsPreferences.load(context).pageNumberSettings

        assertEquals(PageNumberHorizontalPosition.RIGHT, settings.horizontalPosition)
        assertEquals(PageNumberVerticalPosition.TOP, settings.verticalPosition)
        assertEquals("Seite", settings.prefix)
        assertTrue(settings.includeTotalPageCount)
    }

    @Test
    fun `load restores a valid stored pc sync timeout`() {
        val context = mock(Context::class.java)
        val prefs = mock(SharedPreferences::class.java)
        `when`(context.applicationContext).thenReturn(context)
        `when`(context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)).thenReturn(prefs)
        `when`(
            prefs.getInt("local_sync_idle_timeout_minutes", LocalSyncTimeout.DEFAULT_MINUTES)
        ).thenReturn(5)

        val settings = AppSettingsPreferences.load(context)

        assertEquals(5, settings.localSyncIdleTimeoutMinutes)
    }

    @Test
    fun `load normalizes an out-of-range pc sync timeout back to the default`() {
        val context = mock(Context::class.java)
        val prefs = mock(SharedPreferences::class.java)
        `when`(context.applicationContext).thenReturn(context)
        `when`(context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)).thenReturn(prefs)
        `when`(
            prefs.getInt("local_sync_idle_timeout_minutes", LocalSyncTimeout.DEFAULT_MINUTES)
        ).thenReturn(Int.MAX_VALUE)

        val settings = AppSettingsPreferences.load(context)

        assertEquals(LocalSyncTimeout.DEFAULT_MINUTES, settings.localSyncIdleTimeoutMinutes)
    }

    @Test
    fun `load truncates overlong stored page number prefix at persistence boundary`() {
        val context = mock(Context::class.java)
        val prefs = mock(SharedPreferences::class.java)
        `when`(context.applicationContext).thenReturn(context)
        `when`(context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)).thenReturn(prefs)
        `when`(prefs.getString("page_number_prefix", null)).thenReturn(
            "x".repeat(PageNumberSettings.MAX_PREFIX_LENGTH + 10)
        )

        val prefix = AppSettingsPreferences.load(context).pageNumberSettings.prefix

        assertEquals(PageNumberSettings.MAX_PREFIX_LENGTH, prefix.length)
    }

    @Test
    fun `custom chatbot targets survive JSON round trip`() {
        val targets = listOf(
            AiChatbotTarget("Private assistant", "https://assistant.example/"),
            AiChatbotTarget("Team bot", "https://chat.example/path")
        )

        val result = AppSettingsPreferences.parseAiChatbotTargets(
            AppSettingsPreferences.serializeAiChatbotTargets(targets)
        )

        assertEquals(targets, result)
    }

    @Test
    fun `invalid chatbot target JSON results in an empty list`() {
        assertTrue(AppSettingsPreferences.parseAiChatbotTargets("not-json").isEmpty())
    }
}
