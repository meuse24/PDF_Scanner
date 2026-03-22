package info.meuse24.pdf_scanner.ui.home

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TextSnippet
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    scanTrigger:     Boolean = false,
    onScanTriggered: () -> Unit = {},
    viewModel:       HomeViewModel = hiltViewModel()
) {
    val scans      by viewModel.scans.collectAsState()
    val error      by viewModel.error.collectAsState()
    val success    by viewModel.success.collectAsState()
    val ocrText    by viewModel.ocrText.collectAsState()
    val ocrLoading by viewModel.ocrLoading.collectAsState()
    val context    = LocalContext.current
    val clipboard  = LocalClipboardManager.current
    val haptic     = LocalHapticFeedback.current

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
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            viewModel.clearSuccess()
        }
    }

    // ── Main content ──────────────────────────────────────────────────────────
    if (scans.isEmpty()) {
        EmptyStateContent()
    } else {
        LazyColumn(contentPadding = PaddingValues(top = 8.dp, bottom = 88.dp)) {
            items(scans, key = { it.id }) { record ->
                ScanItem(
                    record        = record,
                    modifier      = Modifier
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .animateItem(),
                    onClick       = {
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
                    onShare       = {
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
                    onDelete      = { viewModel.deleteScan(record) },
                    onExport      = { viewModel.exportScan(record) },
                    onExtractText = { viewModel.extractText(record) }
                )
            }
        }
    }

    // ── OCR loading overlay ───────────────────────────────────────────────────
    if (ocrLoading) {
        AlertDialog(
            onDismissRequest = {},
            title = null,
            text  = {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            },
            confirmButton = {}
        )
    }

    // ── OCR result bottom sheet ───────────────────────────────────────────────
    val ocrSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    if (ocrText != null) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.clearOcrText() },
            sheetState       = ocrSheetState
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 32.dp)
            ) {
                Text(stringResource(R.string.ocr_result_title), style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(4.dp))
                HorizontalDivider()
                Spacer(Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    SelectionContainer {
                        Text(ocrText!!, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Spacer(Modifier.height(16.dp))
                Row(
                    horizontalArrangement = Arrangement.End,
                    modifier              = Modifier.fillMaxWidth()
                ) {
                    TextButton(onClick = {
                        context.startActivity(
                            Intent.createChooser(
                                Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, ocrText)
                                },
                                context.getString(R.string.action_share_text)
                            )
                        )
                    }) {
                        Icon(Icons.Default.Share, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.action_share_text))
                    }
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = {
                        clipboard.setText(AnnotatedString(ocrText!!))
                        Toast.makeText(context, context.getString(R.string.ocr_copied), Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.action_copy))
                    }
                }
            }
        }
    }

    // ── Save dialog ───────────────────────────────────────────────────────────
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
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
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

    // ── Error dialog ──────────────────────────────────────────────────────────
    if (error != null) {
        AlertDialog(
            onDismissRequest = viewModel::clearError,
            title            = { Text(stringResource(R.string.error_title)) },
            text             = { Text(error!!) },
            confirmButton    = { TextButton(onClick = viewModel::clearError) { Text(stringResource(R.string.action_ok)) } }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Empty state
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun EmptyStateContent(modifier: Modifier = Modifier) {
    Column(
        modifier            = modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Default.PictureAsPdf,
            contentDescription = null,
            modifier           = Modifier.size(64.dp),
            tint               = MaterialTheme.colorScheme.outlineVariant
        )
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.home_empty_title), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.outline)
        Spacer(Modifier.height(4.dp))
        Text(stringResource(R.string.home_empty_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outlineVariant)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Scan item card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ScanItem(
    record:        ScanRecord,
    modifier:      Modifier = Modifier,
    onClick:       () -> Unit,
    onShare:       () -> Unit,
    onDelete:      () -> Unit,
    onExport:      () -> Unit,
    onExtractText: () -> Unit
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
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, opts)
            var sample = 1
            while (opts.outWidth / (sample * 2) >= 160 && opts.outHeight / (sample * 2) >= 160) {
                sample *= 2
            }
            BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
                ?.asImageBitmap()
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape  = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier          = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val thumb = thumbnail
            if (thumb != null) {
                Image(
                    painter            = BitmapPainter(thumb),
                    contentDescription = stringResource(R.string.cd_pdf_document),
                    contentScale       = ContentScale.Crop,
                    modifier           = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(10.dp))
                )
            } else {
                Icon(
                    Icons.Default.PictureAsPdf,
                    contentDescription = stringResource(R.string.cd_pdf_document),
                    tint               = MaterialTheme.colorScheme.primary,
                    modifier           = Modifier.size(48.dp)
                )
            }
            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    record.filename,
                    style      = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines   = 1,
                    overflow   = TextOverflow.Ellipsis
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.Start) {
                    SmallIconButton(onClick = onShare) {
                        Icon(Icons.Default.Share,    contentDescription = stringResource(R.string.cd_share),        modifier = Modifier.size(18.dp))
                    }
                    SmallIconButton(onClick = onExport) {
                        Icon(Icons.Default.Download, contentDescription = stringResource(R.string.action_export),   modifier = Modifier.size(18.dp))
                    }
                    SmallIconButton(onClick = onExtractText, enabled = record.thumbnailPath != null) {
                        Icon(Icons.AutoMirrored.Filled.TextSnippet, contentDescription = stringResource(R.string.cd_extract_text), modifier = Modifier.size(18.dp))
                    }
                    SmallIconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete,   contentDescription = stringResource(R.string.cd_delete),       modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@Composable
private fun SmallIconButton(
    onClick: () -> Unit,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    IconButton(
        onClick  = onClick,
        enabled  = enabled,
        modifier = Modifier.size(32.dp)
    ) { content() }
}
