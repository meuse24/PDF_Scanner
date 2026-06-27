package info.meuse24.pdf_scanner.domain.usecase

import info.meuse24.pdf_scanner.domain.model.Document
import info.meuse24.pdf_scanner.domain.repository.DocumentRepository
import info.meuse24.pdf_scanner.testutil.TestStorageProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Mockito.mock

class RenameDocumentUseCaseTest {

    @get:Rule
    val tmpFolder = TemporaryFolder()

    @Test
    fun `rename sanitizes name and keeps document in scans directory`() = runTest {
        val storageProvider = TestStorageProvider(tmpFolder.root)
        val scansDir = storageProvider.scansDir()
        val original = scansDir.resolve("original.pdf").apply { writeText("pdf") }
        val repository = mock(DocumentRepository::class.java)
        val useCase = RenameDocumentUseCase(repository, storageProvider)
        val record = Document(
            id = 1,
            filename = "original",
            filepath = original.absolutePath,
            timestamp = 0,
            pageCount = 1,
            fileSize = original.length()
        )

        val result = useCase(record, "../Renamed")

        assertEquals(RenameDocumentResult.Success("_Renamed"), result)
        assertFalse(original.exists())
        val renamed = scansDir.resolve("_Renamed.pdf")
        assertTrue(renamed.exists())
        assertEquals(scansDir.canonicalFile, renamed.parentFile?.canonicalFile)
    }

    @Test
    fun `rename rejects names that sanitize to blank`() = runTest {
        val storageProvider = TestStorageProvider(tmpFolder.root)
        val original = storageProvider.scansDir().resolve("original.pdf").apply { writeText("pdf") }
        val useCase = RenameDocumentUseCase(mock(DocumentRepository::class.java), storageProvider)
        val record = Document(
            filename = "original",
            filepath = original.absolutePath,
            timestamp = 0,
            pageCount = 1,
            fileSize = original.length()
        )

        val result = useCase(record, "...")

        assertEquals(RenameDocumentResult.BlankName, result)
        assertTrue(original.exists())
    }
}
