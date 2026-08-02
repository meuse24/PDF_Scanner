package info.meuse24.pdf_scanner.util.sync

import android.net.Uri
import info.meuse24.pdf_scanner.R
import info.meuse24.pdf_scanner.domain.gateway.ResourceProvider
import info.meuse24.pdf_scanner.domain.gateway.StorageProvider
import info.meuse24.pdf_scanner.domain.repository.DocumentRepository
import info.meuse24.pdf_scanner.domain.usecase.ImportFileUseCase
import io.ktor.http.Cookie
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.*
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import kotlinx.coroutines.flow.first
import java.io.File
import java.net.URLEncoder

/**
 * PIN-login, document-listing and download/upload routes, factored out of
 * [KtorLocalSyncServer] so they can be exercised via `ktor-server-test-host`
 * without binding a real socket.
 */
internal fun Route.localSyncRouting(
    pin: String,
    documentRepository: DocumentRepository,
    importFileUseCase: ImportFileUseCase,
    storageProvider: StorageProvider,
    resourceProvider: ResourceProvider,
    sessionStore: LocalSyncSessionStore,
    rateLimiter: LoginRateLimiter,
    onHardStop: suspend () -> Unit = {},
    activityTracker: LocalSyncActivityTracker = LocalSyncActivityTracker.NoOp
) {
    fun ApplicationCall.isAuthorized(): Boolean {
        val sessionId = request.cookies[LOCAL_SYNC_SESSION_COOKIE_NAME] ?: return false
        val authorized = sessionStore.touch(sessionId, System.currentTimeMillis())
        // Only an authenticated caller may refresh the inactivity timeout.
        if (authorized) activityTracker.recordActivity()
        return authorized
    }

    get("/") {
        call.respondText(renderLoginPage(resourceProvider, error = null), ContentType.Text.Html)
    }

    post("/login") {
        val clientId = call.request.local.remoteHost
        val now = System.currentTimeMillis()

        val gate = rateLimiter.checkAllowed(clientId, now)
        if (gate is LoginAttemptResult.HardStopped) {
            call.respondText(renderLockedOutPage(resourceProvider), ContentType.Text.Html)
            onHardStop()
            return@post
        }
        if (gate is LoginAttemptResult.LockedOut) {
            call.respondText(
                renderLoginPage(
                    resourceProvider,
                    resourceProvider.getString(R.string.local_sync_web_error_locked_out, gate.retryAfterSeconds)
                ),
                ContentType.Text.Html
            )
            return@post
        }

        val submittedPin = call.receiveParameters()["pin"].orEmpty()
        if (submittedPin == pin) {
            rateLimiter.recordSuccess(clientId)
            activityTracker.recordActivity()
            val sessionId = sessionStore.createSession(now)
            call.response.cookies.append(
                Cookie(
                    name = LOCAL_SYNC_SESSION_COOKIE_NAME,
                    value = sessionId,
                    httpOnly = true,
                    path = "/",
                    extensions = mapOf("SameSite" to "Strict")
                )
            )
            call.respondRedirect("/documents")
            return@post
        }

        when (val failure = rateLimiter.recordFailure(clientId, now)) {
            LoginAttemptResult.HardStopped -> {
                call.respondText(renderLockedOutPage(resourceProvider), ContentType.Text.Html)
                onHardStop()
            }
            is LoginAttemptResult.LockedOut ->
                call.respondText(
                    renderLoginPage(
                        resourceProvider,
                        resourceProvider.getString(
                            R.string.local_sync_web_error_locked_out,
                            failure.retryAfterSeconds
                        )
                    ),
                    ContentType.Text.Html
                )
            LoginAttemptResult.Allowed ->
                call.respondText(
                    renderLoginPage(
                        resourceProvider,
                        resourceProvider.getString(R.string.local_sync_web_error_wrong_pin)
                    ),
                    ContentType.Text.Html
                )
        }
    }

    get("/documents") {
        if (!call.isAuthorized()) {
            call.respondRedirect("/")
            return@get
        }
        val documents = documentRepository.getAllScans().first()
        call.respondText(renderDocumentsPage(resourceProvider, documents), ContentType.Text.Html)
    }

    get("/documents/{id}/download") {
        if (!call.isAuthorized()) {
            call.respondRedirect("/")
            return@get
        }
        val id = call.parameters["id"]?.toLongOrNull()
        val document = id?.let { documentRepository.getScansByIds(listOf(it)) }?.firstOrNull()
        val file = document?.let { File(it.filepath) }?.takeIf(File::exists)
        if (document == null || file == null) {
            call.respond(HttpStatusCode.NotFound)
            return@get
        }

        val downloadName = "${document.filename}.pdf"
        val asciiFallback = downloadName
            .filter { it.code in 32..126 }
            .replace("\"", "'")
            .replace("\\", "_")
            .ifBlank { "document.pdf" }
        val encodedName = URLEncoder.encode(downloadName, "UTF-8").replace("+", "%20")
        call.response.header(
            HttpHeaders.ContentDisposition,
            "attachment; filename=\"$asciiFallback\"; filename*=UTF-8''$encodedName"
        )
        // Counted as an in-flight transfer so a large download cannot hit the idle
        // timeout just because no further request arrives while it streams.
        activityTracker.withTransfer { call.respondFile(file) }
    }

    post("/documents/upload") {
        if (!call.isAuthorized()) {
            call.respondRedirect("/")
            return@post
        }

        var feedback: UploadFeedback? = null
        // Counted as an in-flight transfer for the whole multipart read, so a slow
        // upload cannot be cut short by the inactivity watchdog.
        activityTracker.withTransfer {
            val multipart = call.receiveMultipart()
            multipart.forEachPart { part ->
                if (part is PartData.FileItem && feedback == null) {
                    val originalName = part.originalFileName
                        ?.substringAfterLast('/')
                        ?.takeIf(String::isNotBlank)
                        ?: "Upload"
                    val tempFile = File.createTempFile("m24_sync_upload_", ".pdf", storageProvider.tempDir())
                    feedback = try {
                        part.provider().copyToPdfFile(tempFile)
                        val imported = importFileUseCase(
                            Uri.fromFile(tempFile),
                            File(originalName).nameWithoutExtension.ifBlank { "Upload" }
                        )
                        UploadFeedback.Success(resourceProvider.getString(R.string.local_sync_web_upload_success, imported.filename))
                    } catch (_: UploadNotAPdfException) {
                        UploadFeedback.Error(resourceProvider.getString(R.string.local_sync_web_upload_not_pdf))
                    } catch (_: UploadTooLargeException) {
                        UploadFeedback.Error(resourceProvider.getString(R.string.local_sync_web_upload_too_large))
                    } catch (_: Exception) {
                        UploadFeedback.Error(resourceProvider.getString(R.string.local_sync_web_upload_failed))
                    } finally {
                        tempFile.delete()
                    }
                }
                part.dispose()
            }
        }

        val documents = documentRepository.getAllScans().first()
        call.respondText(renderDocumentsPage(resourceProvider, documents, feedback), ContentType.Text.Html)
    }
}
