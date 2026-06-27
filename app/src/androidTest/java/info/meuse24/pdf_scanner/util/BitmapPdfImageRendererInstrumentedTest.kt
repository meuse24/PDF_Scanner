package info.meuse24.pdf_scanner.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BitmapPdfImageRendererInstrumentedTest {

    @Test
    fun decodeBitmapBytes_limitsLargestDimension() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val source = File(context.cacheDir, "bitmap_renderer_source.png")
        val bitmap = Bitmap.createBitmap(1200, 600, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.WHITE)
        }
        source.outputStream().use { output ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
        }
        bitmap.recycle()

        try {
            val bytes = BitmapPdfImageRenderer(context).decodeBitmapBytes(
                uri = Uri.fromFile(source),
                maxDimension = 300
            )

            assertNotNull(bytes)
            val decoded = BitmapFactory.decodeByteArray(bytes, 0, checkNotNull(bytes).size)
            assertNotNull(decoded)
            checkNotNull(decoded).useBitmap {
                assertTrue(maxOf(it.width, it.height) <= 300)
            }
        } finally {
            source.delete()
        }
    }
}

private inline fun <T> Bitmap.useBitmap(block: (Bitmap) -> T): T =
    try {
        block(this)
    } finally {
        recycle()
    }
