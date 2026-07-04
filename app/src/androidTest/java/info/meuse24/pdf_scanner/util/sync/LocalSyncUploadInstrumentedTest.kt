package info.meuse24.pdf_scanner.util.sync

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import info.meuse24.pdf_scanner.R
import info.meuse24.pdf_scanner.data.local.AppDatabase
import info.meuse24.pdf_scanner.data.repository.ScanRepository
import info.meuse24.pdf_scanner.domain.usecase.ImportFileUseCase
import info.meuse24.pdf_scanner.util.AndroidResourceProvider
import info.meuse24.pdf_scanner.util.AndroidStorageProvider
import info.meuse24.pdf_scanner.util.FileUtil
import info.meuse24.pdf_scanner.util.PdfEditor
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Exercises the PC-Sync upload/download routes against the *real* import pipeline
 * (real android.net.Uri, real PdfBox validation, real Room-backed repository).
 * The equivalent JVM test (LocalSyncRoutingTest) mocks ImportFileUseCase because
 * `Uri.fromFile()` is a no-op stub outside an Android runtime.
 */
@RunWith(AndroidJUnit4::class)
class LocalSyncUploadInstrumentedTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val storageProvider = AndroidStorageProvider(context)
    private val resourceProvider = AndroidResourceProvider(context)
    private lateinit var database: AppDatabase
    private lateinit var repository: ScanRepository
    private lateinit var importFileUseCase: ImportFileUseCase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = ScanRepository(database.scanDao())
        importFileUseCase = ImportFileUseCase(
            fileUtil = FileUtil(context, storageProvider, resourceProvider),
            pdfEditor = PdfEditor(),
            repository = repository,
            resourceProvider = resourceProvider
        )
    }

    @After
    fun tearDown() {
        database.close()
        storageProvider.scansDir().listFiles()
            ?.filter { it.name.startsWith("androidtest_sync_") }
            ?.forEach { it.delete() }
    }

    private fun validPdfBytes(): ByteArray {
        val file = File.createTempFile("androidtest_sync_source_", ".pdf", storageProvider.tempDir())
        PDDocument().use { document ->
            document.addPage(PDPage())
            document.save(file)
        }
        return file.readBytes().also { file.delete() }
    }

    @Test
    fun uploadingAValidPdfImportsItThroughTheRealPipeline() = runBlocking {
        testApplication {
            val sessionStore = LocalSyncSessionStore()
            application {
                routing {
                    localSyncRouting(
                        pin = "1234",
                        documentRepository = repository,
                        importFileUseCase = importFileUseCase,
                        storageProvider = storageProvider,
                        resourceProvider = resourceProvider,
                        sessionStore = sessionStore,
                        rateLimiter = LoginRateLimiter()
                    )
                }
            }
            val sessionId = sessionStore.createSession(System.currentTimeMillis())

            val response = client.submitFormWithBinaryData(
                url = "/documents/upload",
                formData = formData {
                    append(
                        "file",
                        validPdfBytes(),
                        Headers.build {
                            append(HttpHeaders.ContentDisposition, "filename=\"androidtest_sync_upload.pdf\"")
                        }
                    )
                }
            ) {
                header(HttpHeaders.Cookie, "$LOCAL_SYNC_SESSION_COOKIE_NAME=$sessionId")
            }

            assertEquals(HttpStatusCode.OK, response.status)
            val body = response.bodyAsText()
            val expectedMessage = resourceProvider.getString(
                R.string.local_sync_web_upload_success,
                "androidtest_sync_upload"
            )
            assertTrue(body.contains(expectedMessage))

            val saved = repository.getAllScans().first()
            assertEquals(1, saved.size)
            assertTrue(File(saved.single().filepath).exists())
        }
    }

    @Test
    fun uploadedDocumentCanBeDownloadedAgain() = runBlocking {
        testApplication {
            val sessionStore = LocalSyncSessionStore()
            application {
                routing {
                    localSyncRouting(
                        pin = "1234",
                        documentRepository = repository,
                        importFileUseCase = importFileUseCase,
                        storageProvider = storageProvider,
                        resourceProvider = resourceProvider,
                        sessionStore = sessionStore,
                        rateLimiter = LoginRateLimiter()
                    )
                }
            }
            val sessionId = sessionStore.createSession(System.currentTimeMillis())
            val cookie = "$LOCAL_SYNC_SESSION_COOKIE_NAME=$sessionId"

            client.submitFormWithBinaryData(
                url = "/documents/upload",
                formData = formData {
                    append(
                        "file",
                        validPdfBytes(),
                        Headers.build {
                            append(HttpHeaders.ContentDisposition, "filename=\"androidtest_sync_roundtrip.pdf\"")
                        }
                    )
                }
            ) {
                header(HttpHeaders.Cookie, cookie)
            }

            val saved = repository.getAllScans().first().single()
            val download = client.get("/documents/${saved.id}/download") {
                header(HttpHeaders.Cookie, cookie)
            }

            assertEquals(HttpStatusCode.OK, download.status)
        }
    }
}
