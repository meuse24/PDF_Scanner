package info.meuse24.pdf_scanner.domain.usecase

import android.content.Context
import android.net.Uri
import info.meuse24.pdf_scanner.R
import info.meuse24.pdf_scanner.data.repository.ScanRepository
import info.meuse24.pdf_scanner.util.FileUtil
import info.meuse24.pdf_scanner.util.PdfEditor
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.io.File

class ImportFileUseCaseTest {

    @get:Rule
    val tmpFolder = TemporaryFolder()

    @Test
    fun `imports pdf, generates thumbnail and stores scan record`() = runTest {
        val context = mock(Context::class.java)
        val savedFile = tmpFolder.newFile("invoice.pdf").apply { writeText("pdf") }
        val dao = FakeScanDao()
        val repository = ScanRepository(dao)
        val useCase = ImportFileUseCase(
            fileUtil = FakeFileUtil(context, savedFile),
            pdfEditor = FakeImportPdfEditor(
                encrypted = false,
                pageCount = 3,
                thumbnailWriter = { file -> file.writeText("thumb") }
            ),
            repository = repository,
            context = context
        )

        val record = useCase(mock(Uri::class.java), "Invoice")

        assertEquals(1, dao.inserted.size)
        assertEquals("invoice", record.filename)
        assertEquals(3, record.pageCount)
        assertFalse(record.isEncrypted)
        assertTrue(record.thumbnailPath?.endsWith("invoice.jpg") == true)
    }

    @Test
    fun `invalid pdf deletes copied file and fails`() = runTest {
        val context = mock(Context::class.java)
        val savedFile = tmpFolder.newFile("broken.pdf").apply { writeText("not-a-pdf") }
        val dao = FakeScanDao()
        val repository = ScanRepository(dao)
        val useCase = ImportFileUseCase(
            fileUtil = FakeFileUtil(context, savedFile),
            pdfEditor = FakeImportPdfEditor(encrypted = false, pageCount = 0),
            repository = repository,
            context = context
        )
        `when`(context.getString(R.string.error_pdf_invalid)).thenReturn("invalid")

        val error = runCatching { useCase(mock(Uri::class.java), "Broken") }.exceptionOrNull()

        assertEquals("invalid", error?.message)
        assertFalse(savedFile.exists())
        assertTrue(dao.inserted.isEmpty())
    }

    @Test
    fun `encrypted pdf can be imported without thumbnail`() = runTest {
        val context = mock(Context::class.java)
        val savedFile = tmpFolder.newFile("locked.pdf").apply { writeText("pdf") }
        val dao = FakeScanDao()
        val repository = ScanRepository(dao)
        val useCase = ImportFileUseCase(
            fileUtil = FakeFileUtil(context, savedFile),
            pdfEditor = FakeImportPdfEditor(encrypted = true, pageCount = 0),
            repository = repository,
            context = context
        )

        val record = useCase(mock(Uri::class.java), "Locked")

        assertTrue(record.isEncrypted)
        assertEquals(0, record.pageCount)
        assertNull(record.thumbnailPath)
    }
}

private class FakeFileUtil(
    context: Context,
    private val savedFile: File
) : FileUtil(context) {
    override fun savePdfFromUri(sourceUri: Uri, filename: String): File = savedFile
}

private class FakeImportPdfEditor(
    private val encrypted: Boolean,
    private val pageCount: Int,
    private val thumbnailWriter: ((File) -> Unit)? = null
) : PdfEditor() {
    override fun isPdfEncrypted(input: File): Boolean = encrypted

    override fun getPageCount(pdfFile: File): Int = pageCount

    override fun generateThumbnail(pdfFile: File, outputFile: File): Boolean {
        val writer = thumbnailWriter ?: return false
        writer(outputFile)
        return true
    }
}
