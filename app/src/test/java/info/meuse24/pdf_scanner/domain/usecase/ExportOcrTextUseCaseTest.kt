package info.meuse24.pdf_scanner.domain.usecase

import info.meuse24.pdf_scanner.domain.model.Document
import info.meuse24.pdf_scanner.domain.gateway.DownloadEntry
import info.meuse24.pdf_scanner.domain.gateway.DownloadsStorage
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportOcrTextUseCaseTest {

    @Test
    fun `single document uses filename suffix and writes text`() = runTest {
        val storage = FakeOcrDownloadsStorage()
        val useCase = ExportOcrTextUseCase(storage)

        val displayName = useCase(listOf(document(filename = "scan", text = "OCR text")))

        assertEquals("scan_ocr.txt", displayName)
        assertEquals("scan_ocr.txt", storage.displayName)
        assertEquals("text/plain", storage.mimeType)
        assertEquals("OCR text", storage.content)
    }

    @Test
    fun `multiple documents include headers and fallback text`() = runTest {
        val storage = FakeOcrDownloadsStorage()
        val useCase = ExportOcrTextUseCase(storage)

        useCase(
            listOf(
                document(filename = "first", text = "Text A"),
                document(filename = "second", text = null)
            )
        )

        assertTrue(storage.displayName.startsWith("ocr_export_"))
        assertTrue(storage.content.contains("=== first ==="))
        assertTrue(storage.content.contains("Text A"))
        assertTrue(storage.content.contains("=== second ==="))
        assertTrue(storage.content.contains("[Kein OCR-Text vorhanden]"))
    }

    private fun document(filename: String, text: String?) = Document(
        id = filename.hashCode().toLong(),
        filename = filename,
        filepath = "/tmp/$filename.pdf",
        timestamp = 0L,
        pageCount = 1,
        fileSize = 0L,
        extractedText = text
    )
}

private class FakeOcrDownloadsStorage : DownloadsStorage {
    var displayName: String = ""
    var mimeType: String = ""
    var content: String = ""

    override fun writeDownload(
        displayName: String,
        mimeType: String,
        writer: (java.io.OutputStream) -> Unit
    ): DownloadEntry {
        this.displayName = displayName
        this.mimeType = mimeType
        val output = ByteArrayOutputStream()
        writer(output)
        content = output.toString(Charsets.UTF_8.name())
        return object : DownloadEntry {
            override val displayName: String = this@FakeOcrDownloadsStorage.displayName
            override fun delete() = Unit
        }
    }
}
