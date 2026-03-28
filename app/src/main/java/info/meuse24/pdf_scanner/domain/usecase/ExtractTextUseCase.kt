package info.meuse24.pdf_scanner.domain.usecase

import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognizer
import info.meuse24.pdf_scanner.data.local.ScanRecord
import info.meuse24.pdf_scanner.util.DispatcherProvider
import info.meuse24.pdf_scanner.util.OcrInputImageLoader
import info.meuse24.pdf_scanner.util.OcrManager
import info.meuse24.pdf_scanner.util.PdfPageInputImageLoader
import info.meuse24.pdf_scanner.util.TextRecognizerRunner
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/**
 * Führt OCR auf einer Liste von ScanRecords aus.
 * Dedupliziert die frühere extractText/extractTexts-Logik aus HomeViewModel.
 * Bei mehreren Records wird der Dateiname als Trenner eingefügt.
 *
 * @return erkannter Text (nie leer; wirft Exception wenn kein Text gefunden)
 */
class ExtractTextUseCase @Inject constructor(
    private val ocrManager: OcrManager,
    private val inputImageLoader: OcrInputImageLoader,
    private val pdfPageInputImageLoader: PdfPageInputImageLoader,
    private val dispatcherProvider: DispatcherProvider,
    private val textRecognizerRunner: TextRecognizerRunner
) {
    suspend operator fun invoke(records: List<ScanRecord>, languageCode: String): String {
        val recognizer = ocrManager.getRecognizer(languageCode)
        val results    = StringBuilder()
        try {
            for (record in records) {
                val pageText = extractFromRecord(record, recognizer)
                if (pageText.isNotBlank()) {
                    if (results.isNotEmpty()) results.append("\n\n")
                    if (records.size > 1) {
                        results.append("— ${record.filename} —\n")
                    }
                    results.append(pageText)
                }
            }
        } finally {
            recognizer.close()
        }
        if (results.isBlank()) error("Kein Text in den übergebenen Records gefunden")
        return results.toString()
    }

    private suspend fun extractFromRecord(record: ScanRecord, recognizer: TextRecognizer): String {
        val pdfFile   = File(record.filepath)
        val pageTexts = StringBuilder()

        if (pdfFile.exists()) {
            withContext(dispatcherProvider.io) {
                pdfPageInputImageLoader.forEachPageImage(pdfFile) { pageImage ->
                    val text = textRecognizerRunner.recognize(recognizer, pageImage)
                    if (text.isNotBlank()) {
                        if (pageTexts.isNotEmpty()) pageTexts.append("\n\n")
                        pageTexts.append(text)
                    }
                }
            }
        } else if (record.thumbnailPath != null) {
            // Fallback: nur erste Seite via Thumbnail
            val image = withContext(dispatcherProvider.io) {
                inputImageLoader.loadFromFile(File(record.thumbnailPath))
            }
            val text = textRecognizerRunner.recognize(recognizer, image)
            if (text.isNotBlank()) pageTexts.append(text)
        }

        return pageTexts.toString()
    }
}
