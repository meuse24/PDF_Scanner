package info.meuse24.pdf_scanner.domain.usecase

import info.meuse24.pdf_scanner.domain.model.Document
import info.meuse24.pdf_scanner.domain.model.PdfMetadata
import info.meuse24.pdf_scanner.domain.pdf.PdfMetadataOps
import info.meuse24.pdf_scanner.domain.repository.DocumentRepository
import java.io.File
import javax.inject.Inject

class UpdatePdfMetadataUseCase @Inject constructor(
    private val pdfMetadataOps: PdfMetadataOps,
    private val repository: DocumentRepository
) {
    suspend operator fun invoke(record: Document, metadata: PdfMetadata) {
        val updatedFile = pdfMetadataOps.updateMetadata(File(record.filepath), metadata)
        repository.updateFileSize(record.id, updatedFile.length())
    }
}

