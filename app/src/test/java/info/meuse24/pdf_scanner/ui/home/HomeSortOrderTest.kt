package info.meuse24.pdf_scanner.ui.home

import info.meuse24.pdf_scanner.data.local.ScanRecord
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeSortOrderTest {

    @Test
    fun `ByDate sorts newest first`() {
        val scans = listOf(
            scan(id = 1L, filename = "Gamma", timestamp = 100L, fileSize = 300L),
            scan(id = 2L, filename = "Alpha", timestamp = 300L, fileSize = 100L),
            scan(id = 3L, filename = "Beta", timestamp = 200L, fileSize = 200L)
        )

        val sorted = sortScans(scans, SortOrder.ByDate)

        assertEquals(listOf(2L, 3L, 1L), sorted.map { it.id })
    }

    @Test
    fun `ByName sorts alphabetically ignoring case`() {
        val scans = listOf(
            scan(id = 1L, filename = "zeta", timestamp = 100L, fileSize = 300L),
            scan(id = 2L, filename = "Alpha", timestamp = 300L, fileSize = 100L),
            scan(id = 3L, filename = "beta", timestamp = 200L, fileSize = 200L)
        )

        val sorted = sortScans(scans, SortOrder.ByName)

        assertEquals(listOf(2L, 3L, 1L), sorted.map { it.id })
    }

    @Test
    fun `BySize sorts largest first`() {
        val scans = listOf(
            scan(id = 1L, filename = "Gamma", timestamp = 100L, fileSize = 300L),
            scan(id = 2L, filename = "Alpha", timestamp = 300L, fileSize = 100L),
            scan(id = 3L, filename = "Beta", timestamp = 200L, fileSize = 200L)
        )

        val sorted = sortScans(scans, SortOrder.BySize)

        assertEquals(listOf(1L, 3L, 2L), sorted.map { it.id })
    }

    private fun scan(
        id: Long,
        filename: String,
        timestamp: Long,
        fileSize: Long
    ) = ScanRecord(
        id = id,
        filename = filename,
        filepath = "/tmp/$filename.pdf",
        timestamp = timestamp,
        pageCount = 1,
        fileSize = fileSize
    )
}
