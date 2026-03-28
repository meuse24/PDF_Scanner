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
import info.meuse24.pdf_scanner.domain.usecase.HighlightRect
import info.meuse24.pdf_scanner.domain.usecase.HighlightStroke
import info.meuse24.pdf_scanner.domain.usecase.PdfCompressionPreset
import info.meuse24.pdf_scanner.domain.usecase.TextComment
import info.meuse24.pdf_scanner.domain.usecase.TextLine
import info.meuse24.pdf_scanner.domain.workflow.AnnotatePdfWorkflow
import info.meuse24.pdf_scanner.domain.workflow.CompressPdfWorkflow
import info.meuse24.pdf_scanner.domain.workflow.ConvertToGrayscaleWorkflow
import info.meuse24.pdf_scanner.domain.workflow.HighlightPdfWorkflow
import info.meuse24.pdf_scanner.domain.workflow.PageNumbersWorkflow
import info.meuse24.pdf_scanner.domain.workflow.ProtectPdfWorkflow
import info.meuse24.pdf_scanner.domain.workflow.RemovePasswordWorkflow
import info.meuse24.pdf_scanner.domain.workflow.RemoveTextLayerWorkflow
import info.meuse24.pdf_scanner.domain.workflow.RestrictUsageWorkflow
import info.meuse24.pdf_scanner.domain.workflow.SignatureStampWorkflow
import info.meuse24.pdf_scanner.domain.workflow.TextWatermarkWorkflow
import info.meuse24.pdf_scanner.domain.workflow.UnlockPdfWorkflow
import info.meuse24.pdf_scanner.domain.workflow.UpdatePdfMetadataWorkflow
import info.meuse24.pdf_scanner.domain.workflow.WorkflowErrorMapper
import info.meuse24.pdf_scanner.domain.workflow.WorkflowResult
import info.meuse24.pdf_scanner.util.PdfEditor
import info.meuse24.pdf_scanner.util.PdfMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
    private val removeTextLayerWorkflow: RemoveTextLayerWorkflow,
    private val removePasswordWorkflow: RemovePasswordWorkflow,
    private val restrictUsageWorkflow: RestrictUsageWorkflow,
    private val highlightPdfWorkflow: HighlightPdfWorkflow,
    private val annotatePdfWorkflow: AnnotatePdfWorkflow,
    private val convertToGrayscaleWorkflow: ConvertToGrayscaleWorkflow,
    private val updatePdfMetadataWorkflow: UpdatePdfMetadataWorkflow,
    private val pdfEditor: PdfEditor,
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

    private val _highlightPageBitmap = MutableStateFlow<Bitmap?>(null)
    val highlightPageBitmap: StateFlow<Bitmap?> = _highlightPageBitmap.asStateFlow()

    private val _metadata = MutableStateFlow<PdfMetadata?>(null)
    val metadata: StateFlow<PdfMetadata?> = _metadata.asStateFlow()

    private val _textLines = MutableStateFlow<List<TextLine>>(emptyList())
    val textLines: StateFlow<List<TextLine>> = _textLines.asStateFlow()

    private var highlightPageJob: Job? = null
    private val highlightTextLineCache = mutableMapOf<Int, List<TextLine>>()
    private var cachedHighlightFilepath: String? = null

    fun loadHighlightPage(pageIndex: Int) {
        val record = _record.value ?: return
        if (cachedHighlightFilepath != record.filepath) {
            cachedHighlightFilepath = record.filepath
            highlightTextLineCache.clear()
        }
        highlightPageJob?.cancel()
        _highlightPageBitmap.value = null
        _textLines.value = emptyList()
        highlightPageJob = viewModelScope.launch(Dispatchers.IO) {
            val inputFile = File(record.filepath)
            _highlightPageBitmap.value = pdfEditor.renderPageThumbnail(inputFile, pageIndex, 1024)
            if (!record.isSearchable) return@launch
            highlightTextLineCache[pageIndex]?.let { cachedLines ->
                _textLines.value = cachedLines
                return@launch
            }
            val lines = pdfEditor.extractTextLines(inputFile, pageIndex)
            highlightTextLineCache[pageIndex] = lines
            _textLines.value = lines
        }
    }

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

    fun removeTextLayer() {
        val record = _record.value ?: return
        if (_editLoading.value) return
        _editLoading.value = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                when (val result = removeTextLayerWorkflow(record, scansDir)) {
                    is WorkflowResult.Success -> _success.value = true
                    is WorkflowResult.Failure -> _error.value = errorMapper.map(result.error)
                }
            } finally {
                _editLoading.value = false
            }
        }
    }

    fun removePassword() {
        val record = _record.value ?: return
        if (_editLoading.value) return
        _editLoading.value = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                when (val result = removePasswordWorkflow(record, scansDir)) {
                    is WorkflowResult.Success -> _success.value = true
                    is WorkflowResult.Failure -> _error.value = errorMapper.map(result.error)
                }
            } finally {
                _editLoading.value = false
            }
        }
    }

    fun restrictUsage(ownerPassword: String, canPrint: Boolean, canCopy: Boolean, canEdit: Boolean) {
        val record = _record.value ?: return
        if (_editLoading.value) return
        _editLoading.value = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                when (val result = restrictUsageWorkflow(record, scansDir, ownerPassword, canPrint, canCopy, canEdit)) {
                    is WorkflowResult.Success -> _success.value = true
                    is WorkflowResult.Failure -> _error.value = errorMapper.map(result.error)
                }
            } finally {
                _editLoading.value = false
            }
        }
    }

    fun applyHighlight(
        strokes: List<HighlightStroke>,
        rects: List<HighlightRect> = emptyList()
    ) {
        val record = _record.value ?: return
        if (_editLoading.value) return
        _editLoading.value = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                when (val result = highlightPdfWorkflow(record, strokes, rects, scansDir)) {
                    is WorkflowResult.Success -> _success.value = true
                    is WorkflowResult.Failure -> _error.value = errorMapper.map(result.error)
                }
            } finally {
                _editLoading.value = false
            }
        }
    }

    fun applyAnnotations(
        strokes: List<HighlightStroke>,
        rects: List<HighlightRect> = emptyList(),
        comments: List<TextComment> = emptyList()
    ) {
        val record = _record.value ?: return
        if (_editLoading.value) return
        _editLoading.value = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                when (val result = annotatePdfWorkflow(record, strokes, rects, comments, scansDir)) {
                    is WorkflowResult.Success -> _success.value = true
                    is WorkflowResult.Failure -> _error.value = errorMapper.map(result.error)
                }
            } finally {
                _editLoading.value = false
            }
        }
    }

    fun convertToGrayscale() {
        val record = _record.value ?: return
        if (_editLoading.value) return
        _editLoading.value = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                when (val result = convertToGrayscaleWorkflow(record, scansDir)) {
                    is WorkflowResult.Success -> _success.value = true
                    is WorkflowResult.Failure -> _error.value = errorMapper.map(result.error)
                }
            } finally {
                _editLoading.value = false
            }
        }
    }

    fun saveMetadata(
        title: String,
        author: String,
        creator: String,
        subject: String,
        keywords: String
    ) {
        val record = _record.value ?: return
        val currentMetadata = _metadata.value ?: return
        if (_editLoading.value) return
        _editLoading.value = true
        val updatedMetadata = currentMetadata.copy(
            title = normalizeMetadataField(title),
            author = normalizeMetadataField(author),
            creator = normalizeMetadataField(creator),
            subject = normalizeMetadataField(subject),
            keywords = normalizeMetadataField(keywords)
        )
        viewModelScope.launch(Dispatchers.IO) {
            try {
                when (val result = updatePdfMetadataWorkflow(record, updatedMetadata)) {
                    is WorkflowResult.Success -> _success.value = true
                    is WorkflowResult.Failure -> _error.value = errorMapper.map(result.error)
                }
            } finally {
                _editLoading.value = false
            }
        }
    }

    fun loadMetadata() {
        val record = _record.value ?: return
        _metadata.value = null
        viewModelScope.launch(Dispatchers.IO) {
            _metadata.value = pdfEditor.readMetadata(File(record.filepath))
        }
    }

    fun clearError() { _error.value = null }

    private fun normalizeMetadataField(value: String): String? = value.trim().takeIf { it.isNotEmpty() }
}
