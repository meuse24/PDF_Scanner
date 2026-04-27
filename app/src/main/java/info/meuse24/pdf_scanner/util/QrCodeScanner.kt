package info.meuse24.pdf_scanner.util

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.core.graphics.createBitmap
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import info.meuse24.pdf_scanner.domain.usecase.QrCodeResult
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal interface QrScannerResource {
    fun close()
}

internal suspend fun <T : QrScannerResource, R> useQrScannerResource(
    resource: T,
    block: suspend (T) -> R
): R {
    try {
        return block(resource)
    } finally {
        resource.close()
    }
}

private class MlKitQrProcessor(
    private val scanner: BarcodeScanner
) : QrScannerResource {
    override fun close() {
        scanner.close()
    }

    suspend fun process(bitmap: Bitmap): List<Barcode> = suspendCancellableCoroutine { continuation ->
        scanner.process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener { continuation.resume(it) }
            .addOnFailureListener { continuation.resumeWithException(it) }
    }
}

open class QrCodeScanner @Inject constructor(
    private val ocrModelInstaller: OcrModelInstaller
) {

    companion object {
        private const val RENDER_DPI = 150f // Höhere DPI für bessere Barcode-Erkennung
        private const val POINTS_PER_INCH = 72f
        private val RENDER_SCALE = RENDER_DPI / POINTS_PER_INCH
    }

    open suspend fun scan(
        pdfFile: File,
        onProgress: (page: Int, total: Int) -> Unit = { _, _ -> },
        onStatus: (OcrPipelineStatus) -> Unit = {}
    ): List<QrCodeResult> {
        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
            .build()
        val scanner = BarcodeScanning.getClient(options)

        // Sicherstellen, dass das Barcode-Modell heruntergeladen wurde
        ocrModelInstaller.ensureBarcodeModelAvailable(scanner, onStatus)

        val processor = MlKitQrProcessor(scanner)

        return useQrScannerResource(processor) { activeProcessor ->
            ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    val results = mutableListOf<QrCodeResult>()
                    val total = renderer.pageCount
                    onProgress(0, total)

                    repeat(total) { index ->
                        renderer.openPage(index).use { page ->
                            val widthPx = (page.width * RENDER_SCALE).toInt().coerceAtLeast(1)
                            val heightPx = (page.height * RENDER_SCALE).toInt().coerceAtLeast(1)
                            val bitmap = createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
                            bitmap.eraseColor(Color.WHITE)

                            try {
                                page.render(
                                    bitmap,
                                    null,
                                    null,
                                    PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY
                                )
                                val barcodes = activeProcessor.process(bitmap)
                                results += barcodes.mapNotNull { barcode ->
                                    barcode.rawValue?.let { rawValue ->
                                        QrCodeResult(
                                            rawValue = rawValue,
                                            valueType = barcode.valueType,
                                            displayUrl = barcode.url?.url,
                                            wifiSsid = barcode.wifi?.ssid,
                                            wifiPassword = barcode.wifi?.password,
                                            pageNumber = index + 1
                                        )
                                    }
                                }
                            } finally {
                                bitmap.recycle()
                            }
                        }
                        onProgress(index + 1, total)
                    }

                    results
                }
            }
        }
    }
}
