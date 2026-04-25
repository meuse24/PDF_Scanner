package info.meuse24.pdf_scanner.util

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import info.meuse24.pdf_scanner.domain.pdf.PdfImageRenderer
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BitmapPdfImageRenderer @Inject constructor(
    @param:ApplicationContext private val context: Context
) : PdfImageRenderer {
    override suspend fun decodeBitmapBytes(uri: Any, maxDimension: Int): ByteArray? {
        val androidUri = uri as? android.net.Uri ?: return null
        return runCatching {
            context.contentResolver.openInputStream(androidUri)?.use { it.readBytes() }
        }.getOrNull()
    }
}
