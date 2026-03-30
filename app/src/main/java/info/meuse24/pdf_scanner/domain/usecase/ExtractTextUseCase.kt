package info.meuse24.pdf_scanner.domain.usecase

import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognizer
import info.meuse24.pdf_scanner.data.local.ScanRecord
import info.meuse24.pdf_scanner.util.DispatcherProvider
import info.meuse24.pdf_scanner.util.OcrPipeline
import info.meuse24.pdf_scanner.util.OcrPipelineStatus
import info.meuse24.pdf_scanner.util.OcrInputImageLoader
import info.meuse24.pdf_scanner.util.OcrUsage
import info.meuse24.pdf_scanner.util.OcrResultStats
import info.meuse24.pdf_scanner.util.PdfPageInputImageLoader
import info.meuse24.pdf_scanner.util.TextRecognizerRunner
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/** Wird geworfen, wenn OCR erfolgreich lief, aber kein Text erkannt wurde. */
class OcrNoTextException : Exception()

/**
 * Führt OCR auf einer Liste von ScanRecords aus.
 * Dedupliziert die frühere extractText/extractTexts-Logik aus HomeViewModel.
 * Bei mehreren Records wird der Dateiname als Trenner eingefügt.
 *
 * @return erkannter Text (nie leer)
 * @throws OcrNoTextException wenn OCR lief, aber kein Text erkannt wurde
 */
open class ExtractTextUseCase @Inject constructor(
    private val ocrPipeline: OcrPipeline,
    private val inputImageLoader: OcrInputImageLoader,
    private val pdfPageInputImageLoader: PdfPageInputImageLoader,
    private val dispatcherProvider: DispatcherProvider,
    private val textRecognizerRunner: TextRecognizerRunner
) {
    open suspend operator fun invoke(
        records: List<ScanRecord>,
        languageCode: String,
        onStatus: (OcrPipelineStatus) -> Unit = {}
    ): Pair<String, OcrResultStats?> {
        val result = ocrPipeline.runWithFallback(
            languageCode = languageCode,
            usage = OcrUsage.EXTRACT_TEXT,
            onStatus = onStatus,
            emptyValue = { "" },
            isSuccess = { text, stats ->
                val isNotEmpty = text.isNotBlank()
                if (languageCode == "auto" && stats != null) {
                    // Im Automatik-Modus: nur akzeptieren wenn Vertrauen hoch genug (z.B. > 50%)
                    // oder wenn Text sehr lang ist (Plattentext).
                    // Hinweis: Entscheidung basiert auf den Stats der ersten erfolgreichen Seite.
                    isNotEmpty && (stats.confidence > 0.5f || text.length > 200)
                } else {
                    isNotEmpty
                }
            }
        ) { recognizer, script ->
            extractFromRecordsWithStats(records, recognizer)
        }

        if (result.value.isBlank()) throw OcrNoTextException()
        return result.value to result.stats
    }

    private suspend fun extractFromRecordsWithStats(
        records: List<ScanRecord>,
        recognizer: TextRecognizer
    ): Pair<String, OcrResultStats?> {
        val results = StringBuilder()
        var firstStats: OcrResultStats? = null

        for (record in records) {
            val (text, stats) = extractFromRecordWithStats(record, recognizer)
            if (text.isNotBlank()) {
                if (results.isNotEmpty()) results.append("\n\n")
                if (records.size > 1) {
                    results.append("— ${record.filename} —\n")
                }
                results.append(text)
                if (firstStats == null) firstStats = stats
            }
        }

        return results.toString() to firstStats
    }

    private suspend fun extractFromRecordWithStats(
        record: ScanRecord,
        recognizer: TextRecognizer
    ): Pair<String, OcrResultStats?> {
        val pdfFile = File(record.filepath)
        val pageTexts = StringBuilder()
        var firstStats: OcrResultStats? = null

        if (pdfFile.exists()) {
            withContext(dispatcherProvider.io) {
                pdfPageInputImageLoader.forEachPageImage(pdfFile) { pageImage ->
                    val (ocrText, ocrStats) = textRecognizerRunner.recognizeWithStats(recognizer, pageImage)
                    if (ocrText.text.isNotBlank()) {
                        if (pageTexts.isNotEmpty()) pageTexts.append("\n\n")
                        pageTexts.append(ocrText.text)
                        if (firstStats == null) firstStats = ocrStats
                    }
                }
            }
        } else if (record.thumbnailPath != null) {
            val image = withContext(dispatcherProvider.io) {
                inputImageLoader.loadFromFile(File(record.thumbnailPath))
            }
            val (ocrText, ocrStats) = textRecognizerRunner.recognizeWithStats(recognizer, image)
            if (ocrText.text.isNotBlank()) {
                pageTexts.append(ocrText.text)
                firstStats = ocrStats
            }
        }

        return pageTexts.toString() to firstStats
    }
}
