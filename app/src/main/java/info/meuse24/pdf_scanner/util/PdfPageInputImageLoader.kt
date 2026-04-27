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

/** Berechnet Bitmap-Dimensionen für OCR: 150-DPI-Skalierung + Größen-Cap. */
internal fun ocrBitmapSize(pageWidthPts: Int, pageHeightPts: Int): Pair<Int, Int> {
    val rawW  = (pageWidthPts  * PDF_OCR_RENDER_SCALE).toInt().coerceAtLeast(1)
    val rawH  = (pageHeightPts * PDF_OCR_RENDER_SCALE).toInt().coerceAtLeast(1)
    val longer = maxOf(rawW, rawH)
    return if (longer > PDF_OCR_MAX_BITMAP_SIDE) {
        rawW * PDF_OCR_MAX_BITMAP_SIDE / longer to rawH * PDF_OCR_MAX_BITMAP_SIDE / longer
    } else {
        rawW to rawH
    }
}

interface PdfPageInputImageLoader {
    suspend fun forEachPageImage(
        sourceFile: File,
        onPageImage: suspend (InputImage) -> Unit
    )
}

class AndroidPdfPageInputImageLoader @Inject constructor() : PdfPageInputImageLoader {
    override suspend fun forEachPageImage(
        sourceFile: File,
        onPageImage: suspend (InputImage) -> Unit
    ) {
        ParcelFileDescriptor.open(sourceFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                repeat(renderer.pageCount) { pageIndex ->
                    renderer.openPage(pageIndex).use { page ->
                        val (bitmapW, bitmapH) = ocrBitmapSize(page.width, page.height)
                        val bitmap = createBitmap(bitmapW, bitmapH, Bitmap.Config.ARGB_8888)
                        try {
                            bitmap.eraseColor(Color.WHITE)
                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            onPageImage(InputImage.fromBitmap(bitmap, 0))
                        } finally {
                            bitmap.recycle()
                        }
                    }
                }
            }
        }
    }
}
