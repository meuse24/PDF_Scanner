package info.meuse24.pdf_scanner.util

import com.google.mlkit.vision.text.TextRecognizer
import info.meuse24.pdf_scanner.domain.model.OcrPipelineResult
import info.meuse24.pdf_scanner.domain.model.OcrPipelineStatus
import info.meuse24.pdf_scanner.domain.model.OcrResultStats
import info.meuse24.pdf_scanner.domain.model.OcrScript
import info.meuse24.pdf_scanner.domain.model.OcrUsage
import javax.inject.Inject
import javax.inject.Singleton

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
        val plan = ocrManager.recognitionPlan(languageCode, usage)
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
