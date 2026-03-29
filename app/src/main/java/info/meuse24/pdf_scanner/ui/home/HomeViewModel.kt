@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class, kotlinx.coroutines.FlowPreview::class)

package info.meuse24.pdf_scanner.ui.home

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import info.meuse24.pdf_scanner.R
import info.meuse24.pdf_scanner.data.local.ScanRecord
import info.meuse24.pdf_scanner.data.repository.ScanRepository
import info.meuse24.pdf_scanner.domain.workflow.MakeSearchableWorkflow
import info.meuse24.pdf_scanner.domain.workflow.MergePdfsWorkflow
import info.meuse24.pdf_scanner.domain.workflow.ScanWorkflowError
import info.meuse24.pdf_scanner.domain.workflow.WorkflowResult
import info.meuse24.pdf_scanner.domain.workflow.WorkflowErrorMapper
import info.meuse24.pdf_scanner.domain.usecase.DeleteScansUseCase
import info.meuse24.pdf_scanner.domain.usecase.ExportAsJpgUseCase
import info.meuse24.pdf_scanner.domain.usecase.ExportScanUseCase
import info.meuse24.pdf_scanner.domain.usecase.ExtractTextUseCase
import info.meuse24.pdf_scanner.domain.usecase.ImportFileUseCase
import info.meuse24.pdf_scanner.domain.usecase.ImportScanUseCase
import info.meuse24.pdf_scanner.util.DispatcherProvider
import info.meuse24.pdf_scanner.util.ResourceProvider
import info.meuse24.pdf_scanner.util.StorageProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository:          ScanRepository,
    private val importScanUseCase:   ImportScanUseCase,
    private val importFileUseCase:   ImportFileUseCase,
    private val exportScanUseCase:   ExportScanUseCase,
    private val exportAsJpgUseCase:  ExportAsJpgUseCase,
    private val deleteScansUseCase:  DeleteScansUseCase,
    private val extractTextUseCase:  ExtractTextUseCase,
    private val makeSearchableWorkflow: MakeSearchableWorkflow,
    private val mergePdfsWorkflow:   MergePdfsWorkflow,
    private val workflowErrorMapper: WorkflowErrorMapper,
    private val resourceProvider: ResourceProvider,
    private val storageProvider: StorageProvider,
    private val dispatcherProvider: DispatcherProvider
) : ViewModel() {

    val scans: StateFlow<List<ScanRecord>> = repository.getAllScans()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _sortOrder = MutableStateFlow(SortOrder.ByDate)
    val sortOrder: StateFlow<SortOrder> = _sortOrder.asStateFlow()

    private val searchResults = _searchQuery
        .debounce(300)
        .flatMapLatest { raw ->
            val query = sanitizeFtsQuery(raw)
            if (query.isBlank()) repository.getAllScans()
            else repository.searchScansFlow(query)
        }

    val filteredScans: StateFlow<List<ScanRecord>> = combine(searchResults, _sortOrder) { scans, sortOrder ->
        sortScans(scans, sortOrder)
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _success = MutableStateFlow<String?>(null)
    val success: StateFlow<String?> = _success.asStateFlow()

    private val _ocrText = MutableStateFlow<String?>(null)
    val ocrText: StateFlow<String?> = _ocrText.asStateFlow()

    private val _ocrLoading = MutableStateFlow(false)
    val ocrLoading: StateFlow<Boolean> = _ocrLoading.asStateFlow()

    /** (aktuelleSeite, gesamtSeiten) während makeSearchable; null sonst */
    private val _ocrProgress = MutableStateFlow<Pair<Int, Int>?>(null)
    val ocrProgress: StateFlow<Pair<Int, Int>?> = _ocrProgress.asStateFlow()

    /** true während merge Operationen */
    private val _editLoading = MutableStateFlow(false)
    val editLoading: StateFlow<Boolean> = _editLoading.asStateFlow()

    /** Temporäre URI-Bridge für den ImagesToPdf-Screen (Android-URIs sind nicht als Nav-Args serialisierbar). */
    private val _pendingImageUris = MutableStateFlow<List<Uri>>(emptyList())
    val pendingImageUris: StateFlow<List<Uri>> = _pendingImageUris.asStateFlow()

    fun setPendingImageUris(uris: List<Uri>) { _pendingImageUris.value = uris }
    fun clearPendingImageUris() { _pendingImageUris.value = emptyList() }

    private val scansDir get() = storageProvider.scansDir()

    // ── Scan importieren ──────────────────────────────────────────────────────

    fun saveScan(
        pdfUri:         Uri,
        pageCount:      Int,
        filename:       String,
        thumbnailUri:   Uri?    = null,
        makeSearchable: Boolean = false,
        languageCode:   String  = Locale.getDefault().language
    ) {
        viewModelScope.launch(dispatcherProvider.io) {
            try {
                if (makeSearchable) _ocrLoading.value = true
                importScanUseCase(
                    pdfUri, pageCount, filename, thumbnailUri, makeSearchable, languageCode
                ) { cur, tot -> _ocrProgress.value = cur to tot }
            } catch (e: Exception) {
                _error.value = e.message ?: resourceProvider.getString(R.string.error_save_failed)
            } finally {
                _ocrLoading.value  = false
                _ocrProgress.value = null
            }
        }
    }

    fun importFile(pdfUri: Uri, filename: String) {
        if (_editLoading.value) return
        _editLoading.value = true
        viewModelScope.launch(dispatcherProvider.io) {
            try {
                importFileUseCase(pdfUri, filename)
            } catch (e: Exception) {
                _error.value = e.message ?: resourceProvider.getString(R.string.error_save_failed)
            } finally {
                _editLoading.value = false
            }
        }
    }

    // ── Löschen ───────────────────────────────────────────────────────────────

    fun deleteScan(record: ScanRecord) = deleteScans(listOf(record))

    fun deleteScans(records: List<ScanRecord>) {
        viewModelScope.launch(dispatcherProvider.io) {
            val allDeleted = deleteScansUseCase(records)
            if (!allDeleted) _error.value = resourceProvider.getString(R.string.error_delete_failed)
        }
    }

    // ── Exportieren ───────────────────────────────────────────────────────────

    fun exportScan(record: ScanRecord) {
        viewModelScope.launch(dispatcherProvider.io) {
            try {
                val displayName = exportScanUseCase(record)
                _success.value = resourceProvider.getString(R.string.export_success, displayName)
            } catch (e: Exception) {
                _error.value = resourceProvider.getString(R.string.error_export_failed)
            }
        }
    }

    // ── Als JPEG exportieren ──────────────────────────────────────────────────

    fun exportAsJpg(record: ScanRecord) {
        viewModelScope.launch(dispatcherProvider.io) {
            try {
                val count = exportAsJpgUseCase(record)
                _success.value = if (count == 1) {
                    resourceProvider.getString(R.string.export_jpg_success, record.filename)
                } else {
                    resourceProvider.getString(R.string.export_jpg_success_multi, count)
                }
            } catch (e: Exception) {
                _error.value = resourceProvider.getString(R.string.error_export_jpg_failed)
            }
        }
    }

    // ── OCR – Text extrahieren ────────────────────────────────────────────────

    fun extractText(record: ScanRecord, languageCode: String = Locale.getDefault().language) {
        extractTexts(listOf(record), languageCode)
    }

    fun extractTexts(records: List<ScanRecord>, languageCode: String = Locale.getDefault().language) {
        if (_ocrLoading.value) return
        val validRecords = records.filter { File(it.filepath).exists() || it.thumbnailPath != null }
        if (validRecords.isEmpty()) {
            _error.value = resourceProvider.getString(R.string.ocr_no_image)
            return
        }
        _ocrLoading.value = true
        viewModelScope.launch(dispatcherProvider.io) {
            try {
                _ocrText.value = extractTextUseCase(validRecords, languageCode)
            } catch (e: Exception) {
                _error.value = resourceProvider.getString(R.string.ocr_failed)
            } finally {
                _ocrLoading.value = false
            }
        }
    }

    // ── Durchsuchbar machen ───────────────────────────────────────────────────

    fun makeSearchableScans(records: List<ScanRecord>, languageCode: String) {
        if (_ocrLoading.value) return
        _ocrLoading.value = true
        viewModelScope.launch(dispatcherProvider.io) {
            try {
                when (val result = makeSearchableWorkflow(records, languageCode) { cur, tot ->
                    _ocrProgress.value = cur to tot
                }) {
                    is WorkflowResult.Success -> {
                        val data = result.value
                        _success.value = if (data.processedCount == 1) {
                            resourceProvider.getString(R.string.searchable_success, data.firstFilename)
                        } else {
                            resourceProvider.getString(R.string.searchable_success_multi, data.processedCount)
                        }
                    }
                    is WorkflowResult.Failure -> handleWorkflowFailure("SearchablePDF", result.error)
                }
            } finally {
                _ocrLoading.value  = false
                _ocrProgress.value = null
            }
        }
    }

    // ── PDF-Bearbeitung: Merge ────────────────────────────────────────────────

    fun mergePdfs(records: List<ScanRecord>, outputFilename: String) {
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

    // ── Hilfsmethoden ─────────────────────────────────────────────────────────

    fun renameScan(record: ScanRecord, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch(dispatcherProvider.io) {
            val scansDir = storageProvider.scansDir()
            val newFile  = File(scansDir, "$trimmed.pdf")
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
                val newThumb  = File(scansDir, "$trimmed.jpg")
                val renamed   = !thumbFile.exists() || thumbFile.renameTo(newThumb)
                if (renamed) newThumb.absolutePath else oldThumb
            }
            repository.updateFilenameAndPath(record.id, trimmed, newFile.absolutePath, newThumbPath)
            _success.value = resourceProvider.getString(R.string.rename_success, trimmed)
        }
    }

    fun updateSearchQuery(query: String) { _searchQuery.value = query }
    fun setSortOrder(sortOrder: SortOrder) { _sortOrder.value = sortOrder }

    fun clearOcrText() { _ocrText.value = null }
    fun reportError(message: String) { _error.value = message }
    fun clearError() { _error.value = null }
    fun clearSuccess() { _success.value = null }

    private fun sanitizeFtsQuery(raw: String): String {
        return raw.trim()
            .filter { it.isLetterOrDigit() || it.isWhitespace() }
            .split("\\s+".toRegex())
            .filter { it.isNotBlank() }
            .joinToString(" ") { "$it*" }
    }

    private fun handleWorkflowFailure(tag: String, error: ScanWorkflowError) {
        Log.e(tag, "workflow failed: $error", error.cause)
        _error.value = workflowErrorMapper.map(error)
    }
}

internal fun sortScans(scans: List<ScanRecord>, sortOrder: SortOrder): List<ScanRecord> {
    val byName = compareBy<ScanRecord>(
        { it.filename.lowercase(Locale.ROOT) },
        { it.filename },
        { it.id }
    )

    return when (sortOrder) {
        SortOrder.ByDate -> scans.sortedWith(
            compareByDescending<ScanRecord> { it.timestamp }
                .then(byName)
        )
        SortOrder.ByName -> scans.sortedWith(
            byName.thenByDescending { it.timestamp }
        )
        SortOrder.BySize -> scans.sortedWith(
            compareByDescending<ScanRecord> { it.fileSize }
                .then(byName)
        )
    }
}


