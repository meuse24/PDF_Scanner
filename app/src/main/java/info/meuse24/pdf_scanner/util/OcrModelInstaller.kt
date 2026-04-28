package info.meuse24.pdf_scanner.util

import android.content.Context
import com.google.android.gms.common.api.OptionalModuleApi
import com.google.android.gms.common.moduleinstall.InstallStatusListener
import com.google.android.gms.common.moduleinstall.ModuleInstall
import com.google.android.gms.common.moduleinstall.ModuleInstallRequest
import com.google.android.gms.common.moduleinstall.ModuleInstallStatusUpdate
import com.google.mlkit.vision.text.TextRecognizer
import dagger.hilt.android.qualifiers.ApplicationContext
import info.meuse24.pdf_scanner.domain.model.OcrModelInstallException
import info.meuse24.pdf_scanner.domain.model.OcrPipelineStatus
import info.meuse24.pdf_scanner.domain.model.OcrScript
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

interface OcrModelInstaller {
    suspend fun ensureModelAvailable(
        recognizer: TextRecognizer,
        script: OcrScript,
        onStatus: (OcrPipelineStatus) -> Unit = {}
    )

    suspend fun ensureBarcodeModelAvailable(
        scanner: OptionalModuleApi,
        onStatus: (OcrPipelineStatus) -> Unit = {}
    )
}

@Singleton
class AndroidOcrModelInstaller @Inject constructor(
    @param:ApplicationContext private val context: Context
) : OcrModelInstaller {

    override suspend fun ensureModelAvailable(
        recognizer: TextRecognizer,
        script: OcrScript,
        onStatus: (OcrPipelineStatus) -> Unit
    ) {
        if (!script.requiresModuleDownload()) return
        ensureApiAvailable(recognizer, onStatus)
    }

    override suspend fun ensureBarcodeModelAvailable(
        scanner: OptionalModuleApi,
        onStatus: (OcrPipelineStatus) -> Unit
    ) {
        ensureApiAvailable(scanner, onStatus)
    }

    private suspend fun ensureApiAvailable(
        api: OptionalModuleApi,
        onStatus: (OcrPipelineStatus) -> Unit
    ) {
        val moduleInstallClient = ModuleInstall.getClient(context)
        val availability = moduleInstallClient
            .areModulesAvailable(api)
            .awaitTask()

        if (availability.areModulesAvailable()) return

        onStatus(OcrPipelineStatus.PreparingModel)

        suspendCancellableCoroutine<Unit> { cont ->
            var completed = false
            lateinit var listener: InstallStatusListener

            fun finish(block: () -> Unit) {
                if (completed) return
                completed = true
                moduleInstallClient.unregisterListener(listener)
                block()
            }

            listener = InstallStatusListener { update ->
                when (update.installState) {
                    ModuleInstallStatusUpdate.InstallState.STATE_PENDING ->
                        onStatus(OcrPipelineStatus.PreparingModel)
                    ModuleInstallStatusUpdate.InstallState.STATE_DOWNLOADING,
                    ModuleInstallStatusUpdate.InstallState.STATE_DOWNLOAD_PAUSED ->
                        onStatus(OcrPipelineStatus.DownloadingModel)
                    ModuleInstallStatusUpdate.InstallState.STATE_INSTALLING ->
                        onStatus(OcrPipelineStatus.InstallingModel)
                    ModuleInstallStatusUpdate.InstallState.STATE_COMPLETED ->
                        finish { cont.resume(Unit) }
                    ModuleInstallStatusUpdate.InstallState.STATE_CANCELED,
                    ModuleInstallStatusUpdate.InstallState.STATE_FAILED ->
                        finish {
                            cont.resumeWithException(
                                OcrModelInstallException(
                                    message = "Model module download failed: ${update.errorCode}"
                                )
                            )
                        }
                    else -> Unit
                }
            }

            val request = ModuleInstallRequest
                .newBuilder()
                .addApi(api)
                .setListener(listener)
                .build()

            moduleInstallClient.installModules(request)
                .addOnSuccessListener { response ->
                    if (response.areModulesAlreadyInstalled()) {
                        finish { cont.resume(Unit) }
                    }
                }
                .addOnFailureListener { error ->
                    finish {
                        cont.resumeWithException(
                            OcrModelInstallException(
                                message = "Model module install request failed",
                                cause = error
                            )
                        )
                    }
                }

            cont.invokeOnCancellation {
                moduleInstallClient.unregisterListener(listener)
            }
        }
    }
}
