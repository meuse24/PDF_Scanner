package info.meuse24.pdf_scanner.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDFont
import com.tom_roush.pdfbox.pdmodel.font.PDType0Font
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.state.RenderingMode
import com.tom_roush.pdfbox.util.Matrix
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Erstellt ein durchsuchbares PDF, indem über jede Seite des Originals eine
 * unsichtbare OCR-Textebene gelegt wird.
 *
 * Ablauf (2 Phasen):
 *  1. PdfRenderer rendert jede Seite einzeln → OCR → Bitmap sofort recyceln.
 *     Nur EINE Bitmap gleichzeitig im RAM (wichtig bei 50-Seiten-Dokumenten).
 *  2. PdfBox öffnet das Original (nach geschlossenem PdfRenderer), hängt den
 *     unsichtbaren Text per AppendMode an und ersetzt die Datei via Temp-File.
 *
 * Technical Debt: Für sehr lange Hintergrundoperationen (z.B. 50-seitige PDFs)
 * wäre WorkManager robuster als ein ViewModel-Coroutine-Scope.
 */
@Singleton
open class SearchablePdfBuilder @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val ocrPipeline: OcrPipeline,
    private val textRecognizerRunner: TextRecognizerRunner,
    private val dispatcherProvider: DispatcherProvider
) {
    // Renderparameter: PDF_OCR_RENDER_SCALE + PDF_OCR_MAX_BITMAP_SIDE aus PdfPageInputImageLoader.kt

    /** Ein OCR-Wort (Element-Ebene) mit seiner Bounding Box, Winkel und erkannter Sprache. */
    private data class WordData(val text: String, val bbox: Rect, val angle: Float = 0f, val language: String? = null)
    private data class PageData(
        val bitmapW: Int,
        val bitmapH: Int,
        val words: List<WordData>
    )

    open suspend fun makeSearchable(
        pdfFile: File,
        languageCode: String,
        onProgress: (current: Int, total: Int) -> Unit,
        onStatus: (OcrPipelineStatus) -> Unit = {}
    ): String = withContext(dispatcherProvider.io) {

        val result = ocrPipeline.runWithFallback(
            languageCode = languageCode,
            usage = OcrUsage.SEARCHABLE_PDF,
            onStatus = onStatus,
            emptyValue = { emptyList<PageData>() },
            isSuccess = { pages, stats ->
                val hasWords = pages.any { it.words.isNotEmpty() }
                if (languageCode == "auto" && stats != null) {
                    // Für Searchable PDF sind wir im Automatik-Modus strenger,
                    // da ein falscher Font/RTL-Logik das PDF visuell nicht ändert,
                    // aber die Durchsuchbarkeit zerstört.
                    // Hinweis: Entscheidung basiert auf den Stats der ersten erfolgreichen Seite.
                    hasWords && stats.confidence > OcrThresholds.MIN_CONFIDENCE_SEARCHABLE
                } else {
                    hasWords
                }
            }
        ) { recognizer, _ ->
            collectPageResults(pdfFile, recognizer, onProgress)
        }
        val pageResults = result.value

        // Collect full extracted text for indexing
        val extractedText = pageResults.joinToString("\n\n") { pd ->
            pd.words.joinToString(" ") { it.text }
        }.trim()

        // ── Phase 2: Textlayer per PdfBox einfügen ──────────────────────────────
        val tempFile = File(pdfFile.parent, "${pdfFile.nameWithoutExtension}_searchable_tmp.pdf")
        PDDocument.load(pdfFile).use { document ->
            val font = loadFont(document, languageCode)

            document.pages.forEachIndexed { i, pdPage ->
                if (i >= pageResults.size) return@forEachIndexed
                val pd     = pageResults[i]
                val scaleX = pdPage.mediaBox.width  / pd.bitmapW
                val scaleY = pdPage.mediaBox.height / pd.bitmapH
                val pageH  = pdPage.mediaBox.height

                PDPageContentStream(
                    document, pdPage,
                    PDPageContentStream.AppendMode.APPEND,
                    true
                ).use { cs ->
                    cs.beginText()
                    cs.setRenderingMode(RenderingMode.NEITHER)
                    // Größe 1 — tatsächliche Skalierung steckt in der Textmatrix
                    cs.setFont(font, 1f)

                    for (word in pd.words) {
                        // PDF-Koordinaten: Ursprung unten links, Y wächst aufwärts
                        val bboxH   = (word.bbox.bottom - word.bbox.top)  * scaleY
                        val bboxW   = (word.bbox.right  - word.bbox.left) * scaleX
                        val fontSize = bboxH.coerceAtLeast(1f)
                        val pdfX    = word.bbox.left   * scaleX
                        val pdfY    = pageH - word.bbox.bottom * scaleY

                        // RTL-Anker (Arabisch) auf Elementebene prüfen
                        val isRtl = if (languageCode == "auto") word.language == "ar" else languageCode == "ar"
                        val anchorX = if (isRtl) {
                            (word.bbox.right * scaleX).coerceAtLeast(0f)
                        } else {
                            pdfX
                        }

                        try {
                            val safeText = sanitizeForFont(word.text, font)
                            if (safeText.isEmpty()) continue

                            // Horizontale Skalierung: Textbreite an Bbox-Breite anpassen →
                            // ermöglicht wortgenaue Auswahl im PDF-Viewer
                            val rawWidth = font.getStringWidth(safeText) / 1000f
                            val hScale   = if (rawWidth > 0f && bboxW > 0f) bboxW / rawWidth
                                           else fontSize

                            // Textmatrix berechnen: Rotation + Skalierung
                            // ML Kit liefert Winkel im Uhrzeigersinn (θ), PDF dreht gegen den Uhrzeigersinn.
                            // Matrix für Rotation (θ) und Skalierung (h, v):
                            // [ h*cos(θ)  h*sin(θ)  v*-sin(θ)  v*cos(θ)  x  y ]
                            val theta = Math.toRadians(-word.angle.toDouble())
                            val cos = Math.cos(theta).toFloat()
                            val sin = Math.sin(theta).toFloat()

                            cs.setTextMatrix(Matrix(
                                hScale * cos, hScale * sin,
                                fontSize * -sin, fontSize * cos,
                                anchorX, pdfY
                            ))
                            cs.showText(safeText)

                        } catch (e: Exception) {
                            Log.w("SearchablePdfBuilder", "Word skip at page $i: ${word.text}", e)
                        }
                    }
                    cs.endText()
                }
            }

            document.save(tempFile)
        }

        // Atomarer Dateiaustausch: Original wird erst überschrieben wenn temp-Datei vollständig ist.
        // Files.move mit REPLACE_EXISTING wirft IOException wenn es fehlschlägt —
        // der Aufrufer kann dann kein isSearchable setzen.
        Files.move(tempFile.toPath(), pdfFile.toPath(), StandardCopyOption.REPLACE_EXISTING)

        extractedText
    }

    private suspend fun collectPageResults(
        pdfFile: File,
        recognizer: com.google.mlkit.vision.text.TextRecognizer,
        onProgress: (current: Int, total: Int) -> Unit
    ): Pair<List<PageData>, OcrResultStats?> {
        val pageResults = mutableListOf<PageData>()
        var firstStats: OcrResultStats? = null

        ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                val total = renderer.pageCount
                repeat(total) { i ->
                    onProgress(i + 1, total)
                    renderer.openPage(i).use { page ->
                        val (bitmapW, bitmapH) = ocrBitmapSize(page.width, page.height)

                        val bitmap = Bitmap.createBitmap(bitmapW, bitmapH, Bitmap.Config.ARGB_8888)
                        val (ocrText, ocrStats) = try {
                            bitmap.eraseColor(Color.WHITE)
                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            textRecognizerRunner.recognizeWithStats(
                                recognizer,
                                com.google.mlkit.vision.common.InputImage.fromBitmap(bitmap, 0)
                            )
                        } finally {
                            bitmap.recycle()
                        }

                        if (firstStats == null && ocrText.text.isNotBlank()) {
                            firstStats = ocrStats
                        }

                        val words = ocrText.textBlocks.flatMap { block ->
                            block.lines.flatMap { line ->
                                line.elements.mapNotNull { element ->
                                    val bbox = element.boundingBox ?: return@mapNotNull null
                                    WordData(element.text, Rect(bbox), element.angle, element.recognizedLanguage)
                                }
                            }
                        }
                        pageResults.add(PageData(bitmapW, bitmapH, words))
                    }
                }
            }
        }

        return pageResults to firstStats
    }

    /**
     * Lädt einen geeigneten Font für den Sprachcode.
     * Priorität:
     * 1) App-eigene Assets (geräteunabhängig)
     * 2) Systemfont-Kandidaten (falls Asset fehlt)
     * 3) Helvetica (eingebauter PDF-Fallback)
     */
    private fun loadFont(document: PDDocument, languageCode: String): PDFont {
        val assetCandidates = buildList {
            when (languageCode) {
                "hi" -> add("fonts/NotoSansDevanagari-Regular.ttf")
                "ar" -> add("fonts/NotoSansArabic-Regular.ttf")
                else -> Unit
            }
            add("fonts/NotoSans-Regular.ttf")
        }

        for (assetPath in assetCandidates) {
            try {
                context.assets.open(assetPath).use { stream ->
                    return PDType0Font.load(document, stream, true)
                }
            } catch (_: Exception) { /* nächsten Kandidaten versuchen */ }
        }

        val candidates = buildList {
            when (languageCode) {
                "hi" -> {
                    add("/system/fonts/NotoSansDevanagari-VF.ttf")
                    add("/system/fonts/NotoSansDevanagari-Regular.ttf")
                    add("/system/fonts/NotoSans-Regular.ttf")
                }
                "ar" -> {
                    add("/system/fonts/NotoSansArabic-Regular.ttf")
                    add("/system/fonts/NotoNaskhArabic-Regular.ttf")
                    add("/system/fonts/DroidSansArabic.ttf")
                    add("/system/fonts/NotoSansArabic-VF.ttf")
                }
                "zh", "ja", "ko" -> {
                    // Hinweis: Dieser Zweig ist aktuell nicht erreichbar, da CJK-Sprachen
                    // bereits in MakeSearchableWorkflow abgefangen werden
                    // (ScanWorkflowError.SearchableUnsupportedForScript).
                    // Verbleibt als Vorbereitung für zukünftige CJK-Unterstützung.
                    // CJK-Fonts auf Android sind herstellerabhängig; .ttc-Collections
                    // werden von PdfBox-Android nicht nativ unterstützt.
                    add("/system/fonts/NotoSansCJK-Regular.ttc")
                    add("/system/fonts/NotoSansSC-Regular.otf")
                    add("/system/fonts/NotoSansJP-Regular.otf")
                    add("/system/fonts/NotoSansKR-Regular.otf")
                    add("/system/fonts/DroidSansFallback.ttf")
                }
                else -> Unit
            }
            add("/system/fonts/Roboto-Regular.ttf")
            add("/system/fonts/NotoSans-Regular.ttf")
            add("/system/fonts/DroidSans.ttf")
        }

        for (path in candidates) {
            try {
                val file = File(path)
                if (file.exists()) {
                    return PDType0Font.load(document, file.inputStream(), true)
                }
            } catch (_: Exception) { /* nächsten Kandidaten versuchen */ }
        }

        // Absoluter Fallback: eingebetteter PDF-Standard-Font (nur Latin/ASCII)
        @Suppress("DEPRECATION")
        return PDType1Font.HELVETICA
    }

    /**
     * Filtert Zeichen heraus, die der gewählte Font nicht encodieren kann.
     */
    private fun sanitizeForFont(text: String, font: PDFont): String {
        if (font is PDType1Font) {
            return text.filter { it.code in 32..126 }
        }
        return text.replace("\n", " ").replace("\r", "")
    }
}
