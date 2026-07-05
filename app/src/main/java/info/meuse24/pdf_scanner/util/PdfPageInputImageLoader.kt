package info.meuse24.pdf_scanner.util

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.core.graphics.createBitmap
import com.google.mlkit.vision.common.InputImage
import java.io.File
import javax.inject.Inject

/**
 * Gemeinsamer Renderstandard für alle OCR-Pfade (Text extrahieren + Searchable PDF).
 *
 * 150 DPI / 72 pt ≈ 2.083 — empfohlene ML-Kit-Mindestqualität für präzise Texterkennung.
 * MAX_BITMAP_SIDE schützt vor OOM bei sehr großen Seiten (z.B. A0-Poster): die längere
 * Seite wird auf max. 3 000 px begrenzt, die kürzere proportional skaliert.
 * Eine normale A4-Seite (1 239 × 1 754 px bei 150 DPI) bleibt unverändert.
 */
internal const val PDF_OCR_RENDER_SCALE   = 150f / 72f   // ≈ 2.083
internal const val PDF_OCR_MAX_BITMAP_SIDE = 3_000

/**
 * Eigener Render-Standard für den Tabellenpfad: 220 DPI statt 150 DPI. Bei eng gedruckten
 * Kontoauszügen (~6-pt-Schrift) liegen Zeichen bei 150 DPI nur bei ~12 px Höhe — unter der
 * ML-Kit-Empfehlung von ~16 px, wodurch benachbarte Spalten schon auf Bitmap-Ebene zu einer
 * Line verschmelzen können. 220 DPI bleibt unter dem 3 000-px-Cap (siehe
 * docs/table-extraction-csv-implementation-plan.md, Abschnitt "Render-Auflösung des
 * Tabellenpfads").
 */
internal const val PDF_TABLE_RENDER_SCALE = 220f / 72f

/** Berechnet Bitmap-Dimensionen für OCR: DPI-Skalierung (Default: 150 DPI) + Größen-Cap. */
internal fun ocrBitmapSize(
    pageWidthPts: Int,
    pageHeightPts: Int,
    renderScale: Float = PDF_OCR_RENDER_SCALE
): Pair<Int, Int> {
    val rawW  = (pageWidthPts  * renderScale).toInt().coerceAtLeast(1)
    val rawH  = (pageHeightPts * renderScale).toInt().coerceAtLeast(1)
    val longer = maxOf(rawW, rawH)
    return if (longer > PDF_OCR_MAX_BITMAP_SIDE) {
        rawW * PDF_OCR_MAX_BITMAP_SIDE / longer to rawH * PDF_OCR_MAX_BITMAP_SIDE / longer
    } else {
        rawW to rawH
    }
}

interface PdfPageInputImageLoader {
    /**
     * @param onPageImage erhält zusätzlich zum Bild den nullbasierten Seitenindex, die
     * Gesamtseitenzahl und die tatsächlichen Bitmap-Abmessungen — benötigt für Positionsmodelle
     * (Tabellenpfad) und Seitenfortschritt.
     */
    suspend fun forEachPageImage(
        sourceFile: File,
        renderScale: Float = PDF_OCR_RENDER_SCALE,
        onPageImage: suspend (pageIndex: Int, pageCount: Int, bitmapWidth: Int, bitmapHeight: Int, image: InputImage) -> Unit
    )
}

class AndroidPdfPageInputImageLoader @Inject constructor() : PdfPageInputImageLoader {
    override suspend fun forEachPageImage(
        sourceFile: File,
        renderScale: Float,
        onPageImage: suspend (pageIndex: Int, pageCount: Int, bitmapWidth: Int, bitmapHeight: Int, image: InputImage) -> Unit
    ) {
        ParcelFileDescriptor.open(sourceFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                val pageCount = renderer.pageCount
                repeat(pageCount) { pageIndex ->
                    renderer.openPage(pageIndex).use { page ->
                        val (bitmapW, bitmapH) = ocrBitmapSize(page.width, page.height, renderScale)
                        val bitmap = createBitmap(bitmapW, bitmapH, Bitmap.Config.ARGB_8888)
                        try {
                            bitmap.eraseColor(Color.WHITE)
                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            onPageImage(pageIndex, pageCount, bitmapW, bitmapH, InputImage.fromBitmap(bitmap, 0))
                        } finally {
                            bitmap.recycle()
                        }
                    }
                }
            }
        }
    }
}
