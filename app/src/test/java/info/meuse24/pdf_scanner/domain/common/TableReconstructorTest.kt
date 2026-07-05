package info.meuse24.pdf_scanner.domain.common

import info.meuse24.pdf_scanner.domain.model.ExtractedTable
import info.meuse24.pdf_scanner.domain.model.OcrElement
import info.meuse24.pdf_scanner.domain.model.OcrLine
import info.meuse24.pdf_scanner.domain.model.OcrPositionedPage
import info.meuse24.pdf_scanner.domain.model.OcrRect
import info.meuse24.pdf_scanner.domain.model.TableIssue
import kotlin.math.cos
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val ROW_HEIGHT = 20f
private const val ROW_PITCH = 30f
private val COLUMN_LEFTS = listOf(0f, 150f, 400f)
private val COLUMN_WIDTHS = listOf(100f, 200f, 150f)

private fun rect(left: Float, top: Float, width: Float, height: Float = ROW_HEIGHT) =
    OcrRect(left, top, left + width, top + height)

private fun unionRect(a: OcrRect, b: OcrRect) = OcrRect(
    minOf(a.left, b.left),
    minOf(a.top, b.top),
    maxOf(a.right, b.right),
    maxOf(a.bottom, b.bottom)
)

private fun element(
    text: String,
    left: Float,
    top: Float,
    width: Float,
    height: Float = ROW_HEIGHT,
    confidence: Float = 0.9f,
    angle: Float = 0f
) = OcrElement(text, rect(left, top, width, height), confidence, angle)

private fun lineOf(vararg elements: OcrElement, confidence: Float = 0.9f, angle: Float = 0f): OcrLine {
    val bounds = elements.map { it.bounds }.reduce(::unionRect)
    return OcrLine(elements.joinToString(" ") { it.text }, bounds, elements.toList(), confidence, angle)
}

private fun page(lines: List<OcrLine>, pageIndex: Int = 0, widthPx: Int = 600, heightPx: Int = 800) =
    OcrPositionedPage(pageIndex, widthPx, heightPx, lines)

private fun ExtractedTable.cellTexts(): List<List<String>> = rows.map { row -> row.cells.map { it.text } }

private val gridRowContents = listOf(
    listOf("1", "Bürostuhl", "99,00"),
    listOf("2", "Schreibtisch", "249,00"),
    listOf("3", "Monitor", "179,00"),
    listOf("4", "Tastatur", "39,00")
)

private fun gridRowLines(rowIdx: Int, top: Float, contents: List<String> = gridRowContents[rowIdx]): List<OcrLine> =
    contents.mapIndexed { colIdx, text ->
        lineOf(element(text, COLUMN_LEFTS[colIdx], top, COLUMN_WIDTHS[colIdx]))
    }

private fun perfectGridLines(): List<OcrLine> =
    gridRowContents.indices.flatMap { rowIdx -> gridRowLines(rowIdx, rowIdx * ROW_PITCH) }

class TableReconstructorTest {

    @Test
    fun `perfektes Raster wird vollstaendig rekonstruiert`() {
        val result = reconstructTable(page(perfectGridLines()))

        assertNotNull(result)
        checkNotNull(result)
        assertEquals(0, result.pageIndex)
        assertEquals(3, result.columnBounds.size)
        assertEquals(gridRowContents, result.cellTexts())
        assertTrue(result.issues.isEmpty())
    }

    @Test
    fun `y-Jitter und ungeordnete Eingabe liefern dasselbe Ergebnis`() {
        val baseline = reconstructTable(page(perfectGridLines()))
        checkNotNull(baseline)

        val jitteredAndShuffled = gridRowContents.indices.flatMap { rowIdx ->
            val jitter = listOf(1.5f, -1.5f, 0.5f)[rowIdx % 3]
            gridRowLines(rowIdx, rowIdx * ROW_PITCH + jitter)
        }.shuffled(kotlin.random.Random(42))

        val result = reconstructTable(page(jitteredAndShuffled))

        assertNotNull(result)
        checkNotNull(result)
        assertEquals(baseline.cellTexts(), result.cellTexts())
    }

    @Test
    fun `Ergebnis ist unabhaengig von der Eingabereihenfolge`() {
        val original = reconstructTable(page(perfectGridLines()))
        val reversed = reconstructTable(page(perfectGridLines().reversed()))

        checkNotNull(original)
        checkNotNull(reversed)
        // sourceLineIds referenzieren die Position in der jeweils übergebenen Line-Liste und
        // duerfen sich daher unterscheiden; Zellinhalt, Struktur und Score muessen gleich sein.
        assertEquals(original.cellTexts(), reversed.cellTexts())
        assertEquals(original.columnBounds, reversed.columnBounds)
        assertEquals(original.confidence, reversed.confidence, 0.0001f)
        assertEquals(original.issues, reversed.issues)
    }

