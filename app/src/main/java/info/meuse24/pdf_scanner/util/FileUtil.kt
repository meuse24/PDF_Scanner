package info.meuse24.pdf_scanner.util

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import info.meuse24.pdf_scanner.R
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FileUtil @Inject constructor(@ApplicationContext private val context: Context) {

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

        val inputStream = context.contentResolver.openInputStream(sourceUri)
            ?: throw IllegalStateException(context.getString(R.string.error_source_unavailable))

        try {
            inputStream.use { input ->
                destFile.outputStream().use { output -> input.copyTo(output) }
            }
            if (destFile.length() == 0L) {
                destFile.delete()
                throw IllegalStateException(context.getString(R.string.error_pdf_empty))
            }
        } catch (e: Exception) {
            destFile.delete()
            throw e
        }

        return destFile
    }

    fun getFileProviderUri(file: File): Uri =
        androidx.core.content.FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", file
        )
}
