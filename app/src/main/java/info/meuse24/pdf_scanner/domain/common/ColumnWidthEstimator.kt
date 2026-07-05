package info.meuse24.pdf_scanner.domain.common

/** Reine Konfiguration für [estimateColumnWidthsDp], damit Tuning keine Magic Numbers verteilt. */
data class ColumnWidthConfig(
    val minWidthDp: Int = 64,
    val maxWidthDp: Int = 240,
    val dpPerChar: Float = 9f,
    val paddingDp: Int = 16
)

/**
 * Schätzt pro Spalte eine feste Breite (in dp) aus der maximalen Zeichenzahl über alle Zeilen,
 * geclampt auf [ColumnWidthConfig.minWidthDp]/[ColumnWidthConfig.maxWidthDp]. Deterministisch und
 * ohne Compose-Abhängigkeit berechnet — die UI komponiert damit keine Zeile zur Breitenmessung
 * (kein `SubcomposeLayout`), das würde die Laziness des Review-Grids zerstören.
 */
fun estimateColumnWidthsDp(
    rows: List<List<String>>,
    columnCount: Int,
    config: ColumnWidthConfig = ColumnWidthConfig()
): List<Int> {
    if (columnCount <= 0) return emptyList()
    return (0 until columnCount).map { colIndex ->
        val maxChars = rows.maxOfOrNull { row -> row.getOrNull(colIndex)?.length ?: 0 } ?: 0
        val estimated = (maxChars * config.dpPerChar).toInt() + config.paddingDp
        estimated.coerceIn(config.minWidthDp, config.maxWidthDp)
    }
}