    @Test
    fun `leere Zellen bleiben als leerer String erhalten`() {
        val lines = gridRowContents.indices.flatMap { rowIdx ->
            val top = rowIdx * ROW_PITCH
            if (rowIdx == 2) {
                // Mittlere Spalte (Beschreibung) fehlt in dieser Zeile komplett.
                listOf(
                    lineOf(element(gridRowContents[rowIdx][0], COLUMN_LEFTS[0], top, COLUMN_WIDTHS[0])),
                    lineOf(element(gridRowContents[rowIdx][2], COLUMN_LEFTS[2], top, COLUMN_WIDTHS[2]))
                )
            } else {
                gridRowLines(rowIdx, top)
            }
        }

        val result = reconstructTable(page(lines))

        assertNotNull(result)
        checkNotNull(result)
        assertEquals("3", result.rows[2].cells[0].text)
        assertEquals("", result.rows[2].cells[1].text)
        assertEquals("179,00", result.rows[2].cells[2].text)
    }

    @Test
    fun `enge Spalten werden per Element-Fallback getrennt`() {
        // Alle drei Zellen einer Zeile landen in einer einzigen ML-Kit-Line (enge Spalten),
        // mit Char-Breite 20 und einem Zwischenraum von 40 (> 1.6 * 20 = 32).
        val narrowColumnLefts = listOf(0f, 60f, 280f)
        val narrowColumnWidths = listOf(20f, 180f, 150f)
        val lines = gridRowContents.mapIndexed { rowIdx, contents ->
            val top = rowIdx * ROW_PITCH
            lineOf(
                *contents.mapIndexed { colIdx, text ->
                    element(text, narrowColumnLefts[colIdx], top, narrowColumnWidths[colIdx])
                }.toTypedArray()
            )
        }

        val result = reconstructTable(page(lines))

        assertNotNull(result)
        checkNotNull(result)
        assertEquals(gridRowContents, result.cellTexts())
        // Jede Zelle stammt aus einer aufgesplitteten Line -> als Fallback markiert.
        result.rows.forEach { row ->
            row.cells.forEach { cell ->
                assertTrue(cell.issues.contains(TableIssue.SPANNING_LINE))
            }
        }
    }

    @Test
    fun `mehrzeilige Beschreibung wird als umgebrochene Zelle angehaengt`() {
        val lines = mutableListOf<OcrLine>()
        lines += gridRowLines(0, 0f, listOf("1", "Bürostuhl schwarz", "99,00"))
        // Zeile B: nur die Beschreibungsspalte, schmale Folgezeile -> WRAPPED_ROW_GUESS.
        lines += lineOf(element("mit Rollen", COLUMN_LEFTS[1], ROW_PITCH, 150f))
        lines += gridRowLines(2, 2 * ROW_PITCH, listOf("2", "Schreibtisch", "249,00"))
        lines += gridRowLines(3, 3 * ROW_PITCH, listOf("3", "Monitor", "179,00"))

        val result = reconstructTable(page(lines))

        assertNotNull(result)
        checkNotNull(result)
        assertEquals(3, result.rows.size)
        assertEquals("Bürostuhl schwarz mit Rollen", result.rows[0].cells[1].text)
        assertTrue(result.rows[0].cells[1].issues.contains(TableIssue.WRAPPED_ROW_GUESS))
    }

