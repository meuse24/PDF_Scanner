package info.meuse24.pdf_scanner.domain.usecase

/**
 * Repräsentiert einen einzelnen Marker-Strich in normalisierten Koordinaten (0..1).
 * [points] enthält die Punktfolge als (x, y)-Paare relativ zur Seitengröße.
 * [pageIndex] gibt die 0-basierte Seite an.
 * [strokeWidthFraction] ist die Strichbreite als Anteil der Seitenbreite.
 */
data class HighlightStroke(
    val points: List<Pair<Float, Float>>,
    val pageIndex: Int,
    val strokeWidthFraction: Float = 0.025f
)
