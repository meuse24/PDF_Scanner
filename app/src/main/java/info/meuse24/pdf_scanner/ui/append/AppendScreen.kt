package info.meuse24.pdf_scanner.ui.append

import android.app.Activity
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.mlkit.common.MlKitException
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import info.meuse24.pdf_scanner.R
import info.meuse24.pdf_scanner.domain.usecase.ImagePageLayout
import info.meuse24.pdf_scanner.ui.components.ScanPreviewCard
import info.meuse24.pdf_scanner.ui.imagestopdf.ImagesPdfOptionsContent

@Composable
fun AppendScreen(
    onAppendComplete: (Long) -> Unit,
    viewModel: AppendViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val record by viewModel.record.collectAsStateWithLifecycle()
    val pendingImageUris by viewModel.pendingImageUris.collectAsStateWithLifecycle()
    val editLoading by viewModel.editLoading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val success by viewModel.success.collectAsStateWithLifecycle()
    val pageSetup by viewModel.pageSetup.collectAsStateWithLifecycle()

    val errorDeviceUnsupported = stringResource(R.string.error_device_unsupported)
    val errorScannerUnavailable = stringResource(R.string.error_scanner_unavailable)

    var selectedLayout by rememberSaveable { mutableStateOf(ImagePageLayout.TWO_PER_PAGE) }

    val scanLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val scanResult = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
            val pdf = scanResult?.pdf ?: return@rememberLauncherForActivityResult
            viewModel.appendScan(pdf.uri, pdf.pageCount)
        }
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.setPendingImageUris(uris)
        }
    }

    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.appendPdf(uri)
        }
    }

    fun launchScanner() {
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
                val message = if (exception is MlKitException && exception.errorCode == MlKitException.UNSUPPORTED) {
                    errorDeviceUnsupported
                } else {
                    exception.message ?: errorScannerUnavailable
                }
                viewModel.clearError()
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            }
    }

    LaunchedEffect(success) {
        val message = success ?: return@LaunchedEffect
        val scanId = record?.id ?: return@LaunchedEffect
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        viewModel.clearSuccess()
        onAppendComplete(scanId)
    }

    error?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::clearError,
            title = { Text(stringResource(R.string.error_title)) },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = viewModel::clearError) {
                    Text(stringResource(R.string.action_ok))
                }
            }
        )
    }

    if (editLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val currentRecord = record
    if (currentRecord == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    if (pendingImageUris.isNotEmpty()) {
        ImagesPdfOptionsContent(
            imageUris = pendingImageUris,
            selectedLayout = selectedLayout,
            onLayoutSelected = { selectedLayout = it },
            pageSetup = pageSetup,
            onPageSetupChange = viewModel::updatePageSetup,
            actionLabel = stringResource(R.string.action_append_pages),
            actionEnabled = true,
            actionInProgress = false,
            onAction = { viewModel.appendImages(selectedLayout) },
            modifier = Modifier.fillMaxSize(),
            topContent = {
                Text(
                    text = stringResource(R.string.append_images_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        )
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.append_pages_title),
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.append_pages_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        item {
            ScanPreviewCard(
                record = currentRecord,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    AppendSourceOption(
                        title = stringResource(R.string.append_source_scan_title),
                        subtitle = stringResource(R.string.append_source_scan_subtitle),
                        icon = Icons.Default.CameraAlt,
                        onClick = ::launchScanner
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    AppendSourceOption(
                        title = stringResource(R.string.append_source_images_title),
                        subtitle = stringResource(R.string.append_source_images_subtitle),
                        icon = Icons.Default.Image,
                        onClick = { imagePickerLauncher.launch("image/*") }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    AppendSourceOption(
                        title = stringResource(R.string.append_source_pdf_title),
                        subtitle = stringResource(R.string.append_source_pdf_subtitle),
                        icon = Icons.Default.UploadFile,
                        onClick = { pdfPickerLauncher.launch(arrayOf("application/pdf")) }
                    )
                }
            }
        }
    }

}

@Composable
private fun AppendSourceOption(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    TextButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
    ) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.size(16.dp))
            Column(horizontalAlignment = Alignment.Start) {
                Text(text = title, style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