    @Test
    fun `leicht gedrehter Scan wird deskewt und korrekt rekonstruiert`() {
        val angleDeg = 2f
        val pivotX = 300f
        val pivotY = 400f
        val rotatedRowPitch = 45f

        // Realistische Fixture: wie ML Kit liefert nur die Achsen-parallele Bounding-Box der
        // (physisch rotierten) Box selbst um die Pivotachse rotiert -> exakt dieselbe
        // Ecken-Rotations-Formel wie TableReconstructor.rotateRect, nicht nur eine
        // Mittelpunkt-Translation. Das bildet nach, dass ML-Kit-Boxen fuer schraeg eingescannte
        // Zeilen bereits als aufgeblähte Achsen-parallele Box geliefert werden; die Rueck-Projektion
        // im Deskew-Schritt ist deshalb nur naeherungsweise (nicht bit-exakt) invers.
        fun rotatedBBox(r: OcrRect): OcrRect {
            val rad = Math.toRadians(angleDeg.toDouble())
            val cosA = cos(rad)
            val sinA = sin(rad)
            fun rotatePoint(x: Float, y: Float): Pair<Float, Float> {
                val dx = (x - pivotX).toDouble()
                val dy = (y - pivotY).toDouble()
                val rx = dx * cosA - dy * sinA
                val ry = dx * sinA + dy * cosA
                return (rx + pivotX).toFloat() to (ry + pivotY).toFloat()
            }
            val corners = listOf(r.left to r.top, r.right to r.top, r.right to r.bottom, r.left to r.bottom)
                .map { (x, y) -> rotatePoint(x, y) }
            return OcrRect(corners.minOf { it.first }, corners.minOf { it.second }, corners.maxOf { it.first }, corners.maxOf { it.second })
        }

        val rotatedLines = gridRowContents.indices.flatMap { rowIdx ->
            val top = rowIdx * rotatedRowPitch
            gridRowContents[rowIdx].mapIndexed { colIdx, text ->
                val el = element(text, COLUMN_LEFTS[colIdx], top, COLUMN_WIDTHS[colIdx], angle = angleDeg)
                val rotatedEl = el.copy(bounds = rotatedBBox(el.bounds))
                OcrLine(rotatedEl.text, rotatedEl.bounds, listOf(rotatedEl), rotatedEl.confidence, angleDeg)
            }
        }

        val result = reconstructTable(page(rotatedLines, widthPx = (2 * pivotX).toInt(), heightPx = (2 * pivotY).toInt()))

        assertNotNull(result)
        checkNotNull(result)
        assertEquals(gridRowContents, result.cellTexts())

        // Geometrie-Pruefung: nicht nur der Zelltext, auch die rueckprojizierten Spalten muessen
        // (bis auf das erwartete, durch doppeltes Bounding-Box-Bloating bedingte Restrauschen)
        // wieder in etwa der Ideal-Geometrie des ungedrehten Rasters entsprechen.
        val idealSeparatorLeft = (COLUMN_LEFTS[0] + COLUMN_WIDTHS[0] + COLUMN_LEFTS[1]) / 2f
        val idealSeparatorRight = (COLUMN_LEFTS[1] + COLUMN_WIDTHS[1] + COLUMN_LEFTS[2]) / 2f
        val geometryTolerance = 12f

        assertEquals(3, result.columnBounds.size)
        assertEquals(0f, result.columnBounds.first().left, geometryTolerance)
        assertEquals(idealSeparatorLeft, result.columnBounds[0].right, geometryTolerance)
        assertEquals(idealSeparatorLeft, result.columnBounds[1].left, geometryTolerance)
        assertEquals(idealSeparatorRight, result.columnBounds[1].right, geometryTolerance)
        assertEquals(idealSeparatorRight, result.columnBounds[2].left, geometryTolerance)
        assertEquals(COLUMN_LEFTS[2] + COLUMN_WIDTHS[2], result.columnBounds.last().right, geometryTolerance)

        val idealTop = 0f
        val idealBottom = (gridRowContents.size - 1) * rotatedRowPitch + ROW_HEIGHT
        result.columnBounds.forEach { column ->
            assertEquals(idealTop, column.top, geometryTolerance)
            assertEquals(idealBottom, column.bottom, geometryTolerance)
        }
    }

    @Test
    fun `Briefkopf und Fusszeile fliessen nicht in die Tabelle ein`() {
        val header = lineOf(element("Rechnung Nr. 4711", 0f, -60f, 550f))
        val footer = lineOf(element("Summe: 566,00", 0f, 4 * ROW_PITCH, 550f))
        val lines = listOf(header) + perfectGridLines() + listOf(footer)

        val result = reconstructTable(page(lines))

        assertNotNull(result)
        checkNotNull(result)
        assertEquals(gridRowContents, result.cellTexts())
        val allText = result.cellTexts().flatten().joinToString(" ")
        assertFalse(allText.contains("Rechnung"))
        assertFalse(allText.contains("Summe"))
    }

    @Test
    fun `schmaler aber weit entfernter Footer wird nicht als Zellumbruch angehaengt`() {
        // Schmal genug, um die reine Breiten-Heuristik zu erfuellen (< 0.6 * Tabellenbreite),
        // aber weit unterhalb der letzten echten Zeile -> darf nicht in die letzte Zelle rutschen.
        val farFooter = lineOf(element("Seite 1", COLUMN_LEFTS[1], 4 * ROW_PITCH + 200f, 100f))
        val lines = perfectGridLines() + listOf(farFooter)

        val result = reconstructTable(page(lines))

        assertNotNull(result)
        checkNotNull(result)
        assertEquals(gridRowContents, result.cellTexts())
        val allText = result.cellTexts().flatten().joinToString(" ")
        assertFalse(allText.contains("Seite"))
    }

