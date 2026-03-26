package info.meuse24.pdf_scanner.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Unit-Tests für die JVM-testbaren Hilfsfunktionen buildRanges und
 * resolveUniqueFilename. Diese Funktionen sind top-level internal und
 * verwenden keine Android-Klassen.
 */
class PdfEditorTest {

    @get:Rule
    val tmpFolder = TemporaryFolder()

    // ── buildRanges ──────────────────────────────────────────────────────────

    @Test
    fun `buildRanges single split point produces two ranges`() {
        val ranges = buildRanges(pageCount = 4, splitPoints = listOf(1))
        assertEquals(2, ranges.size)
        assertEquals(0 until 2, ranges[0])
        assertEquals(2 until 4, ranges[1])
    }

    @Test
    fun `buildRanges two split points produces three ranges`() {
        val ranges = buildRanges(pageCount = 6, splitPoints = listOf(1, 3))
        assertEquals(3, ranges.size)
        assertEquals(0 until 2, ranges[0])
        assertEquals(2 until 4, ranges[1])
        assertEquals(4 until 6, ranges[2])
    }

    @Test
    fun `buildRanges split every two pages produces equal ranges`() {
        val ranges = buildRanges(pageCount = 6, splitPoints = listOf(1, 3))
        ranges.forEach { assertEquals(2, it.last - it.first + 1) }
    }

    @Test
    fun `buildRanges no empty ranges for valid split points`() {
        val ranges = buildRanges(pageCount = 5, splitPoints = listOf(0, 2, 4))
        assertTrue(ranges.none { it.isEmpty() })
    }

    @Test
    fun `buildRanges empty split points returns single range covering all pages`() {
        val ranges = buildRanges(pageCount = 3, splitPoints = emptyList())
        assertEquals(1, ranges.size)
        assertEquals(0 until 3, ranges[0])
    }

    @Test
    fun `buildRanges split at last valid index produces two ranges`() {
        val ranges = buildRanges(pageCount = 3, splitPoints = listOf(1))
        assertEquals(2, ranges.size)
        assertEquals(0 until 2, ranges[0])
        assertEquals(2 until 3, ranges[1])
    }

    @Test
    fun `buildRanges total page count preserved across all ranges`() {
        val pageCount = 10
        val ranges = buildRanges(pageCount, listOf(2, 5, 8))
        val total = ranges.sumOf { it.last - it.first + 1 }
        assertEquals(pageCount, total)
    }

    @Test
    fun `normalizeSplitPoints keeps split after first page`() {
        val normalized = normalizeSplitPoints(pageCount = 4, splitPoints = listOf(0, 2))
        assertEquals(listOf(0, 2), normalized)
    }

    @Test
    fun `normalizeSplitPoints removes last page and duplicates`() {
        val normalized = normalizeSplitPoints(pageCount = 4, splitPoints = listOf(0, 3, 0, 2))
        assertEquals(listOf(0, 2), normalized)
    }

    @Test
    fun `mergeTextBoxesToLines fasst boxes derselben zeile zusammen`() {
        val lines = mergeTextBoxesToLines(
            boxes = listOf(
                NormalizedTextBox(left = 0.10f, top = 0.10f, right = 0.20f, bottom = 0.18f),
                NormalizedTextBox(left = 0.22f, top = 0.11f, right = 0.35f, bottom = 0.19f),
                NormalizedTextBox(left = 0.40f, top = 0.10f, right = 0.52f, bottom = 0.18f)
            ),
            pageIndex = 2
        )

        assertEquals(1, lines.size)
        assertEquals(0.10f, lines.single().left, 0.001f)
        assertEquals(0.10f, lines.single().top, 0.001f)
        assertEquals(0.52f, lines.single().right, 0.001f)
        assertEquals(0.19f, lines.single().bottom, 0.001f)
        assertEquals(2, lines.single().pageIndex)
    }

    @Test
    fun `mergeTextBoxesToLines trennt boxes auf unterschiedlichen zeilen`() {
        val lines = mergeTextBoxesToLines(
            boxes = listOf(
                NormalizedTextBox(left = 0.10f, top = 0.10f, right = 0.20f, bottom = 0.18f),
                NormalizedTextBox(left = 0.10f, top = 0.30f, right = 0.25f, bottom = 0.38f)
            ),
            pageIndex = 0
        )

        assertEquals(2, lines.size)
        assertEquals(0.10f, lines[0].top, 0.001f)
        assertEquals(0.30f, lines[1].top, 0.001f)
    }

    // ── resolveUniqueFilename ────────────────────────────────────────────────

    @Test
    fun `resolveUniqueFilename returns base name when no conflict`() {
        val dir = tmpFolder.newFolder("scans")
        assertEquals("Doc", resolveUniqueFilename(dir, "Doc"))
    }

    @Test
    fun `resolveUniqueFilename appends _2 on first conflict`() {
        val dir = tmpFolder.newFolder("scans")
        File(dir, "Doc.pdf").createNewFile()
        assertEquals("Doc_2", resolveUniqueFilename(dir, "Doc"))
    }

