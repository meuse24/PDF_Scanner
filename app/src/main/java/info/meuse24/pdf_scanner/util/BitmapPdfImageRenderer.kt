package info.meuse24.pdf_scanner.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import info.meuse24.pdf_scanner.domain.pdf.PdfImageRenderer
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

@Singleton
class BitmapPdfImageRenderer @Inject constructor(
    @param:ApplicationContext private val context: Context
) : PdfImageRenderer {
    override suspend fun decodeBitmapBytes(uri: Any, maxDimension: Int): ByteArray? {
        val androidUri = uri as? Uri ?: return null
        // Guard inside the try so an invalid maxDimension returns null like other failures,
        // rather than throwing an unhandled IllegalArgumentException at the call site.
        if (maxDimension <= 0) return null

        return try {
            // Single stream read — both decode passes use decodeByteArray so cloud/share
            // URIs that permit only one sequential read (e.g. Google Drive, email attachments)
            // are handled correctly. sourceBytes goes out of scope after the second
            // decodeByteArray call and is eligible for GC before compress runs.
            val sourceBytes = context.contentResolver.openInputStream(androidUri)
                ?.use { it.readBytes() } ?: return null
            if (sourceBytes.size > MAX_SOURCE_BYTES) return null

            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(sourceBytes, 0, sourceBytes.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

            val sampleSize = calculateInSampleSize(
                width = bounds.outWidth,
                height = bounds.outHeight,
                maxDimension = maxDimension
            )
            val decoded = BitmapFactory.decodeByteArray(
                sourceBytes, 0, sourceBytes.size,
                BitmapFactory.Options().apply { inSampleSize = sampleSize }
            ) ?: return null

            // decoded.recycle() is in the outer finally so OOM from scaleDownTo cannot leak.
            try {
                val bitmap = decoded.scaleDownTo(maxDimension)
                try {
                    ByteArrayOutputStream().use { output ->
                        if (!bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)) {
                            return null
                        }
                        output.toByteArray()
                    }
                } finally {
                    if (bitmap !== decoded) bitmap.recycle()
                }
            } finally {
                decoded.recycle()
            }
        } catch (_: OutOfMemoryError) {
            null
        } catch (_: Exception) {
            null
        }
    }

    private fun calculateInSampleSize(width: Int, height: Int, maxDimension: Int): Int {
        var sampleSize = 1
        while (max(width / sampleSize, height / sampleSize) > maxDimension * 2) {
            sampleSize *= 2
        }
        return sampleSize
    }

    private fun Bitmap.scaleDownTo(maxDimension: Int): Bitmap {
        val largestSide = max(width, height)
        if (largestSide <= maxDimension) return this
        val scale = maxDimension.toFloat() / largestSide
        return Bitmap.createScaledBitmap(
            this,
            (width * scale).toInt().coerceAtLeast(1),
            (height * scale).toInt().coerceAtLeast(1),
            true
        )
    }

    private companion object {
        const val JPEG_QUALITY = 85
        const val MAX_SOURCE_BYTES = 50_000_000 // 50 MB – rejects uncompressed/RAW inputs
    }
}
