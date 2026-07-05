package info.meuse24.pdf_scanner.domain.model

import kotlinx.serialization.Serializable

/**
 * Entwurfs-Persistenz für den Tabellenexport-Review (Schutz gegen Process Death). Enthält
 * bewusst nur das Rekonstruktionsergebnis plus Nutzer-Edits (Zelltexte, Zeilen-/Seitenauswahl,
 * Dialekt) — keine OCR-Geometrie. Die wird nach der Rekonstruktion nicht mehr benötigt, der
 * Draft bleibt dadurch klein. Liegt als JSON in `cacheDir/table_drafts/`: das OS darf diesen
 * Ordner jederzeit räumen, der Draft ist Komfort-Wiederherstellung, keine garantierte Persistenz.
 */
@Serializable
data class TableDraft(
    val scanId: Long,
    val savedAtEpochMillis: Long,
    val delimiter: CsvDelimiter,
    val protectFormulas: Boolean,
    val pages: List<TableDraftPage>,
    /**
     * Fingerabdruck der Quell-PDF zum Zeitpunkt der Extraktion (Dateigroesse, Aenderungszeit,
     * Seitenzahl). Verhindert, dass ein Draft angeboten wird, dessen Quelle sich seither
     * veraendert hat (z. B. Seiten gedreht/geloescht/neu sortiert unter derselben scanId) - ohne
     * Abgleich wuerden veraltete Zeilen aus der alten PDF-Version fuer die neue angeboten.
     * Default 0/0/0 sorgt dafuer, dass vor dieser Aenderung gespeicherte Drafts beim Laden als
     * veraltet erkannt und verworfen werden, statt fehlzuschlagen.
     */
    val sourceFileSize: Long = 0L,
    val sourceLastModified: Long = 0L,
    val sourcePageCount: Int = 0
)

@Serializable
data class TableDraftPage(
    val pageIndex: Int,
    val selectedForExport: Boolean,
    val rows: List<TableDraftRow>,
    /**
     * Unbearbeiteter Rekonstruktionsstand dieser Seite, unabhaengig von [rows]. Ermoeglicht
     * "Zuruecksetzen" auch nach einem Draft-Restore, ohne die (bewusst nicht persistierte)
     * OCR-Geometrie erneut zu benoetigen. Default = [rows], falls ein Aufrufer keinen eigenen
     * Ausgangsstand mitgibt (z. B. Tests).
     */
    val originalRows: List<TableDraftRow> = rows,
    /** Tabellenweite Konfidenz aus der Rekonstruktion (siehe `ExtractedTable.confidence`). */
    val confidence: Float = 0f,
    /**
     * Tabellenweite Issues (siehe `ExtractedTable.issues`) - eine Obermenge aller Zell-Issues,
     * kann aber zusaetzliche, nur auf Tabellenebene erkannte Eintraege enthalten (z. B. eine
     * globale `LOW_CONFIDENCE` ohne dass eine einzelne Zelle markiert ist). Muss separat
     * persistiert werden, sonst geht diese Information beim Draft-Restore verloren.
     */
    val tableIssues: Set<TableIssue> = emptySet()
)

@Serializable
data class TableDraftCell(
    val text: String,
    val issues: Set<TableIssue> = emptySet()
)

@Serializable
data class TableDraftRow(
    val included: Boolean,
    val cells: List<TableDraftCell>
)
