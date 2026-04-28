package info.meuse24.pdf_scanner.domain.usecase

import info.meuse24.pdf_scanner.domain.model.Document
import info.meuse24.pdf_scanner.domain.gateway.OcrDocumentTextExtractor
import info.meuse24.pdf_scanner.domain.model.OcrPipelineStatus
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
    private val ocrDocumentTextExtractor: OcrDocumentTextExtractor
) {
    open suspend operator fun invoke(
        records: List<Document>,
        languageCode: String,
        onStatus: (OcrPipelineStatus) -> Unit = {}
    ): List<OcrDocumentResult> {
        val results = ocrDocumentTextExtractor.extract(records, languageCode, onStatus)
        if (results.none { it.fullText.isNotBlank() }) throw OcrNoTextException()
        return results
    }
}

