package info.meuse24.pdf_scanner.util

import android.content.Context
import android.content.SharedPreferences
import info.meuse24.pdf_scanner.domain.model.PdfMarginPreset
import info.meuse24.pdf_scanner.domain.model.PdfPageOrientation
import info.meuse24.pdf_scanner.domain.model.PdfPageSizePreset
import org.junit.Assert.assertEquals
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
    }

    @Test
    fun `load falls back to ten second trash undo snackbar duration`() {
        val context = mock(Context::class.java)
        val prefs = mock(SharedPreferences::class.java)
        `when`(context.applicationContext).thenReturn(context)
        `when`(context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)).thenReturn(prefs)
        `when`(prefs.getString("theme_mode", null)).thenReturn(null)
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

        assertEquals(10, settings.trashUndoSnackbarSeconds)
    }
}
