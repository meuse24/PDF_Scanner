package info.meuse24.pdf_scanner.util

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.google.mlkit.vision.common.InputImage
import java.io.File
import javax.inject.Inject

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
                        val bitmap = Bitmap.createBitmap(
                            page.width.coerceAtLeast(1),
                            page.height.coerceAtLeast(1),
                            Bitmap.Config.ARGB_8888
                        )
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
