package info.meuse24.pdf_scanner.domain.usecase

import android.content.ContentValues
import android.content.Context
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import info.meuse24.pdf_scanner.data.local.ScanRecord
import java.io.File
import javax.inject.Inject

/**
 * Exportiert einen Scan via MediaStore in den Downloads-Ordner.
 * Nutzt das IS_PENDING-Pattern für atomares Schreiben.
 * @return den angezeigten Dateinamen (für den Erfolgs-Toast)
 */
class ExportScanUseCase @Inject constructor(
    @ApplicationContext private val context: Context
) {
    suspend operator fun invoke(record: ScanRecord): String {
        val sourceFile = File(record.filepath)
        if (!sourceFile.exists()) error("Quelldatei nicht gefunden: ${record.filepath}")

        val displayName   = "${record.filename}.pdf"
        val contentValues = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, displayName)
            put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val resolver   = context.contentResolver
        val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val itemUri    = resolver.insert(collection, contentValues)
            ?: error("MediaStore.insert lieferte null")

        try {
            resolver.openOutputStream(itemUri)?.use { output ->
                sourceFile.inputStream().use { input -> input.copyTo(output) }
            }
            contentValues.clear()
            contentValues.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(itemUri, contentValues, null, null)
            return displayName
        } catch (e: Exception) {
            resolver.delete(itemUri, null, null)
            throw e
        }
    }
}
