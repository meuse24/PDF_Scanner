package info.meuse24.pdf_scanner.domain.usecase

import info.meuse24.pdf_scanner.domain.gateway.DocumentFileStore
import info.meuse24.pdf_scanner.domain.gateway.ResourceProvider
import info.meuse24.pdf_scanner.domain.gateway.StringResource
import info.meuse24.pdf_scanner.domain.model.Document
import info.meuse24.pdf_scanner.domain.pdf.PdfRenderingOps
import info.meuse24.pdf_scanner.domain.pdf.PdfSecurityOps
import info.meuse24.pdf_scanner.domain.repository.DocumentRepository
import java.io.File
import javax.inject.Inject

class ImportFileUseCase @Inject constructor(
    private val fileUtil: DocumentFileStore,
    private val pdfEditor: PdfSecurityOps,
    private val repository: DocumentRepository,
    private val resourceProvider: ResourceProvider,
    private val pdfRenderingOps: PdfRenderingOps = pdfEditor as PdfRenderingOps
) {
    suspend operator fun invoke(pdfUri: Any, filename: String): Document {
        val savedFile = fileUtil.savePdf(pdfUri, filename)
        val thumbnailFile = File(savedFile.parentFile, "${savedFile.nameWithoutExtension}.jpg")

        try {
            val isEncrypted = try {
                pdfEditor.isPdfEncrypted(savedFile)
            } catch (e: Exception) {
                throw IllegalStateException(resourceProvider.getString(StringResource.ErrorPdfInvalid), e)
            }
            val pageCount = pdfRenderingOps.getPageCount(savedFile)
            if (pageCount == 0 && !isEncrypted) {
                throw IllegalStateException(resourceProvider.getString(StringResource.ErrorPdfInvalid))
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

