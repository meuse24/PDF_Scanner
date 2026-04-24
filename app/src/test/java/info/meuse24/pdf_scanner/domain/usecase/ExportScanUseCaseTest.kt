package info.meuse24.pdf_scanner.domain.usecase

import info.meuse24.pdf_scanner.util.DownloadEntry
import info.meuse24.pdf_scanner.domain.model.Document
import info.meuse24.pdf_scanner.util.DownloadsStorage
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream

class ExportScanUseCaseTest {

    @get:Rule
    val tmpFolder = TemporaryFolder()

    @Test
    fun `writes pdf into downloads storage and returns display name`() = runTest {
        val pdfFile = tmpFolder.newFile("contract.pdf").apply { writeText("pdf-content") }
        val downloadsStorage = FakeDownloadsStorage()
        val useCase = ExportScanUseCase(downloadsStorage)

        val displayName = useCase(
            Document(
                id = 1L,
                filename = "Contract",
                filepath = pdfFile.absolutePath,
                timestamp = 0L,
                pageCount = 1,
                fileSize = pdfFile.length()
            )
        )

        assertEquals("Contract.pdf", displayName)
        assertEquals("Contract.pdf", downloadsStorage.displayName)
        assertEquals("application/pdf", downloadsStorage.mimeType)
        assertArrayEquals(pdfFile.readBytes(), downloadsStorage.bytes.toByteArray())
    }

    @Test
    fun `fails before writing when source file is missing`() = runTest {
        val missingFile = File(tmpFolder.root, "missing.pdf")
        val downloadsStorage = FakeDownloadsStorage()
        val useCase = ExportScanUseCase(downloadsStorage)

        val error = runCatching {
            useCase(
                Document(
                    id = 1L,
                    filename = "Missing",
                    filepath = missingFile.absolutePath,
                    timestamp = 0L,
                    pageCount = 1,
                    fileSize = 0L
                )
            )
        }.exceptionOrNull()

        assertTrue(error is IllegalStateException)
        assertEquals(0, downloadsStorage.writeCalls)
    }
}

private class FakeDownloadsStorage : DownloadsStorage {
    var displayName: String? = null
    var mimeType: String? = null
    val bytes = ByteArrayOutputStream()
    var writeCalls: Int = 0

    override fun writeDownload(
        displayName: String,
        mimeType: String,
        writer: (OutputStream) -> Unit
    ): DownloadEntry {
        this.displayName = displayName
        this.mimeType = mimeType
        writeCalls++
        bytes.reset()
        writer(bytes)
        return object : DownloadEntry {
            override val displayName: String = displayName
            override fun delete() = Unit
        }
    }
}

