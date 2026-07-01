package info.meuse24.pdf_scanner.util

import info.meuse24.pdf_scanner.domain.gateway.DispatcherProvider
import info.meuse24.pdf_scanner.domain.gateway.OcrDocumentTextExtractor
import info.meuse24.pdf_scanner.domain.model.Document
import info.meuse24.pdf_scanner.domain.model.OcrPipelineStatus
import info.meuse24.pdf_scanner.domain.model.OcrResultStats
import info.meuse24.pdf_scanner.domain.model.OcrThresholds
import info.meuse24.pdf_scanner.domain.model.OcrUsage
import info.meuse24.pdf_scanner.domain.usecase.OcrDocumentResult
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

class MlKitOcrDocumentTextExtractor @Inject constructor(
    private val ocrPipeline: OcrPipeline,
    private val inputImageLoader: OcrInputImageLoader,
    private val dispatcherProvider: DispatcherProvider,
    private val textRecognizerRunner: TextRecognizerRunner
) : OcrDocumentTextExtractor {
    override suspend fun extract(
        records: List<Document>,
        languageCode: String,
        onStatus: (OcrPipelineStatus) -> Unit
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
                    isNotEmpty && (stats.confidence > OcrThresholds.MIN_CONFIDENCE_EXTRACT || totalTextLength > 200)
                } else {
                    isNotEmpty
                }
            }
        ) { recognizer, _ ->
            val results = mutableListOf<OcrDocumentResult>()
            var firstStats: OcrResultStats? = null

            for (record in records) {
                val pdfFile = File(record.filepath)
                val pageTexts = mutableListOf<String>()
                var recordStats: OcrResultStats? = null

                if (pdfFile.exists()) {
                    withContext(dispatcherProvider.io) {
                        textRecognizerRunner.processPages(pdfFile, recognizer) { text, ocrStats ->
                            pageTexts += text
                            if (text.isNotBlank()) {
                                if (recordStats == null) recordStats = ocrStats
                            }
                        }
                    }
                } else if (record.thumbnailPath != null) {
                    val image = withContext(dispatcherProvider.io) {
                        inputImageLoader.loadFromFile(File(record.thumbnailPath))
                    }
                    val (ocrText, ocrStats) = textRecognizerRunner.recognizeWithStats(recognizer, image)
                    pageTexts += ocrText.text
                    repeat((record.pageCount.coerceAtLeast(1) - 1).coerceAtLeast(0)) {
                        pageTexts += ""
                    }
                    if (ocrText.text.isNotBlank()) {
                        recordStats = ocrStats
                    }
                }

                val document = OcrDocumentResult(
                    recordId = record.id,
                    fullText = pageTexts.joinToString("\n\n"),
                    pageTexts = pageTexts,
                    stats = recordStats
                )
                results += document
                if (firstStats == null && document.fullText.isNotBlank()) {
                    firstStats = document.stats
                }
            }

            results to firstStats
        }

        return result.value
    }
}
