package info.meuse24.pdf_scanner.domain.model

enum class OcrUsage {
    EXTRACT_TEXT,
    SEARCHABLE_PDF,
    SCAN_BARCODES
}

enum class OcrScript {
    LATIN,
    DEVANAGARI,
    JAPANESE,
    CHINESE,
    KOREAN
}

private val unsupportedSearchablePdfLanguages = setOf("ar", "zh", "ja", "ko")

fun String.supportsSearchablePdfTextLayer(): Boolean =
    this !in unsupportedSearchablePdfLanguages

sealed interface OcrPipelineStatus {
    data object PreparingModel : OcrPipelineStatus
    data object DownloadingModel : OcrPipelineStatus
    data object InstallingModel : OcrPipelineStatus
}

data class OcrResultStats(
    val confidence: Float,
    val recognizedLanguage: String?,
    val angle: Float
)

data class OcrPipelineResult<T>(
    val value: T,
    val script: OcrScript,
    val stats: OcrResultStats? = null
)

/** Zentrale Konfidenz-Schwellenwerte fuer alle OCR-Pfade. */
object OcrThresholds {
    /** Auto-Modus Extraktion: Ergebnis akzeptieren wenn Konfidenz > Schwelle oder Text lang genug. */
    const val MIN_CONFIDENCE_EXTRACT = 0.5f

    /** Auto-Modus Searchable-PDF: Strengere Schwelle, da falscher Font die Durchsuchbarkeit zerstoert. */
    const val MIN_CONFIDENCE_SEARCHABLE = 0.6f

    /** Unter dieser Schwelle wird dem Nutzer eine Qualitaetswarnung angezeigt. */
    const val LOW_CONFIDENCE_WARNING = 0.3f

    /** Auto-Modus: Unter dieser Schwelle + keine erkannte Sprache -> "Erkennung unsicher"-Hinweis. */
    const val AUTO_DETECTION_UNCERTAIN = 0.6f
}