    @Test
    fun `mehrteilige Textzeilen ohne wiederkehrende Spaltengeometrie ergeben keine Tabelle`() {
        // Jede Zeile hat zufaellig >= 2 Segmente, aber die Luecken liegen an voellig
        // unterschiedlichen x-Positionen -> keine wiederkehrende Spaltengrenze.
        val lines = listOf(
            lineOf(element("Diese", 0f, 0f, 80f)) to lineOf(element("Rechnung", 200f, 0f, 80f)),
            lineOf(element("wurde am", 0f, ROW_PITCH, 180f)) to lineOf(element("erstellt", 350f, ROW_PITCH, 100f)),
            lineOf(element("Montag", 0f, 2 * ROW_PITCH, 40f)) to lineOf(element("uebergeben", 120f, 2 * ROW_PITCH, 380f)),
            lineOf(element("und noch am", 0f, 3 * ROW_PITCH, 300f)) to lineOf(element("selben Tag", 380f, 3 * ROW_PITCH, 40f))
        ).flatMap { (a, b) -> listOf(a, b) }

        val result = reconstructTable(page(lines))

        assertNull(result)
    }

    @Test
    fun `beste von zwei konkurrierenden Tabellenregionen gewinnt`() {
        val smallTable = listOf(
            listOf("A", "X"),
            listOf("B", "Y"),
            listOf("C", "Z")
        ).mapIndexed { rowIdx, contents ->
            contents.mapIndexed { colIdx, text -> element(text, listOf(0f, 150f)[colIdx], rowIdx * ROW_PITCH, listOf(100f, 100f)[colIdx]) }
                .map { lineOf(it) }
        }.flatten()

        val paragraphGap = listOf(
            lineOf(element("Zwischentext eins der Erklärung.", 0f, 150f, 500f)),
            lineOf(element("Zwischentext zwei der Erklärung.", 0f, 180f, 500f))
        )

        val largeTable = gridRowContents.indices.flatMap { rowIdx ->
            gridRowLines(rowIdx, 250f + rowIdx * ROW_PITCH)
        }

        val result = reconstructTable(page(smallTable + paragraphGap + largeTable))

        assertNotNull(result)
        checkNotNull(result)
        assertEquals(gridRowContents, result.cellTexts())
        val allText = result.cellTexts().flatten().joinToString(" ")
        assertFalse(allText.contains("X"))
    }

    @Test
    fun `reiner Fliesstext ergibt keine Tabelle`() {
        val lines = listOf(
            "Dies ist ein Absatz ohne jede Tabellenstruktur,",
            "der sich über mehrere Zeilen erstreckt und",
            "nur aus normalem Fließtext besteht."
        ).mapIndexed { idx, text -> lineOf(element(text, 0f, idx * ROW_PITCH, 500f)) }

        val result = reconstructTable(page(lines))

        assertNull(result)
    }

    @Test
    fun `leere Seite ergibt keine Tabelle`() {
        val result = reconstructTable(page(emptyList()))

        assertNull(result)
    }

    @Test
    fun `mehrdeutige Spaltenzuordnung wird als Issue markiert`() {
        // Referenzzeile (Zeile 0) bestimmt Spalten: Separatoren bei 125 und 375.
        val lines = mutableListOf<OcrLine>()
        lines += gridRowLines(0, 0f)
        // Zeile 1: Spalte 0 ist leer, das einzige Element strad­delt die Spaltengrenze bei 125
        // nahezu 50/50 -> keine Konkurrenz durch ein anderes Element derselben Spalte.
        lines += listOf(
            lineOf(element("XYZ", 105f, ROW_PITCH, 40f)),
            lineOf(element("249,00", COLUMN_LEFTS[2], ROW_PITCH, COLUMN_WIDTHS[2]))
        )
        lines += gridRowLines(2, 2 * ROW_PITCH)
        lines += gridRowLines(3, 3 * ROW_PITCH)

        val result = reconstructTable(page(lines))

        assertNotNull(result)
        checkNotNull(result)
        val ambiguousCell = result.rows[1].cells.first { it.text == "XYZ" }
        assertTrue(ambiguousCell.issues.contains(TableIssue.AMBIGUOUS_COLUMN))
    }
}
