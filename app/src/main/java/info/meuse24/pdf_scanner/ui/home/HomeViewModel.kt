@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class, kotlinx.coroutines.FlowPreview::class)

package info.meuse24.pdf_scanner.ui.home

import android.app.Activity
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import info.meuse24.pdf_scanner.R
import info.meuse24.pdf_scanner.domain.model.Document
import info.meuse24.pdf_scanner.domain.repository.AppSettingsRepository
import info.meuse24.pdf_scanner.domain.repository.DocumentRepository
import info.meuse24.pdf_scanner.domain.repository.FolderRepository
import info.meuse24.pdf_scanner.domain.usecase.BuildScanSearchQueryUseCase
import info.meuse24.pdf_scanner.domain.usecase.CheckPrintPageSizeWarningUseCase
import info.meuse24.pdf_scanner.domain.usecase.ExportOcrTextUseCase
import info.meuse24.pdf_scanner.domain.usecase.ExportAsJpgUseCase
import info.meuse24.pdf_scanner.domain.usecase.ExportScanUseCase
import info.meuse24.pdf_scanner.domain.usecase.ExtractTextUseCase
import info.meuse24.pdf_scanner.domain.usecase.FindOcrExtractableDocumentsUseCase
import info.meuse24.pdf_scanner.domain.usecase.ImportFileUseCase
import info.meuse24.pdf_scanner.domain.usecase.ImportScanUseCase
import info.meuse24.pdf_scanner.domain.usecase.MoveDocumentsUseCase
import info.meuse24.pdf_scanner.domain.usecase.OcrDocumentResult
import info.meuse24.pdf_scanner.domain.usecase.OcrBackfillUseCase
import info.meuse24.pdf_scanner.domain.usecase.OcrNoTextException
import info.meuse24.pdf_scanner.domain.usecase.RecordReviewPromptActionUseCase
import info.meuse24.pdf_scanner.domain.usecase.RenameDocumentResult
import info.meuse24.pdf_scanner.domain.usecase.RenameDocumentUseCase
import info.meuse24.pdf_scanner.domain.usecase.RestoreMissingFileException
import info.meuse24.pdf_scanner.domain.usecase.RestoreScansUseCase
import info.meuse24.pdf_scanner.domain.usecase.ToggleFavoriteUseCase
import info.meuse24.pdf_scanner.domain.usecase.TrashScansUseCase
import info.meuse24.pdf_scanner.domain.workflow.MakeSearchableWorkflow
import info.meuse24.pdf_scanner.domain.workflow.MergePdfsWorkflow
import info.meuse24.pdf_scanner.domain.workflow.ScanWorkflowError
import info.meuse24.pdf_scanner.domain.workflow.WorkflowErrorMapper
import info.meuse24.pdf_scanner.domain.workflow.WorkflowResult
import info.meuse24.pdf_scanner.ui.ocr.OCR_LANGUAGE_AUTO
import info.meuse24.pdf_scanner.domain.model.AppSortOrder
import info.meuse24.pdf_scanner.domain.gateway.DispatcherProvider
import info.meuse24.pdf_scanner.domain.model.OcrModelInstallException
import info.meuse24.pdf_scanner.domain.model.OcrPipelineStatus
import info.meuse24.pdf_scanner.domain.model.OcrResultStats
import info.meuse24.pdf_scanner.domain.model.OcrThresholds
import info.meuse24.pdf_scanner.util.PlayReviewPromptManager
import info.meuse24.pdf_scanner.domain.gateway.ResourceProvider
import info.meuse24.pdf_scanner.domain.gateway.StorageProvider
import info.meuse24.pdf_scanner.ui.print.PrintRequestCoordinator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: DocumentRepository,
    private val folderRepository: FolderRepository,
    private val settingsRepository: AppSettingsRepository,
    private val importScanUseCase: ImportScanUseCase,
    private val importFileUseCase: ImportFileUseCase,
    private val exportScanUseCase: ExportScanUseCase,
    private val exportAsJpgUseCase: ExportAsJpgUseCase,
    private val exportOcrTextUseCase: ExportOcrTextUseCase,
    checkPrintPageSizeWarningUseCase: CheckPrintPageSizeWarningUseCase,
    private val trashScansUseCase: TrashScansUseCase,
    private val restoreScansUseCase: RestoreScansUseCase,
    private val moveDocumentsUseCase: MoveDocumentsUseCase,
    private val toggleFavoriteUseCase: ToggleFavoriteUseCase,
    private val extractTextUseCase: ExtractTextUseCase,
    private val findOcrExtractableDocumentsUseCase: FindOcrExtractableDocumentsUseCase,
    private val ocrBackfillUseCase: OcrBackfillUseCase,
    private val renameDocumentUseCase: RenameDocumentUseCase,
    private val buildScanSearchQueryUseCase: BuildScanSearchQueryUseCase,
    private val recordReviewPromptActionUseCase: RecordReviewPromptActionUseCase,
    private val makeSearchableWorkflow: MakeSearchableWorkflow,
    private val mergePdfsWorkflow: MergePdfsWorkflow,
    private val workflowErrorMapper: WorkflowErrorMapper,
    private val resourceProvider: ResourceProvider,
    private val storageProvider: StorageProvider,
    private val dispatcherProvider: DispatcherProvider,
    private val archiveFilterStore: ArchiveFilterStore,
    private val playReviewPromptManager: PlayReviewPromptManager
) : ViewModel() {

    private val archiveFilterFlow = archiveFilterStore.filter
        .stateIn(viewModelScope, SharingStarted.Eagerly, ArchiveFilter.AllDocuments)

    private val allScansForListFlow = repository.getAllScansForList()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val scansFlow = archiveFilterFlow
        .flatMapLatest { filter -> listScansFor(filter) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val foldersFlow = folderRepository.observeFolders()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _searchQuery = MutableStateFlow("")
    private val _sortOrder = MutableStateFlow(SortOrder.ByDate)
    private val _selectedIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedIds: StateFlow<Set<Long>> = _selectedIds.asStateFlow()
    private val printRequestCoordinator = PrintRequestCoordinator(checkPrintPageSizeWarningUseCase)
    val pendingPrintDocument: StateFlow<Document?> = printRequestCoordinator.pendingPrintDocument
    val printRequests: SharedFlow<Document> = printRequestCoordinator.printRequests

    private val filteredScansFlow = combine(archiveFilterFlow, _searchQuery, _sortOrder) { filter, rawQuery, sortOrder ->
        SearchListRequest(filter, rawQuery.trim(), sortOrder)
    }.flatMapLatest { request ->
        val query = request.query
        val source = if (query.isBlank()) {
            listScansFor(request.filter)
        } else {
            val ftsQuery = buildScanSearchQueryUseCase(query)
            if (ftsQuery.isBlank()) {
                flowOf(emptyList())
            } else {
                searchScansFor(request.filter, ftsQuery)
            }
        }
        source.map { scans -> sortScans(scans, request.sortOrder) }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _error = MutableStateFlow<String?>(null)
    private val _success = MutableStateFlow<String?>(null)
    private val _trashMessage = MutableStateFlow<String?>(null)
    private val _lastTrashed = MutableStateFlow<List<Long>>(emptyList())

    private val _ocrText = MutableStateFlow<String?>(null)
    private val _ocrReviewRequestId = MutableStateFlow<Long?>(null)
    private val _playReviewRequestId = MutableStateFlow(0L)
    private val _ocrLoading = MutableStateFlow(false)
    private val _ocrProgress = MutableStateFlow<HomeOcrProgress?>(null)
    private val _ocrStatusText = MutableStateFlow<String?>(null)
    private val _editLoading = MutableStateFlow(false)

    private val _pendingImageUris = MutableStateFlow<List<Uri>>(emptyList())

    private val scanArchiveData = combine(
        scansFlow,
        filteredScansFlow,
        allScansForListFlow
    ) { scans, filteredScans, allScans ->
        ScanArchiveData(scans, filteredScans, allScans)
    }

    private val archiveBaseState = combine(
        settingsRepository.settings,
        scanArchiveData,
        foldersFlow,
        archiveFilterFlow
    ) { settings, scanData, folders, archiveFilter ->
        HomeArchiveUiState(
            settings = settings,
            scans = scanData.scans,
            filteredScans = scanData.filteredScans,
            folders = folders,
            currentFolder = if (archiveFilter is ArchiveFilter.Folder) {
                folders.firstOrNull { it.id == archiveFilter.folderId }
            } else {
                null
            },
            favoritesFilter = archiveFilter is ArchiveFilter.Favorites,
            currentTagKey = (archiveFilter as? ArchiveFilter.Tag)?.key,
            availableTagKeys = availableTagKeys(scanData.allScans)
        )
    }

    val archiveUiState: StateFlow<HomeArchiveUiState> = combine(
        archiveBaseState,
        _searchQuery,
        _sortOrder,
        _pendingImageUris
    ) { state, searchQuery, sortOrder, pendingImageUris ->
        state.copy(
            searchQuery = searchQuery,
            sortOrder = sortOrder,
            pendingImageUris = pendingImageUris
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, HomeArchiveUiState())

    val operationUiState: StateFlow<HomeOperationUiState> = combine(
        combine(
            _ocrText,
            _ocrReviewRequestId,
            _ocrLoading,
            _ocrProgress,
            _ocrStatusText
        ) { ocrText, ocrReviewRequestId, ocrLoading, ocrProgress, ocrStatusText ->
            HomeOperationUiState(
                ocrText = ocrText,
                ocrReviewRequestId = ocrReviewRequestId,
                ocrLoading = ocrLoading,
                ocrProgress = ocrProgress,
                ocrStatusText = ocrStatusText
            )
        },
        _playReviewRequestId,
        _editLoading
    ) { state, playReviewRequestId, editLoading ->
        state.copy(
            playReviewRequestId = playReviewRequestId,
            editLoading = editLoading
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, HomeOperationUiState())

    val messageUiState: StateFlow<HomeMessageUiState> = combine(
        _error,
        _success,
        _trashMessage,
        _lastTrashed
    ) { error, success, trashMessage, lastTrashed ->
        HomeMessageUiState(
            error = error,
            success = success,
            trashMessage = trashMessage,
            lastTrashed = lastTrashed
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, HomeMessageUiState())

    private var backfillTriggered = false

    init {
        triggerSilentBackfill()
        viewModelScope.launch {
            settingsRepository.settings.collect { appSettings ->
                _sortOrder.value = appSettings.defaultSortOrder.toUiSortOrder()
            }
        }
    }

    private val scansDir get() = storageProvider.scansDir()

    fun setPendingImageUris(uris: List<Uri>) {
        _pendingImageUris.value = uris
    }

    fun clearPendingImageUris() {
        _pendingImageUris.value = emptyList()
    }

    fun saveScan(
        pdfUri: Uri,
        pageCount: Int,
        filename: String,
        thumbnailUri: Uri? = null,
        makeSearchable: Boolean = false,
        languageCode: String = Locale.getDefault().language
    ) {
        viewModelScope.launch(dispatcherProvider.io) {
            try {
                if (makeSearchable) _ocrLoading.value = true
                val document = importScanUseCase(
                    pdfUri = pdfUri,
                    pageCount = pageCount,
                    filename = filename,
                    thumbnailUri = thumbnailUri,
                    makeSearchable = makeSearchable,
                    languageCode = languageCode,
                    onProgress = { current, total ->
                        _ocrProgress.value = HomeOcrProgress(current, total)
                    },
                    onStatus = ::updateOcrStatus
                )
                assignToCurrentFolder(document.id)
                maybeRequestPlayReview()
            } catch (_: OcrModelInstallException) {
                _error.value = resourceProvider.getString(R.string.ocr_model_download_failed)
            } catch (exception: Exception) {
                _error.value = exception.message ?: resourceProvider.getString(R.string.error_save_failed)
            } finally {
                _ocrLoading.value = false
                _ocrProgress.value = null
                _ocrStatusText.value = null
            }
        }
    }

    fun importFile(pdfUri: Uri, filename: String) {
        if (_editLoading.value) return
        _editLoading.value = true
        viewModelScope.launch(dispatcherProvider.io) {
            try {
                val document = importFileUseCase(pdfUri, filename)
                assignToCurrentFolder(document.id)
                maybeRequestPlayReview()
            } catch (exception: Exception) {
                _error.value = exception.message ?: resourceProvider.getString(R.string.error_save_failed)
            } finally {
                _editLoading.value = false
            }
        }
    }

    fun deleteScan(record: Document) = deleteScans(listOf(record))

    fun deleteScans(records: List<Document>) {
        viewModelScope.launch(dispatcherProvider.io) {
            try {
                val ids = trashScansUseCase(records)
                if (ids.isNotEmpty()) {
                    _lastTrashed.value = ids
                    _trashMessage.value = resourceProvider.getQuantityString(
                        R.plurals.trash_moved,
                        ids.size,
                        ids.size
                    )
                }
            } catch (_: Exception) {
                _error.value = resourceProvider.getString(R.string.error_delete_failed)
            }
        }
    }

    fun restoreLastTrashed() {
        val ids = _lastTrashed.value
        if (ids.isEmpty()) return

        viewModelScope.launch(dispatcherProvider.io) {
            try {
                restoreScansUseCase(ids)
                _success.value = resourceProvider.getQuantityString(
                    R.plurals.trash_restored,
                    ids.size,
                    ids.size
                )
                _lastTrashed.value = emptyList()
            } catch (_: RestoreMissingFileException) {
                _error.value = resourceProvider.getString(R.string.error_restore_missing_file)
            } catch (_: Exception) {
                _error.value = resourceProvider.getString(R.string.error_restore_failed)
            }
        }
    }

    fun exportScan(record: Document) {
        viewModelScope.launch(dispatcherProvider.io) {
            try {
                val displayName = exportScanUseCase(record)
                _success.value = resourceProvider.getString(R.string.export_success, displayName)
            } catch (_: Exception) {
                _error.value = resourceProvider.getString(R.string.error_export_failed)
            }
        }
    }

    fun exportAsJpg(record: Document) {
        viewModelScope.launch(dispatcherProvider.io) {
            try {
                val count = exportAsJpgUseCase(record)
                _success.value = if (count == 1) {
                    resourceProvider.getString(R.string.export_jpg_success, record.filename)
                } else {
                    resourceProvider.getString(R.string.export_jpg_success_multi, count)
                }
            } catch (_: Exception) {
                _error.value = resourceProvider.getString(R.string.error_export_jpg_failed)
            }
        }
    }

    fun extractText(record: Document, languageCode: String = Locale.getDefault().language) {
        extractTexts(listOf(record), languageCode)
    }

    fun extractTexts(records: List<Document>, languageCode: String = Locale.getDefault().language) {
        if (_ocrLoading.value) return

        val validRecords = findOcrExtractableDocumentsUseCase(records)
        if (validRecords.isEmpty()) {
            _error.value = resourceProvider.getString(R.string.ocr_no_image)
            return
        }

        _ocrLoading.value = true
        viewModelScope.launch(dispatcherProvider.io) {
            try {
                val results = extractTextUseCase(validRecords, languageCode, ::updateOcrStatus)
                results.forEach { document ->
                    repository.updateExtractedTextAndOcrStats(
                        id = document.recordId,
                        text = document.fullText.ifBlank { null },
                        confidence = document.stats?.confidence,
                        language = document.stats?.recognizedLanguage,
                        pageTexts = document.pageTexts
                    )
                }

                val firstStats = results.firstOrNull { it.fullText.isNotBlank() }?.stats
                if (validRecords.size == 1) {
                    _ocrText.value = null
                    _ocrReviewRequestId.value = validRecords.single().id
                } else {
                    _ocrText.value = buildCombinedOcrText(validRecords, results)
                }
                maybeWarnAboutUncertainAutoMode(languageCode, firstStats)
            } catch (_: OcrNoTextException) {
                val messageRes = if (languageCode == OCR_LANGUAGE_AUTO) {
                    R.string.ocr_no_text_auto_hint
                } else {
                    R.string.ocr_no_text_found
                }
                _error.value = resourceProvider.getString(messageRes)
            } catch (_: OcrModelInstallException) {
                _error.value = resourceProvider.getString(R.string.ocr_model_download_failed)
            } catch (_: Exception) {
                _error.value = resourceProvider.getString(R.string.ocr_failed)
            } finally {
                _ocrLoading.value = false
                _ocrStatusText.value = null
            }
        }
    }

    fun makeSearchableScans(records: List<Document>, languageCode: String) {
        if (_ocrLoading.value) return

        _ocrLoading.value = true
        viewModelScope.launch(dispatcherProvider.io) {
            try {
                when (
                    val result = makeSearchableWorkflow(
                        records = records,
                        languageCode = languageCode,
                        force = true,
                        onProgress = { current, total ->
                            _ocrProgress.value = HomeOcrProgress(current, total)
                        },
                        onStatus = ::updateOcrStatus
                    )
                ) {
                    is WorkflowResult.Success -> {
                        val data = result.value
                        _success.value = if (data.processedCount == 1) {
                            resourceProvider.getString(R.string.searchable_success, data.firstFilename)
                        } else {
                            resourceProvider.getString(R.string.searchable_success_multi, data.processedCount)
                        }
                        if (data.blankOcrCount > 0) {
                            _error.value = resourceProvider.getString(
                                R.string.searchable_blank_ocr_warning,
                                data.blankOcrCount
                            )
                        }
                    }

                    is WorkflowResult.Failure -> handleWorkflowFailure("SearchablePDF", result.error)
                }
            } finally {
                _ocrLoading.value = false
                _ocrProgress.value = null
                _ocrStatusText.value = null
            }
        }
    }

    fun mergePdfs(records: List<Document>, outputFilename: String) {
        if (_editLoading.value) return

        _editLoading.value = true
        viewModelScope.launch(dispatcherProvider.io) {
            try {
                when (val result = mergePdfsWorkflow(records, outputFilename, scansDir)) {
                    is WorkflowResult.Success -> {
                        _success.value = resourceProvider.getString(
                            R.string.merge_success,
                            result.value.outputFilename
                        )
                    }

                    is WorkflowResult.Failure -> handleWorkflowFailure("PdfEditor", result.error)
                }
            } finally {
                _editLoading.value = false
            }
        }
    }

    fun renameScan(record: Document, newName: String) {
        viewModelScope.launch(dispatcherProvider.io) {
            when (val result = renameDocumentUseCase(record, newName)) {
                RenameDocumentResult.BlankName -> Unit
                RenameDocumentResult.TargetExists -> {
                    _error.value = resourceProvider.getString(R.string.rename_error_exists)
                }
                RenameDocumentResult.RenameFailed -> {
                    _error.value = resourceProvider.getString(R.string.rename_error_failed)
                }
                is RenameDocumentResult.Success -> {
                    _success.value = resourceProvider.getString(R.string.rename_success, result.filename)
                }
            }
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setSelectedIds(ids: Set<Long>) {
        _selectedIds.value = ids
    }

    fun toggleSelectedId(id: Long) {
        val current = _selectedIds.value
        _selectedIds.value = if (id in current) current - id else current + id
    }

    fun clearSelectedIds() {
        _selectedIds.value = emptySet()
    }

    fun showAllDocuments() {
        archiveFilterStore.showAllDocuments()
    }

    fun showFavorites() {
        archiveFilterStore.showFavorites()
    }

    fun showFolder(folderId: Long) {
        archiveFilterStore.showFolder(folderId)
    }

    fun showTag(tagKey: String) {
        archiveFilterStore.showTag(tagKey)
    }

    fun moveScansToFolder(ids: Set<Long>, folderId: Long?) {
        viewModelScope.launch(dispatcherProvider.io) {
            try {
                moveDocumentsUseCase(ids.toList(), folderId)
                _success.value = resourceProvider.getString(R.string.folder_move_success)
            } catch (_: Exception) {
                _error.value = resourceProvider.getString(R.string.folder_move_failed)
            }
        }
    }

    fun toggleFavorite(record: Document) {
        viewModelScope.launch(dispatcherProvider.io) {
            try {
                toggleFavoriteUseCase(record)
            } catch (_: Exception) {
                _error.value = resourceProvider.getString(R.string.favorite_toggle_failed)
            }
        }
    }

    fun setSortOrder(sortOrder: SortOrder) {
        _sortOrder.value = sortOrder
        settingsRepository.updateDefaultSortOrder(sortOrder.toAppSortOrder())
    }

    fun clearOcrText() {
        _ocrText.value = null
    }

    fun clearOcrReviewRequest() {
        _ocrReviewRequestId.value = null
    }

    fun launchPlayReview(activity: Activity, onComplete: () -> Unit) {
        playReviewPromptManager.launchReviewFlow(activity, onComplete)
    }

    fun clearPlayReviewRequest() {
        _playReviewRequestId.value = 0L
    }

    fun reportError(message: String) {
        _error.value = message
    }

    fun clearError() {
        _error.value = null
    }

    fun clearSuccess() {
        _success.value = null
    }

    fun clearTrashMessage() {
        _trashMessage.value = null
    }

    private fun handleWorkflowFailure(tag: String, error: ScanWorkflowError) {
        Log.e(tag, "workflow failed: $error", error.cause)
        _error.value = workflowErrorMapper.map(error)
    }

    private fun triggerSilentBackfill() {
        if (backfillTriggered) return

        backfillTriggered = true
        viewModelScope.launch(dispatcherProvider.io) {
            ocrBackfillUseCase(
                languageCode = OCR_LANGUAGE_AUTO,
                onBackfilled = { filename -> Log.d("Backfill", "Text nacherfasst: $filename") },
                onFailure = { filename, exception ->
                    Log.w("Backfill", "Nacherfassung fehlgeschlagen: $filename", exception)
                }
            )
        }
    }

    private fun updateOcrStatus(status: OcrPipelineStatus) {
        _ocrStatusText.value = when (status) {
            OcrPipelineStatus.PreparingModel -> resourceProvider.getString(R.string.ocr_model_preparing)
            OcrPipelineStatus.DownloadingModel -> resourceProvider.getString(R.string.ocr_model_downloading)
            OcrPipelineStatus.InstallingModel -> resourceProvider.getString(R.string.ocr_model_installing)
        }
    }

    private suspend fun assignToCurrentFolder(documentId: Long) {
        val filter = archiveFilterFlow.value
        if (documentId <= 0L || filter !is ArchiveFilter.Folder) return
        moveDocumentsUseCase(listOf(documentId), filter.folderId)
    }

    private fun maybeRequestPlayReview() {
        if (recordReviewPromptActionUseCase()) {
            _playReviewRequestId.value = System.currentTimeMillis()
        }
    }

    private fun maybeWarnAboutUncertainAutoMode(languageCode: String, stats: OcrResultStats?) {
        if (stats == null) return
        if (
            languageCode == OCR_LANGUAGE_AUTO &&
            stats.recognizedLanguage.isNullOrBlank() &&
            stats.confidence < OcrThresholds.AUTO_DETECTION_UNCERTAIN
        ) {
            _error.value = resourceProvider.getString(R.string.ocr_auto_detection_uncertain)
        }
    }

    fun exportOcrText(record: Document) {
        exportOcrTexts(listOf(record))
    }

    fun exportOcrTexts(records: List<Document>) {
        viewModelScope.launch(dispatcherProvider.io) {
            try {
                val requestedIds = records.map { it.id }
                val fullRecords = repository.getScansByIds(requestedIds)
                    .filter { !it.extractedText.isNullOrBlank() }
                if (fullRecords.isEmpty()) {
                    _error.value = resourceProvider.getString(R.string.ocr_export_nothing_to_export)
                    return@launch
                }
                val displayName = exportOcrTextUseCase(fullRecords)
                _success.value = resourceProvider.getString(R.string.ocr_export_success, displayName)
            } catch (_: Exception) {
                _error.value = resourceProvider.getString(R.string.ocr_export_error)
            }
        }
    }

    fun requestPrint(record: Document) {
        printRequestCoordinator.requestPrint(viewModelScope, record)
    }

    fun confirmPrintWarning() {
        printRequestCoordinator.confirmPrintWarning(viewModelScope)
    }

    fun dismissPrintWarning() {
        printRequestCoordinator.dismissPrintWarning()
    }

    private fun listScansFor(filter: ArchiveFilter) = when (filter) {
        ArchiveFilter.AllDocuments -> repository.getAllScansForList()
        ArchiveFilter.Favorites -> repository.getFavoriteScansForList()
        is ArchiveFilter.Folder -> repository.getScansInFolderForList(filter.folderId)
        is ArchiveFilter.Tag -> repository.getScansWithTagForList(filter.key)
    }

    private fun searchScansFor(filter: ArchiveFilter, ftsQuery: String) = when (filter) {
        ArchiveFilter.AllDocuments -> repository.searchScansForList(ftsQuery)
        ArchiveFilter.Favorites -> repository.searchFavoriteScansForList(ftsQuery)
        is ArchiveFilter.Folder -> repository.searchScansInFolderForList(ftsQuery, filter.folderId)
        is ArchiveFilter.Tag -> repository.searchScansWithTagForList(ftsQuery, filter.key)
    }
}

private val TAG_ORDER = listOf("invoice", "contract", "insurance", "certificate", "bank", "delivery")

private fun availableTagKeys(scans: List<Document>): List<String> =
    scans.asSequence()
        .flatMap { it.tags.orEmpty().split(",").asSequence() }
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()
        .sortedBy { key ->
            val index = TAG_ORDER.indexOf(key)
            if (index >= 0) index else TAG_ORDER.size
        }
        .toList()

private data class SearchListRequest(
    val filter: ArchiveFilter,
    val query: String,
    val sortOrder: SortOrder
)

private data class ScanArchiveData(
    val scans: List<Document>,
    val filteredScans: List<Document>,
    val allScans: List<Document>
)

private fun buildCombinedOcrText(
    records: List<Document>,
    results: List<OcrDocumentResult>
): String {
    val filenamesById = records.associateBy({ it.id }, { it.filename })
    return results
        .filter { it.fullText.isNotBlank() }
        .joinToString("\n\n") { result ->
            val filename = filenamesById[result.recordId]
            if (results.size > 1 && filename != null) {
                "— $filename —\n${result.fullText}"
            } else {
                result.fullText
            }
        }
}

private fun AppSortOrder.toUiSortOrder(): SortOrder = when (this) {
    AppSortOrder.BY_DATE -> SortOrder.ByDate
    AppSortOrder.BY_NAME -> SortOrder.ByName
    AppSortOrder.BY_SIZE -> SortOrder.BySize
}

private fun SortOrder.toAppSortOrder(): AppSortOrder = when (this) {
    SortOrder.ByDate -> AppSortOrder.BY_DATE
    SortOrder.ByName -> AppSortOrder.BY_NAME
    SortOrder.BySize -> AppSortOrder.BY_SIZE
}

internal fun sortScans(scans: List<Document>, sortOrder: SortOrder): List<Document> {
    val byName = compareBy<Document>(
        { it.filename.lowercase(Locale.ROOT) },
        { it.filename },
        { it.id }
    )

    return when (sortOrder) {
        SortOrder.ByDate -> scans.sortedWith(
            compareByDescending<Document> { it.timestamp }
                .then(byName)
        )

        SortOrder.ByName -> scans.sortedWith(
            byName.thenByDescending { it.timestamp }
        )

        SortOrder.BySize -> scans.sortedWith(
            compareByDescending<Document> { it.fileSize }
                .then(byName)
        )
    }
}
