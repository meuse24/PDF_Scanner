package info.meuse24.pdf_scanner.ui.documentaction

import android.graphics.Bitmap
import info.meuse24.pdf_scanner.domain.gateway.DispatcherProvider
import info.meuse24.pdf_scanner.domain.model.Document
import info.meuse24.pdf_scanner.domain.pdf.PdfRenderingOps
import info.meuse24.pdf_scanner.domain.pdf.PdfTextOps
import info.meuse24.pdf_scanner.domain.usecase.TextLine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

internal class DocumentPagePreviewController(
    private val scope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
    private val pdfRenderingOps: PdfRenderingOps,
    private val pdfTextOps: PdfTextOps
) {
    private val _uiState = MutableStateFlow(DocumentPagePreviewUiState())
    val uiState: StateFlow<DocumentPagePreviewUiState> = _uiState.asStateFlow()

    private var pagePreviewJob: Job? = null
    private val textLineCache = mutableMapOf<Int, List<TextLine>>()
    private var cachedFilepath: String? = null

    fun load(record: Document, pageIndex: Int) {
        if (cachedFilepath != record.filepath) {
            cachedFilepath = record.filepath
            textLineCache.clear()
        }

        pagePreviewJob?.cancel()
        _uiState.value = DocumentPagePreviewUiState()

        pagePreviewJob = scope.launch(dispatcherProvider.io) {
            val inputFile = File(record.filepath)
            val pageBitmap = pdfRenderingOps.renderPageThumbnail(inputFile, pageIndex, 1024) as? Bitmap
            if (!record.isSearchable) {
                _uiState.value = DocumentPagePreviewUiState(pageBitmap = pageBitmap)
                return@launch
            }

            val textLines = textLineCache[pageIndex] ?: pdfTextOps.extractTextLines(inputFile, pageIndex).also {
                textLineCache[pageIndex] = it
            }
            _uiState.value = DocumentPagePreviewUiState(
                pageBitmap = pageBitmap,
                textLines = textLines
            )
        }
    }

    fun reset() {
        pagePreviewJob?.cancel()
        cachedFilepath = null
        textLineCache.clear()
        _uiState.value = DocumentPagePreviewUiState()
    }
}
