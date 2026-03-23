package info.meuse24.pdf_scanner.domain.usecase

import info.meuse24.pdf_scanner.data.local.ScanRecord
import info.meuse24.pdf_scanner.data.repository.ScanRepository
import info.meuse24.pdf_scanner.util.PdfEditor
import java.io.File
import javax.inject.Inject

class RotatePagesUseCase @Inject constructor(
    private val pdfEditor: PdfEditor,
    private val repository: ScanRepository
) {
    suspend operator fun invoke(
        record: ScanRecord,
        pageIndexes: List<Int>,
        rotationDegrees: Int,
        saveAsCopy: Boolean,
        scansDir: File
    ) {
        val resultFile = pdfEditor.rotatePages(File(record.filepath), pageIndexes, rotationDegrees, saveAsCopy)
        val thumbFile = thumbnailFile(record, resultFile, saveAsCopy, scansDir)
        pdfEditor.generateThumbnail(resultFile, thumbFile)

        if (saveAsCopy) {
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
        } else {
            repository.updateFileSize(record.id, resultFile.length())
        }
    }

    private fun thumbnailFile(
        record: ScanRecord,
        resultFile: File,
        saveAsCopy: Boolean,
        scansDir: File
    ): File {
        if (saveAsCopy) {
            return File(scansDir, "${resultFile.nameWithoutExtension}.jpg")
        }
        val existing = record.thumbnailPath?.let(::File)
        return existing ?: File(scansDir, "${record.filename}.jpg")
    }
}
