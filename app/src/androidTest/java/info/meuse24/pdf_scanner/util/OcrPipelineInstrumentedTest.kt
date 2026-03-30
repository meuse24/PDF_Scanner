package info.meuse24.pdf_scanner.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OcrPipelineInstrumentedTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val ocrManager = OcrManager()
    private val ocrModelInstaller = AndroidOcrModelInstaller(context)
    private val ocrPipeline = OcrPipeline(ocrManager, ocrModelInstaller)
    private val textRecognizerRunner = MlKitTextRecognizerRunner()

    @Test
    fun latinOcrProducesHighConfidenceOnLatinText() = runBlocking {
        val bitmap = createTextBitmap("This is a test of Latin OCR.", fontSize = 60f)
        val image = InputImage.fromBitmap(bitmap, 0)
        val recognizer = ocrManager.getRecognizer(OcrScript.LATIN)

        try {
            val (text, stats) = textRecognizerRunner.recognizeWithStats(recognizer, image)
            
            assertTrue("Text should contain 'test'", text.text.contains("test", ignoreCase = true))
            // Hinweis: Auf GPU-armen CI-Emulatoren könnte die Confidence niedriger ausfallen.
            // Schwelle > 0.8 ist für physische Geräte sicher.
            assertTrue("Confidence should be high (> 0.8)", stats.confidence > 0.8f)
            assertEquals("Language should be 'en'", "en", stats.recognizedLanguage)
        } finally {
            recognizer.close()
            bitmap.recycle()
        }
    }

    @Test
    fun ocrPipelineAutoModeAcceptsLatinText() = runBlocking {
        val bitmap = createTextBitmap("The quick brown fox jumps over the lazy dog.", fontSize = 50f)
        
        val result = ocrPipeline.runWithFallback(
            languageCode = "auto",
            usage = OcrUsage.EXTRACT_TEXT,
            emptyValue = { "" },
            isSuccess = { text, stats ->
                text.isNotBlank() && (stats?.confidence ?: 0f) > 0.5f
            }
        ) { recognizer, script ->
            val (text, stats) = textRecognizerRunner.recognizeWithStats(
                recognizer, 
                InputImage.fromBitmap(bitmap, 0)
            )
            text.text to stats
        }

        assertEquals(OcrScript.LATIN, result.script)
        assertTrue(result.value.contains("quick brown fox"))
        assertNotNull(result.stats)
        assertTrue(result.stats!!.confidence > 0.8f)
        
        bitmap.recycle()
    }

    private fun createTextBitmap(text: String, fontSize: Float): Bitmap {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = fontSize
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }
        val width = paint.measureText(text).toInt() + 100
        val height = fontSize.toInt() + 100
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        canvas.drawText(text, 50f, height / 2f + fontSize / 3f, paint)
        return bitmap
    }
}
