package info.meuse24.pdf_scanner.domain.usecase

import info.meuse24.pdf_scanner.domain.gateway.DocxBuilder
import info.meuse24.pdf_scanner.domain.gateway.DocxDocument
import info.meuse24.pdf_scanner.domain.gateway.DownloadEntry
import info.meuse24.pdf_scanner.domain.gateway.DownloadsStorage
import info.meuse24.pdf_scanner.domain.model.Document
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportDocxUseCaseTest {
    @Test
    fun `single document writes docx with filename suffix`() = runTest {
        val storage = FakeDocxDownloadsStorage()
        val builder = FakeDocxBuilder()
        val useCase = ExportDocxUseCase(storage, builder)

        val displayName = useCase(listOf(document(filename = "scan", extractedText = "Hello\nworld")))

        assertEquals("scan.docx", displayName)
        assertEquals("scan.docx", storage.displayName)
        assertEquals(DOCX_MIME_TYPE, storage.mimeType)
        assertEquals(listOf("Hello world"), builder.document.pages.single().paragraphs)
    }

    @Test
    fun `page texts take priority over extracted text`() = runTest {
        val storage = FakeDocxDownloadsStorage()
        val builder = FakeDocxBuilder()
        val useCase = ExportDocxUseCase(storage, builder)

        useCase(
            listOf(
                document(
                    filename = "scan",
                    extractedText = "fallback",
                    pageTexts = listOf("page one", "page two")
                )
            )
        )

        assertEquals(2, builder.document.pages.size)
        assertEquals(listOf("page one"), builder.document.pages[0].paragraphs)
        assertEquals(listOf("page two"), builder.document.pages[1].paragraphs)
    }

    @Test(expected = NoExportableTextException::class)
    fun `throws when no document contains text`() = runTest {
        ExportDocxUseCase(FakeDocxDownloadsStorage(), FakeDocxBuilder())(
            listOf(document(filename = "empty"))
        )
    }

    @Test(expected = NoExportableTextException::class)
    fun `throws when document only has list projection text flag`() = runTest {
        ExportDocxUseCase(FakeDocxDownloadsStorage(), FakeDocxBuilder())(
            listOf(document(filename = "list", hasStoredOcrText = true))
        )
    }

    @Test(expected = NoExportableTextException::class)
    fun `throws when document list is empty`() = runTest {
        ExportDocxUseCase(FakeDocxDownloadsStorage(), FakeDocxBuilder())(emptyList())
    }

    @Test
    fun `multiple documents use bulk filename and headings`() = runTest {
        val storage = FakeDocxDownloadsStorage()
        val builder = FakeDocxBuilder()
        val useCase = ExportDocxUseCase(storage, builder)

        useCase(
            listOf(
                document(filename = "first", extractedText = "A"),
                document(filename = "second", extractedText = "B")
            )
        )

        assertTrue(storage.displayName.startsWith("docx_export_"))
        assertEquals("first", builder.document.pages[0].heading)
        assertEquals("second", builder.document.pages[1].heading)
    }

    private fun document(
        filename: String,
        extractedText: String? = null,
        pageTexts: List<String> = emptyList(),
        hasStoredOcrText: Boolean = false
    ) = Document(
        id = filename.hashCode().toLong(),
        filename = filename,
        filepath = "/tmp/$filename.pdf",
        timestamp = 0L,
        pageCount = pageTexts.size.coerceAtLeast(1),
        fileSize = 0L,
        extractedText = extractedText,
        pageTexts = pageTexts,
        hasStoredOcrText = hasStoredOcrText
    )
}

private class FakeDocxBuilder : DocxBuilder {
    lateinit var document: DocxDocument

    override fun writeDocx(doc: DocxDocument, out: OutputStream) {
        document = doc
        out.write(byteArrayOf(1, 2, 3))
    }
}

private class FakeDocxDownloadsStorage : DownloadsStorage {
    var displayName: String = ""
    var mimeType: String = ""
    var content: ByteArray = byteArrayOf()

    override fun writeDownload(
        displayName: String,
        mimeType: String,
        writer: (OutputStream) -> Unit
    ): DownloadEntry {
        this.displayName = displayName
        this.mimeType = mimeType
        val output = ByteArrayOutputStream()
        writer(output)
        content = output.toByteArray()
        return object : DownloadEntry {
            override val displayName: String = this@FakeDocxDownloadsStorage.displayName
            override fun delete() = Unit
        }
    }
}

private const val DOCX_MIME_TYPE = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
