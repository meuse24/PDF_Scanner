package info.meuse24.pdf_scanner.ui.documentaction

import android.graphics.Bitmap
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import info.meuse24.pdf_scanner.R
import info.meuse24.pdf_scanner.domain.model.Document
import info.meuse24.pdf_scanner.domain.model.PdfMetadata
import info.meuse24.pdf_scanner.domain.pdf.PdfMetadataOps
import info.meuse24.pdf_scanner.domain.pdf.PdfRenderingOps
import info.meuse24.pdf_scanner.domain.pdf.PdfTextOps
import info.meuse24.pdf_scanner.domain.repository.DocumentRepository
import info.meuse24.pdf_scanner.domain.usecase.AnnotationOval
import info.meuse24.pdf_scanner.domain.usecase.AnnotationRect
import info.meuse24.pdf_scanner.domain.usecase.AnnotationStroke
import info.meuse24.pdf_scanner.domain.usecase.AnnotationText
import info.meuse24.pdf_scanner.domain.usecase.HighlightRect
import info.meuse24.pdf_scanner.domain.usecase.HighlightStroke
import info.meuse24.pdf_scanner.domain.usecase.PdfCompressionPreset
import info.meuse24.pdf_scanner.domain.usecase.RedactionRect
import info.meuse24.pdf_scanner.domain.workflow.AnnotatePdfWorkflow
import info.meuse24.pdf_scanner.domain.workflow.CompressPdfWorkflow
import info.meuse24.pdf_scanner.domain.workflow.ConvertToGrayscaleWorkflow
import info.meuse24.pdf_scanner.domain.workflow.HighlightPdfWorkflow
import info.meuse24.pdf_scanner.domain.workflow.PageNumbersWorkflow
import info.meuse24.pdf_scanner.domain.workflow.ProtectPdfWorkflow
import info.meuse24.pdf_scanner.domain.workflow.RedactPdfWorkflow
import info.meuse24.pdf_scanner.domain.workflow.RemovePasswordWorkflow
import info.meuse24.pdf_scanner.domain.workflow.RemoveTextLayerWorkflow
import info.meuse24.pdf_scanner.domain.workflow.RestrictUsageWorkflow
import info.meuse24.pdf_scanner.domain.workflow.SignatureStampWorkflow
import info.meuse24.pdf_scanner.domain.workflow.TextWatermarkWorkflow
import info.meuse24.pdf_scanner.domain.workflow.UnlockPdfWorkflow
import info.meuse24.pdf_scanner.domain.workflow.UpdatePdfMetadataWorkflow
import info.meuse24.pdf_scanner.domain.workflow.WorkflowErrorMapper
import info.meuse24.pdf_scanner.domain.workflow.WorkflowResult
import info.meuse24.pdf_scanner.util.DispatcherProvider
import info.meuse24.pdf_scanner.util.ResourceProvider
import info.meuse24.pdf_scanner.util.StorageProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class DocumentEditViewModel @Inject constructor(
    private val repository: DocumentRepository,
    private val pageNumbersWorkflow: PageNumbersWorkflow,
    private val textWatermarkWorkflow: TextWatermarkWorkflow,
    private val compressPdfWorkflow: CompressPdfWorkflow,
    private val protectPdfWorkflow: ProtectPdfWorkflow,
    private val unlockPdfWorkflow: UnlockPdfWorkflow,
    private val signatureStampWorkflow: SignatureStampWorkflow,
    private val redactPdfWorkflow: RedactPdfWorkflow,
    private val removeTextLayerWorkflow: RemoveTextLayerWorkflow,
    private val removePasswordWorkflow: RemovePasswordWorkflow,
    private val restrictUsageWorkflow: RestrictUsageWorkflow,
    private val highlightPdfWorkflow: HighlightPdfWorkflow,
    private val annotatePdfWorkflow: AnnotatePdfWorkflow,
    private val convertToGrayscaleWorkflow: ConvertToGrayscaleWorkflow,
    private val updatePdfMetadataWorkflow: UpdatePdfMetadataWorkflow,
    private val pdfEditor: PdfMetadataOps,
    private val errorMapper: WorkflowErrorMapper,
    private val resourceProvider: ResourceProvider,
    private val storageProvider: StorageProvider,
    private val dispatcherProvider: DispatcherProvider,
    savedStateHandle: SavedStateHandle,
    private val pdfRenderingOps: PdfRenderingOps = pdfEditor as PdfRenderingOps,
    private val pdfTextOps: PdfTextOps = pdfEditor as PdfTextOps
) : ViewModel() {

    private val scanId: Long = checkNotNull(savedStateHandle["scanId"])

    private val recordFlow = repository.getAllScans()
        .map { scans -> scans.find { it.id == scanId } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _editLoading = MutableStateFlow(false)
    private val _error = MutableStateFlow<String?>(null)
    private val _success = MutableStateFlow(false)
    private val _ocrStatusText = MutableStateFlow<String?>(null)
    private val _metadata = MutableStateFlow<PdfMetadata?>(null)

    private val pagePreviewController = DocumentPagePreviewController(
        scope = viewModelScope,
        dispatcherProvider = dispatcherProvider,
        pdfRenderingOps = pdfRenderingOps,
        pdfTextOps = pdfTextOps
    )

    val uiState: StateFlow<DocumentEditUiState> = combine(
        combine(
            recordFlow,
            _editLoading,
            _error,
            _success,
            _ocrStatusText
        ) { record, editLoading, error, success, ocrStatusText ->
            DocumentEditUiState(
                record = record,
                editLoading = editLoading,
                error = error,
                success = success,
                ocrStatusText = ocrStatusText
            )
        },
        _metadata
    ) { state, metadata ->
        state.copy(metadata = metadata)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, DocumentEditUiState())

    val pagePreviewUiState: StateFlow<DocumentPagePreviewUiState> = pagePreviewController.uiState

    private val scansDir get() = storageProvider.scansDir()

    private var trackedPreviewFilepath: String? = null

    init {
        viewModelScope.launch {
            recordFlow.collect { record ->
                val nextFilepath = record?.filepath
                if (trackedPreviewFilepath != nextFilepath) {
                    trackedPreviewFilepath = nextFilepath
                    _metadata.value = null
                    pagePreviewController.reset()
                }
            }
        }
    }

    fun loadDocumentPage(pageIndex: Int) {
        val record = recordFlow.value ?: return
        pagePreviewController.load(record, pageIndex)
    }

    fun addPageNumbers() {
        launchWorkflow { record -> pageNumbersWorkflow(record, scansDir) }
    }

    fun applyTextWatermark(text: String) {
        launchWorkflow { record -> textWatermarkWorkflow(record, text, scansDir) }
    }

    fun compressPdf(preset: PdfCompressionPreset) {
        launchWorkflow { record -> compressPdfWorkflow(record, preset, scansDir) }
    }

    fun protectPdf(password: String) {
        launchWorkflow { record -> protectPdfWorkflow(record, password, scansDir) }
    }

    fun unlockPdf(password: String) {
        launchWorkflow { record -> unlockPdfWorkflow(record, password, scansDir) }
    }

    fun applySignatureStamp(signatureBitmap: Bitmap?, pageIndex: Int, scaleFraction: Float) {
        launchWorkflow { record ->
            signatureStampWorkflow(record, signatureBitmap, pageIndex, scaleFraction, scansDir)
        }
    }

    fun applyRedactions(
        rects: List<RedactionRect>,
        makeSearchable: Boolean = false,
        languageCode: String = "en"
    ) {
        launchWorkflow(
            beforeLaunch = {
                if (makeSearchable) {
                    _ocrStatusText.value = resourceProvider.getString(R.string.ocr_model_preparing)
                }
            },
            afterLaunch = { _ocrStatusText.value = null }
        ) { record ->
            redactPdfWorkflow(
                record = record,
                rects = rects,
                scansDir = scansDir,
                makeSearchable = makeSearchable,
                languageCode = languageCode
            )
        }
    }

    fun removeTextLayer() {
        launchWorkflow { record -> removeTextLayerWorkflow(record, scansDir) }
    }

    fun removePassword() {
        launchWorkflow { record -> removePasswordWorkflow(record, scansDir) }
    }

    fun restrictUsage(ownerPassword: String, canPrint: Boolean, canCopy: Boolean, canEdit: Boolean) {
        launchWorkflow { record ->
            restrictUsageWorkflow(record, scansDir, ownerPassword, canPrint, canCopy, canEdit)
        }
    }

    fun applyHighlight(
        strokes: List<HighlightStroke>,
        rects: List<HighlightRect> = emptyList()
    ) {
        launchWorkflow { record -> highlightPdfWorkflow(record, strokes, rects, scansDir) }
    }

    fun applyAnnotations(
        strokes: List<AnnotationStroke>,
        rects: List<AnnotationRect> = emptyList(),
        ovals: List<AnnotationOval> = emptyList(),
        comments: List<AnnotationText> = emptyList()
    ) {
        launchWorkflow { record -> annotatePdfWorkflow(record, strokes, rects, ovals, comments, scansDir) }
    }

    fun convertToGrayscale() {
        launchWorkflow { record -> convertToGrayscaleWorkflow(record, scansDir) }
    }

    fun saveMetadata(
        title: String,
        author: String,
        creator: String,
        subject: String,
        keywords: String
    ) {
        val currentMetadata = _metadata.value ?: return
        val updatedMetadata = currentMetadata.copy(
            title = normalizeMetadataField(title),
            author = normalizeMetadataField(author),
            creator = normalizeMetadataField(creator),
            subject = normalizeMetadataField(subject),
            keywords = normalizeMetadataField(keywords)
        )
        launchWorkflow { record -> updatePdfMetadataWorkflow(record, updatedMetadata) }
    }

    fun loadMetadata() {
        val record = recordFlow.value ?: return
        _metadata.value = null
        viewModelScope.launch(dispatcherProvider.io) {
            _metadata.value = pdfEditor.readMetadata(File(record.filepath))
        }
    }

    fun clearError() {
        _error.value = null
    }

    private fun launchWorkflow(
        beforeLaunch: () -> Unit = {},
        afterLaunch: () -> Unit = {},
        block: suspend (Document) -> WorkflowResult<*>
    ) {
        val record = recordFlow.value ?: return
        if (_editLoading.value) return

        _success.value = false
        beforeLaunch()
        _editLoading.value = true
        viewModelScope.launch(dispatcherProvider.io) {
            try {
                when (val result = block(record)) {
                    is WorkflowResult.Success -> _success.value = true
                    is WorkflowResult.Failure -> _error.value = errorMapper.map(result.error)
                }
            } finally {
                _editLoading.value = false
                afterLaunch()
            }
        }
    }

    private fun normalizeMetadataField(value: String): String? = value.trim().takeIf { it.isNotEmpty() }
}
