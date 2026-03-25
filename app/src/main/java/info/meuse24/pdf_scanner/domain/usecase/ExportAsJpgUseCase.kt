package info.meuse24.pdf_scanner.domain.usecase

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import info.meuse24.pdf_scanner.data.local.ScanRecord
import java.io.File
import javax.inject.Inject

/**
 * Rendert alle Seiten eines PDFs als JPEG und exportiert sie in MediaStore.Downloads.
 * Nutzt das IS_PENDING-Pattern für atomares Schreiben.
 *
 * Fehlerverhalten:
 * - Null-OutputStream → wirft sofort (kein stiller Fehlschlag, kein exported++)
 * - bitmap.compress() == false → wirft sofort
 * - resolver.update() == 0 → wirft sofort
 * - Fehler bei Seite N → alle bereits geschriebenen Seiten werden aus Downloads gelöscht (Rollback)
 *
 * @return Anzahl der erfolgreich exportierten Seiten
 */
class ExportAsJpgUseCase @Inject constructor(
    @ApplicationContext private val context: Context
) {
    suspend operator fun invoke(record: ScanRecord): Int {
        val sourceFile = File(record.filepath)
        if (!sourceFile.exists()) error("Quelldatei nicht gefunden: ${record.filepath}")

        val resolver    = context.contentResolver
        val collection  = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val baseName    = record.filename
        val committed   = mutableListOf<Uri>()

        try {
            ParcelFileDescriptor.open(sourceFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    val pageCount = renderer.pageCount
                    repeat(pageCount) { pageIndex ->
                        renderer.openPage(pageIndex).use { page ->
                            val scale  = 150f / 72f
                            val bmpW   = (page.width  * scale).toInt().coerceAtLeast(1)
                            val bmpH   = (page.height * scale).toInt().coerceAtLeast(1)
                            val bitmap = Bitmap.createBitmap(bmpW, bmpH, Bitmap.Config.ARGB_8888)
                            try {
                                Canvas(bitmap).drawColor(Color.WHITE)
                                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)

                                val displayName = if (pageCount == 1) "$baseName.jpg"
                                                 else "${baseName}_p${pageIndex + 1}.jpg"

                                val cv = ContentValues().apply {
                                    put(MediaStore.Downloads.DISPLAY_NAME, displayName)
                                    put(MediaStore.Downloads.MIME_TYPE, "image/jpeg")
                                    put(MediaStore.Downloads.IS_PENDING, 1)
                                }
                                val itemUri = resolver.insert(collection, cv)
                                    ?: error("MediaStore.insert lieferte null für Seite ${pageIndex + 1}")
                                try {
                                    val out = resolver.openOutputStream(itemUri)
                                        ?: error("openOutputStream lieferte null für Seite ${pageIndex + 1}")
                                    val ok = out.use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
                                    if (!ok) error("JPEG-Komprimierung fehlgeschlagen für Seite ${pageIndex + 1}")

                                    cv.clear()
                                    cv.put(MediaStore.Downloads.IS_PENDING, 0)
                                    val updated = resolver.update(itemUri, cv, null, null)
                                    if (updated == 0) error("IS_PENDING-Freigabe fehlgeschlagen für Seite ${pageIndex + 1}")

                                    committed.add(itemUri)
                                } catch (e: Exception) {
                                    resolver.delete(itemUri, null, null)
                                    throw e
                                }
                            } finally {
                                bitmap.recycle()
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Rollback: bereits in Downloads geschriebene Seiten löschen
            committed.forEach { uri -> runCatching { resolver.delete(uri, null, null) } }
            throw e
        }

        return committed.size
    }
}
