package info.meuse24.pdf_scanner.util

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import info.meuse24.pdf_scanner.R
import info.meuse24.pdf_scanner.domain.gateway.DocumentFileStore
import info.meuse24.pdf_scanner.domain.gateway.ResourceProvider
import info.meuse24.pdf_scanner.domain.gateway.StorageProvider
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
open class FileUtil @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val storageProvider: StorageProvider,
    private val resourceProvider: ResourceProvider
) : DocumentFileStore {

    override fun savePdf(source: Any, filename: String): File {
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

        try {
            copySourceToFile(source, destFile)
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

    open fun savePdfFromUri(sourceUri: Uri, filename: String): File = savePdf(sourceUri, filename)

    override fun saveThumbnail(source: Any, filename: String): File? {
        return try {
            val scansDir = storageProvider.scansDir()
            val destFile = File(scansDir, "$filename.jpg")
            copySourceToFile(source, destFile)
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

    open fun saveThumbnailFromUri(sourceUri: Uri, filename: String): File? = saveThumbnail(sourceUri, filename)

    override fun copyToTemp(source: Any, suffix: String): File {
        val normalizedSuffix = if (suffix.startsWith(".")) suffix else ".$suffix"
        val destFile = File.createTempFile("pdf_scanner_", normalizedSuffix, storageProvider.tempDir())
        try {
            copySourceToFile(source, destFile)
            if (destFile.length() == 0L) {
                throw IllegalStateException(resourceProvider.getString(R.string.error_pdf_empty))
            }
            return destFile
        } catch (exception: Exception) {
            destFile.delete()
            throw exception
        }
    }

    private fun copySourceToFile(source: Any, destFile: File) {
        val sourceUri = source as? Uri
            ?: throw IllegalArgumentException("Unsupported document source: ${source::class.java.name}")
        val inputStream = context.contentResolver.openInputStream(sourceUri)
            ?: throw IllegalStateException(resourceProvider.getString(R.string.error_source_unavailable))
        inputStream.use { input ->
            destFile.outputStream().use { output -> input.copyTo(output) }
        }
    }
}
