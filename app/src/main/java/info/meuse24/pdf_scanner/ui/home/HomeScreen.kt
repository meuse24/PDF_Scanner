package info.meuse24.pdf_scanner.ui.home

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import info.meuse24.pdf_scanner.R
import info.meuse24.pdf_scanner.domain.model.Document
import info.meuse24.pdf_scanner.ui.components.ScanAction
import info.meuse24.pdf_scanner.ui.entry.AppEntryAction
import info.meuse24.pdf_scanner.ui.home.components.HomeArchiveContent
import info.meuse24.pdf_scanner.ui.home.components.HomeErrorDialog
import info.meuse24.pdf_scanner.ui.home.components.HomeLoadingDialog
import info.meuse24.pdf_scanner.ui.ocr.buildOcrLanguageOptions
import info.meuse24.pdf_scanner.util.PdfPrintHelper
import info.meuse24.pdf_scanner.util.buildPdfShareIntent
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    addActionTrigger: Boolean = false,
    onAddActionTriggered: () -> Unit = {},
    onSelectionModeChange: (Boolean) -> Unit = {},
    pendingAppEntryAction: AppEntryAction? = null,
    onConsumeAppEntryAction: (AppEntryAction) -> Unit = {},
    navigation: HomeNavigationCallbacks = HomeNavigationCallbacks(),
    viewModel: HomeViewModel = hiltViewModel()
) {
    val archiveUiState by viewModel.archiveUiState.collectAsStateWithLifecycle()
    val operationUiState by viewModel.operationUiState.collectAsStateWithLifecycle()
    val messageUiState by viewModel.messageUiState.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val resources = LocalResources.current
    val clipboard = LocalClipboard.current
    val haptic = LocalHapticFeedback.current
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()

    val sharePdfTitle = stringResource(R.string.share_pdf_title)
    val undoLabel = stringResource(R.string.action_undo)
    val errorDeviceUnsupported = stringResource(R.string.error_device_unsupported)
    val errorScannerUnavailable = stringResource(R.string.error_scanner_unavailable)
    val displayLocale = resources.configuration.locales[0] ?: Locale.getDefault()
    val ocrAutoLabel = stringResource(R.string.ocr_language_auto)
    val ocrLanguages = remember(displayLocale, ocrAutoLabel) {
        buildOcrLanguageOptions(ocrAutoLabel, displayLocale)
    }

    var pendingImport by remember { mutableStateOf<PendingImport?>(null) }
    var showAddSheet by remember { mutableStateOf(false) }
    var showSaveDialog by remember { mutableStateOf(false) }
    var filenameInput by rememberSaveable { mutableStateOf("") }
    var makeSearchable by rememberSaveable { mutableStateOf(archiveUiState.settings.defaultMakeSearchable) }
    var selectedLang by rememberSaveable { mutableStateOf(archiveUiState.settings.defaultOcrLanguage) }
    var langMenuExpanded by remember { mutableStateOf(false) }

    var selectedIds by remember { mutableStateOf(emptySet<Long>()) }
    val isSelectionMode = selectedIds.isNotEmpty()
    var pendingDeleteRecord by remember { mutableStateOf<Document?>(null) }
    var showBulkDeleteConfirm by remember { mutableStateOf(false) }

    var showMergeDialog by remember { mutableStateOf(false) }
    var mergeFilenameInput by rememberSaveable { mutableStateOf("") }

    var recordToRename by remember { mutableStateOf<Document?>(null) }
    var renameInput by rememberSaveable { mutableStateOf("") }

    var showBulkLangDialog by remember { mutableStateOf(false) }
    var bulkLangForSearchable by remember { mutableStateOf(false) }
    var selectedBulkLang by rememberSaveable { mutableStateOf(archiveUiState.settings.defaultOcrLanguage) }
    var bulkLangMenuExpanded by remember { mutableStateOf(false) }
    var showFolderPicker by remember { mutableStateOf(false) }

    fun preparePdfImport(uri: Uri) {
        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: SecurityException) {
            // Share targets and some providers only grant a transient session permission.
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
        makeSearchable = archiveUiState.settings.defaultMakeSearchable
        selectedLang = archiveUiState.settings.defaultOcrLanguage
        showSaveDialog = true
    }

    val launchers = rememberHomeScreenLaunchers(
        context = context,
        viewModel = viewModel,
        errorDeviceUnsupported = errorDeviceUnsupported,
        errorScannerUnavailable = errorScannerUnavailable,
        onScanImported = { result ->
            pendingImport = PendingImport.Scan(result)
            filenameInput = resources.getString(
                R.string.scan_filename_default,
                SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            )
            makeSearchable = archiveUiState.settings.defaultMakeSearchable
            selectedLang = archiveUiState.settings.defaultOcrLanguage
            showSaveDialog = true
        },
        onPdfImported = ::preparePdfImport,
        onNavigateToImagesToPdf = navigation.onImagesToPdf
    )

    val actionNavigator = HomeScanActionNavigator(
        onSplit = navigation.onSplit,
        onReorder = navigation.onReorder,
        onRotate = navigation.onRotate,
        onDeletePages = navigation.onDeletePages,
        onExtractPages = navigation.onExtractPages,
        onAppendPages = navigation.onAppendPages,
        onDuplicatePages = navigation.onDuplicatePages,
        onPageNumbers = navigation.onPageNumbers,
        onTextWatermark = navigation.onTextWatermark,
        onCompressPdf = navigation.onCompressPdf,
        onProtectPdf = navigation.onProtectPdf,
        onUnlockPdf = navigation.onUnlockPdf,
        onSignature = navigation.onSignature,
        onRemoveTextLayer = navigation.onRemoveTextLayer,
        onRemovePassword = navigation.onRemovePassword,
        onRestrictUsage = navigation.onRestrictUsage,
        onAnnotate = navigation.onAnnotate,
        onRedact = navigation.onRedact,
        onGrayscale = navigation.onGrayscale,
        onPdfMetadata = navigation.onPdfMetadata,
        onQrScan = navigation.onQrScan,
        onBusinessCard = navigation.onBusinessCard,
        onExportAsJpg = viewModel::exportAsJpg,
        onPrint = { record ->
            PdfPrintHelper.print(
                context = context,
                pdf = File(record.filepath),
                jobName = record.filename,
                pageCount = record.pageCount
            )
        },
        onRename = { record ->
            renameInput = record.filename
            recordToRename = record
        }
    )

    HandleHomeAddActionEffect(
        addActionTrigger = addActionTrigger,
        onAddActionTriggered = onAddActionTriggered,
        onOpenSheet = { showAddSheet = true }
    )
    HandleHomeAppEntryActionEffect(
        pendingAppEntryAction = pendingAppEntryAction,
        onConsume = onConsumeAppEntryAction,
        onLaunchScanner = launchers.launchScanner,
        onLaunchImportImages = launchers.launchImageImport,
        onImportSharedPdf = ::preparePdfImport,
        onImportSharedImages = { uris ->
            viewModel.setPendingImageUris(uris)
            navigation.onImagesToPdf()
        }
    )
    HandleHomeSuccessEffect(
        success = messageUiState.success,
        context = context,
        haptic = haptic,
        onConsumed = viewModel::clearSuccess
    )
    HandleHomeTrashEffect(
        trashMessage = messageUiState.trashMessage,
        trashUndoSnackbarSeconds = archiveUiState.settings.trashUndoSnackbarSeconds,
        snackbarHostState = snackbarHostState,
        undoLabel = undoLabel,
        onUndo = viewModel::restoreLastTrashed,
        onConsumed = viewModel::clearTrashMessage
    )
    HandleHomeOcrReviewEffect(
        ocrReviewRequestId = operationUiState.ocrReviewRequestId,
        onNavigateToOcrReview = navigation.onOcrReview,
        onConsumed = viewModel::clearOcrReviewRequest
    )
    HandleHomePlayReviewEffect(
        playReviewRequestId = operationUiState.playReviewRequestId,
        context = context,
        onLaunchReview = viewModel::launchPlayReview,
        onConsumed = viewModel::clearPlayReviewRequest
    )
    HandleHomeListHaptics(listState = listState, haptic = haptic)

    BackHandler(enabled = isSelectionMode) { selectedIds = emptySet() }

    androidx.compose.runtime.LaunchedEffect(isSelectionMode) {
        onSelectionModeChange(isSelectionMode)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            HomeArchiveContent(
                scans = archiveUiState.scans,
                filteredScans = archiveUiState.filteredScans,
                folders = archiveUiState.folders,
                currentFolder = archiveUiState.currentFolder,
                favoritesFilter = archiveUiState.favoritesFilter,
                searchQuery = archiveUiState.searchQuery,
                sortOrder = archiveUiState.sortOrder,
                selectedIds = selectedIds,
                isSelectionMode = isSelectionMode,
                listState = listState,
                onSearchQueryChange = viewModel::updateSearchQuery,
                onSortOrderSelected = viewModel::setSortOrder,
                onClearSelection = { selectedIds = emptySet() },
                onSelectAll = { selectedIds = archiveUiState.filteredScans.map { it.id }.toSet() },
                onSelectionToggle = { record ->
                    selectedIds = if (record.id in selectedIds) selectedIds - record.id else selectedIds + record.id
                },
                onOpenRecord = { record -> navigation.onViewer(record.id) },
                onToggleFavorite = viewModel::toggleFavorite,
                onAction = { record, action ->
                    dispatchHomeScanAction(record, action, actionNavigator)
                },
                modifier = Modifier.weight(1f)
            )
        }

        if (isSelectionMode) {
            val selectedRecords = archiveUiState.scans.filter { it.id in selectedIds }
            HomeSelectionBar(
                selectedRecords = selectedRecords,
                onShare = {
                    buildPdfShareIntent(context, selectedRecords)?.let { intent ->
                        context.startActivity(Intent.createChooser(intent, sharePdfTitle))
                    }
                },
                onExport = {
                    selectedRecords.forEach(viewModel::exportScan)
                    selectedIds = emptySet()
                },
                onExtractTexts = {
                    selectedBulkLang = archiveUiState.settings.defaultOcrLanguage
                    bulkLangForSearchable = false
                    showBulkLangDialog = true
                },
                onMakeSearchable = {
                    if (selectedRecords.none { !it.isSearchable || it.extractedText == null }) {
                        viewModel.reportError(resources.getString(R.string.searchable_nothing_to_do))
                    } else {
                        selectedBulkLang = archiveUiState.settings.defaultOcrLanguage
                        bulkLangForSearchable = true
                        showBulkLangDialog = true
                    }
                },
                onMerge = {
                    mergeFilenameInput = resources.getString(
                        R.string.merge_filename_default,
                        SimpleDateFormat("ddMMyyyy", Locale.getDefault()).format(Date())
                    )
                    showMergeDialog = true
                },
                onMoveToFolder = { showFolderPicker = true },
                onDelete = {
                    if (selectedRecords.size == 1) {
                        pendingDeleteRecord = selectedRecords.first()
                    } else {
                        showBulkDeleteConfirm = true
                    }
                },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 16.dp, vertical = if (isSelectionMode) 84.dp else 16.dp)
        )
    }

    HomeBulkOverlays(
        scans = archiveUiState.scans,
        selectedIds = selectedIds,
        onSelectedIdsChange = { selectedIds = it },
        pendingDeleteRecord = pendingDeleteRecord,
        onPendingDeleteRecordChange = { pendingDeleteRecord = it },
        showBulkDeleteConfirm = showBulkDeleteConfirm,
        onShowBulkDeleteConfirmChange = { showBulkDeleteConfirm = it },
        showMergeDialog = showMergeDialog,
        onShowMergeDialogChange = { showMergeDialog = it },
        mergeFilenameInput = mergeFilenameInput,
        onMergeFilenameInputChange = { mergeFilenameInput = it },
        recordToRename = recordToRename,
        onRecordToRenameChange = { recordToRename = it },
        renameInput = renameInput,
        onRenameInputChange = { renameInput = it },
        showFolderPicker = showFolderPicker,
        onShowFolderPickerChange = { showFolderPicker = it },
        folders = archiveUiState.folders,
        showBulkLangDialog = showBulkLangDialog,
        onShowBulkLangDialogChange = { showBulkLangDialog = it },
        bulkLangMenuExpanded = bulkLangMenuExpanded,
        onBulkLangMenuExpandedChange = { bulkLangMenuExpanded = it },
        selectedBulkLang = selectedBulkLang,
        onSelectedBulkLangChange = { selectedBulkLang = it },
        bulkLangForSearchable = bulkLangForSearchable,
        ocrLanguages = ocrLanguages,
        viewModel = viewModel
    )

    HomeImportOverlays(
        showAddSheet = showAddSheet,
        onShowAddSheetChange = { showAddSheet = it },
        showSaveDialog = showSaveDialog,
        onShowSaveDialogChange = { showSaveDialog = it },
        pendingImport = pendingImport,
        onPendingImportChange = { pendingImport = it },
        filenameInput = filenameInput,
        onFilenameInputChange = { filenameInput = it },
        makeSearchable = makeSearchable,
        onMakeSearchableChange = { makeSearchable = it },
        selectedLang = selectedLang,
        onSelectedLangChange = { selectedLang = it },
        langMenuExpanded = langMenuExpanded,
        onLangMenuExpandedChange = { langMenuExpanded = it },
        settings = archiveUiState.settings,
        ocrLanguages = ocrLanguages,
        ocrText = operationUiState.ocrText,
        clipboard = clipboard,
        haptic = haptic,
        resources = resources,
        onLaunchScanner = launchers.launchScanner,
        onLaunchImportPdf = launchers.launchPdfImport,
        onLaunchImportImages = launchers.launchImageImport,
        onClearOcrText = viewModel::clearOcrText,
        viewModel = viewModel
    )

    if (operationUiState.ocrLoading) {
        HomeLoadingDialog(
            statusText = operationUiState.ocrStatusText ?: operationUiState.ocrProgress?.let { progress ->
                resources.getString(
                    R.string.searchable_progress,
                    progress.currentPage,
                    progress.totalPages
                )
            }
        )
    }

    if (operationUiState.editLoading) {
        HomeLoadingDialog()
    }

    messageUiState.error?.let { message ->
        HomeErrorDialog(message = message, onDismiss = viewModel::clearError)
    }
}
