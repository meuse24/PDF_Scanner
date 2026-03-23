package info.meuse24.pdf_scanner.domain.usecase

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognizer
import dagger.hilt.android.qualifiers.ApplicationContext
import info.meuse24.pdf_scanner.data.local.ScanRecord
import info.meuse24.pdf_scanner.util.OcrManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Führt OCR auf einer Liste von ScanRecords aus.
 * Dedupliziert die frühere extractText/extractTexts-Logik aus HomeViewModel.
 * Bei mehreren Records wird der Dateiname als Trenner eingefügt.
 *
 * @return erkannter Text (nie leer; wirft Exception wenn kein Text gefunden)
 */
class ExtractTextUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val ocrManager: OcrManager
) {
    suspend operator fun invoke(records: List<ScanRecord>, languageCode: String): String {
        val recognizer = ocrManager.getRecognizer(languageCode)
        val results    = StringBuilder()
        try {
            for (record in records) {
                val pageText = extractFromRecord(record, recognizer)
                if (pageText.isNotBlank()) {
                    if (results.isNotEmpty()) results.append("\n\n")
                    if (records.size > 1) {
                        results.append("— ${record.filename} —\n")
                    }
                    results.append(pageText)
                }
            }
        } finally {
            recognizer.close()
        }
        if (results.isBlank()) error("Kein Text in den übergebenen Records gefunden")
        return results.toString()
    }

    private suspend fun extractFromRecord(record: ScanRecord, recognizer: TextRecognizer): String {
        val pdfFile   = File(record.filepath)
        val pageTexts = StringBuilder()

        if (pdfFile.exists()) {
            withContext(Dispatchers.IO) {
                ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                    PdfRenderer(pfd).use { renderer ->
                        repeat(renderer.pageCount) { i ->
                            renderer.openPage(i).use { page ->
                                val bmp = Bitmap.createBitmap(
                                    page.width.coerceAtLeast(1),
                                    page.height.coerceAtLeast(1),
                                    Bitmap.Config.ARGB_8888
                                )
                                bmp.eraseColor(Color.WHITE)
                                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                val text = try {
                                    suspendCancellableCoroutine { cont ->
                                        recognizer.process(InputImage.fromBitmap(bmp, 0))
                                            .addOnSuccessListener { cont.resume(it.text) }
                                            .addOnFailureListener { e -> cont.resumeWithException(e) }
                                        cont.invokeOnCancellation { recognizer.close() }
                                    }
                                } finally {
                                    bmp.recycle()
                                }
                                if (text.isNotBlank()) {
                                    if (pageTexts.isNotEmpty()) pageTexts.append("\n\n")
                                    pageTexts.append(text)
                                }
                            }
                        }
                    }
                }
            }
        } else if (record.thumbnailPath != null) {
            // Fallback: nur erste Seite via Thumbnail
            val image = withContext(Dispatchers.IO) {
                InputImage.fromFilePath(context, Uri.fromFile(File(record.thumbnailPath)))
            }
            val text = suspendCancellableCoroutine { cont ->
                recognizer.process(image)
                    .addOnSuccessListener { cont.resume(it.text) }
                    .addOnFailureListener { e -> cont.resumeWithException(e) }
                cont.invokeOnCancellation { recognizer.close() }
            }
            if (text.isNotBlank()) pageTexts.append(text)
        }

        return pageTexts.toString()
    }
}
