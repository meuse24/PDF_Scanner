package info.meuse24.pdf_scanner.util

import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognizer
import info.meuse24.pdf_scanner.domain.model.OcrResultStats
import java.io.File
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
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

    /**
     * Mehrseitenvariante mit Callback auf das rohe ML-Kit-`Text`-Objekt (statt `String`) plus
     * Seitenindex/-anzahl und Bitmap-Abmessungen — Grundlage für die Tabellenextraktion, die
     * Zeilen-/Element-Geometrie benötigt. Nutzt denselben Renderpfad (Bitmap-Recycling,
     * OOM-Schutz) wie [processPages]; Renderauflösung ist über [renderScale] konfigurierbar.
     */
    suspend fun processPagesFullText(
        pdfFile: File,
        recognizer: TextRecognizer,
        renderScale: Float = PDF_OCR_RENDER_SCALE,
        onPage: suspend (pageIndex: Int, pageCount: Int, bitmapWidth: Int, bitmapHeight: Int, text: Text) -> Unit
    )
}

class MlKitTextRecognizerRunner @Inject constructor(
    private val pdfPageInputImageLoader: PdfPageInputImageLoader
) : TextRecognizerRunner {
    override suspend fun recognizeText(recognizer: TextRecognizer, image: InputImage): String {
        return recognizeFullText(recognizer, image).text
    }

    override suspend fun recognizeFullText(recognizer: TextRecognizer, image: InputImage): Text {
        // ML Kit bietet keine Möglichkeit, den laufenden Task abzubrechen. Würde eine
        // Coroutine-Cancellation suspendCancellableCoroutine sofort mit CancellationException
        // verlassen, liefe der ML-Kit-Task im Hintergrund weiter, während der Aufrufer (Loader/
        // OcrPipeline) das Bitmap bereits recycelt bzw. den Recognizer bereits schließt — ein
        // Use-after-recycle-Risiko. NonCancellable erzwingt, dass wir auf den tatsächlichen
        // Task-Abschluss warten, bevor die Cancellation nach außen weitergegeben wird (siehe
        // ML-Kit-Doku: Eingaberessourcen erst im OnComplete-Listener freigeben).
        //
        // NonCancellable liefert danach den Wert aber kommentarlos zurück, selbst wenn der
        // ursprüngliche Aufrufer inzwischen abgebrochen wurde (bekannter Fallstrick, siehe KDoc
        // von kotlinx.coroutines.NonCancellable) — ohne das folgende ensureActive() würden
        // onPage(), das Geometrie-Mapping und ggf. weitere PDF-Seiten trotzdem noch ausgeführt,
        // bevor die Cancellation zufällig an anderer Stelle auffliegt.
        //
        // ensureActive() steht bewusst in finally statt direkt nach dem withContext-Ausdruck:
        // Schlägt der ML-Kit-Task fehl (resumeWithException), wirft withContext(NonCancellable)
        // diese Exception direkt — eine Anweisung danach würde dann nie erreicht. Im finally
        // läuft die Prüfung in jedem Fall; ist der Aufrufer inzwischen abgebrochen, ersetzt die
        // resultierende CancellationException die ML-Kit-Exception (Standard-JVM-Verhalten bei
        // einer werfenden finally), statt sie als gewöhnlichen Fehler durchzureichen.
        try {
            return withContext(NonCancellable) {
                suspendCancellableCoroutine { cont ->
                    recognizer.process(image)
                        .addOnSuccessListener { cont.resume(it) }
                        .addOnFailureListener { e -> cont.resumeWithException(e) }
                }
            }
        } finally {
            currentCoroutineContext().ensureActive()
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
        // Delegiert bewusst an processPagesFullText, damit Text-OCR und Tabellen-OCR exakt
        // denselben Rendering-/Erkennungspfad durchlaufen und nicht auseinanderlaufen können.
        processPagesFullText(pdfFile, recognizer) { _, _, _, _, text ->
            onPage(text.text, text.extractStats())
        }
    }

    override suspend fun processPagesFullText(
        pdfFile: File,
        recognizer: TextRecognizer,
        renderScale: Float,
        onPage: suspend (pageIndex: Int, pageCount: Int, bitmapWidth: Int, bitmapHeight: Int, text: Text) -> Unit
    ) {
        pdfPageInputImageLoader.forEachPageImage(pdfFile, renderScale) { pageIndex, pageCount, bitmapWidth, bitmapHeight, pageImage ->
            val text = recognizeFullText(recognizer, pageImage)
            onPage(pageIndex, pageCount, bitmapWidth, bitmapHeight, text)
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
