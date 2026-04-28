package info.meuse24.pdf_scanner.util

import android.content.ContentValues
import android.content.Context
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import info.meuse24.pdf_scanner.domain.gateway.DownloadEntry
import info.meuse24.pdf_scanner.domain.gateway.DownloadsStorage
import java.io.OutputStream
import javax.inject.Inject

class AndroidDownloadsStorage @Inject constructor(
    @param:ApplicationContext private val context: Context
) : DownloadsStorage {
    override fun writeDownload(
        displayName: String,
        mimeType: String,
        writer: (OutputStream) -> Unit
    ): DownloadEntry {
        val contentValues = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, displayName)
            put(MediaStore.Downloads.MIME_TYPE, mimeType)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val itemUri = resolver.insert(collection, contentValues)
            ?: error("MediaStore.insert lieferte null")

        try {
            val outputStream = resolver.openOutputStream(itemUri)
                ?: error("openOutputStream lieferte null")
            outputStream.use(writer)

            contentValues.clear()
            contentValues.put(MediaStore.Downloads.IS_PENDING, 0)
            val updated = resolver.update(itemUri, contentValues, null, null)
            if (updated == 0) error("IS_PENDING-Freigabe fehlgeschlagen")
        } catch (e: Exception) {
            resolver.delete(itemUri, null, null)
            throw e
        }

        return MediaStoreDownloadEntry(
            displayName = displayName,
            deleteEntry = { resolver.delete(itemUri, null, null) }
        )
    }
}

private class MediaStoreDownloadEntry(
    override val displayName: String,
    private val deleteEntry: () -> Unit
) : DownloadEntry {
    override fun delete() = deleteEntry()
}
