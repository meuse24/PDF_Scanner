package info.meuse24.pdf_scanner.ui.home

import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
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
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.toClipEntry
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import android.print.PrintManager
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import info.meuse24.pdf_scanner.util.PdfPrintAdapter
import info.meuse24.pdf_scanner.util.buildPdfShareIntent
import com.google.mlkit.common.MlKitException
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import info.meuse24.pdf_scanner.R
import info.meuse24.pdf_scanner.data.local.ScanRecord
import info.meuse24.pdf_scanner.ui.home.components.BulkActionBar
import info.meuse24.pdf_scanner.ui.home.components.EmptyStateContent
import info.meuse24.pdf_scanner.ui.home.components.HomeAddDocumentSheet
import info.meuse24.pdf_scanner.ui.home.components.HomeArchiveContent
import info.meuse24.pdf_scanner.ui.home.components.HomeBulkDeleteDialog
import info.meuse24.pdf_scanner.ui.home.components.HomeBulkLanguageDialog
import info.meuse24.pdf_scanner.ui.home.components.HomeDeleteDialog
import info.meuse24.pdf_scanner.ui.home.components.HomeErrorDialog
import info.meuse24.pdf_scanner.ui.home.components.HomeLoadingDialog
import info.meuse24.pdf_scanner.ui.home.components.HomeOcrResultSheet
import info.meuse24.pdf_scanner.ui.home.components.HomeRenameDialog
import info.meuse24.pdf_scanner.ui.home.components.HomeSaveImportDialog
import info.meuse24.pdf_scanner.ui.home.components.MergeDialog
import info.meuse24.pdf_scanner.ui.ocr.buildOcrLanguageOptions
import info.meuse24.pdf_scanner.ui.home.components.ScanAction
import info.meuse24.pdf_scanner.ui.home.components.ScanItem
import info.meuse24.pdf_scanner.ui.home.components.ScannerLoadingAnimation
import info.meuse24.pdf_scanner.ui.home.components.SelectionTitleBar
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    addActionTrigger:      Boolean    = false,
    onAddActionTriggered:  () -> Unit = {},
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
    onNavigateToRedact:             (Long) -> Unit = {},
    onNavigateToGrayscale:          (Long) -> Unit = {},
    onNavigateToPdfMetadata:        (Long) -> Unit = {},
    onNavigateToQrScan:             (Long) -> Unit = {},
    onNavigateToViewer:             (Long) -> Unit = {},
    onNavigateToImagesToPdf:        () -> Unit     = {},
    viewModel:                      HomeViewModel  = hiltViewModel()
) {
    val scans          by viewModel.scans.collectAsStateWithLifecycle()
    val settings       by viewModel.settings.collectAsStateWithLifecycle()
    val filteredScans  by viewModel.filteredScans.collectAsStateWithLifecycle()
    val searchQuery    by viewModel.searchQuery.collectAsStateWithLifecycle()
    val sortOrder      by viewModel.sortOrder.collectAsStateWithLifecycle()
    val error          by viewModel.error.collectAsStateWithLifecycle()
    val success        by viewModel.success.collectAsStateWithLifecycle()
    val ocrText        by viewModel.ocrText.collectAsStateWithLifecycle()
    val ocrLoading     by viewModel.ocrLoading.collectAsStateWithLifecycle()
    val ocrProgress    by viewModel.ocrProgress.collectAsStateWithLifecycle()
    val ocrStatusText  by viewModel.ocrStatusText.collectAsStateWithLifecycle()
    val editLoading    by viewModel.editLoading.collectAsStateWithLifecycle()
    val context   = LocalContext.current
    val resources = LocalResources.current
    val clipboard = LocalClipboard.current
    val coroutineScope = rememberCoroutineScope()
    val haptic    = LocalHapticFeedback.current
    val errorDeviceUnsupported = stringResource(R.string.error_device_unsupported)
    val errorScannerUnavailable = stringResource(R.string.error_scanner_unavailable)
    val sharePdfTitle = stringResource(R.string.share_pdf_title)
    val actionShareTextLabel = stringResource(R.string.action_share_text)
    val ocrCopiedMessage = stringResource(R.string.ocr_copied)
    val displayLocale = resources.configuration.locales[0] ?: Locale.getDefault()
    val ocrAutoLabel = stringResource(R.string.ocr_language_auto)
    val ocrLanguages = remember(displayLocale, ocrAutoLabel) { buildOcrLanguageOptions(ocrAutoLabel, displayLocale) }

    var pendingImport      by remember { mutableStateOf<PendingImport?>(null) }
    var showAddSheet       by remember { mutableStateOf(false) }
    var showSaveDialog     by remember { mutableStateOf(false) }
    var filenameInput      by rememberSaveable { mutableStateOf("") }
    var makeSearchable     by rememberSaveable { mutableStateOf(settings.defaultMakeSearchable) }
    var selectedLang       by rememberSaveable { mutableStateOf(settings.defaultOcrLanguage) }
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
    var selectedBulkLang      by rememberSaveable { mutableStateOf(settings.defaultOcrLanguage) }
    var bulkLangMenuExpanded  by remember { mutableStateOf(false) }

    LaunchedEffect(isSelectionMode) { onSelectionModeChange(isSelectionMode) }

    BackHandler(enabled = isSelectionMode) { selectedIds = emptySet() }

    // ── Scanner-Launcher ──────────────────────────────────────────────────────
    val scanLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val scanResult = GmsDocumentScanningResult.fromActivityResultIntent(result.data) ?: return@rememberLauncherForActivityResult
            pendingImport = PendingImport.Scan(scanResult)
            filenameInput = resources.getString(
                R.string.scan_filename_default,
                SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            )
            makeSearchable = settings.defaultMakeSearchable
            selectedLang = settings.defaultOcrLanguage
            showSaveDialog = true
        }
    }

    val importFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: SecurityException) {
                // Some providers only grant a transient permission for the current session.
            }

            val displayName = queryDisplayName(context, uri)
                ?: uri.lastPathSegment
                ?: resources.getString(
                    R.string.import_filename_default,
                    SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                )
            pendingImport = PendingImport.File(uri, displayName)
            filenameInput = suggestedFilenameFromDisplayName(displayName).ifBlank {
                resources.getString(
                    R.string.import_filename_default,
                    SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                )
            }
            makeSearchable = settings.defaultMakeSearchable
            selectedLang = settings.defaultOcrLanguage
            showSaveDialog = true
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
            .addOnFailureListener { e ->
                val message = if (e is MlKitException && e.errorCode == MlKitException.UNSUPPORTED) {
                    errorDeviceUnsupported
                } else {
                    e.message ?: errorScannerUnavailable
                }
                viewModel.reportError(message)
            }
    }

    LaunchedEffect(addActionTrigger) {
        if (addActionTrigger) {
            showAddSheet = true
            onAddActionTriggered()
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
            HomeArchiveContent(
                scans = scans,
                filteredScans = filteredScans,
                searchQuery = searchQuery,
                sortOrder = sortOrder,
                selectedIds = selectedIds,
                isSelectionMode = isSelectionMode,
                listState = listState,
                onSearchQueryChange = viewModel::updateSearchQuery,
                onSortOrderSelected = viewModel::setSortOrder,
                onClearSelection = { selectedIds = emptySet() },
                onSelectAll = { selectedIds = filteredScans.map { it.id }.toSet() },
                onSelectionToggle = { record ->
                    selectedIds = if (record.id in selectedIds) selectedIds - record.id else selectedIds + record.id
                },
                onOpenRecord = { record -> onNavigateToViewer(record.id) },
                onAction = { record, action ->
                    when (action) {
                        ScanAction.Split -> onNavigateToSplit(record.id)
                        ScanAction.Reorder -> onNavigateToReorder(record.id)
                        ScanAction.Rotate -> onNavigateToRotate(record.id)
                        ScanAction.DeletePages -> onNavigateToDeletePages(record.id)
                        ScanAction.ExtractPages -> onNavigateToExtractPages(record.id)
                        ScanAction.DuplicatePages -> onNavigateToDuplicatePages(record.id)
                        ScanAction.PageNumbers -> onNavigateToPageNumbers(record.id)
                        ScanAction.TextWatermark -> onNavigateToTextWatermark(record.id)
                        ScanAction.CompressPdf -> onNavigateToCompressPdf(record.id)
                        ScanAction.ProtectPdf -> onNavigateToProtectPdf(record.id)
                        ScanAction.UnlockPdf -> onNavigateToUnlockPdf(record.id)
                        ScanAction.Signature -> onNavigateToSignature(record.id)
                        ScanAction.RemoveTextLayer -> onNavigateToRemoveTextLayer(record.id)
                        ScanAction.RemovePassword -> onNavigateToRemovePassword(record.id)
                        ScanAction.RestrictUsage -> onNavigateToRestrictUsage(record.id)
                        ScanAction.ExportAsJpg -> viewModel.exportAsJpg(record)
                        ScanAction.Annotate -> onNavigateToAnnotate(record.id)
                        ScanAction.Redact -> onNavigateToRedact(record.id)
                        ScanAction.Grayscale -> onNavigateToGrayscale(record.id)
                        ScanAction.PdfMetadata -> onNavigateToPdfMetadata(record.id)
                        ScanAction.ScanQrCodes -> onNavigateToQrScan(record.id)
                        ScanAction.Print -> {
                            val printManager = context.getSystemService(PrintManager::class.java)
                            printManager?.print(
                                record.filename,
                                PdfPrintAdapter(
                                    file = java.io.File(record.filepath),
                                    jobName = record.filename,
                                    pageCount = record.pageCount
                                ),
                                null
                            )
                        }
                        ScanAction.Rename -> {
                            renameInput = record.filename
                            recordToRename = record
                        }
                    }
                },
                modifier = Modifier.weight(1f)
            )
        }

        // Bulk-Aktionsleiste (unten)
        if (isSelectionMode) {
            val selectedRecords = scans.filter { it.id in selectedIds }
            BulkActionBar(
                onShare = {
                    val intent = buildPdfShareIntent(context, selectedRecords)
                    if (intent != null) {
                        context.startActivity(Intent.createChooser(intent, sharePdfTitle))
                    }
                },
                onExport = {
                    selectedRecords.forEach { viewModel.exportScan(it) }
                    selectedIds = emptySet()
                },
                onExtractTexts        = {
                    selectedBulkLang = settings.defaultOcrLanguage
                    bulkLangForSearchable = false
                    showBulkLangDialog = true
                },
                extractEnabled        = selectedRecords.isNotEmpty(),
                onMakeSearchable      = {
                    if (selectedRecords.none { !it.isSearchable || it.extractedText == null }) {
                        viewModel.reportError(resources.getString(R.string.searchable_nothing_to_do))
                    } else {
                        selectedBulkLang = settings.defaultOcrLanguage
                        bulkLangForSearchable = true
                        showBulkLangDialog = true
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
        HomeDeleteDialog(
            record = record,
            onConfirm = {
                viewModel.deleteScan(record)
                selectedIds = selectedIds - record.id
                pendingDeleteRecord = null
            },
            onDismiss = { pendingDeleteRecord = null }
        )
    }

    // ── Bulk-Lösch-Bestätigung ────────────────────────────────────────────────
    if (showBulkDeleteConfirm) {
        HomeBulkDeleteDialog(
            selectedCount = selectedIds.size,
            onConfirm = {
                viewModel.deleteScans(scans.filter { it.id in selectedIds })
                selectedIds = emptySet()
                showBulkDeleteConfirm = false
            },
            onDismiss = { showBulkDeleteConfirm = false }
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
        HomeRenameDialog(
            input = renameInput,
            onInputChange = { renameInput = it },
            onConfirm = {
                viewModel.renameScan(record, renameInput)
                recordToRename = null
            },
            onDismiss = { recordToRename = null }
        )
    }

    // ── Bulk-OCR-Sprachauswahl ────────────────────────────────────────────────
    if (showBulkLangDialog) {
        val bulkRecords = scans.filter { it.id in selectedIds }
        HomeBulkLanguageDialog(
            expanded = bulkLangMenuExpanded,
            languageCode = selectedBulkLang,
            languages = ocrLanguages,
            onExpandedChange = { bulkLangMenuExpanded = it },
            onLanguageSelected = {
                selectedBulkLang = it
                bulkLangMenuExpanded = false
            },
            onConfirm = {
                showBulkLangDialog = false
                if (bulkLangForSearchable) {
                    viewModel.makeSearchableScans(bulkRecords, selectedBulkLang)
                    selectedIds = emptySet()
                } else {
                    viewModel.extractTexts(bulkRecords, selectedBulkLang)
                }
            },
            onDismiss = { showBulkLangDialog = false }
        )
    }

    // ── OCR-Lade-Overlay ──────────────────────────────────────────────────────
    if (ocrLoading) {
        HomeLoadingDialog(
            statusText = ocrStatusText ?: ocrProgress?.let { progress ->
                resources.getString(R.string.searchable_progress, progress.first, progress.second)
                }
            )
        }

    // ── Edit-Lade-Overlay ─────────────────────────────────────────────────────
    if (editLoading) {
        HomeLoadingDialog()
    }

    // ── OCR-Ergebnis-BottomSheet ──────────────────────────────────────────────
    if (showAddSheet) {
        HomeAddDocumentSheet(
            onDismiss = { showAddSheet = false },
            onScanClick = {
                showAddSheet = false
                launchScanner()
            },
            onImportClick = {
                showAddSheet = false
                importFileLauncher.launch(arrayOf("application/pdf"))
            },
            onImagesToPdfClick = {
                showAddSheet = false
                imagePickerLauncher.launch("image/*")
            }
        )
    }

    if (ocrText != null) {
        HomeOcrResultSheet(
            text = ocrText!!,
            onDismiss = viewModel::clearOcrText,
            onShare = {
                context.startActivity(
                    Intent.createChooser(
                        Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, ocrText)
                        },
                        actionShareTextLabel
                    )
                )
            },
            onCopy = {
                coroutineScope.launch {
                    clipboard.setClipEntry(ClipData.newPlainText("", ocrText!!).toClipEntry())
                    Toast.makeText(context, ocrCopiedMessage, Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    // ── Speichern-Dialog ──────────────────────────────────────────────────────
    if (showSaveDialog) {
        val currentImport = pendingImport
        HomeSaveImportDialog(
            pendingImport = currentImport,
            filenameInput = filenameInput,
            makeSearchable = makeSearchable,
            selectedLanguage = selectedLang,
            languageMenuExpanded = langMenuExpanded,
            ocrLanguages = ocrLanguages,
            onFilenameChange = { filenameInput = it },
            onMakeSearchableChange = { makeSearchable = it },
            onLanguageMenuExpandedChange = { langMenuExpanded = it },
            onLanguageSelected = {
                selectedLang = it
                langMenuExpanded = false
            },
            onConfirm = {
                when (val import = currentImport) {
                    is PendingImport.Scan -> {
                        val pdf = import.result.pdf
                        if (filenameInput.isNotBlank() && pdf != null) {
                            val thumbnailUri = import.result.pages?.firstOrNull()?.imageUri
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.saveScan(
                                pdf.uri,
                                pdf.pageCount,
                                filenameInput.trim(),
                                thumbnailUri,
                                makeSearchable,
                                selectedLang
                            )
                            showSaveDialog = false
                            pendingImport = null
                            makeSearchable = settings.defaultMakeSearchable
                            selectedLang = settings.defaultOcrLanguage
                        }
                    }
                    is PendingImport.File -> {
                        if (filenameInput.isNotBlank()) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.importFile(import.uri, filenameInput.trim())
                            showSaveDialog = false
                            pendingImport = null
                            makeSearchable = settings.defaultMakeSearchable
                            selectedLang = settings.defaultOcrLanguage
                        }
                    }
                    null -> Unit
                }
            },
            onDismiss = {
                showSaveDialog = false
                pendingImport = null
                makeSearchable = settings.defaultMakeSearchable
                selectedLang = settings.defaultOcrLanguage
            }
        )
    }

    // ── Fehler-Dialog ─────────────────────────────────────────────────────────
    if (error != null) {
        HomeErrorDialog(message = error!!, onDismiss = viewModel::clearError)
    }
}
