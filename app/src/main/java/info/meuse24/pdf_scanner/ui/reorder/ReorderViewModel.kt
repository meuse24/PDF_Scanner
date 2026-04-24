package info.meuse24.pdf_scanner.ui.reorder

import android.graphics.Bitmap
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import info.meuse24.pdf_scanner.domain.model.Document
import info.meuse24.pdf_scanner.domain.pdf.PdfRenderingOps
import info.meuse24.pdf_scanner.domain.repository.DocumentRepository
import info.meuse24.pdf_scanner.domain.workflow.ReorderPagesWorkflow
import info.meuse24.pdf_scanner.domain.workflow.WorkflowErrorMapper
import info.meuse24.pdf_scanner.domain.workflow.WorkflowResult
import info.meuse24.pdf_scanner.util.DispatcherProvider
import info.meuse24.pdf_scanner.util.StorageProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class ReorderPage(
    val originalIndex: Int,
    val bitmap: Bitmap? = null,
    val isLoaded: Boolean = false
)

@HiltViewModel
class ReorderViewModel @Inject constructor(
    private val repository: DocumentRepository,
    private val pdfEditor: PdfRenderingOps,
    private val reorderPagesWorkflow: ReorderPagesWorkflow,
    private val errorMapper: WorkflowErrorMapper,
    private val storageProvider: StorageProvider,
    private val dispatcherProvider: DispatcherProvider,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val scanId: Long = checkNotNull(savedStateHandle["scanId"])

    private val _record = MutableStateFlow<Document?>(null)
    val record: StateFlow<Document?> = _record.asStateFlow()

    private val _pages = MutableStateFlow<List<ReorderPage>>(emptyList())
    val pages: StateFlow<List<ReorderPage>> = _pages.asStateFlow()

    private val _saveAsCopy = MutableStateFlow(false)
    val saveAsCopy: StateFlow<Boolean> = _saveAsCopy.asStateFlow()

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

    private fun initPages(record: Document) {
        val count = record.pageCount.coerceAtLeast(1)
        _pages.value = List(count) { ReorderPage(it) }
        loadThumbnails(record, count)
    }

    private fun loadThumbnails(record: Document, count: Int) {
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
                    current[i] = ReorderPage(i, bmp, isLoaded = true)
                    _pages.value = current
                }
            }
            _loading.value = false
        }
    }

    fun setSaveAsCopy(value: Boolean) {
        _saveAsCopy.value = value
    }

    /** Verschiebt ein Item von Position [from] nach Position [to]. */
    fun movePage(from: Int, to: Int) {
        if (from == to) return
        val list = _pages.value.toMutableList()
        if (from !in list.indices || to !in list.indices) return
        val item = list.removeAt(from)
        list.add(to, item)
        _pages.value = list
    }

    /** Verschiebt eine Seite um eine Position nach oben (kleinerer Index). */
    fun movePageUp(index: Int) = movePage(index, (index - 1).coerceAtLeast(0))

    /** Verschiebt eine Seite um eine Position nach unten (größerer Index). */
    fun movePageDown(index: Int) = movePage(index, (index + 1).coerceAtMost(_pages.value.lastIndex))

    /** Gibt die neue Seitenreihenfolge als Liste von Original-Indizes zurück. */
    fun getCurrentOrder(): List<Int> = _pages.value.map { it.originalIndex }

    fun reorderPages() {
        val record = _record.value ?: return
        if (_editLoading.value) return
        _editLoading.value = true
        viewModelScope.launch(dispatcherProvider.io) {
            try {
                when (val result = reorderPagesWorkflow(record, getCurrentOrder(), _saveAsCopy.value, scansDir)) {
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

