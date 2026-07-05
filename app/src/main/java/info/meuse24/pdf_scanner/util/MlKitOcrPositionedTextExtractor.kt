package info.meuse24.pdf_scanner.util

import android.graphics.Rect
import com.google.mlkit.vision.text.Text
import info.meuse24.pdf_scanner.domain.gateway.DispatcherProvider
import info.meuse24.pdf_scanner.domain.gateway.OcrPositionedTextExtractor
import info.meuse24.pdf_scanner.domain.model.Document
import info.meuse24.pdf_scanner.domain.model.OcrElement
import info.meuse24.pdf_scanner.domain.model.OcrLine
import info.meuse24.pdf_scanner.domain.model.OcrPipelineStatus
import info.meuse24.pdf_scanner.domain.model.OcrPositionedPage
import info.meuse24.pdf_scanner.domain.model.OcrRect
import info.meuse24.pdf_scanner.domain.model.OcrResultStats
import info.meuse24.pdf_scanner.domain.model.OcrThresholds
import info.meuse24.pdf_scanner.domain.model.OcrUsage
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.withContext

/**
 * ML-Kit-Implementierung von [OcrPositionedTextExtractor]. Rendert mit dem eigenen
 * 220-DPI-Tabellen-Scale (siehe [PDF_TABLE_RENDER_SCALE]) statt des 150-DPI-Volltextstandards,
 * damit enge Spalten (z. B. Kontoauszüge) nicht schon auf Bitmap-Ebene verschmelzen.
 */
class MlKitOcrPositionedTextExtractor @Inject constructor(
    private val ocrPipeline: OcrPipeline,
    private val textRecognizerRunner: TextRecognizerRunner,
    private val dispatcherProvider: DispatcherProvider
) : OcrPositionedTextExtractor {
    override suspend fun extract(
        document: Document,
        languageCode: String,
        onStatus: (OcrPipelineStatus) -> Unit,
        onProgress: (current: Int, total: Int) -> Unit
    ): List<OcrPositionedPage> {
        val pdfFile = File(document.filepath)
        if (!pdfFile.exists()) return emptyList()

        val result = ocrPipeline.runWithFallback(
            languageCode = languageCode,
            usage = OcrUsage.TABLE_EXTRACTION,
            onStatus = onStatus,
            emptyValue = { emptyList<OcrPositionedPage>() },
            isSuccess = { pages, stats ->
                val hasText = pages.any { it.lines.isNotEmpty() }
                if (languageCode == "auto" && stats != null) {
                    hasText && stats.confidence > OcrThresholds.MIN_CONFIDENCE_EXTRACT
                } else {
                    hasText
                }
            }
        ) { recognizer, _ ->
            val pages = mutableListOf<OcrPositionedPage>()
            var firstStats: OcrResultStats? = null

            withContext(dispatcherProvider.io) {
                textRecognizerRunner.processPagesFullText(
                    pdfFile = pdfFile,
                    recognizer = recognizer,
                    renderScale = PDF_TABLE_RENDER_SCALE
                ) { pageIndex, pageCount, bitmapWidth, bitmapHeight, text ->
                    onProgress(pageIndex + 1, pageCount)
                    val positionedPage = text.toPositionedPage(pageIndex, bitmapWidth, bitmapHeight)
                    pages += positionedPage
                    if (firstStats == null && positionedPage.lines.isNotEmpty()) {
                        firstStats = text.extractStats()
                    }
                }
            }

            pages.toList() to firstStats
        }

        return result.value
    }
}

private fun Text.toPositionedPage(pageIndex: Int, widthPx: Int, heightPx: Int): OcrPositionedPage {
    // Geometrieobjekte ohne Bounding-Box werden übersprungen; die Seite selbst bleibt aber auch
    // mit leerer Line-Liste im Ergebnis, damit PDF- und Ergebnisindex ausgerichtet bleiben.
    val lines = textBlocks.flatMap { block -> block.lines.mapNotNull { it.toOcrLineOrNull() } }
    return OcrPositionedPage(pageIndex = pageIndex, widthPx = widthPx, heightPx = heightPx, lines = lines)
}

private fun Text.Line.toOcrLineOrNull(): OcrLine? {
    val lineBox = boundingBox ?: return null
    val mappedElements = elements.mapNotNull { it.toOcrElementOrNull() }
    // Besitzt die Line selbst eine gültige Box, aber kein Element eine eigene (seltener
    // ML-Kit-Fall), wird ein einzelnes Fallback-Element aus der Line-Geometrie synthetisiert
    // statt die ganze Line zu verwerfen — sonst bliebe der Line-Level-Fallback in
    // TableReconstructor.cleanLines() für reale OCR-Daten unerreichbar.
    val ocrElements = mappedElements.ifEmpty {
        listOf(
            OcrElement(
                text = text,
                bounds = lineBox.toOcrRect(),
                confidence = elements.map { it.confidence }.averageOrZero(),
                angleDeg = elements.map { it.angle }.averageOrZero()
            )
        )
    }
    return OcrLine(
        text = text,
        bounds = lineBox.toOcrRect(),
        elements = ocrElements,
        confidence = ocrElements.map { it.confidence }.average().toFloat(),
        angleDeg = ocrElements.map { it.angleDeg }.average().toFloat()
    )
}

private fun Text.Element.toOcrElementOrNull(): OcrElement? {
    val box = boundingBox ?: return null
    return OcrElement(text = text, bounds = box.toOcrRect(), confidence = confidence, angleDeg = angle)
}

private fun List<Float>.averageOrZero(): Float = if (isEmpty()) 0f else average().toFloat()

private fun Rect.toOcrRect() = mlKitRectToOcrRect(left, top, right, bottom)

/**
 * Reine Koordinaten-Umrechnung, getrennt von [android.graphics.Rect] extrahiert: Unter dem
 * Android-Unit-Test-Stub-Jar (`isReturnDefaultValues = true`) liefert sowohl eine echte
 * `Rect(...)`-Konstruktion als auch ein Mockito-Mock ausschließlich Nullfelder (leere
 * Methoden-/Konstruktor-Bodies, direkter Feldzugriff kann von Mockito ohnehin nicht gestubbt
 * werden). Die eigentliche Umrechnungslogik bleibt dadurch trotzdem mit konkreten Koordinaten
 * isoliert testbar.
 */
internal fun mlKitRectToOcrRect(left: Int, top: Int, right: Int, bottom: Int) =
    OcrRect(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat())
