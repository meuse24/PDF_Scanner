package info.meuse24.pdf_scanner.util

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OcrPageTextJsonInstrumentedTest {

    @Test
    fun pageTextJsonPreservesBlankPagePositions() {
        val pages = listOf("First page", "", "Third page")

        val encoded = pages.toOcrPageTextJson()

        assertNotNull(encoded)
        assertEquals(pages, encoded.fromOcrPageTextJson())
    }
}
