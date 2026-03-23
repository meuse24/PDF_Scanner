package info.meuse24.pdf_scanner.domain.usecase

import info.meuse24.pdf_scanner.data.local.ScanRecord
import info.meuse24.pdf_scanner.data.repository.ScanRepository
import info.meuse24.pdf_scanner.util.PdfEditor
import java.io.File
import javax.inject.Inject

class UnlockPdfUseCase @Inject constructor(
    private val pdfEditor: PdfEditor,
    private val repository: ScanRepository
) {
    suspend operator fun invoke(record: ScanRecord, password: String, scansDir: File): String {
        val resultFile = pdfEditor.unlockPdf(File(record.filepath), scansDir, password)
        val thumbFile = File(scansDir, "${resultFile.nameWithoutExtension}.jpg")
        pdfEditor.generateThumbnail(resultFile, thumbFile)
        repository.saveScan(
            ScanRecord(
                filename = resultFile.nameWithoutExtension,
                filepath = resultFile.absolutePath,
                timestamp = System.currentTimeMillis(),
                pageCount = record.pageCount,
                fileSize = resultFile.length(),
                thumbnailPath = thumbFile.takeIf { it.exists() }?.absolutePath,
                isSearchable = record.isSearchable
            )
        )
        return resultFile.nameWithoutExtension
    }
}
