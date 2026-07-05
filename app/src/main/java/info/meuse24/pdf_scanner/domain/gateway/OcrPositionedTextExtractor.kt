package info.meuse24.pdf_scanner.domain.gateway

import info.meuse24.pdf_scanner.domain.model.Document
import info.meuse24.pdf_scanner.domain.model.OcrPipelineStatus
import info.meuse24.pdf_scanner.domain.model.OcrPositionedPage

/**
 * Liefert positionierte OCR-Geometrie (Zeilen/Elemente inkl. Bounding-Boxen) für genau ein
 * Dokument — Grundlage der Tabellenextraktion (siehe
 * docs/table-extraction-csv-implementation-plan.md). Im Gegensatz zu
 * [OcrDocumentTextExtractor] wird kein reiner Text, sondern die Zeilen-/Element-Hierarchie samt
 * Geometrie zurückgegeben; die Seitenreihenfolge im Ergebnis ist stabil und entspricht der
 * PDF-Seitenreihenfolge.
 */
interface OcrPositionedTextExtractor {
    suspend fun extract(
        document: Document,
        languageCode: String,
        onStatus: (OcrPipelineStatus) -> Unit = {},
        onProgress: (current: Int, total: Int) -> Unit = { _, _ -> }
    ): List<OcrPositionedPage>
}
