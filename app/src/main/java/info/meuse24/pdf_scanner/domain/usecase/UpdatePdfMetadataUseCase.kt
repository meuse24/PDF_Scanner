package info.meuse24.pdf_scanner.domain.usecase

import info.meuse24.pdf_scanner.data.local.ScanRecord
import info.meuse24.pdf_scanner.data.repository.ScanRepository
import info.meuse24.pdf_scanner.util.PdfEditor
import info.meuse24.pdf_scanner.util.PdfMetadata
import java.io.File
import javax.inject.Inject

class UpdatePdfMetadataUseCase @Inject constructor(
    private val pdfEditor: PdfEditor,
    private val repository: ScanRepository
) {
    suspend operator fun invoke(record: ScanRecord, metadata: PdfMetadata) {
        val updatedFile = pdfEditor.updateMetadata(File(record.filepath), metadata)
        repository.updateFileSize(record.id, updatedFile.length())
    }
}
