package info.meuse24.pdf_scanner.util

import info.meuse24.pdf_scanner.domain.model.OcrScript
import info.meuse24.pdf_scanner.domain.model.OcrUsage
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class OcrManagerTest {

    private val manager = OcrManager()

    @Test
    fun `auto extract keeps latin first and prefers system locale script next`() {
        val plan = manager.recognitionPlan(
            languageCode = "auto",
            usage = OcrUsage.EXTRACT_TEXT,
            systemLocale = Locale.JAPANESE
        )

        assertEquals(
            listOf(OcrScript.LATIN, OcrScript.JAPANESE, OcrScript.DEVANAGARI, OcrScript.CHINESE, OcrScript.KOREAN),
            plan
        )
    }

    @Test
    fun `auto searchable limits fallback to latin and devanagari`() {
        val plan = manager.recognitionPlan(
            languageCode = "auto",
            usage = OcrUsage.SEARCHABLE_PDF,
            systemLocale = Locale.JAPANESE
        )

        assertEquals(listOf(OcrScript.LATIN, OcrScript.DEVANAGARI), plan)
    }

    @Test
    fun `manual non latin language keeps dedicated recognizer`() {
        val plan = manager.recognitionPlan(
            languageCode = "hi",
            usage = OcrUsage.EXTRACT_TEXT,
            systemLocale = Locale.ENGLISH
        )

        assertEquals(listOf(OcrScript.DEVANAGARI), plan)
    }
}
