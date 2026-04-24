package info.meuse24.pdf_scanner.domain.usecase

import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognizer
import info.meuse24.pdf_scanner.domain.model.Document
import info.meuse24.pdf_scanner.util.DispatcherProvider
import info.meuse24.pdf_scanner.util.OcrPipeline
import info.meuse24.pdf_scanner.util.OcrPipelineStatus
import info.meuse24.pdf_scanner.util.OcrInputImageLoader
import info.meuse24.pdf_scanner.util.OcrThresholds
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
 * @return strukturierte OCR-Ergebnisse pro Record
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
        records: List<Document>,
        languageCode: String,
        onStatus: (OcrPipelineStatus) -> Unit = {}
    ): List<OcrDocumentResult> {
        val result = ocrPipeline.runWithFallback(
            languageCode = languageCode,
            usage = OcrUsage.EXTRACT_TEXT,
            onStatus = onStatus,
            emptyValue = { emptyList<OcrDocumentResult>() },
            isSuccess = { documents, stats ->
                val totalTextLength = documents.sumOf { it.fullText.length }
                val isNotEmpty = documents.any { it.fullText.isNotBlank() }
                if (languageCode == "auto" && stats != null) {
                    // Im Automatik-Modus: nur akzeptieren wenn Vertrauen hoch genug
                    // oder wenn Text sehr lang ist (Fließtext ohne klare Konfidenz).
                    // Hinweis: Entscheidung basiert auf den Stats der ersten erfolgreichen Seite.
                    isNotEmpty && (stats.confidence > OcrThresholds.MIN_CONFIDENCE_EXTRACT || totalTextLength > 200)
                } else {
                    isNotEmpty
                }
            }
        ) { recognizer, script ->
            extractFromRecordsWithStats(records, recognizer)
        }

        if (result.value.none { it.fullText.isNotBlank() }) throw OcrNoTextException()
        return result.value
    }

    private suspend fun extractFromRecordsWithStats(
        records: List<Document>,
        recognizer: TextRecognizer
    ): Pair<List<OcrDocumentResult>, OcrResultStats?> {
        val results = mutableListOf<OcrDocumentResult>()
        var firstStats: OcrResultStats? = null

        for (record in records) {
            val document = extractFromRecordWithStats(record, recognizer)
            results += document
            if (firstStats == null && document.fullText.isNotBlank()) {
                firstStats = document.stats
            }
        }

        return results to firstStats
    }

    private suspend fun extractFromRecordWithStats(
        record: Document,
        recognizer: TextRecognizer
    ): OcrDocumentResult {
        val pdfFile = File(record.filepath)
        val pageTexts = mutableListOf<String>()
        var firstStats: OcrResultStats? = null

        if (pdfFile.exists()) {
            withContext(dispatcherProvider.io) {
                pdfPageInputImageLoader.forEachPageImage(pdfFile) { pageImage ->
                    val (ocrText, ocrStats) = textRecognizerRunner.recognizeWithStats(recognizer, pageImage)
                    if (ocrText.text.isNotBlank()) {
                        pageTexts += ocrText.text
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
                pageTexts += ocrText.text
                firstStats = ocrStats
            }
        }

        return OcrDocumentResult(
            recordId = record.id,
            fullText = pageTexts.joinToString("\n\n"),
            pageTexts = pageTexts,
            stats = firstStats
        )
    }
}

