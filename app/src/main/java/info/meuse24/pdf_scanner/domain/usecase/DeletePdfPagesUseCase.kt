package info.meuse24.pdf_scanner.domain.usecase

import info.meuse24.pdf_scanner.data.local.ScanRecord
import info.meuse24.pdf_scanner.data.repository.ScanRepository
import info.meuse24.pdf_scanner.util.PdfEditor
import java.io.File
import javax.inject.Inject

class DeletePdfPagesUseCase @Inject constructor(
    private val pdfEditor: PdfEditor,
    private val repository: ScanRepository
) {
    suspend operator fun invoke(
        record: ScanRecord,
        pageIndexes: List<Int>,
        saveAsCopy: Boolean,
        scansDir: File
    ): Int {
        val resultFile = pdfEditor.deletePages(File(record.filepath), pageIndexes, saveAsCopy)
        val pageCount = pdfEditor.getPageCount(resultFile)
        val thumbFile = thumbnailFile(record, resultFile, saveAsCopy, scansDir)
        pdfEditor.generateThumbnail(resultFile, thumbFile)

        if (saveAsCopy) {
            repository.saveScan(
                ScanRecord(
                    filename = resultFile.nameWithoutExtension,
                    filepath = resultFile.absolutePath,
                    timestamp = System.currentTimeMillis(),
                    pageCount = pageCount,
                    fileSize = resultFile.length(),
                    thumbnailPath = thumbFile.takeIf { it.exists() }?.absolutePath,
                    isSearchable = record.isSearchable
                )
            )
        } else {
            repository.updatePageMetrics(record.id, pageCount, resultFile.length())
        }
        return pageCount
    }

}
