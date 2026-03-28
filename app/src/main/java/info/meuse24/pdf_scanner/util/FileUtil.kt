package info.meuse24.pdf_scanner.util

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import info.meuse24.pdf_scanner.R
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
open class FileUtil @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val storageProvider: StorageProvider,
    private val resourceProvider: ResourceProvider
) {

    open fun savePdfFromUri(sourceUri: Uri, filename: String): File {
        val scansDir = storageProvider.scansDir()

        // Make filename unique: append _2, _3, … if the target already exists (#1)
        var destFile = File(scansDir, "$filename.pdf")
        if (destFile.exists()) {
            var counter = 2
            do {
                destFile = File(scansDir, "${filename}_$counter.pdf")
                counter++
            } while (destFile.exists())
        }

        val inputStream = context.contentResolver.openInputStream(sourceUri)
            ?: throw IllegalStateException(resourceProvider.getString(R.string.error_source_unavailable))

        try {
            inputStream.use { input ->
                destFile.outputStream().use { output -> input.copyTo(output) }
            }
            if (destFile.length() == 0L) {
                destFile.delete()
                throw IllegalStateException(resourceProvider.getString(R.string.error_pdf_empty))
            }
        } catch (e: Exception) {
            destFile.delete()
            throw e
        }

        return destFile
    }

    open fun saveThumbnailFromUri(sourceUri: Uri, filename: String): File? {
        return try {
            val scansDir = storageProvider.scansDir()
            val destFile = File(scansDir, "$filename.jpg")
            val inputStream = context.contentResolver.openInputStream(sourceUri) ?: return null
            inputStream.use { input ->
                destFile.outputStream().use { output -> input.copyTo(output) }
            }
            if (destFile.length() == 0L) {
                destFile.delete()
                null
            } else {
                destFile
            }
        } catch (_: Exception) {
            null
        }
    }

}
