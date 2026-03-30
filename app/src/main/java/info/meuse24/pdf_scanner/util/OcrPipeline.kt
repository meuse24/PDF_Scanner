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
        var lastValue = emptyValue()
        var lastStats: OcrResultStats? = null

        for (script in plan) {
            val recognizer = ocrManager.getRecognizer(script)
            try {
                ocrModelInstaller.ensureModelAvailable(
                    recognizer = recognizer,
                    script = script,
                    onStatus = onStatus
                )
                val (value, stats) = block(recognizer, script)
                lastValue = value
                lastStats = stats
                if (isSuccess(value, stats)) {
                    return OcrPipelineResult(value, script, stats)
                }
            } finally {
                recognizer.close()
            }
        }

        return OcrPipelineResult(lastValue, plan.lastOrNull() ?: OcrScript.LATIN, lastStats)
    }
}
