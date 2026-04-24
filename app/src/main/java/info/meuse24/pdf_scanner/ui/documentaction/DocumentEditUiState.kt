package info.meuse24.pdf_scanner.ui.documentaction

import android.graphics.Bitmap
import info.meuse24.pdf_scanner.domain.model.Document
import info.meuse24.pdf_scanner.domain.model.PdfMetadata
import info.meuse24.pdf_scanner.domain.usecase.TextLine

data class DocumentEditUiState(
    val record: Document? = null,
    val editLoading: Boolean = false,
    val error: String? = null,
    val success: Boolean = false,
    val ocrStatusText: String? = null,
    val metadata: PdfMetadata? = null
)

data class DocumentPagePreviewUiState(
    val pageBitmap: Bitmap? = null,
    val textLines: List<TextLine> = emptyList()
)
