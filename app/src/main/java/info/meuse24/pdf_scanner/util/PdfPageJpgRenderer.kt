package info.meuse24.pdf_scanner.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.core.graphics.createBitmap
import info.meuse24.pdf_scanner.domain.gateway.PdfPageJpgRenderer
import java.io.File
import java.io.OutputStream
import javax.inject.Inject

class AndroidPdfPageJpgRenderer @Inject constructor() : PdfPageJpgRenderer {
    override fun renderPages(
        pdfFile: File,
        onPage: (
            pageIndex: Int,
            pageCount: Int,
            writeJpeg: (OutputStream) -> Unit
        ) -> Unit
    ) {
        ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                val pageCount = renderer.pageCount
                repeat(pageCount) { pageIndex ->
                    renderer.openPage(pageIndex).use { page ->
                        val scale = 150f / 72f
                        val bmpW = (page.width * scale).toInt().coerceAtLeast(1)
                        val bmpH = (page.height * scale).toInt().coerceAtLeast(1)
                        val bitmap = createBitmap(bmpW, bmpH, Bitmap.Config.ARGB_8888)
                        try {
                            Canvas(bitmap).drawColor(Color.WHITE)
                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                            onPage(pageIndex, pageCount) { output ->
                                val ok = bitmap.compress(Bitmap.CompressFormat.JPEG, 90, output)
                                if (!ok) {
                                    error("JPEG-Komprimierung fehlgeschlagen für Seite ${pageIndex + 1}")
                                }
                            }
                        } finally {
                            bitmap.recycle()
                        }
                    }
                }
            }
        }
    }
}
