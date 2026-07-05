package info.meuse24.pdf_scanner.domain.common

import org.junit.Assert.assertEquals
import org.junit.Test

class ColumnWidthEstimatorTest {

    @Test
    fun `Breite folgt der maximalen Zeichenzahl je Spalte`() {
        val rows = listOf(
            listOf("1", "Bürostuhl"),
            listOf("22", "Tisch")
        )

        val widths = estimateColumnWidthsDp(rows, columnCount = 2)

        // Spalte 0: max 2 Zeichen -> 2*9+16=34 -> geclampt auf minWidthDp(64).
        assertEquals(64, widths[0])
        // Spalte 1: max 9 Zeichen ("Bürostuhl") -> 9*9+16=97.
        assertEquals(97, widths[1])
    }

    @Test
    fun `sehr lange Zellen werden auf maxWidthDp geclampt`() {
        val longText = "x".repeat(100)
        val widths = estimateColumnWidthsDp(listOf(listOf(longText)), columnCount = 1)

        assertEquals(240, widths.single())
    }

    @Test
    fun `leere Zellen ergeben die Mindestbreite`() {
        val widths = estimateColumnWidthsDp(listOf(listOf("", "")), columnCount = 2)

        assertEquals(listOf(64, 64), widths)
    }

    @Test
    fun `fehlende Zellen in kuerzeren Zeilen werden wie leer behandelt`() {
        val rows = listOf(listOf("Lang genug fuer Spalte 0"), listOf("a", "b"))

        val widths = estimateColumnWidthsDp(rows, columnCount = 2)

        // Spalte 1 hat nur "b" (1 Zeichen) aus der zweiten Zeile - erste Zeile hat keine Spalte 1.
        assertEquals(64, widths[1])
    }

    @Test
    fun `columnCount 0 oder kleiner ergibt leere Liste`() {
        assertEquals(emptyList<Int>(), estimateColumnWidthsDp(listOf(listOf("a")), columnCount = 0))
    }

    @Test
    fun `eigene Config wird angewendet`() {
        val config = ColumnWidthConfig(minWidthDp = 40, maxWidthDp = 100, dpPerChar = 10f, paddingDp = 0)
        val widths = estimateColumnWidthsDp(listOf(listOf("12345")), columnCount = 1, config = config)

        assertEquals(50, widths.single())
    }
}
