package info.meuse24.pdf_scanner.ui.ocr

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class OcrReviewScreenHelpersTest {

    @Test
    fun `undetermined language code maps to unknown label`() {
        val label = displayLanguageName(
            languageCode = "und",
            displayLocale = Locale.ENGLISH,
            unknownLabel = "Unknown"
        )

        assertEquals("Unknown", label)
    }
}
