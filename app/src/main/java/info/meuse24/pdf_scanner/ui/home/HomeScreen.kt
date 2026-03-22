package info.meuse24.pdf_scanner.ui.home

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TextSnippet
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.automirrored.filled.ManageSearch
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    scanTrigger:           Boolean    = false,
    onScanTriggered:       () -> Unit = {},
    onSelectionModeChange: (Boolean) -> Unit = {},
    viewModel:             HomeViewModel = hiltViewModel()
) {
    val scans      by viewModel.scans.collectAsState()
    val error      by viewModel.error.collectAsState()
    val success    by viewModel.success.collectAsState()
    val ocrText     by viewModel.ocrText.collectAsState()
    val ocrLoading  by viewModel.ocrLoading.collectAsState()
    val ocrProgress by viewModel.ocrProgress.collectAsState()
    val context    = LocalContext.current
    val clipboard  = LocalClipboardManager.current
    val haptic     = LocalHapticFeedback.current

    var pendingScanResult  by remember { mutableStateOf<GmsDocumentScanningResult?>(null) }
    var showSaveDialog     by remember { mutableStateOf(false) }
    var filenameInput      by rememberSaveable { mutableStateOf("") }
    var makeSearchable     by rememberSaveable { mutableStateOf(false) }
    val unsupportedLangs = setOf("zh", "ja")
    var selectedLang       by rememberSaveable { mutableStateOf(
        Locale.getDefault().language.let { if (it in unsupportedLangs) "en" else it }
    ) }
    var langMenuExpanded   by remember { mutableStateOf(false) }

    // ── Selection state ────────────────────────────────────────────────────────
    var selectedIds           by remember { mutableStateOf(emptySet<Long>()) }
    val isSelectionMode        = selectedIds.isNotEmpty()
    var pendingDeleteRecord   by remember { mutableStateOf<ScanRecord?>(null) }
    var showBulkDeleteConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(isSelectionMode) { onSelectionModeChange(isSelectionMode) }

    BackHandler(enabled = isSelectionMode) { selectedIds = emptySet() }

    // ── Scanner launcher ──────────────────────────────────────────────────────
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

    // ── Scroll haptic tick ────────────────────────────────────────────────────
    val listState = rememberLazyListState()
    LaunchedEffect(listState) {
        var initialized = false
        snapshotFlow { listState.firstVisibleItemIndex }.collect {
            if (initialized) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            initialized = true
        }
    }

    // ── Main content ──────────────────────────────────────────────────────────
    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Selection title bar (top)
            if (isSelectionMode) {
                SelectionTitleBar(
                    count            = selectedIds.size,
                    total            = scans.size,
                    onClearSelection = { selectedIds = emptySet() },
                    onSelectAll      = { selectedIds = scans.map { it.id }.toSet() }
                )
            }

            if (scans.isEmpty()) {
                EmptyStateContent(modifier = Modifier.weight(1f))
            } else {
                LazyColumn(
                    state   = listState,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(
                        top    = 8.dp,
                        bottom = if (isSelectionMode) 80.dp else 88.dp
                    )
                ) {
                    items(scans, key = { it.id }) { record ->
                        val isSelected = record.id in selectedIds
                        val toggleSelect = {
                            selectedIds = if (isSelected) selectedIds - record.id
                                          else            selectedIds + record.id
                        }
                        ScanItem(
                            record   = record,
                            isSelected = isSelected,
                            modifier = Modifier
                                .padding(horizontal = 16.dp, vertical = 4.dp)
                                .animateItem(),
                            onClick = {
                                if (isSelectionMode) toggleSelect()
                                else {
                                    val file = File(record.filepath)
                                    if (file.exists()) {
                                        val uri = FileProvider.getUriForFile(
                                            context, "${context.packageName}.fileprovider", file)
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
                                }
                            },
                            onCheckboxToggle = toggleSelect,
                        )
                    }
                }
            }
        }

        // Bulk action bar (bottom)
        if (isSelectionMode) {
            val selectedRecords = scans.filter { it.id in selectedIds }
            BulkActionBar(
                onShare = {
                    val uris = ArrayList(selectedRecords.mapNotNull { record ->
                        val file = File(record.filepath)
                        if (file.exists()) FileProvider.getUriForFile(
                            context, "${context.packageName}.fileprovider", file)
                        else null
                    })
                    if (uris.isNotEmpty()) {
                        val intent = if (uris.size == 1) {
                            Intent(Intent.ACTION_SEND).apply {
                                type = "application/pdf"
                                putExtra(Intent.EXTRA_STREAM, uris[0])
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                        } else {
                            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                                type = "application/pdf"
                                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                        }
                        context.startActivity(Intent.createChooser(intent, context.getString(R.string.share_pdf_title)))
                    }
                },
                onExport = {
                    selectedRecords.forEach { viewModel.exportScan(it) }
                    selectedIds = emptySet()
                },
                onExtractTexts     = { viewModel.extractTexts(selectedRecords) },
                extractEnabled     = selectedRecords.any { it.thumbnailPath != null },
                onMakeSearchable   = {
                    viewModel.makeSearchableScans(selectedRecords)
                    selectedIds = emptySet()
                },
                makeSearchableEnabled = selectedRecords.any { !it.isSearchable },
                onDelete = {
                    if (selectedRecords.size == 1) pendingDeleteRecord = selectedRecords.first()
                    else showBulkDeleteConfirm = true
                },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }

    // ── Delete single confirmation ────────────────────────────────────────────
    pendingDeleteRecord?.let { record ->
        AlertDialog(
            onDismissRequest = { pendingDeleteRecord = null },
            title   = { Text(stringResource(R.string.confirm_delete_title)) },
            text    = { Text(stringResource(R.string.confirm_delete_single, record.filename)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteScan(record)
                    selectedIds = selectedIds - record.id
                    pendingDeleteRecord = null
                }) { Text(stringResource(R.string.cd_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteRecord = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    // ── Delete bulk confirmation ──────────────────────────────────────────────
    if (showBulkDeleteConfirm) {
        val toDelete = scans.filter { it.id in selectedIds }
        AlertDialog(
            onDismissRequest = { showBulkDeleteConfirm = false },
            title   = { Text(stringResource(R.string.confirm_delete_title)) },
            text    = { Text(stringResource(R.string.confirm_delete_multi, selectedIds.size)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteScans(toDelete)
                    selectedIds = emptySet()
                    showBulkDeleteConfirm = false
                }) { Text(stringResource(R.string.cd_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showBulkDeleteConfirm = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    // ── OCR loading overlay ───────────────────────────────────────────────────
    if (ocrLoading) {
        AlertDialog(
            onDismissRequest = {},
            title = null,
            text  = {
                Column(
                    modifier            = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    ScannerLoadingAnimation()
                    val progress = ocrProgress
                    if (progress != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            stringResource(R.string.searchable_progress, progress.first, progress.second),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
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
                Column {
                    OutlinedTextField(
                        value         = filenameInput,
                        onValueChange = { filenameInput = it },
                        label         = { Text(stringResource(R.string.dialog_filename_label)) },
                        singleLine    = true,
                        modifier      = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier          = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            stringResource(R.string.dialog_searchable_pdf),
                            style    = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked         = makeSearchable,
                            onCheckedChange = { makeSearchable = it }
                        )
                    }
                    if (makeSearchable) {
                        Text(
                            stringResource(R.string.dialog_searchable_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                        Spacer(Modifier.height(10.dp))
                        val ocrLanguages = listOf(
                            "de" to "Deutsch", "en" to "English", "es" to "Español",
                            "fr" to "Français", "pt" to "Português", "ru" to "Русский",
                            "ar" to "العربية", "hi" to "हिन्दी"
                        )
                        ExposedDropdownMenuBox(
                            expanded        = langMenuExpanded,
                            onExpandedChange = { langMenuExpanded = it }
                        ) {
                            OutlinedTextField(
                                value         = ocrLanguages.find { it.first == selectedLang }?.second ?: selectedLang,
                                onValueChange = {},
                                readOnly      = true,
                                label         = { Text(stringResource(R.string.dialog_ocr_language)) },
                                trailingIcon  = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = langMenuExpanded) },
                                colors        = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                                modifier      = Modifier
                                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                    .fillMaxWidth()
                            )
                            ExposedDropdownMenu(
                                expanded        = langMenuExpanded,
                                onDismissRequest = { langMenuExpanded = false }
                            ) {
                                ocrLanguages.forEach { (code, name) ->
                                    DropdownMenuItem(
                                        text    = { Text(name) },
                                        onClick = { selectedLang = code; langMenuExpanded = false }
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val pdf = pendingScanResult?.pdf
                    if (filenameInput.isNotBlank() && pdf != null) {
                        val thumbnailUri = pendingScanResult?.pages?.firstOrNull()?.imageUri
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.saveScan(
                            pdf.uri, pdf.pageCount, filenameInput.trim(),
                            thumbnailUri, makeSearchable, selectedLang
                        )
                        showSaveDialog    = false
                        pendingScanResult = null
                        makeSearchable    = false
                        selectedLang      = Locale.getDefault().language.let { if (it in unsupportedLangs) "en" else it }
                    }
                }) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    showSaveDialog    = false
                    pendingScanResult = null
                    makeSearchable    = false
                    selectedLang      = Locale.getDefault().language.let { if (it in unsupportedLangs) "en" else it }
                }) {
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
// Selection title bar (top) + Bulk action bar (bottom)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SelectionTitleBar(
    count:            Int,
    total:            Int,
    onClearSelection: () -> Unit,
    onSelectAll:      () -> Unit
) {
    Surface(
        modifier        = Modifier.fillMaxWidth(),
        shadowElevation = 4.dp,
        color           = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Row(
            modifier                = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment       = Alignment.CenterVertically,
            horizontalArrangement   = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = onClearSelection) {
                Icon(Icons.Default.Close, contentDescription = null)
            }
            Text(
                stringResource(R.string.selection_count_fraction, count, total),
                style      = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            IconButton(onClick = onSelectAll) {
                Icon(Icons.Default.SelectAll,
                    contentDescription = stringResource(R.string.action_select_all))
            }
        }
    }
}

@Composable
private fun BulkActionBar(
    onShare:           () -> Unit,
    onExport:          () -> Unit,
    onExtractTexts:    () -> Unit,
    onMakeSearchable:      () -> Unit,
    onDelete:              () -> Unit,
    extractEnabled:        Boolean  = true,
    makeSearchableEnabled: Boolean  = true,
    modifier:              Modifier = Modifier
) {
    Surface(
        modifier        = modifier.fillMaxWidth(),
        shadowElevation = 8.dp,
        color           = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Row(
            modifier              = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            IconButton(onClick = onShare) {
                Icon(Icons.Default.Share,    contentDescription = stringResource(R.string.cd_share))
            }
            IconButton(onClick = onExport) {
                Icon(Icons.Default.Download, contentDescription = stringResource(R.string.action_export))
            }
            IconButton(onClick = onExtractTexts, enabled = extractEnabled) {
                Icon(Icons.AutoMirrored.Filled.TextSnippet,
                    contentDescription = stringResource(R.string.cd_extract_text))
            }
            IconButton(onClick = onMakeSearchable, enabled = makeSearchableEnabled) {
                Icon(Icons.AutoMirrored.Filled.ManageSearch,
                    contentDescription = stringResource(R.string.cd_make_searchable))
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.cd_delete),
                    tint = MaterialTheme.colorScheme.error)
            }
        }
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
        Text(stringResource(R.string.home_empty_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.outline)
        Spacer(Modifier.height(4.dp))
        Text(stringResource(R.string.home_empty_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outlineVariant)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Scan item card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ScanItem(
    record:           ScanRecord,
    isSelected:       Boolean,
    modifier:         Modifier = Modifier,
    onClick:          () -> Unit,
    onCheckboxToggle: () -> Unit,
) {
    val dateStr = remember(record.timestamp) {
        SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(record.timestamp))
    }
    val sizeStr = remember(record.fileSize) {
        if (record.fileSize < 1024 * 1024) "${record.fileSize / 1024} KB"
        else "%.1f MB".format(record.fileSize / (1024.0 * 1024.0))
    }
    val subtitle = stringResource(R.string.scan_item_subtitle, dateStr, record.pageCount, sizeStr)

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val cardScale by animateFloatAsState(
        targetValue   = if (isPressed) 0.97f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label         = "cardScale"
    )

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
            .scale(cardScale)
            .clickable(
                interactionSource = interactionSource,
                indication        = LocalIndication.current,
                onClick           = onClick
            ),
        shape   = RoundedCornerShape(28.dp),
        colors  = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                             else            MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier          = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Thumbnail
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
                if (record.isSearchable) {
                    Spacer(Modifier.height(2.dp))
                    Badge(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor   = MaterialTheme.colorScheme.onSecondaryContainer
                    ) {
                        Text(
                            stringResource(R.string.searchable_badge),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }

            // Checkbox always on the right
            Checkbox(
                checked         = isSelected,
                onCheckedChange = { onCheckboxToggle() }
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Scanner loading animation
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ScannerLoadingAnimation() {
    val infiniteTransition = rememberInfiniteTransition(label = "scanner")
    val scanProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue  = 1f,
        animationSpec = infiniteRepeatable(
            animation  = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scanProgress"
    )
    val primary       = MaterialTheme.colorScheme.primary
    val surfaceVar    = MaterialTheme.colorScheme.surfaceVariant
    val outlineColor  = MaterialTheme.colorScheme.outline

    Canvas(modifier = Modifier.size(72.dp)) {
        val pad   = 8.dp.toPx()
        val left  = pad
        val top   = pad
        val right = size.width - pad
        val bot   = size.height - pad
        val docW  = right - left
        val docH  = bot - top

        // Document background
        drawRoundRect(
            color       = surfaceVar,
            topLeft     = Offset(left, top),
            size        = Size(docW, docH),
            cornerRadius = CornerRadius(4.dp.toPx())
        )

        // Three simulated text lines
        val lx0   = left  + 8.dp.toPx()
        val lx1   = right - 8.dp.toPx()
        val lxSh  = right - 20.dp.toPx()
        val lStroke = 2.dp.toPx()
        drawLine(outlineColor.copy(0.35f), Offset(lx0, top + docH * 0.28f), Offset(lx1,  top + docH * 0.28f), lStroke, StrokeCap.Round)
        drawLine(outlineColor.copy(0.35f), Offset(lx0, top + docH * 0.50f), Offset(lx1,  top + docH * 0.50f), lStroke, StrokeCap.Round)
        drawLine(outlineColor.copy(0.35f), Offset(lx0, top + docH * 0.72f), Offset(lxSh, top + docH * 0.72f), lStroke, StrokeCap.Round)

        // Scanning beam
        val scanY     = top + 4.dp.toPx() + (docH - 8.dp.toPx()) * scanProgress
        val glowBrush = Brush.horizontalGradient(
            colors = listOf(Color.Transparent, primary.copy(0.9f), primary, primary.copy(0.9f), Color.Transparent),
            startX = left, endX = right
        )
        drawLine(glowBrush, Offset(left + 2.dp.toPx(), scanY - 3.dp.toPx()), Offset(right - 2.dp.toPx(), scanY - 3.dp.toPx()), 5.dp.toPx())
        drawLine(glowBrush, Offset(left + 2.dp.toPx(), scanY),                Offset(right - 2.dp.toPx(), scanY),                2.dp.toPx())
    }
}
