package info.meuse24.pdf_scanner.domain.usecase

import info.meuse24.pdf_scanner.data.local.ScanRecord
import info.meuse24.pdf_scanner.data.repository.ScanRepository
import info.meuse24.pdf_scanner.util.SearchablePdfBuilder
import java.io.File
import javax.inject.Inject

/**
 * Macht eine Liste von Scans durchsuchbar (OCR-Textlayer einfügen).
 * Bereits durchsuchbare Records werden übersprungen (Idempotenz).
 * Speichert den extrahierten Text in der Datenbank.
 * @return Anzahl der tatsächlich verarbeiteten Records
 */
class MakeSearchableUseCase @Inject constructor(
    private val searchablePdfBuilder: SearchablePdfBuilder,
    private val repository:           ScanRepository
) {
    suspend operator fun invoke(
        records:      List<ScanRecord>,
        languageCode: String,
        onProgress:   (Int, Int) -> Unit = { _, _ -> }
    ): Int {
        val pending = records.filter { !it.isSearchable || it.extractedText == null }
        for (record in pending) {
            val pdfFile = File(record.filepath)
            if (!pdfFile.exists()) continue
            val text = searchablePdfBuilder.makeSearchable(pdfFile, languageCode, onProgress)
            repository.markSearchableWithContent(
                id       = record.id,
                fileSize = pdfFile.length(),
                text     = text.ifBlank { null },
                tags     = null
            )
        }
        return pending.size
    }
}
