package info.meuse24.pdf_scanner.ui.home

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.mlkit.common.MlKitException
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import info.meuse24.pdf_scanner.R
import info.meuse24.pdf_scanner.data.local.ScanRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(
    scanTrigger:     Boolean = false,
    onScanTriggered: () -> Unit = {},
    viewModel:       HomeViewModel = hiltViewModel()
) {
    val scans   by viewModel.scans.collectAsState()
    val error   by viewModel.error.collectAsState()
    val success by viewModel.success.collectAsState()
    val context = LocalContext.current

    var pendingScanResult by remember { mutableStateOf<GmsDocumentScanningResult?>(null) }
    var showSaveDialog    by remember { mutableStateOf(false) }
    var filenameInput     by rememberSaveable { mutableStateOf("") }

    val scanLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            pendingScanResult = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
            filenameInput = "Scan_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}"
            showSaveDialog = true
        }
    }

    LaunchedEffect(scanTrigger) {
        if (scanTrigger) {
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
                .addOnFailureListener { e ->
                    val message = if (e is MlKitException && e.errorCode == MlKitException.UNSUPPORTED) {
                        context.getString(R.string.error_device_unsupported)
                    } else {
                        e.message ?: context.getString(R.string.error_scanner_unavailable)
                    }
                    viewModel.reportError(message)
                }
            onScanTriggered()
        }
    }

    LaunchedEffect(success) {
        val msg = success
        if (msg != null) {
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            viewModel.clearSuccess()
        }
    }

    if (scans.isEmpty()) {
        EmptyState()
    } else {
        ScanList(
            scans         = scans,
            onItemClick   = { record ->
                val file = File(record.filepath)
                if (file.exists()) {
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                    try {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(uri, "application/pdf")
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                        )
                    } catch (e: ActivityNotFoundException) {
                        viewModel.reportError(context.getString(R.string.error_no_pdf_viewer))
                    }
                }
            },
            onShareClick  = { record ->
                val file = File(record.filepath)
                if (file.exists()) {
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                    context.startActivity(
                        Intent.createChooser(
                            Intent(Intent.ACTION_SEND).apply {
                                type = "application/pdf"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            },
                            context.getString(R.string.share_pdf_title)
                        )
                    )
                }
            },
            onDeleteClick  = viewModel::deleteScan,
            onExportClick  = { viewModel.exportScan(it) }
        )
    }

    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false; pendingScanResult = null },
            title   = { Text(stringResource(R.string.dialog_save_title)) },
            text    = {
                OutlinedTextField(
                    value         = filenameInput,
                    onValueChange = { filenameInput = it },
                    label         = { Text(stringResource(R.string.dialog_filename_label)) },
                    singleLine    = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val pdf = pendingScanResult?.pdf
                    if (filenameInput.isNotBlank() && pdf != null) {
                        val thumbnailUri = pendingScanResult?.pages?.firstOrNull()?.imageUri
                        viewModel.saveScan(pdf.uri, pdf.pageCount, filenameInput.trim(), thumbnailUri)
                        showSaveDialog = false
                        pendingScanResult = null
                    }
                }) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false; pendingScanResult = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    if (error != null) {
        AlertDialog(
            onDismissRequest = viewModel::clearError,
            title   = { Text(stringResource(R.string.error_title)) },
            text    = { Text(error!!) },
            confirmButton = { TextButton(onClick = viewModel::clearError) { Text(stringResource(R.string.action_ok)) } }
        )
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier            = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Default.PictureAsPdf,
            contentDescription = null,
            modifier           = Modifier.size(80.dp),
            tint               = MaterialTheme.colorScheme.outline
        )
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.home_empty_title), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.outline)
        Spacer(Modifier.height(4.dp))
        Text(stringResource(R.string.home_empty_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
    }
}

@Composable
private fun ScanList(
    scans:         List<ScanRecord>,
    modifier:      Modifier = Modifier,
    onItemClick:   (ScanRecord) -> Unit,
    onShareClick:  (ScanRecord) -> Unit,
    onDeleteClick: (ScanRecord) -> Unit,
    onExportClick: (ScanRecord) -> Unit
) {
    LazyColumn(modifier = modifier, contentPadding = PaddingValues(8.dp)) {
        items(scans, key = { it.id }) { record ->
            ScanItem(
                record   = record,
                onClick  = { onItemClick(record) },
                onShare  = { onShareClick(record) },
                onDelete = { onDeleteClick(record) },
                onExport = { onExportClick(record) }
            )
        }
    }
}

@Composable
private fun ScanItem(
    record:   ScanRecord,
    onClick:  () -> Unit,
    onShare:  () -> Unit,
    onDelete: () -> Unit,
    onExport: () -> Unit
) {
    val dateStr = remember(record.timestamp) {
        SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(record.timestamp))
    }
    val sizeStr = remember(record.fileSize) {
        if (record.fileSize < 1024 * 1024) "${record.fileSize / 1024} KB"
        else "%.1f MB".format(record.fileSize / (1024.0 * 1024.0))
    }
    val subtitle = stringResource(R.string.scan_item_subtitle, dateStr, record.pageCount, sizeStr)

    val thumbnail by produceState<ImageBitmap?>(initialValue = null, key1 = record.thumbnailPath) {
        value = withContext(Dispatchers.IO) {
            val path = record.thumbnailPath ?: return@withContext null
            val file = File(path)
            if (!file.exists()) return@withContext null
            BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap()
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .clickable(onClick = onClick)
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            val thumb = thumbnail
            if (thumb != null) {
                Image(
                    painter           = BitmapPainter(thumb),
                    contentDescription = stringResource(R.string.cd_pdf_document),
                    contentScale      = ContentScale.Crop,
                    modifier          = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(4.dp))
                )
            } else {
                Icon(
                    Icons.Default.PictureAsPdf,
                    contentDescription = stringResource(R.string.cd_pdf_document),
                    tint               = MaterialTheme.colorScheme.primary,
                    modifier           = Modifier.size(40.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(record.filename, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            }
            IconButton(onClick = onShare) {
                Icon(Icons.Default.Share, contentDescription = stringResource(R.string.cd_share))
            }
            IconButton(onClick = onExport) {
                Icon(Icons.Default.Download, contentDescription = stringResource(R.string.action_export))
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.cd_delete), tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}
