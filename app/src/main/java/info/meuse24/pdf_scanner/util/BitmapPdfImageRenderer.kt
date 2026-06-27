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
        require(maxDimension > 0) { "maxDimension must be positive" }

        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(androidUri)?.use { input ->
                BitmapFactory.decodeStream(input, null, bounds)
            } ?: return null
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

            val sampleSize = calculateInSampleSize(
                width = bounds.outWidth,
                height = bounds.outHeight,
                maxDimension = maxDimension
            )
            val decoded = context.contentResolver.openInputStream(androidUri)?.use { input ->
                BitmapFactory.decodeStream(
                    input,
                    null,
                    BitmapFactory.Options().apply { inSampleSize = sampleSize }
                )
            } ?: return null

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
                decoded.recycle()
            }
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
    }
}
