package info.meuse24.pdf_scanner.domain.usecase

import info.meuse24.pdf_scanner.domain.model.Document
import info.meuse24.pdf_scanner.util.DownloadEntry
import info.meuse24.pdf_scanner.util.DownloadsStorage
import info.meuse24.pdf_scanner.util.PdfPageJpgRenderer
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream

class ExportAsJpgUseCaseTest {

    @get:Rule
    val tmpFolder = TemporaryFolder()

    @Test
    fun `writes one jpg per rendered page`() = runTest {
        val pdfFile = tmpFolder.newFile("scan.pdf").apply { writeText("pdf") }
        val renderer = FakePdfPageJpgRenderer(
            pages = listOf(
                RenderedPage(byteArrayOf(1, 2, 3)),
                RenderedPage(byteArrayOf(4, 5))
            )
        )
        val downloadsStorage = FakeJpgDownloadsStorage()
        val useCase = ExportAsJpgUseCase(downloadsStorage, renderer)

        val exportedPages = useCase(scanRecord(pdfFile, "Invoice"))

        assertEquals(2, exportedPages)
        assertEquals(listOf("Invoice_p1.jpg", "Invoice_p2.jpg"), downloadsStorage.entries.map { it.displayName })
        assertEquals(listOf("image/jpeg", "image/jpeg"), downloadsStorage.entries.map { it.mimeType })
        assertArrayEquals(byteArrayOf(1, 2, 3), downloadsStorage.entries[0].bytes.toByteArray())
        assertArrayEquals(byteArrayOf(4, 5), downloadsStorage.entries[1].bytes.toByteArray())
        assertFalse(downloadsStorage.entries[0].deleted)
        assertFalse(downloadsStorage.entries[1].deleted)
    }

    @Test
    fun `rolls back already written jpgs when later page export fails`() = runTest {
        val pdfFile = tmpFolder.newFile("scan.pdf").apply { writeText("pdf") }
        val renderer = FakePdfPageJpgRenderer(
            pages = listOf(
                RenderedPage(byteArrayOf(1)),
                RenderedPage(byteArrayOf(2))
            )
        )
        val downloadsStorage = FakeJpgDownloadsStorage(failOnWriteCall = 2)
        val useCase = ExportAsJpgUseCase(downloadsStorage, renderer)

        val error = runCatching { useCase(scanRecord(pdfFile, "Invoice")) }.exceptionOrNull()

        assertTrue(error is IllegalStateException)
        assertEquals("synthetic download failure", error?.message)
        assertEquals(2, downloadsStorage.writeCalls)
        assertEquals(1, downloadsStorage.entries.size)
        assertTrue(downloadsStorage.entries[0].deleted)
    }

    @Test
    fun `fails before rendering when source file is missing`() = runTest {
        val missingFile = File(tmpFolder.root, "missing.pdf")
        val renderer = FakePdfPageJpgRenderer()
        val downloadsStorage = FakeJpgDownloadsStorage()
        val useCase = ExportAsJpgUseCase(downloadsStorage, renderer)

        val error = runCatching {
            useCase(scanRecord(missingFile, "Missing"))
        }.exceptionOrNull()

        assertTrue(error is IllegalStateException)
        assertEquals(0, renderer.renderCalls)
        assertEquals(0, downloadsStorage.writeCalls)
    }

    private fun scanRecord(file: File, filename: String) = Document(
        id = 1L,
        filename = filename,
        filepath = file.absolutePath,
        timestamp = 0L,
        pageCount = 1,
        fileSize = file.length()
    )
}

private data class RenderedPage(val jpegBytes: ByteArray)

private class FakePdfPageJpgRenderer(
    private val pages: List<RenderedPage> = emptyList()
) : PdfPageJpgRenderer {
    var renderCalls: Int = 0

    override fun renderPages(
        sourceFile: File,
        onPageRendered: (pageIndex: Int, pageCount: Int, writeJpeg: (OutputStream) -> Unit) -> Unit
    ) {
        renderCalls++
        pages.forEachIndexed { index, page ->
            onPageRendered(index, pages.size) { output -> output.write(page.jpegBytes) }
        }
    }
}

private class FakeJpgDownloadsStorage(
    private val failOnWriteCall: Int? = null
) : DownloadsStorage {
    val entries = mutableListOf<FakeDownloadEntry>()
    var writeCalls: Int = 0

    override fun writeDownload(
        displayName: String,
        mimeType: String,
        writer: (OutputStream) -> Unit
    ): DownloadEntry {
        writeCalls++
        if (writeCalls == failOnWriteCall) {
            error("synthetic download failure")
        }

        val bytes = ByteArrayOutputStream()
        writer(bytes)

        return FakeDownloadEntry(
            displayName = displayName,
            mimeType = mimeType,
            bytes = bytes
        ).also(entries::add)
    }
}

private class FakeDownloadEntry(
    override val displayName: String,
    val mimeType: String,
    val bytes: ByteArrayOutputStream
) : DownloadEntry {
    var deleted: Boolean = false

    override fun delete() {
        deleted = true
    }
}

