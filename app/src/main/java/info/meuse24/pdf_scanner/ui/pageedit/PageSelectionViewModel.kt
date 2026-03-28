package info.meuse24.pdf_scanner.ui.pageedit

import android.graphics.Bitmap
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import info.meuse24.pdf_scanner.data.local.ScanRecord
import info.meuse24.pdf_scanner.data.repository.ScanRepository
import info.meuse24.pdf_scanner.domain.workflow.DeletePagesWorkflow
import info.meuse24.pdf_scanner.domain.workflow.DuplicatePagesWorkflow
import info.meuse24.pdf_scanner.domain.workflow.ExtractPagesWorkflow
import info.meuse24.pdf_scanner.domain.workflow.RotatePagesWorkflow
import info.meuse24.pdf_scanner.domain.workflow.WorkflowErrorMapper
import info.meuse24.pdf_scanner.domain.workflow.WorkflowResult
import info.meuse24.pdf_scanner.util.DispatcherProvider
import info.meuse24.pdf_scanner.util.PdfEditor
import info.meuse24.pdf_scanner.util.StorageProvider
import info.meuse24.pdf_scanner.util.normalizePageIndexes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class SelectablePage(
    val index: Int,
    val bitmap: Bitmap? = null,
    val isLoaded: Boolean = false
)

@HiltViewModel
class PageSelectionViewModel @Inject constructor(
    private val repository: ScanRepository,
    private val pdfEditor: PdfEditor,
    private val rotatePagesWorkflow: RotatePagesWorkflow,
    private val deletePagesWorkflow: DeletePagesWorkflow,
    private val extractPagesWorkflow: ExtractPagesWorkflow,
    private val duplicatePagesWorkflow: DuplicatePagesWorkflow,
    private val errorMapper: WorkflowErrorMapper,
    private val storageProvider: StorageProvider,
    private val dispatcherProvider: DispatcherProvider,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val scanId: Long = checkNotNull(savedStateHandle["scanId"])

    private val _record = MutableStateFlow<ScanRecord?>(null)
    val record: StateFlow<ScanRecord?> = _record.asStateFlow()

    private val _pages = MutableStateFlow<List<SelectablePage>>(emptyList())
    val pages: StateFlow<List<SelectablePage>> = _pages.asStateFlow()

    private val _selectedPages = MutableStateFlow<Set<Int>>(emptySet())
    val selectedPages: StateFlow<Set<Int>> = _selectedPages.asStateFlow()

    private val _saveAsCopy = MutableStateFlow(true)
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

    private fun initPages(record: ScanRecord) {
        val count = record.pageCount.coerceAtLeast(1)
        _pages.value = List(count) { SelectablePage(index = it) }
        loadThumbnails(record, count)
    }

    private fun loadThumbnails(record: ScanRecord, count: Int) {
        viewModelScope.launch(dispatcherProvider.io) {
            val pdfFile = File(record.filepath)
            if (!pdfFile.exists()) {
                _loading.value = false
                return@launch
            }
            repeat(count) { pageIndex ->
                val bitmap = pdfEditor.renderPageThumbnail(pdfFile, pageIndex, 120)
                val current = _pages.value.toMutableList()
                if (pageIndex < current.size) {
                    current[pageIndex] = SelectablePage(pageIndex, bitmap, isLoaded = true)
                    _pages.value = current
                }
            }
            _loading.value = false
        }
    }

    fun togglePage(index: Int) {
        val current = _selectedPages.value.toMutableSet()
        if (index in current) current.remove(index) else current.add(index)
        _selectedPages.value = current
    }

    fun setSaveAsCopy(value: Boolean) {
        _saveAsCopy.value = value
    }

    fun getSelectedPages(): List<Int> {
        val pageCount = _record.value?.pageCount ?: return emptyList()
        return normalizePageIndexes(pageCount, _selectedPages.value.toList())
    }

    fun rotatePages(rotationDegrees: Int) {
        val record = _record.value ?: return
        if (_editLoading.value) return
        _editLoading.value = true
        viewModelScope.launch(dispatcherProvider.io) {
            try {
                when (val result = rotatePagesWorkflow(record, getSelectedPages(), rotationDegrees, _saveAsCopy.value, scansDir)) {
                    is WorkflowResult.Success -> _success.value = true
                    is WorkflowResult.Failure -> _error.value = errorMapper.map(result.error)
                }
            } finally {
                _editLoading.value = false
            }
        }
    }

    fun deletePages() {
        val record = _record.value ?: return
        if (_editLoading.value) return
        _editLoading.value = true
        viewModelScope.launch(dispatcherProvider.io) {
            try {
                when (val result = deletePagesWorkflow(record, getSelectedPages(), _saveAsCopy.value, scansDir)) {
                    is WorkflowResult.Success -> _success.value = true
                    is WorkflowResult.Failure -> _error.value = errorMapper.map(result.error)
                }
            } finally {
                _editLoading.value = false
            }
        }
    }

    fun extractPages() {
        val record = _record.value ?: return
        if (_editLoading.value) return
        _editLoading.value = true
        viewModelScope.launch(dispatcherProvider.io) {
            try {
                when (val result = extractPagesWorkflow(record, getSelectedPages(), scansDir)) {
                    is WorkflowResult.Success -> _success.value = true
                    is WorkflowResult.Failure -> _error.value = errorMapper.map(result.error)
                }
            } finally {
                _editLoading.value = false
            }
        }
    }

    fun duplicatePages() {
        val record = _record.value ?: return
        if (_editLoading.value) return
        _editLoading.value = true
        viewModelScope.launch(dispatcherProvider.io) {
            try {
                when (val result = duplicatePagesWorkflow(record, getSelectedPages(), scansDir)) {
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
