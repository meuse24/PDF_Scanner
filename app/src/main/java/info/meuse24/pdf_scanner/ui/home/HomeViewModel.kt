@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class, kotlinx.coroutines.FlowPreview::class)

package info.meuse24.pdf_scanner.ui.home

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
import info.meuse24.pdf_scanner.domain.usecase.ExportAsJpgUseCase
import info.meuse24.pdf_scanner.domain.usecase.ExportScanUseCase
import info.meuse24.pdf_scanner.domain.usecase.ExtractTextUseCase
import info.meuse24.pdf_scanner.domain.usecase.ImportFileUseCase
import info.meuse24.pdf_scanner.domain.usecase.ImportScanUseCase
import info.meuse24.pdf_scanner.domain.usecase.MoveDocumentsUseCase
import info.meuse24.pdf_scanner.domain.usecase.OcrDocumentResult
import info.meuse24.pdf_scanner.domain.usecase.OcrNoTextException
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
import info.meuse24.pdf_scanner.util.AppSortOrder
import info.meuse24.pdf_scanner.util.DispatcherProvider
import info.meuse24.pdf_scanner.util.OcrModelInstallException
import info.meuse24.pdf_scanner.util.OcrPipelineStatus
import info.meuse24.pdf_scanner.util.OcrResultStats
import info.meuse24.pdf_scanner.util.OcrThresholds
import info.meuse24.pdf_scanner.util.ResourceProvider
import info.meuse24.pdf_scanner.util.StorageProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
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
    private val trashScansUseCase: TrashScansUseCase,
    private val restoreScansUseCase: RestoreScansUseCase,
    private val moveDocumentsUseCase: MoveDocumentsUseCase,
    private val toggleFavoriteUseCase: ToggleFavoriteUseCase,
    private val extractTextUseCase: ExtractTextUseCase,
    private val makeSearchableWorkflow: MakeSearchableWorkflow,
    private val mergePdfsWorkflow: MergePdfsWorkflow,
    private val workflowErrorMapper: WorkflowErrorMapper,
    private val resourceProvider: ResourceProvider,
    private val storageProvider: StorageProvider,
    private val dispatcherProvider: DispatcherProvider,
    private val archiveFilterStore: ArchiveFilterStore
) : ViewModel() {

    private val archiveFilterFlow = archiveFilterStore.filter
        .stateIn(viewModelScope, SharingStarted.Eagerly, ArchiveFilter.AllDocuments)

    private val scansFlow = archiveFilterFlow
        .flatMapLatest { filter ->
            when (filter) {
                ArchiveFilter.AllDocuments -> repository.getAllScans()
                ArchiveFilter.Favorites -> repository.getFavoriteScans()
                is ArchiveFilter.Folder -> repository.getScansInFolder(filter.folderId)
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val foldersFlow = folderRepository.observeFolders()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _searchQuery = MutableStateFlow("")
    private val _sortOrder = MutableStateFlow(SortOrder.ByDate)

    private val filteredScansFlow = combine(scansFlow, _searchQuery, _sortOrder) { scans, rawQuery, sortOrder ->
        val filtered = filterScans(scans, rawQuery)
        sortScans(filtered, sortOrder)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _error = MutableStateFlow<String?>(null)
    private val _success = MutableStateFlow<String?>(null)
    private val _trashMessage = MutableStateFlow<String?>(null)
    private val _lastTrashed = MutableStateFlow<List<Long>>(emptyList())

    private val _ocrText = MutableStateFlow<String?>(null)
    private val _ocrReviewRequestId = MutableStateFlow<Long?>(null)
    private val _ocrLoading = MutableStateFlow(false)
    private val _ocrProgress = MutableStateFlow<HomeOcrProgress?>(null)
    private val _ocrStatusText = MutableStateFlow<String?>(null)
    private val _editLoading = MutableStateFlow(false)

    private val _pendingImageUris = MutableStateFlow<List<Uri>>(emptyList())

    private val archiveBaseState = combine(
        settingsRepository.settings,
        scansFlow,
        filteredScansFlow,
        foldersFlow,
        archiveFilterFlow
    ) { settings, scans, filteredScans, folders, archiveFilter ->
        HomeArchiveUiState(
            settings = settings,
            scans = scans,
            filteredScans = filteredScans,
            folders = folders,
            currentFolder = if (archiveFilter is ArchiveFilter.Folder) {
                folders.firstOrNull { it.id == archiveFilter.folderId }
            } else {
                null
            },
            favoritesFilter = archiveFilter is ArchiveFilter.Favorites
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
        _editLoading
    ) { state, editLoading ->
        state.copy(editLoading = editLoading)
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

        val validRecords = records.filter { File(it.filepath).exists() || it.thumbnailPath != null }
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
        val trimmed = newName.trim()
        if (trimmed.isBlank()) return

        viewModelScope.launch(dispatcherProvider.io) {
            val targetScansDir = storageProvider.scansDir()
            val newFile = File(targetScansDir, "$trimmed.pdf")
            if (newFile.exists()) {
                _error.value = resourceProvider.getString(R.string.rename_error_exists)
                return@launch
            }

            val oldFile = File(record.filepath)
            if (!oldFile.renameTo(newFile)) {
                _error.value = resourceProvider.getString(R.string.rename_error_failed)
                return@launch
            }

            val newThumbPath = record.thumbnailPath?.let { oldThumb ->
                val thumbFile = File(oldThumb)
                val newThumb = File(targetScansDir, "$trimmed.jpg")
                val renamed = !thumbFile.exists() || thumbFile.renameTo(newThumb)
                if (renamed) newThumb.absolutePath else oldThumb
            }

            repository.updateFilenameAndPath(record.id, trimmed, newFile.absolutePath, newThumbPath)
            _success.value = resourceProvider.getString(R.string.rename_success, trimmed)
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
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
            val allScans = withTimeoutOrNull(10_000L) {
                repository.getAllScans().first()
            } ?: return@launch

            val candidates = allScans
                .filter { it.isSearchable && it.extractedText == null && File(it.filepath).exists() }
                .take(10)
            if (candidates.isEmpty()) return@launch

            for (record in candidates) {
                runCatching {
                    val result = extractTextUseCase(listOf(record), OCR_LANGUAGE_AUTO).singleOrNull()
                    if (result != null && result.fullText.isNotBlank()) {
                        repository.markSearchableWithContent(
                            id = record.id,
                            fileSize = File(record.filepath).length(),
                            text = result.fullText,
                            tags = null,
                            confidence = result.stats?.confidence,
                            language = result.stats?.recognizedLanguage,
                            pageTexts = result.pageTexts
                        )
                        Log.d("Backfill", "Text nacherfasst: ${record.filename}")
                    }
                }.onFailure { exception ->
                    Log.w("Backfill", "Nacherfassung fehlgeschlagen: ${record.filename}", exception)
                }
            }
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
}

private fun filterScans(scans: List<Document>, rawQuery: String): List<Document> {
    val query = rawQuery.trim()
    if (query.isBlank()) return scans
    val queryLower = query.lowercase(Locale.ROOT)
    return scans.filter { document ->
        document.filename.lowercase(Locale.ROOT).contains(queryLower) ||
            document.extractedText?.lowercase(Locale.ROOT)?.contains(queryLower) == true
    }
}

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
