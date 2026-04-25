package info.meuse24.pdf_scanner.util

import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognizer
import java.io.File
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

interface TextRecognizerRunner {
    suspend fun recognizeText(recognizer: TextRecognizer, image: InputImage): String
    suspend fun recognizeFullText(recognizer: TextRecognizer, image: InputImage): Text
    suspend fun recognizeWithStats(recognizer: TextRecognizer, image: InputImage): Pair<Text, OcrResultStats>
    suspend fun processPages(
        pdfFile: File,
        recognizer: TextRecognizer,
        onPage: suspend (text: String, stats: OcrResultStats?) -> Unit
    )
}

class MlKitTextRecognizerRunner @Inject constructor(
    private val pdfPageInputImageLoader: PdfPageInputImageLoader
) : TextRecognizerRunner {
    override suspend fun recognizeText(recognizer: TextRecognizer, image: InputImage): String {
        return recognizeFullText(recognizer, image).text
    }

    override suspend fun recognizeFullText(recognizer: TextRecognizer, image: InputImage): Text {
        return suspendCancellableCoroutine { cont ->
            recognizer.process(image)
                .addOnSuccessListener { cont.resume(it) }
                .addOnFailureListener { e -> cont.resumeWithException(e) }
            cont.invokeOnCancellation { /* recognizer is closed by caller */ }
        }
    }

    override suspend fun recognizeWithStats(recognizer: TextRecognizer, image: InputImage): Pair<Text, OcrResultStats> {
        val text = recognizeFullText(recognizer, image)
        return text to text.extractStats()
    }

    override suspend fun processPages(
        pdfFile: File,
        recognizer: TextRecognizer,
        onPage: suspend (text: String, stats: OcrResultStats?) -> Unit
    ) {
        pdfPageInputImageLoader.forEachPageImage(pdfFile) { pageImage ->
            val (ocrText, ocrStats) = recognizeWithStats(recognizer, pageImage)
            onPage(ocrText.text, ocrStats)
        }
    }
}

fun Text.extractStats(): OcrResultStats {
    val elements = textBlocks.flatMap { it.lines }.flatMap { it.elements }
    if (elements.isEmpty()) return OcrResultStats(0f, null, 0f)

    val avgConfidence = elements.map { it.confidence }.average().toFloat()
    val avgAngle = elements.map { it.angle }.average().toFloat()

    val lang = elements.mapNotNull { it.recognizedLanguage }
        .filter { it.isNotBlank() }
        .groupingBy { it }
        .eachCount()
        .maxByOrNull { it.value }
        ?.key

    return OcrResultStats(avgConfidence, lang, avgAngle)
}
