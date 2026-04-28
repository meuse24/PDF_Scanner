package info.meuse24.pdf_scanner.domain.usecase

import android.content.Context
import android.net.Uri
import info.meuse24.pdf_scanner.domain.model.Document
import info.meuse24.pdf_scanner.domain.pdf.PdfImageRenderer
import info.meuse24.pdf_scanner.data.repository.ScanRepository
import info.meuse24.pdf_scanner.testutil.FakeResourceProvider
import info.meuse24.pdf_scanner.testutil.TestStorageProvider
import info.meuse24.pdf_scanner.util.FileUtil
import info.meuse24.pdf_scanner.util.PdfEditor
import info.meuse24.pdf_scanner.util.TestPdfFactory
import info.meuse24.pdf_scanner.util.TestPdfPage
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Mockito.mock
import java.io.File
import java.io.IOException
import com.tom_roush.pdfbox.pdmodel.PDDocument

class AppendToPdfUseCaseTest {

    @get:Rule
    val tmpFolder = TemporaryFolder()

    private fun record(
        id: Long,
        file: File,
        pageCount: Int,
        isSearchable: Boolean = true
    ) = Document(
        id = id,
        filename = file.nameWithoutExtension,
        filepath = file.absolutePath,
        timestamp = 0L,
        pageCount = pageCount,
        fileSize = file.length(),
        isSearchable = isSearchable,
        extractedText = "cached text",
        tags = "invoice"
    )

    @Test
    fun `append pdf merges pages and invalidates searchable metadata`() = runTest {
        val targetFile = TestPdfFactory.createPdf(
            File(tmpFolder.root, "target.pdf"),
            List(3) { TestPdfPage(420f, 600f) }
        )
        val sourceTemp = TestPdfFactory.createPdf(
            File(tmpFolder.root, "source_temp.pdf"),
            List(2) { TestPdfPage(430f, 610f) }
        )
        val dao = FakeScanDao()
        val pdfEditor = JvmAppendPdfEditor()
        val useCase = AppendToPdfUseCase(
            fileUtil = StubFileUtil(sourceTemp, tmpFolder.root),
            pdfEditor = pdfEditor,
            repository = ScanRepository(dao),
            imagePdfBuilder = StubImagePdfBuilder(sourceTemp, pageCount = 2, rootDir = tmpFolder.root)
        )

        val result = useCase(record(7L, targetFile, pageCount = 3), AppendSource.Pdf(mock(Uri::class.java)))

        assertEquals(5, result.pageCount)
        assertEquals(2, result.appendedPageCount)
        assertEquals(5, pdfEditor.getPageCount(targetFile))
        assertEquals(1, dao.appendInvalidations.size)
        assertEquals(Triple(7L, targetFile.length(), 5), dao.appendInvalidations.single())
        assertFalse(sourceTemp.exists())
    }

    @Test
    fun `append images uses image builder temp pdf and deletes it afterwards`() = runTest {
        val targetFile = TestPdfFactory.createPdf(
            File(tmpFolder.root, "target_images.pdf"),
            listOf(TestPdfPage(420f, 600f))
        )
        val imageTemp = TestPdfFactory.createPdf(
            File(tmpFolder.root, "image_temp.pdf"),
            listOf(TestPdfPage(400f, 580f))
        )
        val dao = FakeScanDao()
        val useCase = AppendToPdfUseCase(
            fileUtil = StubFileUtil(imageTemp, tmpFolder.root),
            pdfEditor = JvmAppendPdfEditor(),
            repository = ScanRepository(dao),
            imagePdfBuilder = StubImagePdfBuilder(imageTemp, pageCount = 1, rootDir = tmpFolder.root)
        )

        val result = useCase(
            record(9L, targetFile, pageCount = 1),
            AppendSource.Images(listOf(mock(Uri::class.java)), ImagePdfOptions(ImagePageLayout.SINGLE))
        )

        assertEquals(2, result.pageCount)
        assertEquals(1, result.appendedPageCount)
        assertFalse(imageTemp.exists())
    }

    @Test
    fun `encrypted target throws append target encrypted exception`() = runTest {
        val targetFile = TestPdfFactory.createPdf(
            File(tmpFolder.root, "target_encrypted.pdf"),
            listOf(TestPdfPage(420f, 600f))
        )
        val sourceTemp = TestPdfFactory.createPdf(
            File(tmpFolder.root, "source_for_encrypted_target.pdf"),
            listOf(TestPdfPage(430f, 610f))
        )
        val useCase = AppendToPdfUseCase(
            fileUtil = StubFileUtil(sourceTemp, tmpFolder.root),
            pdfEditor = SelectiveEncryptedPdfEditor(encryptedFiles = setOf(targetFile.name)),
            repository = ScanRepository(FakeScanDao()),
            imagePdfBuilder = StubImagePdfBuilder(sourceTemp, pageCount = 1, rootDir = tmpFolder.root)
        )

        try {
            useCase(record(10L, targetFile, pageCount = 1, isSearchable = false), AppendSource.Pdf(mock(Uri::class.java)))
            fail("Expected AppendTargetEncryptedException")
        } catch (_: AppendTargetEncryptedException) {
            assertTrue(targetFile.exists())
            assertTrue(sourceTemp.exists())
        }
    }

