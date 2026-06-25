package info.meuse24.pdf_scanner.domain.usecase

import info.meuse24.pdf_scanner.domain.gateway.DownloadEntry
import info.meuse24.pdf_scanner.domain.gateway.DownloadsStorage
import info.meuse24.pdf_scanner.domain.gateway.PdfPageJpgRenderer
import info.meuse24.pdf_scanner.domain.model.Document
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

class ExportAsJpgUseCaseTest {

    @get:Rule
    val tmpFolder = TemporaryFolder()

    @Test
    fun `writes every page with stable numbering into sanitized folder`() = runTest {
        val source = tmpFolder.newFile("source.pdf").apply { writeText("pdf") }
        val storage = FakeJpgFolderDownloadsStorage()
        val useCase = ExportAsJpgUseCase(
            downloadsStorage = storage,
            pdfPageJpgRenderer = FakeJpgPageRenderer(
                listOf(byteArrayOf(1), byteArrayOf(2, 3), byteArrayOf(4))
            )
        )

        val result = useCase(document(source, "my/file:name?.PDF"))

        assertEquals("my_file_name_", result.folderName)
        assertEquals(3, result.pageCount)
        assertEquals(
            listOf("page_1.jpg", "page_2.jpg", "page_3.jpg"),
            storage.entries.map { it.displayName }
        )
        assertTrue(storage.entries.all { it.subfolder == "my_file_name_" })
        assertArrayEquals(byteArrayOf(2, 3), storage.entries[1].bytes.toByteArray())
    }

    @Test
    fun `single page still uses page one filename`() = runTest {
        val source = tmpFolder.newFile("source.pdf").apply { writeText("pdf") }
        val storage = FakeJpgFolderDownloadsStorage()
        val useCase = ExportAsJpgUseCase(
            downloadsStorage = storage,
            pdfPageJpgRenderer = FakeJpgPageRenderer(listOf(byteArrayOf(1)))
        )

        val result = useCase(document(source, ".pdf"))

        assertEquals("export", result.folderName)
        assertEquals("page_1.jpg", storage.entries.single().displayName)
    }

    @Test
    fun `rolls back committed pages when a later write fails`() = runTest {
        val source = tmpFolder.newFile("source.pdf").apply { writeText("pdf") }
        val storage = FakeJpgFolderDownloadsStorage(failOnWriteCall = 2)
        val useCase = ExportAsJpgUseCase(
            downloadsStorage = storage,
            pdfPageJpgRenderer = FakeJpgPageRenderer(
                listOf(byteArrayOf(1), byteArrayOf(2))
            )
        )

        val error = runCatching { useCase(document(source, "scan.pdf")) }.exceptionOrNull()

        assertTrue(error is IllegalStateException)
        assertEquals(1, storage.entries.size)
        assertTrue(storage.entries.single().deleted)
    }

    @Test
    fun `fails before rendering when source file is missing`() = runTest {
        val renderer = FakeJpgPageRenderer(emptyList())
        val storage = FakeJpgFolderDownloadsStorage()
        val useCase = ExportAsJpgUseCase(storage, renderer)

        val error = runCatching {
            useCase(document(File(tmpFolder.root, "missing.pdf"), "missing.pdf"))
        }.exceptionOrNull()

        assertTrue(error is IllegalStateException)
        assertEquals(0, renderer.renderCalls)
        assertEquals(0, storage.writeCalls)
    }

    private fun document(file: File, filename: String) = Document(
        id = 1L,
        filename = filename,
        filepath = file.absolutePath,
        timestamp = 0L,
        pageCount = 1,
        fileSize = file.length()
    )
}

private class FakeJpgPageRenderer(
    private val pages: List<ByteArray>
) : PdfPageJpgRenderer {
    var renderCalls = 0

    override fun renderPages(
        pdfFile: File,
        onPage: (pageIndex: Int, pageCount: Int, writeJpeg: (OutputStream) -> Unit) -> Unit
    ) {
        renderCalls++
        pages.forEachIndexed { index, bytes ->
            onPage(index, pages.size) { output -> output.write(bytes) }
        }
    }
}

private class FakeJpgFolderDownloadsStorage(
    private val failOnWriteCall: Int? = null
) : DownloadsStorage {
    val entries = mutableListOf<FakeJpgFolderDownloadEntry>()
    var writeCalls = 0

    override fun writeDownload(
        displayName: String,
        mimeType: String,
        writer: (OutputStream) -> Unit
    ): DownloadEntry = error("Root download not expected")

    override fun writeDownloadToSubfolder(
        displayName: String,
        mimeType: String,
        subfolder: String,
        writer: (OutputStream) -> Unit
    ): DownloadEntry {
        writeCalls++
        if (writeCalls == failOnWriteCall) error("synthetic download failure")

        val bytes = ByteArrayOutputStream()
        writer(bytes)
        return FakeJpgFolderDownloadEntry(displayName, subfolder, bytes).also(entries::add)
    }
}

private class FakeJpgFolderDownloadEntry(
    override val displayName: String,
    val subfolder: String,
    val bytes: ByteArrayOutputStream
) : DownloadEntry {
    var deleted = false

    override fun delete() {
        deleted = true
    }
}
