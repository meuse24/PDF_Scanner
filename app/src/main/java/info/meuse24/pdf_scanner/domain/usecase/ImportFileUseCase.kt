package info.meuse24.pdf_scanner.domain.usecase

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import info.meuse24.pdf_scanner.R
import info.meuse24.pdf_scanner.data.local.ScanRecord
import info.meuse24.pdf_scanner.data.repository.ScanRepository
import info.meuse24.pdf_scanner.util.FileUtil
import info.meuse24.pdf_scanner.util.PdfEditor
import java.io.File
import javax.inject.Inject

class ImportFileUseCase @Inject constructor(
    private val fileUtil: FileUtil,
    private val pdfEditor: PdfEditor,
    private val repository: ScanRepository,
    @ApplicationContext private val context: Context
) {
    suspend operator fun invoke(pdfUri: Uri, filename: String): ScanRecord {
        val savedFile = fileUtil.savePdfFromUri(pdfUri, filename)
        val thumbnailFile = File(savedFile.parentFile, "${savedFile.nameWithoutExtension}.jpg")

        try {
            val isEncrypted = pdfEditor.isPdfEncrypted(savedFile)
            val pageCount = pdfEditor.getPageCount(savedFile)
            if (pageCount == 0 && !isEncrypted) {
                throw IllegalStateException(context.getString(R.string.error_pdf_invalid))
            }

            val thumbnailPath = if (pageCount > 0 && pdfEditor.generateThumbnail(savedFile, thumbnailFile)) {
                thumbnailFile.absolutePath
            } else {
                thumbnailFile.delete()
                null
            }

            val record = ScanRecord(
                filename = savedFile.nameWithoutExtension,
                filepath = savedFile.absolutePath,
                timestamp = System.currentTimeMillis(),
                pageCount = pageCount,
                fileSize = savedFile.length(),
                thumbnailPath = thumbnailPath,
                isSearchable = false,
                isEncrypted = isEncrypted,
                extractedText = null,
                tags = null
            )
            repository.saveScan(record)
            return record
        } catch (e: Exception) {
            savedFile.delete()
            thumbnailFile.delete()
            throw e
        }
    }
}
