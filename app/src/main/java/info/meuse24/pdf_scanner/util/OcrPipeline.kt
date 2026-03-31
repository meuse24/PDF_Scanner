package info.meuse24.pdf_scanner.util

import com.google.mlkit.vision.text.TextRecognizer
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

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

/** Zentrale Konfidenz-Schwellenwerte für alle OCR-Pfade. */
object OcrThresholds {
    /** Auto-Modus Extraktion: Ergebnis akzeptieren wenn Konfidenz > Schwelle oder Text lang genug. */
    const val MIN_CONFIDENCE_EXTRACT = 0.5f
    /** Auto-Modus Searchable-PDF: Strengere Schwelle, da falscher Font die Durchsuchbarkeit zerstört. */
    const val MIN_CONFIDENCE_SEARCHABLE = 0.6f
    /** Unter dieser Schwelle wird dem Nutzer eine Qualitätswarnung angezeigt. */
    const val LOW_CONFIDENCE_WARNING = 0.3f
    /** Auto-Modus: Unter dieser Schwelle + keine erkannte Sprache → "Erkennung unsicher"-Hinweis. */
    const val AUTO_DETECTION_UNCERTAIN = 0.6f
}

@Singleton
open class OcrPipeline @Inject constructor(
    private val ocrManager: OcrManager,
    private val ocrModelInstaller: OcrModelInstaller
) {
    open suspend fun <T> runWithFallback(
        languageCode: String,
        usage: OcrUsage,
        onStatus: (OcrPipelineStatus) -> Unit = {},
        emptyValue: () -> T,
        isSuccess: (T, OcrResultStats?) -> Boolean,
        block: suspend (recognizer: TextRecognizer, script: OcrScript) -> Pair<T, OcrResultStats?>
    ): OcrPipelineResult<T> {
        val plan = ocrManager.recognitionPlan(languageCode, usage, Locale.getDefault())
        // Bestes Ergebnis tracken: höchste Konfidenz gewinnt, falls kein Skript isSuccess besteht.
        var bestValue = emptyValue()
        var bestStats: OcrResultStats? = null
        var bestScript = plan.firstOrNull() ?: OcrScript.LATIN

        for (script in plan) {
            val recognizer = ocrManager.getRecognizer(script)
            try {
                ocrModelInstaller.ensureModelAvailable(
                    recognizer = recognizer,
                    script = script,
                    onStatus = onStatus
                )
                val (value, stats) = block(recognizer, script)
                val newConf = stats?.confidence ?: 0f
                val bestConf = bestStats?.confidence ?: -1f
                if (newConf > bestConf) {
                    bestValue = value
                    bestStats = stats
                    bestScript = script
                }
                if (isSuccess(value, stats)) {
                    return OcrPipelineResult(value, script, stats)
                }
            } finally {
                recognizer.close()
            }
        }

        return OcrPipelineResult(bestValue, bestScript, bestStats)
    }
}
