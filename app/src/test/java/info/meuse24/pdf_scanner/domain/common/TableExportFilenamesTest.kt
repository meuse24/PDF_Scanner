package info.meuse24.pdf_scanner.domain.common

import org.junit.Assert.assertEquals
import org.junit.Test

class TableExportFilenamesTest {

    @Test
    fun `eine Seite ergibt einen einzigen Dateinamen ohne Seitenzahl`() {
        assertEquals(listOf("scan_table.csv"), buildTableExportFilenames("scan", 1, "csv"))
    }

    @Test
    fun `mehrere Seiten werden fortlaufend mit dreistelliger Seitenzahl nummeriert`() {
        assertEquals(
            listOf("scan_table_p001.tsv", "scan_table_p002.tsv", "scan_table_p003.tsv"),
            buildTableExportFilenames("scan", 3, "tsv")
        )
    }

    @Test
    fun `null Seiten ergibt eine leere Liste`() {
        assertEquals(emptyList<String>(), buildTableExportFilenames("scan", 0, "csv"))
    }
}
