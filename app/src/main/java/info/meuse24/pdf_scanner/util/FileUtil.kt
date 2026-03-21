package info.meuse24.pdf_scanner.util

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FileUtil @Inject constructor(@ApplicationContext private val context: Context) {

    fun savePdfFromUri(sourceUri: Uri, filename: String): File {
        val scansDir = File(context.filesDir, "scans").apply { mkdirs() }
        val destFile = File(scansDir, "$filename.pdf")
        context.contentResolver.openInputStream(sourceUri)?.use { input ->
            destFile.outputStream().use { output -> input.copyTo(output) }
        }
        return destFile
    }

    fun getFileProviderUri(file: File): Uri =
        androidx.core.content.FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", file
        )
}
