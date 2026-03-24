package info.meuse24.pdf_scanner.ui.documentaction

import android.content.Context
import android.graphics.Bitmap
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import info.meuse24.pdf_scanner.data.local.ScanRecord
import info.meuse24.pdf_scanner.data.repository.ScanRepository
import info.meuse24.pdf_scanner.domain.usecase.PdfCompressionPreset
import info.meuse24.pdf_scanner.domain.workflow.CompressPdfWorkflow
import info.meuse24.pdf_scanner.domain.workflow.PageNumbersWorkflow
import info.meuse24.pdf_scanner.domain.workflow.ProtectPdfWorkflow
import info.meuse24.pdf_scanner.domain.workflow.SignatureStampWorkflow
import info.meuse24.pdf_scanner.domain.workflow.TextWatermarkWorkflow
import info.meuse24.pdf_scanner.domain.workflow.UnlockPdfWorkflow
import info.meuse24.pdf_scanner.domain.workflow.WorkflowErrorMapper
import info.meuse24.pdf_scanner.domain.workflow.WorkflowResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class DocumentEditViewModel @Inject constructor(
    private val repository: ScanRepository,
    private val pageNumbersWorkflow: PageNumbersWorkflow,
    private val textWatermarkWorkflow: TextWatermarkWorkflow,
    private val compressPdfWorkflow: CompressPdfWorkflow,
    private val protectPdfWorkflow: ProtectPdfWorkflow,
    private val unlockPdfWorkflow: UnlockPdfWorkflow,
    private val signatureStampWorkflow: SignatureStampWorkflow,
    private val errorMapper: WorkflowErrorMapper,
    @ApplicationContext private val context: Context,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val scanId: Long = checkNotNull(savedStateHandle["scanId"])

    private val _record = MutableStateFlow<ScanRecord?>(null)
    val record: StateFlow<ScanRecord?> = _record.asStateFlow()

    private val _editLoading = MutableStateFlow(false)
    val editLoading: StateFlow<Boolean> = _editLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _success = MutableStateFlow(false)
    val success: StateFlow<Boolean> = _success.asStateFlow()

    private val scansDir get() = File(context.filesDir, "scans").apply { mkdirs() }

    init {
        viewModelScope.launch {
            repository.getAllScans().collect { scans ->
                _record.value = scans.find { it.id == scanId }
            }
        }
    }

    fun addPageNumbers() {
        val record = _record.value ?: return
        if (_editLoading.value) return
        _editLoading.value = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                when (val result = pageNumbersWorkflow(record, scansDir)) {
                    is WorkflowResult.Success -> _success.value = true
                    is WorkflowResult.Failure -> _error.value = errorMapper.map(result.error)
                }
            } finally {
                _editLoading.value = false
            }
        }
    }

    fun applyTextWatermark(text: String) {
        val record = _record.value ?: return
        if (_editLoading.value) return
        _editLoading.value = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                when (val result = textWatermarkWorkflow(record, text, scansDir)) {
                    is WorkflowResult.Success -> _success.value = true
                    is WorkflowResult.Failure -> _error.value = errorMapper.map(result.error)
                }
            } finally {
                _editLoading.value = false
            }
        }
    }

    fun compressPdf(preset: PdfCompressionPreset) {
        val record = _record.value ?: return
        if (_editLoading.value) return
        _editLoading.value = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                when (val result = compressPdfWorkflow(record, preset, scansDir)) {
                    is WorkflowResult.Success -> _success.value = true
                    is WorkflowResult.Failure -> _error.value = errorMapper.map(result.error)
                }
            } finally {
                _editLoading.value = false
            }
        }
    }

    fun protectPdf(password: String) {
        val record = _record.value ?: return
        if (_editLoading.value) return
        _editLoading.value = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                when (val result = protectPdfWorkflow(record, password, scansDir)) {
                    is WorkflowResult.Success -> _success.value = true
                    is WorkflowResult.Failure -> _error.value = errorMapper.map(result.error)
                }
            } finally {
                _editLoading.value = false
            }
        }
    }

    fun unlockPdf(password: String) {
        val record = _record.value ?: return
        if (_editLoading.value) return
        _editLoading.value = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                when (val result = unlockPdfWorkflow(record, password, scansDir)) {
                    is WorkflowResult.Success -> _success.value = true
                    is WorkflowResult.Failure -> _error.value = errorMapper.map(result.error)
                }
            } finally {
                _editLoading.value = false
            }
        }
    }

    fun applySignatureStamp(signatureBitmap: Bitmap?, pageIndex: Int, scaleFraction: Float) {
        val record = _record.value ?: return
        if (_editLoading.value) return
        _editLoading.value = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                when (val result = signatureStampWorkflow(record, signatureBitmap, pageIndex, scaleFraction, scansDir)) {
                    is WorkflowResult.Success -> _success.value = true
                    is WorkflowResult.Failure -> _error.value = errorMapper.map(result.error)
                }
            } finally {
                _editLoading.value = false
            }
        }
    }

    fun clearError() { _error.value = null }
}
