package info.meuse24.pdf_scanner.domain.usecase

import android.net.Uri
import info.meuse24.pdf_scanner.data.local.ScanRecord
import info.meuse24.pdf_scanner.data.repository.ScanRepository
import info.meuse24.pdf_scanner.util.FileUtil
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
    private val repository:           ScanRepository,
    private val autoTagUseCase:       AutoTagUseCase
) {
    suspend operator fun invoke(
        pdfUri:         Uri,
        pageCount:      Int,
        filename:       String,
        thumbnailUri:   Uri?    = null,
        makeSearchable: Boolean = false,
        languageCode:   String  = Locale.getDefault().language,
        onProgress:     (Int, Int) -> Unit = { _, _ -> }
    ): ScanRecord {
        val savedFile     = fileUtil.savePdfFromUri(pdfUri, filename)
        val baseName      = savedFile.nameWithoutExtension
        val thumbnailPath = thumbnailUri?.let {
            fileUtil.saveThumbnailFromUri(it, baseName)?.absolutePath
        }

        var isSearchable  = false
        var extractedText: String? = null
        var tags: String? = null
        if (makeSearchable) {
            val text = searchablePdfBuilder.makeSearchable(savedFile, languageCode, onProgress)
            isSearchable  = true
            extractedText = text.ifBlank { null }
            tags          = autoTagUseCase.extractTags(text)
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
            tags          = tags
        )
        repository.saveScan(record)
        return record
    }
}
