package info.meuse24.pdf_scanner.ui.formfill

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import info.meuse24.pdf_scanner.R
import info.meuse24.pdf_scanner.domain.gateway.DispatcherProvider
import info.meuse24.pdf_scanner.domain.gateway.ResourceProvider
import info.meuse24.pdf_scanner.domain.gateway.StorageProvider
import info.meuse24.pdf_scanner.domain.model.AcroFormCapability
import info.meuse24.pdf_scanner.domain.model.FormFieldValue
import info.meuse24.pdf_scanner.domain.repository.DocumentRepository
import info.meuse24.pdf_scanner.domain.usecase.ReadFormFieldsUseCase
import info.meuse24.pdf_scanner.domain.workflow.FormFillWorkflow
import info.meuse24.pdf_scanner.domain.workflow.WorkflowErrorMapper
import info.meuse24.pdf_scanner.domain.workflow.WorkflowResult
import info.meuse24.pdf_scanner.util.PdfDocumentBitmapHandle
import info.meuse24.pdf_scanner.util.PdfPageBitmapRenderer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

private const val FORM_PAGE_RENDER_WIDTH_PX = 1_600
private const val FORM_PAGE_RENDER_MAX_SIDE_PX = 2_048

@HiltViewModel
class FormFillViewModel @Inject constructor(
    repository: DocumentRepository,
    private val readFormFieldsUseCase: ReadFormFieldsUseCase,
    private val formFillWorkflow: FormFillWorkflow,
    private val pageBitmapRenderer: PdfPageBitmapRenderer,
    private val storageProvider: StorageProvider,
    private val errorMapper: WorkflowErrorMapper,
    private val resourceProvider: ResourceProvider,
    private val dispatcherProvider: DispatcherProvider,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val scanId: Long = checkNotNull(savedStateHandle["scanId"])
    private val _uiState = MutableStateFlow(FormFillUiState())
    val uiState: StateFlow<FormFillUiState> = _uiState.asStateFlow()

    private var documentHandle: PdfDocumentBitmapHandle? = null
    private var renderJob: Job? = null

    init {
        viewModelScope.launch {
            val record = repository.getAllScans().first().find { it.id == scanId }
            if (record == null) {
                _uiState.update {
                    it.copy(
                        loading = false,
                        errorMessage = resourceProvider.getString(R.string.pdf_viewer_file_missing)
                    )
                }
                return@launch
            }
            loadForm(record)
        }
    }

    fun showPage(pageIndex: Int) {
        val pageCount = _uiState.value.pageCount
        if (pageIndex !in 0 until pageCount || pageIndex == _uiState.value.currentPageIndex) return
        _uiState.update { it.copy(currentPageIndex = pageIndex, pageBitmap = null) }
        renderPage(pageIndex)
    }

    fun updateSingleValue(fieldName: String, value: String) {
        _uiState.update { state ->
            state.copy(values = state.values + (fieldName to FormFieldValue.single(value)))
        }
    }

    fun toggleListValue(fieldName: String, value: String) {
        _uiState.update { state ->
            val current = state.values[fieldName]?.values.orEmpty()
            val updated = if (value in current) current - value else current + value
            state.copy(values = state.values + (fieldName to FormFieldValue(updated)))
        }
    }

    fun setFlatten(flatten: Boolean) {
        _uiState.update { it.copy(flatten = flatten) }
    }

    fun resetValues() {
        _uiState.update { state ->
            state.copy(values = state.fields
                .distinctBy { it.fullyQualifiedName }
                .associate { it.fullyQualifiedName to it.value })
        }
    }

    fun save() {
        val state = _uiState.value
        val record = state.record ?: return
        if (state.saving || state.capability != AcroFormCapability.FILLABLE) return
        _uiState.update { it.copy(saving = true, errorMessage = null) }
        viewModelScope.launch(dispatcherProvider.io) {
            try {
                when (
                    val result = formFillWorkflow(
                        record = record,
                        fields = state.fields,
                        values = state.values,
                        flatten = state.flatten,
                        scansDir = storageProvider.scansDir()
                    )
                ) {
                    is WorkflowResult.Success ->
                        _uiState.update { it.copy(saving = false, saved = true) }
                    is WorkflowResult.Failure ->
                        _uiState.update {
                            it.copy(saving = false, errorMessage = errorMapper.map(result.error))
                        }
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (_: Exception) {
                _uiState.update {
                    it.copy(
                        saving = false,
                        errorMessage = resourceProvider.getString(R.string.form_fill_error)
                    )
                }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    override fun onCleared() {
        renderJob?.cancel()
        _uiState.value.pageBitmap?.recycle()
        documentHandle?.close()
        documentHandle = null
        super.onCleared()
    }

    private suspend fun loadForm(record: info.meuse24.pdf_scanner.domain.model.Document) {
        try {
            val result = withContext(dispatcherProvider.io) {
                readFormFieldsUseCase(File(record.filepath))
            }
            if (result.capability != AcroFormCapability.FILLABLE) {
                _uiState.update {
                    it.copy(
                        record = record,
                        capability = result.capability,
                        loading = false,
                        errorMessage = capabilityMessage(result.capability)
                    )
                }
                return
            }

            val handle = pageBitmapRenderer.openDocument(File(record.filepath))
            documentHandle = handle
            val initialValues = result.fields
                .distinctBy { it.fullyQualifiedName }
                .associate { it.fullyQualifiedName to it.value }
            _uiState.update {
                it.copy(
                    record = record,
                    capability = result.capability,
                    fields = result.fields,
                    values = initialValues,
                    pageCount = handle.pageCount,
                    loading = false,
                    errorMessage = if (result.fields.isEmpty()) {
                        resourceProvider.getString(R.string.form_fill_no_fields)
                    } else {
                        null
                    }
                )
            }
            if (result.fields.isNotEmpty()) renderPage(0)
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: Exception) {
            _uiState.update {
                it.copy(
                    record = record,
                    loading = false,
                    errorMessage = resourceProvider.getString(R.string.form_fill_error)
                )
            }
        }
    }

    private fun renderPage(pageIndex: Int) {
        val handle = documentHandle ?: return
        renderJob?.cancel()
        renderJob = viewModelScope.launch {
            try {
                val rendered = handle.renderPage(
                    pageIndex = pageIndex,
                    targetWidthPx = FORM_PAGE_RENDER_WIDTH_PX,
                    maxBitmapSidePx = FORM_PAGE_RENDER_MAX_SIDE_PX
                )
                if (_uiState.value.currentPageIndex != pageIndex) {
                    rendered.bitmap.recycle()
                    return@launch
                }
                val previous = _uiState.value.pageBitmap
                _uiState.update { it.copy(pageBitmap = rendered.bitmap) }
                if (previous !== rendered.bitmap) previous?.recycle()
            } catch (exception: CancellationException) {
                throw exception
            } catch (_: Exception) {
                _uiState.update {
                    it.copy(errorMessage = resourceProvider.getString(R.string.form_fill_error))
                }
            }
        }
    }

    private fun capabilityMessage(capability: AcroFormCapability): String = when (capability) {
        AcroFormCapability.NONE -> resourceProvider.getString(R.string.form_fill_not_fillable)
        AcroFormCapability.XFA_UNSUPPORTED ->
            resourceProvider.getString(R.string.form_fill_xfa_unsupported)
        AcroFormCapability.SIGNED_LOCKED ->
            resourceProvider.getString(R.string.form_fill_signed_locked)
        AcroFormCapability.FILLABLE -> resourceProvider.getString(R.string.form_fill_error)
    }
}
