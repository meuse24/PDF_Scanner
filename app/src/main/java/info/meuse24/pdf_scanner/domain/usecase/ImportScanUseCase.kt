package info.meuse24.pdf_scanner.domain.usecase

import android.net.Uri
import info.meuse24.pdf_scanner.domain.model.Document
import info.meuse24.pdf_scanner.domain.repository.DocumentRepository
import info.meuse24.pdf_scanner.util.FileUtil
import info.meuse24.pdf_scanner.util.OcrPipelineStatus
import info.meuse24.pdf_scanner.util.SearchablePdfBuilder
import java.util.Locale
import javax.inject.Inject

/**
 * Speichert einen neuen Scan: Datei kopieren, optional Thumbnail, optional OCR-Textlayer.
 * Wirft eine Exception bei Fehler; das ViewModel übersetzt sie in UI-Strings.
 */
class ImportScanUseCase @Inject constructor(
    private val fileUtil:             FileUtil,
    private val searchablePdfBuilder: SearchablePdfBuilder,
    private val repository:           DocumentRepository
) {
    suspend operator fun invoke(
        pdfUri:         Uri,
        pageCount:      Int,
        filename:       String,
        thumbnailUri:   Uri?    = null,
        makeSearchable: Boolean = false,
        languageCode:   String  = Locale.getDefault().language,
        onProgress:     (Int, Int) -> Unit = { _, _ -> },
        onStatus:       (OcrPipelineStatus) -> Unit = {}
    ): Document {
        val savedFile     = fileUtil.savePdfFromUri(pdfUri, filename)
        val baseName      = savedFile.nameWithoutExtension
        val thumbnailPath = thumbnailUri?.let {
            fileUtil.saveThumbnailFromUri(it, baseName)?.absolutePath
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