    @Test
    fun `encrypted source throws append source encrypted exception and deletes temp file`() = runTest {
        val targetFile = TestPdfFactory.createPdf(
            File(tmpFolder.root, "target_for_encrypted_source.pdf"),
            listOf(TestPdfPage(420f, 600f))
        )
        val sourceTemp = TestPdfFactory.createPdf(
            File(tmpFolder.root, "source_encrypted.pdf"),
            listOf(TestPdfPage(430f, 610f))
        )
        val useCase = AppendToPdfUseCase(
            fileUtil = StubFileUtil(sourceTemp, tmpFolder.root),
            pdfEditor = SelectiveEncryptedPdfEditor(encryptedFiles = setOf(sourceTemp.name)),
            repository = ScanRepository(FakeScanDao()),
            imagePdfBuilder = StubImagePdfBuilder(sourceTemp, pageCount = 1, rootDir = tmpFolder.root)
        )

        try {
            useCase(record(10L, targetFile, pageCount = 1, isSearchable = false), AppendSource.Pdf(mock(Uri::class.java)))
            fail("Expected AppendSourceEncryptedException")
        } catch (_: AppendSourceEncryptedException) {
            assertTrue(targetFile.exists())
            assertFalse(sourceTemp.exists())
        }
    }

    @Test
    fun `merge failure leaves target intact and does not invalidate repository`() = runTest {
        val targetFile = TestPdfFactory.createPdf(
            File(tmpFolder.root, "target_failure.pdf"),
            List(3) { TestPdfPage(420f, 600f) }
        )
        val originalSize = targetFile.length()
        val sourceTemp = TestPdfFactory.createPdf(
            File(tmpFolder.root, "source_failure.pdf"),
            listOf(TestPdfPage(430f, 610f))
        )
        val dao = FakeScanDao()
        val pdfEditor = FailingMergePdfEditor()
        val useCase = AppendToPdfUseCase(
            fileUtil = StubFileUtil(sourceTemp, tmpFolder.root),
            pdfEditor = pdfEditor,
            repository = ScanRepository(dao),
            imagePdfBuilder = StubImagePdfBuilder(sourceTemp, pageCount = 1, rootDir = tmpFolder.root)
        )

        val error = runCatching {
            useCase(record(11L, targetFile, pageCount = 3), AppendSource.Pdf(mock(Uri::class.java)))
        }.exceptionOrNull()

        assertTrue(error is IOException)
        assertEquals(3, pdfEditor.getPageCount(targetFile))
        assertEquals(originalSize, targetFile.length())
        assertTrue(dao.appendInvalidations.isEmpty())
        assertFalse(sourceTemp.exists())
    }
}

private open class JvmAppendPdfEditor : PdfEditor() {
    override fun getPageCount(pdfFile: File): Int = PDDocument.load(pdfFile).use { it.numberOfPages }
}

private class StubFileUtil(
    private val tempFile: File,
    rootDir: File
) : FileUtil(
    context = mock(Context::class.java),
    storageProvider = TestStorageProvider(rootDir),
    resourceProvider = FakeResourceProvider()
) {
    override fun copyToTemp(source: Any, suffix: String): File = tempFile
}

private class StubImagePdfBuilder(
    private val tempFile: File,
    private val pageCount: Int,
    rootDir: File
) : ImagePdfBuilder(
    imageRenderer = EmptyPdfImageRenderer,
    pdfEditor = PdfEditor(),
    storageProvider = TestStorageProvider(rootDir)
) {
    override suspend fun createTempPdf(
        imageUris: List<Any>,
        options: ImagePdfOptions
    ): ImagePdfBuildResult = ImagePdfBuildResult(
        pdfFile = tempFile,
        pageCount = pageCount,
        skippedCount = 0
    )
}

private object EmptyPdfImageRenderer : PdfImageRenderer {
    override suspend fun decodeBitmapBytes(uri: Any, maxDimension: Int): ByteArray? = null
}

private class FailingMergePdfEditor : JvmAppendPdfEditor() {
    override fun mergePdfs(inputs: List<File>, output: File) {
        throw IOException("disk full")
    }
}

private class SelectiveEncryptedPdfEditor(
    private val encryptedFiles: Set<String>
) : JvmAppendPdfEditor() {
    override fun isPdfEncrypted(input: File): Boolean = input.name in encryptedFiles
}

