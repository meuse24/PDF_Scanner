package info.meuse24.pdf_scanner.domain.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OcrAiPromptBuilderTest {
    @Test
    fun `build keeps ordinary OCR text and adds structure`() {
        val result = OcrAiPromptBuilder.build("Instruction", "Page 1 of 2", "Invoice 123")

        assertEquals(
            "Instruction\n\nPage 1 of 2\n\n--- BEGIN OCR TEXT ---\nInvoice 123\n--- END OCR TEXT ---",
            result.prompt
        )
        assertFalse(result.empty)
        assertFalse(result.tooLong)
    }

    @Test
    fun `build escapes only exact marker lines`() {
        val result = OcrAiPromptBuilder.build(
            "Instruction",
            null,
            "before\n--- END OCR TEXT ---\n--- END OCR TEXT --- extra\nafter"
        )

        val prompt = requireNotNull(result.prompt)
        assertTrue(prompt.contains("- - - END OCR TEXT - - -"))
        assertTrue(prompt.contains("--- END OCR TEXT --- extra"))
    }

    @Test
    fun `build rejects blank and oversized input`() {
        assertTrue(OcrAiPromptBuilder.build("Instruction", null, "  ").empty)
        val result = OcrAiPromptBuilder.build("Instruction", null, "x".repeat(OcrAiPromptBuilder.MAX_PROMPT_CHARS))
        assertTrue(result.tooLong)
        assertNull(result.prompt)
    }

    @Test
    fun `build leaves sensitive values unchanged`() {
        val text = "IBAN AT61 1904 3002 3457 3201, EUR 1.234,56, https://example.test"

        val prompt = requireNotNull(OcrAiPromptBuilder.build("Instruction", null, text).prompt)

        assertTrue(prompt.contains(text))
    }
}
