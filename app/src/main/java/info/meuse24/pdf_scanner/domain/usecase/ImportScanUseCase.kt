package info.meuse24.pdf_scanner.domain.usecase

import android.net.Uri
import info.meuse24.pdf_scanner.data.local.ScanRecord
import info.meuse24.pdf_scanner.data.repository.ScanRepository
import info.meuse24.pdf_scanner.util.FileUtil
import info.meuse24.pdf_scanner.util.OcrPipelineStatus
import info.meuse24.pdf_scanner.util.SearchablePdfBuilder
import info.meuse24.pdf_scanner.util.toOcrPageTextJson
import java.util.Locale
import javax.inject.Inject

/**
 * Speichert einen neuen Scan: Datei kopieren, optional Thumbnail, optional OCR-Textlayer.
 * Wirft eine Exception bei Fehler; das ViewModel übersetzt sie in UI-Strings.
 */
class ImportScanUseCase @Inject constructor(
    private val fileUtil:             FileUtil,
    private val searchablePdfBuilder: SearchablePdfBuilder,
    private val repository:           ScanRepository
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
    ): ScanRecord {
        val savedFile     = fileUtil.savePdfFromUri(pdfUri, filename)
        val baseName      = savedFile.nameWithoutExtension
        val thumbnailPath = thumbnailUri?.let {
            fileUtil.saveThumbnailFromUri(it, baseName)?.absolutePath
        }

        var isSearchable  = false
        var extractedText: String? = null
        var ocrConfidence: Float? = null
        var ocrLanguage: String? = null
        var ocrPageTextJson: String? = null
        if (makeSearchable) {
            val searchableResult = searchablePdfBuilder.makeSearchable(savedFile, languageCode, onProgress, onStatus)
            isSearchable  = true
            extractedText = searchableResult.extractedText.ifBlank { null }
            ocrConfidence = searchableResult.stats?.confidence
            ocrLanguage = searchableResult.stats?.recognizedLanguage
            ocrPageTextJson = searchableResult.pageTexts.toOcrPageTextJson()
        }

        val record = ScanRecord(
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
            ocrPageTextJson = ocrPageTextJson
        )
        repository.saveScan(record)
        return record
    }
}
