package info.meuse24.pdf_scanner.ui.tableexport

import info.meuse24.pdf_scanner.domain.model.ExtractedCell
import info.meuse24.pdf_scanner.domain.model.ExtractedRow
import info.meuse24.pdf_scanner.domain.model.ExtractedTable
import info.meuse24.pdf_scanner.domain.model.OcrRect
import info.meuse24.pdf_scanner.domain.model.TableIssue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EditableTableTest {

    private fun sampleTable(): ExtractedTable = ExtractedTable(
        pageIndex = 2,
        rows = listOf(
            ExtractedRow(
                cells = listOf(
                    ExtractedCell("1", listOf(0), 0.9f),
                    ExtractedCell("Stuhl", listOf(1), 0.9f, issues = setOf(TableIssue.LOW_CONFIDENCE))
                )
            ),
            ExtractedRow(cells = listOf(ExtractedCell("2", listOf(2), 0.9f), ExtractedCell("Tisch", listOf(3), 0.9f)))
        ),
        columnBounds = listOf(OcrRect(0f, 0f, 50f, 20f), OcrRect(50f, 0f, 150f, 20f)),
        confidence = 0.85f,
        issues = setOf(TableIssue.LOW_CONFIDENCE)
    )

    @Test
    fun `toEditablePage uebernimmt Zellwerte Issues und Seitenindex`() {
        val editable = sampleTable().toEditablePage()

        assertEquals(2, editable.pageIndex)
        assertEquals(listOf("1", "Stuhl"), editable.rows[0].cells.map { it.text })
        assertTrue(editable.rows[0].cells[1].issues.contains(TableIssue.LOW_CONFIDENCE))
        assertTrue(editable.hasIssues)
        assertEquals(2, editable.columnWidthsDp.size)
        assertTrue(editable.rows.all { it.included })
        assertTrue(editable.selectedForExport)
    }

    @Test
    fun `withCellText aendert nur die Zielzelle`() {
        val editable = sampleTable().toEditablePage()

        val updated = editable.withCellText(rowIndex = 0, columnIndex = 1, newText = "Bürostuhl")

        assertEquals("Bürostuhl", updated.rows[0].cells[1].text)
        assertEquals("1", updated.rows[0].cells[0].text)
        assertEquals("2", updated.rows[1].cells[0].text)
    }

    @Test
    fun `withRowIncluded schliesst nur die Zielzeile aus`() {
        val editable = sampleTable().toEditablePage()

        val updated = editable.withRowIncluded(rowIndex = 1, included = false)

        assertTrue(updated.rows[0].included)
        assertFalse(updated.rows[1].included)
    }

    @Test
    fun `toCsvPage liefert nur eingeschlossene Zeilen als reine Zellwerte`() {
        val editable = sampleTable().toEditablePage()
            .withRowIncluded(rowIndex = 1, included = false)
            .withCellText(rowIndex = 0, columnIndex = 1, newText = "Bürostuhl")

        val csvPage = editable.toCsvPage()

        assertEquals(listOf(listOf("1", "Bürostuhl")), csvPage.rows)
    }

    @Test
    fun `resetTo verwirft Edits behaelt aber die Export-Auswahl`() {
        val original = sampleTable()
        val edited = original.toEditablePage()
            .withCellText(rowIndex = 0, columnIndex = 1, newText = "geaendert")
            .withRowIncluded(rowIndex = 1, included = false)
            .withSelectedForExport(false)

        val reset = edited.resetTo(original.toEditablePage())

        assertEquals("Stuhl", reset.rows[0].cells[1].text)
        assertTrue(reset.rows[1].included)
        assertFalse(reset.selectedForExport)
    }

    @Test
    fun `Draft-Roundtrip erhaelt Zelltexte Zeilen- Seitenauswahl und Issues`() {
        val editable = sampleTable().toEditablePage()
            .withCellText(rowIndex = 0, columnIndex = 1, newText = "Bürostuhl")
            .withRowIncluded(rowIndex = 1, included = false)
            .withSelectedForExport(false)

        val draftPage = editable.toDraftPage()
        val restored = draftPage.toEditablePage()

        assertEquals(editable.pageIndex, restored.pageIndex)
        assertEquals(editable.selectedForExport, restored.selectedForExport)
        assertEquals(editable.rows.map { it.included }, restored.rows.map { it.included })
        assertEquals(editable.rows.map { row -> row.cells.map { it.text } }, restored.rows.map { row -> row.cells.map { it.text } })
        assertTrue(restored.rows[0].cells[1].issues.contains(TableIssue.LOW_CONFIDENCE))
        assertTrue(restored.hasIssues)
    }

    @Test
    fun `toDraftPage mit eigenem Original ermoeglicht Reset nach Restore`() {
        val original = sampleTable().toEditablePage()
        val edited = original
            .withCellText(rowIndex = 0, columnIndex = 1, newText = "geaendert")
            .withRowIncluded(rowIndex = 1, included = false)

        // So wie TableExportViewModel.saveDraftNow() es beim Speichern tut: der Draft traegt
        // sowohl den bearbeiteten Stand als auch den unbearbeiteten Ausgangsstand.
        val draftPage = edited.toDraftPage(original = original)

        // So wie TableExportViewModel.continueDraft() den Ausgangsstand nach einem Prozess-Restart
        // aus dem Draft rekonstruiert, ohne erneute OCR-Geometrie zu benoetigen.
        val restoredOriginal = draftPage.copy(rows = draftPage.originalRows).toEditablePage()
        val restoredEdited = draftPage.toEditablePage()

        val reset = restoredEdited.resetTo(restoredOriginal)

        assertEquals("Stuhl", reset.rows[0].cells[1].text)
        assertTrue(reset.rows[1].included)
        assertTrue(reset.rows[0].cells[1].issues.contains(TableIssue.LOW_CONFIDENCE))
    }

    @Test
    fun `rein tabellenweite Issue ohne Zell-Issue uebersteht den Draft-Roundtrip`() {
        // TableReconstructor kann LOW_CONFIDENCE ausschliesslich auf Tabellenebene setzen (aus der
        // gemittelten Zellkonfidenz), ohne dass irgendeine einzelne Zelle markiert ist. hasIssues
        // darf sich deshalb nicht aus den Zell-Issues neu berechnen lassen.
        val table = ExtractedTable(
            pageIndex = 0,
            rows = listOf(ExtractedRow(cells = listOf(ExtractedCell("1", listOf(0), 0.5f), ExtractedCell("2", listOf(1), 0.5f)))),
            columnBounds = listOf(OcrRect(0f, 0f, 50f, 20f), OcrRect(50f, 0f, 150f, 20f)),
            confidence = 0.5f,
            issues = setOf(TableIssue.LOW_CONFIDENCE)
        )
        val editable = table.toEditablePage()
        assertTrue("Vorbedingung: keine einzelne Zelle traegt ein Issue", editable.rows.all { row -> row.cells.all { it.issues.isEmpty() } })
        assertTrue(editable.hasIssues)

        val restored = editable.toDraftPage().toEditablePage()

        assertTrue("Tabellenweite Issue darf beim Restore nicht verloren gehen", restored.hasIssues)
        assertTrue(restored.tableIssues.contains(TableIssue.LOW_CONFIDENCE))
    }
}
