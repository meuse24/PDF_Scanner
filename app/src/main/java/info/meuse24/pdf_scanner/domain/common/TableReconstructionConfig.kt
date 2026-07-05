package info.meuse24.pdf_scanner.domain.common

/**
 * Alle Schwellwerte und Toleranzen des [TableReconstructor]s an einem Ort, damit Tuning und Tests
 * keine über den Algorithmus verstreuten Magic Numbers anfassen müssen.
 */
data class TableReconstructionConfig(
    /** Mindestanzahl an Spalten, damit eine Zeile als Tabellenzeile zählt. */
    val minColumns: Int = 2,
    /** Mindestanzahl an "echten" (nicht umgebrochenen) Tabellenzeilen für eine gültige Region. */
    val minSupportingRows: Int = 3,
    /** Vertikale Überlappung (relativ zur kleineren Höhe), ab der zwei Lines derselben Zeile zugeordnet werden. */
    val rowOverlapRatioThreshold: Float = 0.3f,
    /** Toleranz für den Center-Y-Abstand zweier Lines derselben Zeile, als Vielfaches der Median-Zeilenhöhe. */
    val rowBaselineToleranceFactor: Float = 0.6f,
    /** Ab diesem Medianwinkel (Grad) wird die Seite vor Zeilen-/Spaltenanalyse deskewt. */
    val relevantSkewDegrees: Float = 0.75f,
    /** Ein interner Element-Abstand muss dieses Vielfache der robusten Zeichenbreite überschreiten, um als Spaltengrenze zu gelten. */
    val columnGapCharWidthFactor: Float = 1.6f,
    /** Zwei Spalten-Überlappungswerte innerhalb dieser Differenz gelten als mehrdeutig (AMBIGUOUS_COLUMN). */
    val ambiguousOverlapMarginRatio: Float = 0.15f,
    /** Tabellen-Konfidenz unterhalb dieser Schwelle erhält das Issue LOW_CONFIDENCE. */
    val lowConfidenceThreshold: Float = 0.35f,
    /** Eine einzelne, schmale Folgezeile wird nur dann als umgebrochene Zelle gewertet, wenn sie schmäler ist als dieser Anteil der Tabellenbreite. */
    val wrappedRowMaxWidthRatio: Float = 0.6f,
    /** Zusätzlich zur Breite: der vertikale Abstand zur vorherigen Zeile darf dieses Vielfache der Median-Zeilenhöhe nicht überschreiten, sonst gilt die Zeile nicht als Zellumbruch. */
    val wrappedRowMaxGapFactor: Float = 1.8f,
    /** Toleranz für den Abgleich einer Zeilenlücke gegen eine Kandidaten-Spaltengrenze, als Anteil der schmäleren der beiden angrenzenden Spaltenbreiten. */
    val separatorMatchToleranceRatio: Float = 0.12f,
    /** Anteil der qualifizierenden Zeilen einer Region, die die erkannten Spalten sauber (nicht mehrdeutig, ausreichend überlappend) bestätigen müssen. */
    val minGapRecurrenceRatio: Float = 0.6f,
    /** Mindest-Score (siehe Regionen-Score-Gewichte), unter dem eine Region verworfen wird, auch wenn sie strukturell durchgeht. */
    val minRegionScore: Float = 0.35f,
    /** Gewicht der Zeilenanzahl im Regionen-Score. */
    val regionScoreRowCountWeight: Float = 0.3f,
    /** Gewicht der Spaltenkonsistenz im Regionen-Score. */
    val regionScoreColumnConsistencyWeight: Float = 0.3f,
    /** Gewicht der Zellbelegung im Regionen-Score. */
    val regionScoreCellOccupancyWeight: Float = 0.2f,
    /** Gewicht der OCR-Konfidenz im Regionen-Score. */
    val regionScoreConfidenceWeight: Float = 0.2f,
    /** Fallback-Zeilenhöhe, falls keine gültigen Lines vorhanden sind. */
    val fallbackLineHeight: Float = 12f,
    /** Fallback-Zeichenbreite, falls keine gültigen Elemente vorhanden sind. */
    val fallbackCharWidth: Float = 6f
)
