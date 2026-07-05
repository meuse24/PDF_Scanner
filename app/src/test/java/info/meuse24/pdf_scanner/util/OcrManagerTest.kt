package info.meuse24.pdf_scanner.util

import info.meuse24.pdf_scanner.domain.model.OcrScript
import info.meuse24.pdf_scanner.domain.model.OcrUsage
import org.junit.Assert.assertEquals
import org.junit.Test

class OcrManagerTest {

    private val manager = OcrManager()

    @Test
    fun `auto extract uses bundled latin recognizer only`() {
        val plan = manager.recognitionPlan(
            languageCode = "auto",
            usage = OcrUsage.EXTRACT_TEXT
        )

        assertEquals(listOf(OcrScript.LATIN), plan)
    }

    @Test
    fun `auto searchable uses bundled latin recognizer only`() {
        val plan = manager.recognitionPlan(
            languageCode = "auto",
            usage = OcrUsage.SEARCHABLE_PDF
        )

        assertEquals(listOf(OcrScript.LATIN), plan)
    }

    @Test
    fun `auto table extraction uses bundled latin recognizer only`() {
        val plan = manager.recognitionPlan(
            languageCode = "auto",
            usage = OcrUsage.TABLE_EXTRACTION
        )

        assertEquals(listOf(OcrScript.LATIN), plan)
    }

    @Test
    fun `manual korean keeps dedicated recognizer`() {
        val plan = manager.recognitionPlan(
            languageCode = "ko",
            usage = OcrUsage.EXTRACT_TEXT
        )

        assertEquals(listOf(OcrScript.KOREAN), plan)
    }
}
