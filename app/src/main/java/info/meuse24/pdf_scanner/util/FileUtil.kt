package info.meuse24.pdf_scanner.util

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FileUtil @Inject constructor(@ApplicationContext private val context: Context) {

    /**
     * Copies the ML Kit result PDF to app-internal storage.
     * - Ensures a unique destination filename to prevent silent overwrite (#1).
     * - Throws if the source stream is null or the copy yields an empty file (#3).
     * - Deletes any partial file on error (#3).
     */
    fun savePdfFromUri(sourceUri: Uri, filename: String): File {
        val scansDir = File(context.filesDir, "scans").apply { mkdirs() }

        // Make filename unique: append _2, _3, … if the target already exists (#1)
        var destFile = File(scansDir, "$filename.pdf")
        if (destFile.exists()) {
            var counter = 2
            do {
                destFile = File(scansDir, "${filename}_$counter.pdf")
                counter++
            } while (destFile.exists())
        }

        // Validate source stream (#3)
        val inputStream = context.contentResolver.openInputStream(sourceUri)
            ?: throw IllegalStateException("PDF-Quelle konnte nicht geöffnet werden")

        try {
            inputStream.use { input ->
                destFile.outputStream().use { output -> input.copyTo(output) }
            }
            // Reject empty result (#3)
            if (destFile.length() == 0L) {
                destFile.delete()
                throw IllegalStateException("Gespeicherte PDF ist leer")
            }
        } catch (e: Exception) {
            destFile.delete() // clean up partial write (#3)
            throw e
        }

        return destFile
    }

    fun getFileProviderUri(file: File): Uri =
        androidx.core.content.FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", file
        )
}
