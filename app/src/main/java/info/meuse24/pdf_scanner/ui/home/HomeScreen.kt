package info.meuse24.pdf_scanner.ui.home

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.mlkit.common.MlKitException
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import info.meuse24.pdf_scanner.R
import info.meuse24.pdf_scanner.data.local.ScanRecord
import info.meuse24.pdf_scanner.ui.home.components.BulkActionBar
import info.meuse24.pdf_scanner.ui.home.components.EmptyStateContent
import info.meuse24.pdf_scanner.ui.home.components.MergeDialog
import info.meuse24.pdf_scanner.ui.home.components.ScanAction
import info.meuse24.pdf_scanner.ui.home.components.ScanItem
import info.meuse24.pdf_scanner.ui.home.components.ScannerLoadingAnimation
import info.meuse24.pdf_scanner.ui.home.components.SelectionTitleBar
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
    onNavigateToSplit:          (Long) -> Unit = {},
    onNavigateToReorder:        (Long) -> Unit = {},
    onNavigateToRotate:         (Long) -> Unit = {},
    onNavigateToDeletePages:    (Long) -> Unit = {},
    onNavigateToExtractPages:   (Long) -> Unit = {},
    onNavigateToDuplicatePages: (Long) -> Unit = {},
    onNavigateToPageNumbers:    (Long) -> Unit = {},
    onNavigateToTextWatermark:  (Long) -> Unit = {},
    onNavigateToCompressPdf:    (Long) -> Unit = {},
    onNavigateToProtectPdf:     (Long) -> Unit = {},
    onNavigateToUnlockPdf:      (Long) -> Unit = {},
    onNavigateToSignature:          (Long) -> Unit = {},
    onNavigateToRemoveTextLayer:    (Long) -> Unit = {},
    onNavigateToRemovePassword:     (Long) -> Unit = {},
    onNavigateToRestrictUsage:      (Long) -> Unit = {},
    onNavigateToAnnotate:           (Long) -> Unit = {},
    viewModel:                      HomeViewModel  = hiltViewModel()
) {
    val scans          by viewModel.scans.collectAsState()
    val filteredScans  by viewModel.filteredScans.collectAsState()
    val searchQuery    by viewModel.searchQuery.collectAsState()
    val error          by viewModel.error.collectAsState()
    val success      by viewModel.success.collectAsState()
    val ocrText      by viewModel.ocrText.collectAsState()
    val ocrLoading   by viewModel.ocrLoading.collectAsState()
    val ocrProgress  by viewModel.ocrProgress.collectAsState()
    val editLoading  by viewModel.editLoading.collectAsState()
    val context   = LocalContext.current
    val resources = LocalResources.current
    val clipboard = LocalClipboardManager.current
    val haptic    = LocalHapticFeedback.current
    val errorDeviceUnsupported = stringResource(R.string.error_device_unsupported)
    val errorScannerUnavailable = stringResource(R.string.error_scanner_unavailable)
    val errorNoPdfViewer = stringResource(R.string.error_no_pdf_viewer)
    val sharePdfTitle = stringResource(R.string.share_pdf_title)
    val actionShareTextLabel = stringResource(R.string.action_share_text)
    val ocrCopiedMessage = stringResource(R.string.ocr_copied)
    val displayLocale = resources.configuration.locales[0] ?: Locale.getDefault()
    val ocrLanguages = remember(displayLocale) { buildOcrLanguageOptions(displayLocale) }

    var pendingScanResult  by remember { mutableStateOf<GmsDocumentScanningResult?>(null) }
    var showSaveDialog     by remember { mutableStateOf(false) }
    var filenameInput      by rememberSaveable { mutableStateOf("") }
    var makeSearchable     by rememberSaveable { mutableStateOf(false) }
    val unsupportedLangs = setOf("zh", "ja")
    var selectedLang       by rememberSaveable { mutableStateOf(
        Locale.getDefault().language.let { if (it in unsupportedLangs) "en" else it }
    ) }
    var langMenuExpanded   by remember { mutableStateOf(false) }

    // ── Auswahlmodus ──────────────────────────────────────────────────────────
    var selectedIds           by remember { mutableStateOf(emptySet<Long>()) }
    val isSelectionMode        = selectedIds.isNotEmpty()
    var pendingDeleteRecord   by remember { mutableStateOf<ScanRecord?>(null) }
    var showBulkDeleteConfirm by remember { mutableStateOf(false) }

    // ── Merge-Dialog-State ────────────────────────────────────────────────────
    var showMergeDialog    by remember { mutableStateOf(false) }
    var mergeFilenameInput by rememberSaveable { mutableStateOf("") }

    // ── Rename-Dialog-State ───────────────────────────────────────────────────
    var recordToRename   by remember { mutableStateOf<ScanRecord?>(null) }
    var renameInput      by rememberSaveable { mutableStateOf("") }

    // ── Bulk-OCR-Sprachdialog ─────────────────────────────────────────────────
    var showBulkLangDialog    by remember { mutableStateOf(false) }
    var bulkLangForSearchable by remember { mutableStateOf(false) }
    var selectedBulkLang      by rememberSaveable { mutableStateOf(
        Locale.getDefault().language.let { if (it in unsupportedLangs) "en" else it }
    ) }
    var bulkLangMenuExpanded  by remember { mutableStateOf(false) }

    LaunchedEffect(isSelectionMode) { onSelectionModeChange(isSelectionMode) }

    BackHandler(enabled = isSelectionMode) { selectedIds = emptySet() }

    // ── Scanner-Launcher ──────────────────────────────────────────────────────
    val scanLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            pendingScanResult = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
            filenameInput = resources.getString(
                R.string.scan_filename_default,
                SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            )
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
                        errorDeviceUnsupported
                    } else {
                        e.message ?: errorScannerUnavailable
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

    // ── Scroll-Haptic-Tick ────────────────────────────────────────────────────
    val listState = rememberLazyListState()
    LaunchedEffect(listState) {
        var initialized = false
        snapshotFlow { listState.firstVisibleItemIndex }.collect {
            if (initialized) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            initialized = true
        }
    }

    // ── Hauptinhalt ───────────────────────────────────────────────────────────
    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (isSelectionMode) {
                SelectionTitleBar(
                    count            = selectedIds.size,
                    total            = filteredScans.size,
                    onClearSelection = { selectedIds = emptySet() },
                    onSelectAll      = { selectedIds = filteredScans.map { it.id }.toSet() }
                )
            } else {
                // ── Suchfeld ─────────────────────────────────────────────────
                OutlinedTextField(
                    value         = searchQuery,
                    onValueChange = viewModel::updateSearchQuery,
                    placeholder   = { Text(stringResource(R.string.search_placeholder)) },
                    leadingIcon   = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon  = if (searchQuery.isNotEmpty()) {
                        {
                            IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = stringResource(R.string.cd_search_clear)
                                )
                            }
                        }
                    } else null,
                    singleLine    = true,
                    modifier      = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            if (scans.isEmpty()) {
                EmptyStateContent(modifier = Modifier.weight(1f))
            } else if (filteredScans.isEmpty() && searchQuery.isNotEmpty()) {
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        stringResource(R.string.search_no_results, searchQuery),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            } else {
                LazyColumn(
                    state          = listState,
                    modifier       = Modifier.weight(1f),
                    contentPadding = PaddingValues(
                        top    = 8.dp,
                        bottom = if (isSelectionMode) 80.dp else 88.dp
                    )
                ) {
                    itemsIndexed(filteredScans, key = { _, it -> it.id }) { index, record ->
                        val isSelected = record.id in selectedIds
                        val toggleSelect = {
                            selectedIds = if (isSelected) selectedIds - record.id
                                          else            selectedIds + record.id
                        }
                        ScanItem(
                            record          = record,
                            isSelected      = isSelected,
                            inSelectionMode = isSelectionMode,
                            modifier        = Modifier
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
                                            viewModel.reportError(errorNoPdfViewer)
                                        }
                                    }
                                }
                            },
                            onCheckboxToggle = toggleSelect,
                            onAction = { action ->
                                when (action) {
                                    ScanAction.Split          -> onNavigateToSplit(record.id)
                                    ScanAction.Reorder        -> onNavigateToReorder(record.id)
                                    ScanAction.Rotate         -> onNavigateToRotate(record.id)
                                    ScanAction.DeletePages    -> onNavigateToDeletePages(record.id)
                                    ScanAction.ExtractPages   -> onNavigateToExtractPages(record.id)
                                    ScanAction.DuplicatePages -> onNavigateToDuplicatePages(record.id)
                                    ScanAction.PageNumbers    -> onNavigateToPageNumbers(record.id)
                                    ScanAction.TextWatermark  -> onNavigateToTextWatermark(record.id)
                                    ScanAction.CompressPdf    -> onNavigateToCompressPdf(record.id)
                                    ScanAction.ProtectPdf     -> onNavigateToProtectPdf(record.id)
                                    ScanAction.UnlockPdf      -> onNavigateToUnlockPdf(record.id)
                                    ScanAction.Signature      -> onNavigateToSignature(record.id)
                                    ScanAction.RemoveTextLayer -> onNavigateToRemoveTextLayer(record.id)
                                    ScanAction.RemovePassword  -> onNavigateToRemovePassword(record.id)
                                    ScanAction.RestrictUsage   -> onNavigateToRestrictUsage(record.id)
                                    ScanAction.ExportAsJpg     -> viewModel.exportAsJpg(record)
                                    ScanAction.Annotate        -> onNavigateToAnnotate(record.id)
                                    ScanAction.Rename          -> {
                                        renameInput    = record.filename
                                        recordToRename = record
                                    }
                                }
                            }
                        )
                        if (index < filteredScans.lastIndex) {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 28.dp),
                                color    = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                            )
                        }
                    }
                }
            }
        }

        // Bulk-Aktionsleiste (unten)
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
                        context.startActivity(Intent.createChooser(intent, sharePdfTitle))
                    }
                },
                onExport = {
                    selectedRecords.forEach { viewModel.exportScan(it) }
                    selectedIds = emptySet()
                },
                onExtractTexts        = { bulkLangForSearchable = false; showBulkLangDialog = true },
                extractEnabled        = selectedRecords.isNotEmpty(),
                onMakeSearchable      = {
                    if (selectedRecords.none { !it.isSearchable || it.extractedText == null }) {
                        viewModel.reportError(resources.getString(R.string.searchable_nothing_to_do))
                    } else {
                        bulkLangForSearchable = true; showBulkLangDialog = true
                    }
                },
                makeSearchableEnabled = true,
                onMerge = {
                    val fmt = SimpleDateFormat("ddMMyyyy", Locale.getDefault())
                    mergeFilenameInput = resources.getString(
                        R.string.merge_filename_default,
                        fmt.format(Date())
                    )
                    showMergeDialog = true
                },
                mergeEnabled = selectedRecords.size >= 2,
                onDelete = {
                    if (selectedRecords.size == 1) pendingDeleteRecord = selectedRecords.first()
                    else showBulkDeleteConfirm = true
                },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }

    // ── Einzellösch-Bestätigung ───────────────────────────────────────────────
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

    // ── Bulk-Lösch-Bestätigung ────────────────────────────────────────────────
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

    // ── Merge-Dialog ──────────────────────────────────────────────────────────
    if (showMergeDialog) {
        val selectedRecordsForMerge = scans.filter { it.id in selectedIds }
        MergeDialog(
            filename         = mergeFilenameInput,
            onFilenameChange = { mergeFilenameInput = it },
            records          = selectedRecordsForMerge,
            onConfirm        = {
                if (mergeFilenameInput.isNotBlank()) {
                    viewModel.mergePdfs(selectedRecordsForMerge, mergeFilenameInput.trim())
                    selectedIds = emptySet()
                    showMergeDialog = false
                }
            },
            onDismiss = { showMergeDialog = false }
        )
    }

    // ── Rename-Dialog ─────────────────────────────────────────────────────────
    recordToRename?.let { record ->
        AlertDialog(
            onDismissRequest = { recordToRename = null },
            title   = { Text(stringResource(R.string.rename_dialog_title)) },
            text    = {
                OutlinedTextField(
                    value         = renameInput,
                    onValueChange = { renameInput = it },
                    label         = { Text(stringResource(R.string.rename_hint)) },
                    singleLine    = true,
                    modifier      = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick  = {
                        viewModel.renameScan(record, renameInput)
                        recordToRename = null
                    },
                    enabled  = renameInput.isNotBlank()
                ) { Text(stringResource(R.string.rename_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { recordToRename = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    // ── Bulk-OCR-Sprachauswahl ────────────────────────────────────────────────
    if (showBulkLangDialog) {
        val bulkRecords  = scans.filter { it.id in selectedIds }
        AlertDialog(
            onDismissRequest = { showBulkLangDialog = false },
            title   = { Text(stringResource(R.string.dialog_ocr_language)) },
            text    = {
                ExposedDropdownMenuBox(
                    expanded         = bulkLangMenuExpanded,
                    onExpandedChange = { bulkLangMenuExpanded = it }
                ) {
                    OutlinedTextField(
                        value         = ocrLanguages.find { it.first == selectedBulkLang }?.second ?: selectedBulkLang,
                        onValueChange = {},
                        readOnly      = true,
                        label         = { Text(stringResource(R.string.dialog_ocr_language)) },
                        trailingIcon  = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = bulkLangMenuExpanded) },
                        colors        = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                        modifier      = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded         = bulkLangMenuExpanded,
                        onDismissRequest = { bulkLangMenuExpanded = false }
                    ) {
                        ocrLanguages.forEach { (code, name) ->
                            DropdownMenuItem(
                                text    = { Text(name) },
                                onClick = { selectedBulkLang = code; bulkLangMenuExpanded = false }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showBulkLangDialog = false
                    if (bulkLangForSearchable) {
                        viewModel.makeSearchableScans(bulkRecords, selectedBulkLang)
                        selectedIds = emptySet()
                    } else {
                        viewModel.extractTexts(bulkRecords, selectedBulkLang)
                    }
                }) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showBulkLangDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    // ── OCR-Lade-Overlay ──────────────────────────────────────────────────────
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

    // ── Edit-Lade-Overlay ─────────────────────────────────────────────────────
    if (editLoading) {
        AlertDialog(
            onDismissRequest = {},
            title = null,
            text  = {
                Column(
                    modifier            = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    ScannerLoadingAnimation()
                }
            },
            confirmButton = {}
        )
    }

    // ── OCR-Ergebnis-BottomSheet ──────────────────────────────────────────────
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
                                actionShareTextLabel
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
                        Toast.makeText(context, ocrCopiedMessage, Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.action_copy))
                    }
                }
            }
        }
    }

    // ── Speichern-Dialog ──────────────────────────────────────────────────────
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
                        ExposedDropdownMenuBox(
                            expanded         = langMenuExpanded,
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
                                expanded         = langMenuExpanded,
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

    // ── Fehler-Dialog ─────────────────────────────────────────────────────────
    if (error != null) {
        AlertDialog(
            onDismissRequest = viewModel::clearError,
            title            = { Text(stringResource(R.string.error_title)) },
            text             = { Text(error!!) },
            confirmButton    = {
                TextButton(onClick = viewModel::clearError) { Text(stringResource(R.string.action_ok)) }
            }
        )
    }
}

private fun buildOcrLanguageOptions(displayLocale: Locale): List<Pair<String, String>> {
    val supportedCodes = listOf("de", "en", "es", "fr", "pt", "ru", "ar", "hi")
    return supportedCodes.map { code ->
        val name = Locale.forLanguageTag(code).getDisplayLanguage(displayLocale)
            .replaceFirstChar { char ->
                if (char.isLowerCase()) char.titlecase(displayLocale) else char.toString()
            }
        code to name
    }
}
