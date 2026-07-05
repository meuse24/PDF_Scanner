package info.meuse24.pdf_scanner.ui.tableexport

import info.meuse24.pdf_scanner.domain.common.ColumnWidthConfig
import info.meuse24.pdf_scanner.domain.common.estimateColumnWidthsDp
import info.meuse24.pdf_scanner.domain.model.ExtractedTable
import info.meuse24.pdf_scanner.domain.model.TableDraftCell
import info.meuse24.pdf_scanner.domain.model.TableDraftPage
import info.meuse24.pdf_scanner.domain.model.TableDraftRow
import info.meuse24.pdf_scanner.domain.model.TableIssue
import info.meuse24.pdf_scanner.domain.usecase.TableCsvPage

/**
 * Immutable UI-Darstellung einer Tabellenseite für den Review-/Editier-Screen. Getrennt von
 * [ExtractedTable]: OCR-Quellgeometrie wird durch UI-Zustände nicht verändert, Editieren
 * arbeitet ausschließlich auf dieser Darstellung.
 */
data class EditableCell(
    val text: String,
    val issues: Set<TableIssue> = emptySet()
)

data class EditableRow(
    val cells: List<EditableCell>,
    val included: Boolean = true
)

data class EditableTablePage(
    val pageIndex: Int,
    val rows: List<EditableRow>,
    val columnWidthsDp: List<Int>,
    val selectedForExport: Boolean = true,
    val confidence: Float = 0f,
    /**
     * Tabellenweite Issues (siehe [ExtractedTable.issues]) - eine Obermenge aller Zell-Issues,
     * kann aber zusaetzliche, nur auf Tabellenebene erkannte Eintraege enthalten (z. B.
     * `LOW_CONFIDENCE` aus der gemittelten Zellkonfidenz ohne dass eine einzelne Zelle markiert
     * ist). [hasIssues] darf deshalb nicht aus den Zell-Issues neu berechnet werden.
     */
    val tableIssues: Set<TableIssue> = emptySet()
) {
    val hasIssues: Boolean get() = tableIssues.isNotEmpty()
}

fun ExtractedTable.toEditablePage(columnWidthConfig: ColumnWidthConfig = ColumnWidthConfig()): EditableTablePage {
    val editableRows = rows.map { row ->
        EditableRow(
            cells = row.cells.map { cell -> EditableCell(text = cell.text, issues = cell.issues) },
            included = row.included
        )
    }
    val rawRows = editableRows.map { row -> row.cells.map { it.text } }
    val widths = estimateColumnWidthsDp(rawRows, columnBounds.size, columnWidthConfig)
    return EditableTablePage(
        pageIndex = pageIndex,
        rows = editableRows,
        columnWidthsDp = widths,
        confidence = confidence,
        tableIssues = issues
    )
}

fun TableDraftPage.toEditablePage(columnWidthConfig: ColumnWidthConfig = ColumnWidthConfig()): EditableTablePage {
    val editableRows = rows.map { draftRow ->
        EditableRow(
            cells = draftRow.cells.map { cell -> EditableCell(text = cell.text, issues = cell.issues) },
            included = draftRow.included
        )
    }
    val rawRows = editableRows.map { row -> row.cells.map { it.text } }
    val columnCount = rawRows.maxOfOrNull { it.size } ?: 0
    val widths = estimateColumnWidthsDp(rawRows, columnCount, columnWidthConfig)
    return EditableTablePage(
        pageIndex = pageIndex,
        rows = editableRows,
        columnWidthsDp = widths,
        selectedForExport = selectedForExport,
        confidence = confidence,
        // Bewusst der persistierte Tabellenstand, nicht aus den Zell-Issues neu berechnet - sonst
        // ginge eine rein tabellenweite Warnung (z. B. globale Niedrig-Konfidenz ohne einzelne
        // markierte Zelle) beim Restore verloren.
        tableIssues = tableIssues
    )
}

private fun EditableTablePage.toDraftRows(): List<TableDraftRow> =
    rows.map { row -> TableDraftRow(included = row.included, cells = row.cells.map { TableDraftCell(it.text, it.issues) }) }

/**
 * [original] ist der unbearbeitete Rekonstruktionsstand dieser Seite (fuer "Zuruecksetzen", auch
 * nach einem spaeteren Draft-Restore). Ohne Angabe wird die aktuelle Seite selbst als eigener
 * Ausgangsstand gespeichert (kein Unterschied zu vorher, falls kein Reset noetig ist).
 * Tabellenweite Konfidenz/Issues sind vom Editieren unberuehrt und werden deshalb immer von der
 * aktuellen Seite selbst uebernommen, unabhaengig von [original].
 */
fun EditableTablePage.toDraftPage(original: EditableTablePage = this): TableDraftPage = TableDraftPage(
    pageIndex = pageIndex,
    selectedForExport = selectedForExport,
    rows = toDraftRows(),
    originalRows = original.toDraftRows(),
    confidence = confidence,
    tableIssues = tableIssues
)

/** Nur die eingeschlossenen Zeilen, als reine Zellwerte für den CSV/TSV-Export. */
fun EditableTablePage.toCsvPage(): TableCsvPage =
    TableCsvPage(rows = rows.filter { it.included }.map { row -> row.cells.map { it.text } })

fun EditableTablePage.withCellText(rowIndex: Int, columnIndex: Int, newText: String): EditableTablePage {
    val updatedRows = rows.toMutableList()
    val row = updatedRows[rowIndex]
    val updatedCells = row.cells.toMutableList()
    updatedCells[columnIndex] = updatedCells[columnIndex].copy(text = newText)
    updatedRows[rowIndex] = row.copy(cells = updatedCells)
    return copy(rows = updatedRows)
}

fun EditableTablePage.withRowIncluded(rowIndex: Int, included: Boolean): EditableTablePage {
    val updatedRows = rows.toMutableList()
    updatedRows[rowIndex] = updatedRows[rowIndex].copy(included = included)
    return copy(rows = updatedRows)
}

fun EditableTablePage.withSelectedForExport(selected: Boolean): EditableTablePage = copy(selectedForExport = selected)

/**
 * Verwirft alle Zelltext-/Zeilen-Edits der Seite, behält aber die aktuelle Export-Auswahl.
 * [original] ist der unbearbeitete Ausgangsstand (siehe [toDraftPage]) - funktioniert dadurch
 * unveraendert auch nach einem Draft-Restore, ohne erneute OCR-Geometrie zu benoetigen.
 */
fun EditableTablePage.resetTo(original: EditableTablePage): EditableTablePage =
    original.copy(selectedForExport = selectedForExport)
