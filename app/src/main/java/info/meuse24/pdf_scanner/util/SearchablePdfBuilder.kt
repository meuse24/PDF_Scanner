package info.meuse24.pdf_scanner.util

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.google.mlkit.vision.common.InputImage
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDFont
import com.tom_roush.pdfbox.pdmodel.font.PDType0Font
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.state.RenderingMode
import com.tom_roush.pdfbox.util.Matrix
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

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
class SearchablePdfBuilder @Inject constructor(
    private val ocrManager: OcrManager
) {
    companion object {
        private const val RENDER_DPI = 150f
        private const val POINTS_PER_INCH = 72f
        private val RENDER_SCALE = RENDER_DPI / POINTS_PER_INCH // ≈ 2.083
    }

    /** Kompaktes Ergebnis pro Seite — enthält keine Bitmap mehr. */
    private data class BlockData(val text: String, val bbox: Rect)
    private data class PageData(
        val widthPts: Float,
        val heightPts: Float,
        val bitmapW: Int,
        val bitmapH: Int,
        val blocks: List<BlockData>
    )

    suspend fun makeSearchable(
        pdfFile: File,
        languageCode: String,
        onProgress: (current: Int, total: Int) -> Unit
    ) = withContext(Dispatchers.IO) {

        val recognizer = ocrManager.getRecognizer(languageCode)
        val pageResults = mutableListOf<PageData>()

        // ── Phase 1: Rendern + OCR — jeweils eine Seite, Bitmap sofort recyceln ──
        // Nur EINE Bitmap (≈ 4 MB bei A4/150 DPI) gleichzeitig im Heap.
        // Recognizer in try-finally: wird auch bei OCR-Fehler mitten in der Schleife
        // sauber geschlossen.
        try {
            ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    val total = renderer.pageCount
                    repeat(total) { i ->
                        onProgress(i + 1, total)
                        renderer.openPage(i).use { page ->
                            val widthPts = page.width.toFloat()
                            val heightPts = page.height.toFloat()
                            val bitmapW = (widthPts * RENDER_SCALE).toInt().coerceAtLeast(1)
                            val bitmapH = (heightPts * RENDER_SCALE).toInt().coerceAtLeast(1)

                            val bitmap = Bitmap.createBitmap(bitmapW, bitmapH, Bitmap.Config.RGB_565)
                            bitmap.eraseColor(Color.WHITE)
                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                            val ocrText = try {
                                suspendCancellableCoroutine { cont ->
                                    recognizer.process(InputImage.fromBitmap(bitmap, 0))
                                        .addOnSuccessListener { cont.resume(it) }
                                        .addOnFailureListener { cont.resumeWithException(it) }
                                    cont.invokeOnCancellation { recognizer.close() }
                                }
                            } finally {
                                bitmap.recycle() // immer freigeben — auch bei OCR-Fehler
                            }

                            pageResults.add(
                                PageData(
                                    widthPts, heightPts, bitmapW, bitmapH,
                                    ocrText.textBlocks.mapNotNull { block ->
                                        val bbox = block.boundingBox ?: return@mapNotNull null
                                        BlockData(block.text, Rect(bbox))
                                    }
                                )
                            )
                        }
                    }
                }
            } // PdfRenderer hier geschlossen — PdfBox kann jetzt dieselbe Datei öffnen
        } finally {
            recognizer.close()
        }

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
                    cs.setFont(font, 1f)

                    for (block in pd.blocks) {
                        val fontSize = ((block.bbox.bottom - block.bbox.top) * scaleY)
                            .coerceAtLeast(1f)

                        // PDF-Koordinaten: Ursprung unten links, Y wächst aufwärts
                        val pdfX = block.bbox.left * scaleX
                        val pdfY = pageH - block.bbox.bottom * scaleY

                        // RTL (Arabisch): Text am rechten Rand der BoundingBox verankern
                        val adjustedX = if (languageCode == "ar") {
                            (block.bbox.right * scaleX).coerceAtLeast(0f)
                        } else {
                            pdfX
                        }

                        try {
                            cs.setFont(font, fontSize)
                            cs.setTextMatrix(Matrix.getTranslateInstance(adjustedX, pdfY))
                            val safeText = sanitizeForFont(block.text, font)
                            if (safeText.isNotEmpty()) cs.showText(safeText)
                        } catch (_: Exception) {
                            // Block überspringen wenn Font Zeichen nicht encodieren kann
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
    }

    /**
     * Lädt einen geeigneten Systemfont für den Sprachcode.
     * Fallback-Kette: skriptspezifisch → Roboto (Latin+Cyrillic) → Helvetica (PDF built-in).
     *
     * Alle Pfad-Kandidaten werden per file.exists() geprüft, PDType0Font.load()
     * ist in try-catch eingeschlossen — kein Absturz bei herstellerspezifischen Pfaden.
     *
     * Technical Debt: Robustere Alternative wäre eine NotoSans-Subset-Datei in
     * src/main/assets/, die geräteunabhängig immer vorhanden ist.
     */
    private fun loadFont(document: PDDocument, languageCode: String): PDFont {
        val candidates = buildList {
            when (languageCode) {
                "zh", "ja" -> {
                    add("/system/fonts/NotoSansCJK-Regular.ttc")
                    add("/system/fonts/NotoSansJP-Regular.otf")
                    add("/system/fonts/DroidSansFallback.ttf")
                    add("/system/fonts/NotoSansSC-Regular.otf")  // Samsung
                }
                "hi" -> {
                    add("/system/fonts/NotoSansDevanagari-Regular.ttf")
                    add("/system/fonts/NotoSans-Regular.ttf")
                }
                "ar" -> {
                    add("/system/fonts/NotoSansArabic-Regular.ttf")
                    add("/system/fonts/NotoNaskhArabic-Regular.ttf")
                    add("/system/fonts/DroidSansArabic.ttf")         // ältere Geräte
                    add("/system/fonts/NotoSansArabic-VF.ttf")       // Variable Font (neuere AOSP)
                }
                else -> Unit
            }
            add("/system/fonts/Roboto-Regular.ttf")
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
