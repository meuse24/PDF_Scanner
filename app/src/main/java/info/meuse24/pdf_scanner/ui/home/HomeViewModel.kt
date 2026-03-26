@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class, kotlinx.coroutines.FlowPreview::class)

package info.meuse24.pdf_scanner.ui.home

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import info.meuse24.pdf_scanner.R
import info.meuse24.pdf_scanner.data.local.ScanRecord
import info.meuse24.pdf_scanner.data.repository.ScanRepository
import info.meuse24.pdf_scanner.domain.workflow.MakeSearchableWorkflow
import info.meuse24.pdf_scanner.domain.workflow.MergePdfsWorkflow
import info.meuse24.pdf_scanner.domain.workflow.ScanWorkflowError
import info.meuse24.pdf_scanner.domain.workflow.WorkflowResult
import info.meuse24.pdf_scanner.domain.usecase.DeleteScansUseCase
import info.meuse24.pdf_scanner.domain.usecase.ExportAsJpgUseCase
import info.meuse24.pdf_scanner.domain.usecase.ExportScanUseCase
import info.meuse24.pdf_scanner.domain.usecase.ExtractTextUseCase
import info.meuse24.pdf_scanner.domain.usecase.ImportScanUseCase
import kotlinx.coroutines.Dispatchers
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
    private val exportScanUseCase:   ExportScanUseCase,
    private val exportAsJpgUseCase:  ExportAsJpgUseCase,
    private val deleteScansUseCase:  DeleteScansUseCase,
    private val extractTextUseCase:  ExtractTextUseCase,
    private val makeSearchableWorkflow: MakeSearchableWorkflow,
    private val mergePdfsWorkflow:   MergePdfsWorkflow,
    @ApplicationContext private val context: Context
) : ViewModel() {

    val scans: StateFlow<List<ScanRecord>> = repository.getAllScans()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val filteredScans: StateFlow<List<ScanRecord>> = _searchQuery
        .debounce(300)
        .flatMapLatest { raw ->
            val query = sanitizeFtsQuery(raw)
            if (query.isBlank()) repository.getAllScans()
            else repository.searchScansFlow(query)
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

    private val scansDir get() = File(context.filesDir, "scans").apply { mkdirs() }

    // ── Scan importieren ──────────────────────────────────────────────────────

    fun saveScan(
        pdfUri:         Uri,
        pageCount:      Int,
        filename:       String,
        thumbnailUri:   Uri?    = null,
        makeSearchable: Boolean = false,
        languageCode:   String  = Locale.getDefault().language
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (makeSearchable) _ocrLoading.value = true
                importScanUseCase(
                    pdfUri, pageCount, filename, thumbnailUri, makeSearchable, languageCode
                ) { cur, tot -> _ocrProgress.value = cur to tot }
            } catch (e: Exception) {
                _error.value = e.message ?: context.getString(R.string.error_save_failed)
            } finally {
                _ocrLoading.value  = false
                _ocrProgress.value = null
            }
        }
    }

    // ── Löschen ───────────────────────────────────────────────────────────────

    fun deleteScan(record: ScanRecord) = deleteScans(listOf(record))

    fun deleteScans(records: List<ScanRecord>) {
        viewModelScope.launch(Dispatchers.IO) {
            val allDeleted = deleteScansUseCase(records)
            if (!allDeleted) _error.value = context.getString(R.string.error_delete_failed)
        }
    }

    // ── Exportieren ───────────────────────────────────────────────────────────

    fun exportScan(record: ScanRecord) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val displayName = exportScanUseCase(record)
                _success.value = context.getString(R.string.export_success, displayName)
            } catch (e: Exception) {
                _error.value = context.getString(R.string.error_export_failed)
            }
        }
    }

    // ── Als JPEG exportieren ──────────────────────────────────────────────────

    fun exportAsJpg(record: ScanRecord) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val count = exportAsJpgUseCase(record)
                _success.value = if (count == 1) {
                    context.getString(R.string.export_jpg_success, record.filename)
                } else {
                    context.getString(R.string.export_jpg_success_multi, count)
                }
            } catch (e: Exception) {
                _error.value = context.getString(R.string.error_export_jpg_failed)
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
            _error.value = context.getString(R.string.ocr_no_image)
            return
        }
        _ocrLoading.value = true
        viewModelScope.launch {
            try {
                _ocrText.value = extractTextUseCase(validRecords, languageCode)
            } catch (e: Exception) {
                _error.value = context.getString(R.string.ocr_failed)
            } finally {
                _ocrLoading.value = false
            }
        }
    }

    // ── Durchsuchbar machen ───────────────────────────────────────────────────

    fun makeSearchableScans(records: List<ScanRecord>, languageCode: String) {
        if (_ocrLoading.value) return
        _ocrLoading.value = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                when (val result = makeSearchableWorkflow(records, languageCode) { cur, tot ->
                    _ocrProgress.value = cur to tot
                }) {
                    is WorkflowResult.Success -> {
                        val data = result.value
                        _success.value = if (data.processedCount == 1) {
                            context.getString(R.string.searchable_success, data.firstFilename)
                        } else {
                            context.getString(R.string.searchable_success_multi, data.processedCount)
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
        viewModelScope.launch(Dispatchers.IO) {
            try {
                when (val result = mergePdfsWorkflow(records, outputFilename, scansDir)) {
                    is WorkflowResult.Success -> {
                        _success.value = context.getString(
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

    fun updateSearchQuery(query: String) { _searchQuery.value = query }

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
        _error.value = mapWorkflowError(error)
    }

    private fun mapWorkflowError(error: ScanWorkflowError): String = when (error) {
        ScanWorkflowError.NothingSelected -> context.getString(R.string.workflow_nothing_selected)
        ScanWorkflowError.NotEnoughScans -> context.getString(R.string.merge_not_enough_scans)
        ScanWorkflowError.NoEligibleScans -> context.getString(R.string.searchable_nothing_to_do)
        ScanWorkflowError.InvalidSplitSelection -> context.getString(R.string.split_no_points)
        ScanWorkflowError.InvalidPageSelection -> context.getString(R.string.page_selection_invalid)
        ScanWorkflowError.InvalidPageOrder -> context.getString(R.string.reorder_invalid_order)
        ScanWorkflowError.InvalidWatermarkText -> context.getString(R.string.watermark_invalid)
        ScanWorkflowError.SignatureRequired -> context.getString(R.string.signature_required)
        ScanWorkflowError.InvalidSignatureScale -> context.getString(R.string.signature_scale_invalid)
        ScanWorkflowError.CompressionUnsupportedForSearchablePdf -> context.getString(R.string.compress_pdf_searchable_unsupported)
        ScanWorkflowError.ProtectedPdfUnsupported -> context.getString(R.string.protected_pdf_unsupported)
        ScanWorkflowError.PasswordRequired -> context.getString(R.string.password_required)
        ScanWorkflowError.WrongPassword -> context.getString(R.string.password_wrong)
        ScanWorkflowError.AlreadyProtected -> context.getString(R.string.protect_pdf_already_protected)
        ScanWorkflowError.NotProtected -> context.getString(R.string.unlock_pdf_not_protected)
        ScanWorkflowError.CannotDeleteAllPages -> context.getString(R.string.delete_pages_all_error)
        is ScanWorkflowError.MissingFiles -> context.getString(R.string.workflow_missing_files)
        is ScanWorkflowError.StorageWriteFailed -> context.getString(R.string.workflow_storage_failed)
        is ScanWorkflowError.OcrFailed -> context.getString(R.string.searchable_failed)
        is ScanWorkflowError.MergeFailed -> context.getString(R.string.merge_error)
        is ScanWorkflowError.SplitFailed -> context.getString(R.string.split_error)
        is ScanWorkflowError.ReorderFailed -> context.getString(R.string.reorder_error)
        is ScanWorkflowError.RotateFailed -> context.getString(R.string.rotate_error)
        is ScanWorkflowError.DeletePagesFailed -> context.getString(R.string.delete_pages_error)
        is ScanWorkflowError.ExtractPagesFailed -> context.getString(R.string.extract_pages_error)
        is ScanWorkflowError.DuplicatePagesFailed -> context.getString(R.string.duplicate_pages_error)
        is ScanWorkflowError.PageNumbersFailed -> context.getString(R.string.page_numbers_error)
        is ScanWorkflowError.TextWatermarkFailed -> context.getString(R.string.watermark_error)
        is ScanWorkflowError.CompressionFailed -> context.getString(R.string.compress_pdf_error)
        is ScanWorkflowError.ProtectFailed -> context.getString(R.string.protect_pdf_error)
        is ScanWorkflowError.UnlockFailed -> context.getString(R.string.unlock_pdf_error)
        is ScanWorkflowError.SignatureFailed -> context.getString(R.string.signature_error)
        ScanWorkflowError.NotSearchable -> context.getString(R.string.remove_text_layer_not_searchable)
        is ScanWorkflowError.RemoveTextLayerFailed -> context.getString(R.string.remove_text_layer_error)
        ScanWorkflowError.PasswordRequiredToRemove -> context.getString(R.string.remove_password_requires_input)
        is ScanWorkflowError.RemovePasswordFailed -> context.getString(R.string.remove_password_error)
        is ScanWorkflowError.UsageRestrictionFailed -> context.getString(R.string.restrict_usage_error)
        ScanWorkflowError.NoHighlightStrokes -> context.getString(R.string.highlight_no_strokes)
        is ScanWorkflowError.HighlightFailed -> context.getString(R.string.highlight_error)
        ScanWorkflowError.NoAnnotations -> context.getString(R.string.annotate_no_items)
        is ScanWorkflowError.AnnotateFailed -> context.getString(R.string.annotate_error)
    }
}
