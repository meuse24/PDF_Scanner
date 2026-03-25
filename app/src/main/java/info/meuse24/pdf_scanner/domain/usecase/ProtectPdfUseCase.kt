package info.meuse24.pdf_scanner.domain.usecase

import info.meuse24.pdf_scanner.data.local.ScanRecord
import info.meuse24.pdf_scanner.data.repository.ScanRepository
import info.meuse24.pdf_scanner.util.PdfEditor
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import javax.inject.Inject

class ProtectPdfUseCase @Inject constructor(
    private val pdfEditor: PdfEditor,
    private val repository: ScanRepository
) {
    suspend operator fun invoke(record: ScanRecord, password: String, scansDir: File): String {
        val resultFile = pdfEditor.protectPdf(File(record.filepath), scansDir, password)
        val thumbFile = copyThumbnail(record, scansDir, resultFile.nameWithoutExtension)
        repository.saveScan(
            ScanRecord(
                filename = resultFile.nameWithoutExtension,
                filepath = resultFile.absolutePath,
                timestamp = System.currentTimeMillis(),
                pageCount = record.pageCount,
                fileSize = resultFile.length(),
                thumbnailPath = thumbFile?.takeIf { it.exists() }?.absolutePath,
                isSearchable = record.isSearchable,
                isEncrypted  = true
            )
        )
        return resultFile.nameWithoutExtension
    }

    private fun copyThumbnail(record: ScanRecord, scansDir: File, outputName: String): File? {
        val source = record.thumbnailPath?.let(::File)?.takeIf { it.exists() } ?: return null
        val target = File(scansDir, "$outputName.jpg")
        Files.copy(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        return target
    }
}
