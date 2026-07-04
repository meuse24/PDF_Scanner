package info.meuse24.pdf_scanner.util.sync

import info.meuse24.pdf_scanner.R
import info.meuse24.pdf_scanner.domain.gateway.ResourceProvider
import info.meuse24.pdf_scanner.domain.gateway.StorageProvider
import info.meuse24.pdf_scanner.domain.model.Document
import info.meuse24.pdf_scanner.domain.repository.DocumentRepository
import info.meuse24.pdf_scanner.domain.usecase.ImportFileUseCase
import info.meuse24.pdf_scanner.testutil.FakeResourceProvider
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.parameters
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Mockito.mock
import org.mockito.Mockito.verifyNoInteractions
import java.io.File

private const val PIN = "4711"

class LocalSyncRoutingTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    // Plain Mockito argument matchers (any()) don't play well with Kotlin suspend
    // functions (the compiler-added Continuation parameter throws off Mockito's
    // matcher stack), so the two methods actually used by the routes are
    // hand-implemented via interface delegation instead of being stubbed.
    private fun repositoryWithDocuments(vararg documents: Document): DocumentRepository {
        val list = documents.toList()
        return object : DocumentRepository by mock(DocumentRepository::class.java) {
            override fun getAllScans(): kotlinx.coroutines.flow.Flow<List<Document>> = flowOf(list)
            override suspend fun getScansByIds(ids: List<Long>): List<Document> = list.filter { it.id in ids }
        }
    }

    private fun noOpImportFileUseCase(): ImportFileUseCase = mock(ImportFileUseCase::class.java)

    private fun testStorageProvider(): StorageProvider = object : StorageProvider {
        override fun scansDir(): File = temporaryFolder.newFolder("scans-${System.nanoTime()}")
        override fun tempDir(): File = temporaryFolder.newFolder("temp-${System.nanoTime()}")
    }

    private fun testResourceProvider(): ResourceProvider = FakeResourceProvider(
        strings = mapOf(
            R.string.local_sync_web_login_title to "M24 PDF Scanner – PC connection",
            R.string.local_sync_web_login_heading to "PC connection",
            R.string.local_sync_web_login_instructions to "Enter the 4-digit PIN shown on your phone.",
            R.string.local_sync_web_login_button to "Connect",
            R.string.local_sync_web_error_wrong_pin to "Wrong PIN.",
            R.string.local_sync_web_error_locked_out to "Too many failed attempts. Please try again in %1\$d seconds.",
            R.string.local_sync_web_locked_out_title to "M24 PDF Scanner – locked",
            R.string.local_sync_web_locked_out_heading to "Too many failed attempts",
            R.string.local_sync_web_locked_out_message to "The PC connection was stopped for security reasons.",
            R.string.local_sync_web_documents_title to "M24 PDF Scanner – Archive",
            R.string.local_sync_web_documents_heading to "Your archive",
            R.string.local_sync_web_empty_state to "No documents yet.",
            R.string.local_sync_web_document_meta to "%1\$d pages, %2\$s",
            R.string.local_sync_web_download_label to "Download %1\$s",
            R.string.local_sync_web_pin_digit_label to "PIN digit %1\$d of %2\$d",
            R.string.local_sync_web_upload_heading to "Upload PDF",
            R.string.local_sync_web_upload_hint to "Maximum 25 MB per file.",
            R.string.local_sync_web_upload_dropzone_label to "Choose a PDF or drop it here",
            R.string.local_sync_web_upload_button to "Upload",
            R.string.local_sync_web_upload_success to "%1\$s.pdf was imported.",
            R.string.local_sync_web_upload_not_pdf to "The file is not a valid PDF.",
            R.string.local_sync_web_upload_too_large to "The file is larger than 25 MB.",
            R.string.local_sync_web_upload_failed to "The upload failed.",
            R.string.local_sync_web_html_lang to "en"
        )
    )

    private fun sampleDocument(name: String = "<Rechnung> & \"Q1\"", id: Long = 1, filepath: String? = null) =
        Document(
            id = id,
            filename = name,
            filepath = filepath ?: temporaryFolder.newFile("$id.pdf").apply { writeText("%PDF-1.7") }.absolutePath,
            timestamp = 0,
            pageCount = 3,
            fileSize = 2_500_000
        )

    @Test
    fun `root page renders the pin form without requiring auth`() = runTest {
        testApplication {
            val repository = repositoryWithDocuments()
            application {
                routing {
                    localSyncRouting(
                        PIN, repository, noOpImportFileUseCase(), testStorageProvider(),
                        testResourceProvider(), LocalSyncSessionStore(), LoginRateLimiter()
                    )
                }
            }

            val response = client.get("/")

            assertEquals(HttpStatusCode.OK, response.status)
            assertTrue(response.bodyAsText().contains("PIN"))
        }
    }

    @Test
    fun `documents page redirects to login when no session cookie is present`() = runTest {
        testApplication {
            val repository = repositoryWithDocuments()
            application {
                routing {
                    localSyncRouting(
                        PIN, repository, noOpImportFileUseCase(), testStorageProvider(),
                        testResourceProvider(), LocalSyncSessionStore(), LoginRateLimiter()
                    )
                }
            }
            val noRedirectClient = createClient { followRedirects = false }

            val response = noRedirectClient.get("/documents")

            assertEquals(HttpStatusCode.Found, response.status)
            assertEquals("/", response.headers[HttpHeaders.Location])
        }
    }

    @Test
    fun `correct pin sets a session cookie and unlocks the document list`() = runTest {
        testApplication {
            val repository = repositoryWithDocuments(sampleDocument())
            application {
                routing {
                    localSyncRouting(
                        PIN,
                        repository,
                        noOpImportFileUseCase(),
                        testStorageProvider(),
                        testResourceProvider(),
                        LocalSyncSessionStore(),
                        LoginRateLimiter()
                    )
                }
            }

            val loginResponse = client.submitForm("/login", parameters { append("pin", PIN) })
            val setCookie = loginResponse.headers[HttpHeaders.SetCookie]
            assertTrue(setCookie.orEmpty().contains(LOCAL_SYNC_SESSION_COOKIE_NAME))
            assertTrue(setCookie.orEmpty().contains("HttpOnly", ignoreCase = true))

            val sessionCookie = setCookie!!.substringBefore(';')
            val documentsResponse = client.get("/documents") {
                header(HttpHeaders.Cookie, sessionCookie)
            }

            assertEquals(HttpStatusCode.OK, documentsResponse.status)
            val body = documentsResponse.bodyAsText()
            assertTrue(body.contains("&lt;Rechnung&gt;"))
            assertFalse(body.contains("<Rechnung>"))
        }
    }

    @Test
    fun `wrong pin does not set a session cookie`() = runTest {
        testApplication {
            val repository = repositoryWithDocuments()
            application {
                routing {
                    localSyncRouting(
                        PIN, repository, noOpImportFileUseCase(), testStorageProvider(),
                        testResourceProvider(), LocalSyncSessionStore(), LoginRateLimiter()
                    )
                }
            }

            val response = client.submitForm("/login", parameters { append("pin", "0000") })

            assertNull(response.headers[HttpHeaders.SetCookie])
            assertTrue(response.bodyAsText().contains("Wrong PIN"))
        }
    }

    @Test
    fun `repeated wrong pins get locked out with backoff`() = runTest {
        testApplication {
            val repository = repositoryWithDocuments()
            application {
                routing {
                    localSyncRouting(
                        PIN, repository, noOpImportFileUseCase(), testStorageProvider(),
                        testResourceProvider(), LocalSyncSessionStore(), LoginRateLimiter()
                    )
                }
            }

            repeat(5) { client.submitForm("/login", parameters { append("pin", "0000") }) }
            val sixth = client.submitForm("/login", parameters { append("pin", "0000") })

            assertTrue(sixth.bodyAsText().contains("Too many failed attempts"))
        }
    }

    @Test
    fun `hard stop message appears once the total-failure cap is hit`() = runTest {
        testApplication {
            val repository = repositoryWithDocuments()
            application {
                routing {
                    localSyncRouting(
                        PIN,
                        repository,
                        noOpImportFileUseCase(),
                        testStorageProvider(),
                        testResourceProvider(),
                        LocalSyncSessionStore(),
                        LoginRateLimiter(failuresBeforeLockout = 100, hardStopAfterTotalFailures = 2)
                    )
                }
            }

            client.submitForm("/login", parameters { append("pin", "0000") })
            val second = client.submitForm("/login", parameters { append("pin", "0000") })

            assertTrue(second.bodyAsText().contains("security reasons"))
        }
    }

    @Test
    fun `download without a session cookie redirects to login`() = runTest {
        testApplication {
            val repository = repositoryWithDocuments(sampleDocument())
            application {
                routing {
                    localSyncRouting(
                        PIN, repository, noOpImportFileUseCase(), testStorageProvider(),
                        testResourceProvider(), LocalSyncSessionStore(), LoginRateLimiter()
                    )
                }
            }
            val noRedirectClient = createClient { followRedirects = false }

            val response = noRedirectClient.get("/documents/1/download")

            assertEquals(HttpStatusCode.Found, response.status)
        }
    }

    @Test
    fun `authorized download serves the file with an escaped rfc6266 filename`() = runTest {
        testApplication {
            val repository = repositoryWithDocuments(sampleDocument(id = 7))
            val sessionStore = LocalSyncSessionStore()
            application {
                routing {
                    localSyncRouting(
                        PIN,
                        repository,
                        noOpImportFileUseCase(),
                        testStorageProvider(),
                        testResourceProvider(),
                        sessionStore,
                        LoginRateLimiter()
                    )
                }
            }
            val sessionId = sessionStore.createSession(System.currentTimeMillis())

            val response = client.get("/documents/7/download") {
                header(HttpHeaders.Cookie, "$LOCAL_SYNC_SESSION_COOKIE_NAME=$sessionId")
            }

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("%PDF-1.7", response.bodyAsText())
            val disposition = response.headers[HttpHeaders.ContentDisposition].orEmpty()
            assertTrue(disposition.contains("filename*=UTF-8''"))
        }
    }

    @Test
    fun `download of an unknown id returns 404`() = runTest {
        testApplication {
            val repository = repositoryWithDocuments()
            val sessionStore = LocalSyncSessionStore()
            application {
                routing {
                    localSyncRouting(
                        PIN, repository, noOpImportFileUseCase(), testStorageProvider(),
                        testResourceProvider(), sessionStore, LoginRateLimiter()
                    )
                }
            }
            val sessionId = sessionStore.createSession(System.currentTimeMillis())

            val response = client.get("/documents/999/download") {
                header(HttpHeaders.Cookie, "$LOCAL_SYNC_SESSION_COOKIE_NAME=$sessionId")
            }

            assertEquals(HttpStatusCode.NotFound, response.status)
        }
    }

    // NOTE: the happy-path "valid PDF gets imported" case cannot be exercised as a pure
    // JVM test: the upload route builds a real android.net.Uri (Uri.fromFile) to hand off
    // to ImportFileUseCase, and android.net.Uri is a stub on the JVM unit-test classpath
    // (returns null even with isReturnDefaultValues=true, which then fails Kotlin's
    // not-null assertion on the platform call's result). That exact path is covered by
    // the on-device instrumentation test LocalSyncUploadInstrumentedTest instead.

    @Test
    fun `uploading a non-pdf payload is rejected without touching the import pipeline`() = runTest {
        testApplication {
            val repository = repositoryWithDocuments()
            val sessionStore = LocalSyncSessionStore()
            val importFileUseCase = mock(ImportFileUseCase::class.java)
            application {
                routing {
                    localSyncRouting(
                        PIN, repository, importFileUseCase, testStorageProvider(),
                        testResourceProvider(), sessionStore, LoginRateLimiter()
                    )
                }
            }
            val sessionId = sessionStore.createSession(System.currentTimeMillis())

            val response = client.submitFormWithBinaryData(
                url = "/documents/upload",
                formData = formData {
                    append(
                        "file",
                        "this is definitely not a pdf".toByteArray(),
                        Headers.build {
                            append(HttpHeaders.ContentDisposition, "filename=\"fake.pdf\"")
                        }
                    )
                }
            ) {
                header(HttpHeaders.Cookie, "$LOCAL_SYNC_SESSION_COOKIE_NAME=$sessionId")
            }

            assertEquals(HttpStatusCode.OK, response.status)
            val body = response.bodyAsText()
            assertTrue(body.contains("not a valid PDF"))
            assertTrue("rejected uploads must announce via role=\"alert\", not a status region", body.contains("role=\"alert\""))
            verifyNoInteractions(importFileUseCase)
        }
    }

    @Test
    fun `documents page shows an empty state when there are no documents`() = runTest {
        testApplication {
            val repository = repositoryWithDocuments()
            val sessionStore = LocalSyncSessionStore()
            application {
                routing {
                    localSyncRouting(
                        PIN, repository, noOpImportFileUseCase(), testStorageProvider(),
                        testResourceProvider(), sessionStore, LoginRateLimiter()
                    )
                }
            }
            val sessionId = sessionStore.createSession(System.currentTimeMillis())

            val response = client.get("/documents") {
                header(HttpHeaders.Cookie, "$LOCAL_SYNC_SESSION_COOKIE_NAME=$sessionId")
            }

            assertEquals(HttpStatusCode.OK, response.status)
            assertTrue(response.bodyAsText().contains("No documents yet."))
        }
    }

    @Test
    fun `download link has a per-document accessible name instead of the generic heading`() = runTest {
        testApplication {
            val repository = repositoryWithDocuments(sampleDocument(id = 7))
            val sessionStore = LocalSyncSessionStore()
            application {
                routing {
                    localSyncRouting(
                        PIN, repository, noOpImportFileUseCase(), testStorageProvider(),
                        testResourceProvider(), sessionStore, LoginRateLimiter()
                    )
                }
            }
            val sessionId = sessionStore.createSession(System.currentTimeMillis())

            val response = client.get("/documents") {
                header(HttpHeaders.Cookie, "$LOCAL_SYNC_SESSION_COOKIE_NAME=$sessionId")
            }

            val body = response.bodyAsText()
            assertTrue(body.contains("aria-label=\"Download &lt;Rechnung&gt; &amp; &quot;Q1&quot;.pdf\""))
            assertFalse(
                "download links must not all share the generic archive heading as their accessible name",
                body.contains("aria-label=\"Your archive\"")
            )
        }
    }
}
