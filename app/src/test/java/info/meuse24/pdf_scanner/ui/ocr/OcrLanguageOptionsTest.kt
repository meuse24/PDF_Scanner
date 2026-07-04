package info.meuse24.pdf_scanner.ui.ocr

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OcrLanguageOptionsTest {

    @Test
    fun `extract text keeps arabic and korean`() {
        val codes = buildOcrLanguageOptions("Automatic", Locale.ENGLISH).map { it.first }

        assertTrue("ar" in codes)
        assertTrue("ko" in codes)
    }

    @Test
    fun `searchable pdf excludes unsupported text layer languages`() {
        val codes = buildSearchablePdfLanguageOptions("Automatic", Locale.ENGLISH).map { it.first }

        assertFalse("ar" in codes)
        assertFalse("zh" in codes)
        assertFalse("ja" in codes)
        assertFalse("ko" in codes)
        assertTrue("hi" in codes)
    }

    @Test
    fun `unsupported searchable default falls back to automatic`() {
        assertEquals(OCR_LANGUAGE_AUTO, searchablePdfLanguageOrAuto("ar"))
        assertEquals("hi", searchablePdfLanguageOrAuto("hi"))
    }
}
