package info.meuse24.pdf_scanner.domain.usecase

import info.meuse24.pdf_scanner.domain.gateway.DocumentFileStore
import info.meuse24.pdf_scanner.domain.gateway.SearchablePdfGenerator
import info.meuse24.pdf_scanner.domain.model.Document
import info.meuse24.pdf_scanner.domain.model.OcrPipelineStatus
import info.meuse24.pdf_scanner.domain.repository.DocumentRepository
import java.util.Locale
import javax.inject.Inject

/**
 * Speichert einen neuen Scan: Datei kopieren, optional Thumbnail, optional OCR-Textlayer.
 * Wirft eine Exception bei Fehler; das ViewModel übersetzt sie in UI-Strings.
 */
class ImportScanUseCase @Inject constructor(
    private val fileStore:            DocumentFileStore,
    private val searchablePdfBuilder: SearchablePdfGenerator,
    private val repository:           DocumentRepository
) {
    suspend operator fun invoke(
        pdfUri:         Any,
        pageCount:      Int,
        filename:       String,
        thumbnailUri:   Any?    = null,
        makeSearchable: Boolean = false,
        languageCode:   String  = Locale.getDefault().language,
        onProgress:     (Int, Int) -> Unit = { _, _ -> },
        onStatus:       (OcrPipelineStatus) -> Unit = {}
    ): Document {
        val savedFile     = fileStore.savePdf(pdfUri, filename)
        val baseName      = savedFile.nameWithoutExtension
        val thumbnailPath = thumbnailUri?.let {
            fileStore.saveThumbnail(it, baseName)?.absolutePath
        }

        var isSearchable  = false
        var extractedText: String? = null
        var ocrConfidence: Float? = null
        var ocrLanguage: String? = null
        var pageTexts = emptyList<String>()
        if (makeSearchable) {
            val searchableResult = searchablePdfBuilder.makeSearchable(savedFile, languageCode, onProgress, onStatus)
            isSearchable  = true
            extractedText = searchableResult.extractedText.ifBlank { null }
            ocrConfidence = searchableResult.stats?.confidence
            ocrLanguage = searchableResult.stats?.recognizedLanguage
            pageTexts = searchableResult.pageTexts
        }

        val record = Document(
            filename      = baseName,
            filepath      = savedFile.absolutePath,
            timestamp     = System.currentTimeMillis(),
            pageCount     = pageCount,
            fileSize      = savedFile.length(),
            thumbnailPath = thumbnailPath,
            isSearchable  = isSearchable,
            extractedText = extractedText,
            tags          = null,
            ocrConfidence = ocrConfidence,
            ocrLanguage = ocrLanguage,
            pageTexts = pageTexts
        )
        val id = repository.saveScan(record)
        return record.copy(id = id)
    }
}

