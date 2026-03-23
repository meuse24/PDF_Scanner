package info.meuse24.pdf_scanner.domain.usecase

import info.meuse24.pdf_scanner.data.local.ScanRecord
import info.meuse24.pdf_scanner.data.repository.ScanRepository
import info.meuse24.pdf_scanner.util.PdfEditor
import info.meuse24.pdf_scanner.util.resolveUniqueFilename
import java.io.File
import javax.inject.Inject

/**
 * Führt mehrere PDFs zu einer neuen Datei zusammen und legt einen DB-Eintrag an.
 * @return der finale Dateiname (ohne Extension) des erstellten PDFs
 */
class MergePdfsUseCase @Inject constructor(
    private val pdfEditor:  PdfEditor,
    private val repository: ScanRepository
) {
    suspend operator fun invoke(
        records:        List<ScanRecord>,
        outputFilename: String,
        scansDir:       File
    ): String {
        val baseName = resolveUniqueFilename(scansDir, outputFilename)
        val destFile = File(scansDir, "$baseName.pdf")
        pdfEditor.mergePdfs(records.map { File(it.filepath) }, destFile)

        val thumbFile = File(scansDir, "$baseName.jpg")
        pdfEditor.generateThumbnail(destFile, thumbFile)

        repository.saveScan(
            ScanRecord(
                filename      = baseName,
                filepath      = destFile.absolutePath,
                timestamp     = System.currentTimeMillis(),
                pageCount     = records.sumOf { it.pageCount },
                fileSize      = destFile.length(),
                thumbnailPath = thumbFile.takeIf { it.exists() }?.absolutePath
            )
        )
        return baseName
    }
}
