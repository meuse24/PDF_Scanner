package info.meuse24.pdf_scanner.domain.usecase

import info.meuse24.pdf_scanner.domain.gateway.TextTranslator
import info.meuse24.pdf_scanner.domain.model.Document
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TranslateTextUseCaseTest {

    @Test
    fun `uses pageTexts when available`() = runTest {
        val doc = document(
            pageTexts = listOf("Seite 1", "Seite 2"),
            extractedText = "Seite 1\n\nSeite 2"
        )
        val translator = RecordingTextTranslator(translatedPages = listOf("Page 1", "Page 2"))
        val useCase = TranslateTextUseCase(translator)

        val result = useCase(doc, "de", "en")

        assertEquals(listOf("Seite 1", "Seite 2"), translator.receivedPageTexts)
        assertEquals(listOf("Page 1", "Page 2"), result.pageTranslations)
        assertEquals("de", result.sourceLanguage)
        assertEquals("en", result.targetLanguage)
    }

    @Test
    fun `falls back to extractedText as single page when pageTexts empty`() = runTest {
        val doc = document(
            pageTexts = emptyList(),
            extractedText = "Nur ein Block"
        )
        val translator = RecordingTextTranslator(translatedPages = listOf("Just one block"))
        val useCase = TranslateTextUseCase(translator)

        useCase(doc, "de", "en")

        assertEquals(listOf("Nur ein Block"), translator.receivedPageTexts)
    }

    @Test
    fun `throws TranslationNoTextException when both pageTexts and extractedText are empty`() = runTest {
        val doc = document(pageTexts = emptyList(), extractedText = null)
        val translator = RecordingTextTranslator(translatedPages = emptyList())
        val useCase = TranslateTextUseCase(translator)

        val error = runCatching { useCase(doc, "de", "en") }.exceptionOrNull()

        assertTrue("expected TranslationNoTextException", error is TranslationNoTextException)
        assertTrue("translator must not be called", translator.receivedPageTexts.isEmpty())
    }

    @Test
    fun `throws TranslationNoTextException when extractedText is blank`() = runTest {
        val doc = document(pageTexts = emptyList(), extractedText = "   ")
        val translator = RecordingTextTranslator(translatedPages = emptyList())
        val useCase = TranslateTextUseCase(translator)

        val error = runCatching { useCase(doc, "de", "en") }.exceptionOrNull()

        assertTrue(error is TranslationNoTextException)
    }

    @Test
    fun `throws TranslationNoTextException when all pageTexts are blank`() = runTest {
        val doc = document(pageTexts = listOf("   ", "\t", ""))
        val translator = RecordingTextTranslator(translatedPages = emptyList())
        val useCase = TranslateTextUseCase(translator)

        val error = runCatching { useCase(doc, "de", "en") }.exceptionOrNull()

        assertTrue("expected TranslationNoTextException for all-blank pages", error is TranslationNoTextException)
        assertTrue("translator must not be called", translator.receivedPageTexts.isEmpty())
    }

    @Test
    fun `filters blank pages and only translates non-blank ones`() = runTest {
        val doc = document(pageTexts = listOf("Seite 1", "  ", "Seite 3"))
        val translator = RecordingTextTranslator(translatedPages = listOf("Page 1", "Page 3"))
        val useCase = TranslateTextUseCase(translator)

        val result = useCase(doc, "de", "en")

        assertEquals(listOf("Seite 1", "Seite 3"), translator.receivedPageTexts)
        assertEquals(listOf("Page 1", "Page 3"), result.pageTranslations)
        assertEquals(listOf(0, 2), result.sourcePageIndices)
    }

    @Test
    fun `forwards progress callbacks`() = runTest {
        val doc = document(pageTexts = listOf("A", "B", "C"))
        val translator = RecordingTextTranslator(translatedPages = listOf("X", "Y", "Z"))
        val useCase = TranslateTextUseCase(translator)
        val progressEvents = mutableListOf<Pair<Int, Int>>()

        useCase(doc, "de", "en", onProgress = { cur, total -> progressEvents += cur to total })

        assertEquals(listOf(1 to 3, 2 to 3, 3 to 3), progressEvents)
    }

    @Test
    fun `source and target language passed through to translator`() = runTest {
        val doc = document(pageTexts = listOf("text"))
        val translator = RecordingTextTranslator(translatedPages = listOf("text"))
        val useCase = TranslateTextUseCase(translator)

        useCase(doc, sourceLanguage = "fr", targetLanguage = "ja")

        assertEquals("fr", translator.receivedSource)
        assertEquals("ja", translator.receivedTarget)
    }
}

private fun document(
    pageTexts: List<String> = emptyList(),
    extractedText: String? = null
) = Document(
    id = 1L,
    filename = "test.pdf",
    filepath = "/tmp/test.pdf",
    timestamp = 0L,
    pageCount = pageTexts.size.coerceAtLeast(1),
    fileSize = 0L,
    pageTexts = pageTexts,
    extractedText = extractedText
)

private class RecordingTextTranslator(
    private val translatedPages: List<String>
) : TextTranslator {
    var receivedPageTexts: List<String> = emptyList()
    var receivedSource: String = ""
    var receivedTarget: String = ""

    override suspend fun translate(
        pageTexts: List<String>,
        sourceLanguage: String,
        targetLanguage: String,
        onProgress: (Int, Int) -> Unit
    ): List<String> {
        receivedPageTexts = pageTexts
        receivedSource = sourceLanguage
        receivedTarget = targetLanguage
        pageTexts.forEachIndexed { i, _ -> onProgress(i + 1, pageTexts.size) }
        return translatedPages
    }
}
