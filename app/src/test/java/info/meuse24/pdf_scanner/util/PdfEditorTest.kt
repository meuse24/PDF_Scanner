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
}
