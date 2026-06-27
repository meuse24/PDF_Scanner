package info.meuse24.pdf_scanner.domain.usecase

import info.meuse24.pdf_scanner.domain.gateway.DocumentFileStore
import info.meuse24.pdf_scanner.domain.gateway.SearchablePdfGenerator
import info.meuse24.pdf_scanner.domain.model.OcrPipelineStatus
import info.meuse24.pdf_scanner.domain.repository.DocumentRepository
import java.io.File
import java.io.IOException
import java.lang.reflect.Proxy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Mockito.mock

class ImportScanUseCaseTest {

    @get:Rule
    val tmpFolder = TemporaryFolder()

    @Test
    fun `OCR failure deletes copied PDF and thumbnail`() = runTest {
        val savedPdf = tmpFolder.newFile("scan.pdf").apply { writeText("pdf") }
        val thumbnail = tmpFolder.newFile("scan.jpg").apply { writeText("thumb") }
        val useCase = ImportScanUseCase(
            fileStore = CleanupFileStore(savedPdf, thumbnail),
            searchablePdfBuilder = object : SearchablePdfGenerator {
                override suspend fun makeSearchable(
                    inputFile: File,
                    languageCode: String,
                    onProgress: (Int, Int) -> Unit,
                    onStatus: (OcrPipelineStatus) -> Unit
                ): SearchableResult = throw IOException("OCR failed")
            },
            repository = mock(DocumentRepository::class.java)
        )

        runCatching {
            useCase(
                pdfUri = Any(),
                pageCount = 1,
                filename = "scan",
                thumbnailUri = Any(),
                makeSearchable = true
            )
        }

        assertFalse(savedPdf.exists())
        assertFalse(thumbnail.exists())
    }

    @Test
    fun `database failure deletes copied PDF and thumbnail`() = runTest {
        val savedPdf = tmpFolder.newFile("scan.pdf").apply { writeText("pdf") }
        val thumbnail = tmpFolder.newFile("scan.jpg").apply { writeText("thumb") }
        val repository = failingDocumentRepository()
        val useCase = ImportScanUseCase(
            fileStore = CleanupFileStore(savedPdf, thumbnail),
            searchablePdfBuilder = object : SearchablePdfGenerator {
                override suspend fun makeSearchable(
                    inputFile: File,
                    languageCode: String,
                    onProgress: (Int, Int) -> Unit,
                    onStatus: (OcrPipelineStatus) -> Unit
                ) = SearchableResult("", emptyList(), null)
            },
            repository = repository
        )

        runCatching {
            useCase(
                pdfUri = Any(),
                pageCount = 1,
                filename = "scan",
                thumbnailUri = Any()
            )
        }

        assertFalse(savedPdf.exists())
        assertFalse(thumbnail.exists())
    }
}

private fun failingDocumentRepository(): DocumentRepository =
    Proxy.newProxyInstance(
        DocumentRepository::class.java.classLoader,
        arrayOf(DocumentRepository::class.java)
    ) { _, method, _ ->
        if (method.name == "saveScan") throw IOException("database failed")
        null
    } as DocumentRepository

private class CleanupFileStore(
    private val savedPdf: File,
    private val thumbnail: File
) : DocumentFileStore {
    override fun savePdf(source: Any, filename: String): File = savedPdf

    override fun saveThumbnail(source: Any, filename: String): File = thumbnail

    override fun copyToTemp(source: Any, suffix: String): File =
        throw UnsupportedOperationException()

    override fun exists(path: String): Boolean = File(path).exists()
}
