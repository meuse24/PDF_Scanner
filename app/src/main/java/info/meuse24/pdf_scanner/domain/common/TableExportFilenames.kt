package info.meuse24.pdf_scanner.domain.common

/**
 * Baut die Exportdateinamen für ausgewählte Tabellenseiten: eine Seite → `<basis>_table.<ext>`,
 * mehrere Seiten → `<basis>_table_p001.<ext>`, `<basis>_table_p002.<ext>`, … — fortlaufend über
 * die exportierte Menge, nicht über die ursprüngliche PDF-Seitenzahl. CSV kennt weder
 * Arbeitsblätter noch eine standardisierte Seitentrennung, daher eine Datei pro Seite.
 */
fun buildTableExportFilenames(baseName: String, pageCount: Int, extension: String): List<String> {
    if (pageCount <= 0) return emptyList()
    return if (pageCount == 1) {
        listOf("${baseName}_table.$extension")
    } else {
        (1..pageCount).map { index -> "${baseName}_table_p${index.toString().padStart(3, '0')}.$extension" }
    }
}
