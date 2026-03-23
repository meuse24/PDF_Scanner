package info.meuse24.pdf_scanner.domain.usecase

import info.meuse24.pdf_scanner.data.local.ScanRecord
import info.meuse24.pdf_scanner.data.repository.ScanRepository
import info.meuse24.pdf_scanner.util.PdfEditor
import java.io.File
import javax.inject.Inject

/**
 * Teilt ein PDF an den angegebenen Seitenindizes auf.
 * Für jede erzeugte Datei wird ein neuer DB-Eintrag angelegt.
 * @return Anzahl der erzeugten Teile
 */
class SplitPdfUseCase @Inject constructor(
    private val pdfEditor:  PdfEditor,
    private val repository: ScanRepository
) {
    suspend operator fun invoke(
        record:       ScanRecord,
        splitAtPages: List<Int>,
        scansDir:     File
    ): Int {
        val parts = pdfEditor.splitPdf(File(record.filepath), scansDir, splitAtPages)
        val newRecords = parts.map { partFile ->
            val baseName  = partFile.nameWithoutExtension
            val thumbFile = File(scansDir, "$baseName.jpg")
            pdfEditor.generateThumbnail(partFile, thumbFile)
            ScanRecord(
                filename      = baseName,
                filepath      = partFile.absolutePath,
                timestamp     = System.currentTimeMillis(),
                pageCount     = pdfEditor.getPageCount(partFile),
                fileSize      = partFile.length(),
                thumbnailPath = thumbFile.takeIf { it.exists() }?.absolutePath
            )
        }
        repository.saveScans(newRecords)
        return parts.size
    }
}
