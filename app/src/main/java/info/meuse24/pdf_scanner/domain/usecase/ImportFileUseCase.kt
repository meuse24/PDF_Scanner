package info.meuse24.pdf_scanner.domain.usecase

import android.net.Uri
import info.meuse24.pdf_scanner.R
import info.meuse24.pdf_scanner.domain.model.Document
import info.meuse24.pdf_scanner.domain.pdf.PdfRenderingOps
import info.meuse24.pdf_scanner.domain.pdf.PdfSecurityOps
import info.meuse24.pdf_scanner.domain.repository.DocumentRepository
import info.meuse24.pdf_scanner.util.FileUtil
import info.meuse24.pdf_scanner.util.ResourceProvider
import java.io.File
import javax.inject.Inject

class ImportFileUseCase @Inject constructor(
    private val fileUtil: FileUtil,
    private val pdfEditor: PdfSecurityOps,
    private val repository: DocumentRepository,
    private val resourceProvider: ResourceProvider,
    private val pdfRenderingOps: PdfRenderingOps = pdfEditor as PdfRenderingOps
) {
    suspend operator fun invoke(pdfUri: Uri, filename: String): Document {
        val savedFile = fileUtil.savePdfFromUri(pdfUri, filename)
        val thumbnailFile = File(savedFile.parentFile, "${savedFile.nameWithoutExtension}.jpg")

        try {
            val isEncrypted = try {
                pdfEditor.isPdfEncrypted(savedFile)
            } catch (e: Exception) {
                throw IllegalStateException(resourceProvider.getString(R.string.error_pdf_invalid), e)
            }
            val pageCount = pdfRenderingOps.getPageCount(savedFile)
            if (pageCount == 0 && !isEncrypted) {
                throw IllegalStateException(resourceProvider.getString(R.string.error_pdf_invalid))
            }

            val thumbnailPath = if (pageCount > 0 && pdfRenderingOps.generateThumbnail(savedFile, thumbnailFile)) {
                thumbnailFile.absolutePath
            } else {
                thumbnailFile.delete()
                null
            }

            val record = Document(
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
            val id = repository.saveScan(record)
            return record.copy(id = id)
        } catch (e: Exception) {
            savedFile.delete()
            thumbnailFile.delete()
            throw e
        }
    }
}

