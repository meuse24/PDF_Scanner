package info.meuse24.pdf_scanner.domain.usecase

import android.net.Uri
import info.meuse24.pdf_scanner.R
import info.meuse24.pdf_scanner.data.local.ScanRecord
import info.meuse24.pdf_scanner.data.repository.ScanRepository
import info.meuse24.pdf_scanner.util.FileUtil
import info.meuse24.pdf_scanner.util.PdfEditor
import info.meuse24.pdf_scanner.util.ResourceProvider
import java.io.File
import javax.inject.Inject

class ImportFileUseCase @Inject constructor(
    private val fileUtil: FileUtil,
    private val pdfEditor: PdfEditor,
    private val repository: ScanRepository,
    private val resourceProvider: ResourceProvider
) {
    suspend operator fun invoke(pdfUri: Uri, filename: String): ScanRecord {
        val savedFile = fileUtil.savePdfFromUri(pdfUri, filename)
        val thumbnailFile = File(savedFile.parentFile, "${savedFile.nameWithoutExtension}.jpg")

        try {
            val isEncrypted = try {
                pdfEditor.isPdfEncrypted(savedFile)
            } catch (e: Exception) {
                throw IllegalStateException(resourceProvider.getString(R.string.error_pdf_invalid), e)
            }
            val pageCount = pdfEditor.getPageCount(savedFile)
            if (pageCount == 0 && !isEncrypted) {
                throw IllegalStateException(resourceProvider.getString(R.string.error_pdf_invalid))
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
