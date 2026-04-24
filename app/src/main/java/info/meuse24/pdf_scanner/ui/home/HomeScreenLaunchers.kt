package info.meuse24.pdf_scanner.ui.home

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import com.google.mlkit.common.MlKitException
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult

internal data class HomeScreenLaunchers(
    val launchScanner: () -> Unit,
    val launchPdfImport: () -> Unit,
    val launchImageImport: () -> Unit
)

@Composable
internal fun rememberHomeScreenLaunchers(
    context: Context,
    viewModel: HomeViewModel,
    errorDeviceUnsupported: String,
    errorScannerUnavailable: String,
    onScanImported: (GmsDocumentScanningResult) -> Unit,
    onPdfImported: (Uri) -> Unit,
    onNavigateToImagesToPdf: () -> Unit
): HomeScreenLaunchers {
    val scanLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            GmsDocumentScanningResult.fromActivityResultIntent(result.data)?.let(onScanImported)
        }
    }

    val importFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            onPdfImported(uri)
        }
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.setPendingImageUris(uris)
            onNavigateToImagesToPdf()
        }
    }

    return HomeScreenLaunchers(
        launchScanner = {
            val options = GmsDocumentScannerOptions.Builder()
                .setGalleryImportAllowed(true)
                .setResultFormats(
                    GmsDocumentScannerOptions.RESULT_FORMAT_PDF,
                    GmsDocumentScannerOptions.RESULT_FORMAT_JPEG
                )
                .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
                .setPageLimit(50)
                .build()
            GmsDocumentScanning.getClient(options)
                .getStartScanIntent(context as Activity)
                .addOnSuccessListener { intentSender ->
                    scanLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
                }
                .addOnFailureListener { exception ->
                    val message = if (
                        exception is MlKitException &&
                        exception.errorCode == MlKitException.UNSUPPORTED
                    ) {
                        errorDeviceUnsupported
                    } else {
                        exception.message ?: errorScannerUnavailable
                    }
                    viewModel.reportError(message)
                }
        },
        launchPdfImport = {
            importFileLauncher.launch(arrayOf("application/pdf"))
        },
        launchImageImport = {
            imagePickerLauncher.launch("image/*")
        }
    )
}
