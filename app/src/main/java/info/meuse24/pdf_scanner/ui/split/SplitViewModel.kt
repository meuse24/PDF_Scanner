package info.meuse24.pdf_scanner.ui.split

import android.graphics.Bitmap
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import info.meuse24.pdf_scanner.data.local.ScanRecord
import info.meuse24.pdf_scanner.data.repository.ScanRepository
import info.meuse24.pdf_scanner.domain.workflow.SplitPdfWorkflow
import info.meuse24.pdf_scanner.domain.workflow.WorkflowErrorMapper
import info.meuse24.pdf_scanner.domain.workflow.WorkflowResult
import info.meuse24.pdf_scanner.util.DispatcherProvider
import info.meuse24.pdf_scanner.util.PdfEditor
import info.meuse24.pdf_scanner.util.StorageProvider
import info.meuse24.pdf_scanner.util.buildRanges
import info.meuse24.pdf_scanner.util.normalizeSplitPoints
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class PageThumb(
    val index: Int,
    val bitmap: Bitmap? = null,
    val isLoaded: Boolean = false
)

@HiltViewModel
class SplitViewModel @Inject constructor(
    private val repository: ScanRepository,
    private val pdfEditor: PdfEditor,
    private val splitPdfWorkflow: SplitPdfWorkflow,
    private val errorMapper: WorkflowErrorMapper,
    private val storageProvider: StorageProvider,
    private val dispatcherProvider: DispatcherProvider,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val scanId: Long = checkNotNull(savedStateHandle["scanId"])

    private val _record = MutableStateFlow<ScanRecord?>(null)
    val record: StateFlow<ScanRecord?> = _record.asStateFlow()

    private val _pages = MutableStateFlow<List<PageThumb>>(emptyList())
    val pages: StateFlow<List<PageThumb>> = _pages.asStateFlow()

    private val _splitPoints = MutableStateFlow<Set<Int>>(emptySet())
    val splitPoints: StateFlow<Set<Int>> = _splitPoints.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _editLoading = MutableStateFlow(false)
    val editLoading: StateFlow<Boolean> = _editLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _success = MutableStateFlow(false)
    val success: StateFlow<Boolean> = _success.asStateFlow()

    private val scansDir get() = storageProvider.scansDir()

    init {
        loadRecord()
    }

    private fun loadRecord() {
        viewModelScope.launch {
            repository.getAllScans().collect { scans ->
                val found = scans.find { it.id == scanId }
                if (found != null && _record.value == null) {
                    _record.value = found
                    initPages(found)
                }
            }
        }
    }

    private fun initPages(record: ScanRecord) {
        val count = record.pageCount.coerceAtLeast(1)
        _pages.value = List(count) { PageThumb(it) }
        loadThumbnails(record, count)
    }

    private fun loadThumbnails(record: ScanRecord, count: Int) {
        viewModelScope.launch(dispatcherProvider.io) {
            val pdfFile = File(record.filepath)
            if (!pdfFile.exists()) {
                _loading.value = false
                return@launch
            }
            for (i in 0 until count) {
                val bmp = pdfEditor.renderPageThumbnail(pdfFile, i, 120)
                val current = _pages.value.toMutableList()
                if (i < current.size) {
                    current[i] = PageThumb(i, bmp, isLoaded = true)
                    _pages.value = current
                }
            }
            _loading.value = false
        }
    }

    fun toggleSplitPoint(afterIndex: Int) {
        val current = _splitPoints.value.toMutableSet()
        if (current.contains(afterIndex)) current.remove(afterIndex)
        else current.add(afterIndex)
        _splitPoints.value = current
    }

    /** Gibt lesbare Vorschau zurück, z.B. "1–2 | 3–5 | 6" */
    fun computePreview(): String {
        val pageCount = _record.value?.pageCount ?: return ""
        val sorted = normalizeSplitPoints(pageCount, _splitPoints.value.toList())
        return buildRanges(pageCount, sorted).joinToString(" | ") { range ->
            val from = range.first + 1
            val to = range.last + 1
            if (from == to) "$from" else "$from\u2013$to"
        }
    }

    /** Gibt die sortierten Split-Punkte zurück (0-basiert). */
    fun getSplitPoints(): List<Int> {
        val pageCount = _record.value?.pageCount ?: return emptyList()
        return normalizeSplitPoints(pageCount, _splitPoints.value.toList())
    }

    fun splitPdf(splitAtPages: List<Int>) {
        val record = _record.value ?: return
        if (_editLoading.value) return
        _editLoading.value = true
        viewModelScope.launch(dispatcherProvider.io) {
            try {
                when (val result = splitPdfWorkflow(record, splitAtPages, scansDir)) {
                    is WorkflowResult.Success -> _success.value = true
                    is WorkflowResult.Failure -> _error.value = errorMapper.map(result.error)
                }
            } finally {
                _editLoading.value = false
            }
        }
    }

    fun clearError() { _error.value = null }

    override fun onCleared() {
        super.onCleared()
        _pages.value.forEach { it.bitmap?.recycle() }
    }
}
