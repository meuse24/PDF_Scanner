package info.meuse24.pdf_scanner.ui.append

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import info.meuse24.pdf_scanner.R
import info.meuse24.pdf_scanner.domain.model.Document
import info.meuse24.pdf_scanner.domain.model.PdfPageSetup
import info.meuse24.pdf_scanner.domain.repository.DocumentRepository
import info.meuse24.pdf_scanner.domain.repository.AppSettingsRepository
import info.meuse24.pdf_scanner.domain.usecase.AppendSource
import info.meuse24.pdf_scanner.domain.usecase.ImagePdfOptions
import info.meuse24.pdf_scanner.domain.workflow.AppendToPdfWorkflow
import info.meuse24.pdf_scanner.domain.workflow.WorkflowErrorMapper
import info.meuse24.pdf_scanner.domain.workflow.WorkflowResult
import info.meuse24.pdf_scanner.domain.gateway.DispatcherProvider
import info.meuse24.pdf_scanner.domain.gateway.ResourceProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AppendViewModel @Inject constructor(
    repository: DocumentRepository,
    private val appendWorkflow: AppendToPdfWorkflow,
    private val workflowErrorMapper: WorkflowErrorMapper,
    private val resourceProvider: ResourceProvider,
    private val dispatcherProvider: DispatcherProvider,
    private val settingsRepository: AppSettingsRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val scanId: Long = checkNotNull(savedStateHandle["scanId"])

    val record: StateFlow<Document?> = repository.getAllScans()
        .map { records -> records.find { it.id == scanId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _editLoading = MutableStateFlow(false)
    val editLoading: StateFlow<Boolean> = _editLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _success = MutableStateFlow<String?>(null)
    val success: StateFlow<String?> = _success.asStateFlow()

    private val _pendingImageUris = MutableStateFlow<List<Uri>>(emptyList())
    val pendingImageUris: StateFlow<List<Uri>> = _pendingImageUris.asStateFlow()

    private val _pageSetup = MutableStateFlow(settingsRepository.settings.value.defaultImagePdfPageSetup)
    val pageSetup: StateFlow<PdfPageSetup> = _pageSetup.asStateFlow()

    fun updatePageSetup(setup: PdfPageSetup) {
        _pageSetup.value = setup
        settingsRepository.updateDefaultImagePdfPageSetup(setup)
    }

    fun setPendingImageUris(uris: List<Uri>) {
        _pendingImageUris.value = uris
    }

    fun clearPendingImageUris() {
        _pendingImageUris.value = emptyList()
    }

    fun appendScan(pdfUri: Uri, pageCount: Int) {
        runAppend(AppendSource.Scan(pdfUri, pageCount))
    }

    fun appendPdf(pdfUri: Uri) {
        runAppend(AppendSource.Pdf(pdfUri))
    }

    fun appendImages(options: ImagePdfOptions) {
        val uris = pendingImageUris.value
        if (uris.isEmpty()) return
        runAppend(AppendSource.Images(uris, options)) {
            clearPendingImageUris()
        }
    }

    fun clearError() {
        _error.value = null
    }

    fun clearSuccess() {
        _success.value = null
    }

    private fun runAppend(source: AppendSource, onSuccess: () -> Unit = {}) {
        val target = record.value ?: return
        if (_editLoading.value) return
        _editLoading.value = true
        viewModelScope.launch(dispatcherProvider.io) {
            try {
                when (val result = appendWorkflow(target, source)) {
                    is WorkflowResult.Success -> {
                        onSuccess()
                        _success.value = resourceProvider.getQuantityString(
                            R.plurals.append_success,
                            result.value.appendedPageCount,
                            result.value.appendedPageCount
                        )
                    }
                    is WorkflowResult.Failure -> {
                        _error.value = workflowErrorMapper.map(result.error)
                    }
                }
            } finally {
                _editLoading.value = false
            }
        }
    }
}