    @Test
    fun `resolveUniqueFilename increments past existing counters`() {
        val dir = tmpFolder.newFolder("scans")
        File(dir, "Doc.pdf").createNewFile()
        File(dir, "Doc_2.pdf").createNewFile()
        File(dir, "Doc_3.pdf").createNewFile()
        assertEquals("Doc_4", resolveUniqueFilename(dir, "Doc"))
    }

    @Test
    fun `resolveUniqueFilename skips gap in sequence`() {
        val dir = tmpFolder.newFolder("scans")
        File(dir, "Doc.pdf").createNewFile()
        // Doc_2 existiert nicht — trotzdem _2 zurückgeben
        assertEquals("Doc_2", resolveUniqueFilename(dir, "Doc"))
    }

    @Test
    fun `resolveUniqueFilename different base names do not interfere`() {
        val dir = tmpFolder.newFolder("scans")
        File(dir, "Alpha.pdf").createNewFile()
        // Beta hat keinen Konflikt
        assertEquals("Beta", resolveUniqueFilename(dir, "Beta"))
    }

    @Test
    fun `resolveUniqueFilename handles special characters in name`() {
        val dir = tmpFolder.newFolder("scans")
        val name = "Scan 2024-01"
        assertEquals(name, resolveUniqueFilename(dir, name))
    }

    @Test
    fun `calculateWatermarkFontSize clamps to lower bound`() {
        assertEquals(18f, calculateWatermarkFontSize(pageWidth = 120f, textLength = 80), 0.001f)
    }

    @Test
    fun `calculateWatermarkFontSize clamps to upper bound`() {
        assertEquals(42f, calculateWatermarkFontSize(pageWidth = 1200f, textLength = 4), 0.001f)
    }

    // ── mapDisplayToPdfCoord ──────────────────────────────────────────────────

    private val W = 200f
    private val H = 300f

    @Test
    fun `mapDisplayToPdfCoord rotation 0 top-left maps to top-left in PDF`() {
        val (x, y) = mapDisplayToPdfCoord(0f, 0f, W, H, 0)
        assertEquals(0f,  x, 0.001f)
        assertEquals(300f, y, 0.001f)   // pdfY = H*(1-0) = H
    }

    @Test
    fun `mapDisplayToPdfCoord rotation 0 center maps to center`() {
        val (x, y) = mapDisplayToPdfCoord(0.5f, 0.5f, W, H, 0)
        assertEquals(100f, x, 0.001f)
        assertEquals(150f, y, 0.001f)
    }

    @Test
    fun `mapDisplayToPdfCoord rotation 90 top-left maps to bottom-left in PDF`() {
        // display top-left (0,0) → pdfX=ny*W=0, pdfY=nx*H=0  → PDF origin
        val (x, y) = mapDisplayToPdfCoord(0f, 0f, W, H, 90)
        assertEquals(0f, x, 0.001f)
        assertEquals(0f, y, 0.001f)
    }

    @Test
    fun `mapDisplayToPdfCoord rotation 90 center maps to center`() {
        val (x, y) = mapDisplayToPdfCoord(0.5f, 0.5f, W, H, 90)
        assertEquals(100f, x, 0.001f)   // ny*W = 0.5*200
        assertEquals(150f, y, 0.001f)   // nx*H = 0.5*300
    }

    @Test
    fun `mapDisplayToPdfCoord rotation 180 top-left maps to bottom-right in PDF`() {
        val (x, y) = mapDisplayToPdfCoord(0f, 0f, W, H, 180)
        assertEquals(200f, x, 0.001f)   // (1-0)*W
        assertEquals(300f, y, 0.001f)   // (1-0)*H
    }

    @Test
    fun `mapDisplayToPdfCoord rotation 270 top-left maps to top-right in PDF`() {
        // display (0,0) → pdfX=(1-ny)*W=(1-0)*200=200, pdfY=(1-nx)*H=(1-0)*300=300
        val (x, y) = mapDisplayToPdfCoord(0f, 0f, W, H, 270)
        assertEquals(200f, x, 0.001f)
        assertEquals(300f, y, 0.001f)
    }

    @Test
    fun `mapDisplayToPdfCoord rotation 270 center maps to center`() {
        val (x, y) = mapDisplayToPdfCoord(0.5f, 0.5f, W, H, 270)
        assertEquals(100f, x, 0.001f)
        assertEquals(150f, y, 0.001f)
    }

    @Test
    fun `mapDisplayToPdfCoord unknown rotation falls back to rotation 0`() {
        val (x0, y0) = mapDisplayToPdfCoord(0.3f, 0.7f, W, H, 0)
        val (xUnk, yUnk) = mapDisplayToPdfCoord(0.3f, 0.7f, W, H, 45)
        assertEquals(x0, xUnk, 0.001f)
        assertEquals(y0, yUnk, 0.001f)
    }
}
