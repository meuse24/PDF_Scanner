package info.meuse24.pdf_scanner.domain.common

import org.junit.Assert.assertEquals
import org.junit.Test

class ParagraphReconstructionTest {
    @Test
    fun `joins wrapped lines into editable paragraph`() {
        val paragraphs = linesToParagraphs(
            listOf(
                "This is a wrapped line",
                "that continues the same sentence."
            )
        )

        assertEquals(listOf("This is a wrapped line that continues the same sentence."), paragraphs)
    }

    @Test
    fun `blank line starts a new paragraph`() {
        val paragraphs = linesToParagraphs(
            listOf(
                "First paragraph.",
                "",
                "Second paragraph."
            )
        )

        assertEquals(listOf("First paragraph.", "Second paragraph."), paragraphs)
    }

    @Test
    fun `latin hyphenation is merged`() {
        val paragraphs = linesToParagraphs(
            listOf(
                "The applica-",
                "tion exports Word files."
            )
        )

        assertEquals(listOf("The application exports Word files."), paragraphs)
    }

    @Test
    fun `cjk punctuation creates paragraph without uppercase signal`() {
        val paragraphs = linesToParagraphs(
            listOf(
                "これは最初の段落です。",
                "これは次の段落です。"
            )
        )

        assertEquals(listOf("これは最初の段落です。", "これは次の段落です。"), paragraphs)
    }

    @Test
    fun `rtl punctuation creates paragraph without uppercase signal`() {
        val paragraphs = linesToParagraphs(
            listOf(
                "هذا هو النص الأول؟",
                "هذا نص آخر."
            )
        )

        assertEquals(listOf("هذا هو النص الأول؟", "هذا نص آخر."), paragraphs)
    }
}
