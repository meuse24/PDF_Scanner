package info.meuse24.pdf_scanner.domain.usecase

import info.meuse24.pdf_scanner.domain.model.Document
import info.meuse24.pdf_scanner.domain.repository.DocumentRepository
import info.meuse24.pdf_scanner.testutil.FakeSettingsRepository
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

class OcrBackfillUseCaseTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `repairs searchable legacy document with misaligned page text`() = runTest {
        val file = temporaryFolder.newFile("legacy.pdf")
        val record = Document(
            id = 7L,
            filename = "legacy.pdf",
            filepath = file.absolutePath,
            timestamp = 0L,
            pageCount = 3,
            fileSize = file.length(),
            isSearchable = true,
            extractedText = "Old text",
            pageTexts = listOf("Old text")
        )
        val repository = mock(DocumentRepository::class.java)
        `when`(repository.getAllScans()).thenReturn(flowOf(listOf(record)))
        val repairedPages = listOf("First", "", "Third")
        val extractTextUseCase = FakeExtractTextUseCase(
            fullText = "First\n\n\n\nThird",
            pageTexts = repairedPages
        )
        val backfilled = mutableListOf<String>()
        val useCase = OcrBackfillUseCase(
            repository,
            FakeSettingsRepository(),
            extractTextUseCase,
            AutoTagUseCase()
        )

        useCase(languageCode = "en", onBackfilled = backfilled::add)

        verify(repository).markSearchableWithContent(
            id = record.id,
            fileSize = file.length(),
            text = "First\n\n\n\nThird",
            tags = null,
            confidence = null,
            language = null,
            pageTexts = repairedPages
        )
        assertEquals(listOf("legacy.pdf"), backfilled)
    }

    @Test
    fun `skips searchable document with complete aligned OCR metadata`() = runTest {
        val file = temporaryFolder.newFile("complete.pdf")
        val record = Document(
            id = 8L,
            filename = "complete.pdf",
            filepath = file.absolutePath,
            timestamp = 0L,
            pageCount = 2,
            fileSize = file.length(),
            isSearchable = true,
            extractedText = "First\n\nSecond",
            pageTexts = listOf("First", "Second")
        )
        val repository = mock(DocumentRepository::class.java)
        `when`(repository.getAllScans()).thenReturn(flowOf(listOf(record)))
        val extractTextUseCase = FakeExtractTextUseCase()
        val useCase = OcrBackfillUseCase(
            repository,
            FakeSettingsRepository(),
            extractTextUseCase,
            AutoTagUseCase()
        )

        useCase(languageCode = "en")

        assertEquals(0, extractTextUseCase.invocationCount)
    }
}
