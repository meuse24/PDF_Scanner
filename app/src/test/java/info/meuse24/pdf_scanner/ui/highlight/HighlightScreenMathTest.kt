package info.meuse24.pdf_scanner.ui.highlight

import androidx.compose.ui.unit.IntSize
import org.junit.Assert.assertEquals
import org.junit.Test

class HighlightScreenMathTest {

    @Test
    fun `clampPanOffset begrenzt pan auf sichtbaren bereich`() {
        val result = clampPanOffset(
            canvasSize = IntSize(1000, 800),
            scale = 3f,
            offsetX = 1500f,
            offsetY = -1200f
        )

        assertEquals(1000f, result.x, 0f)
        assertEquals(-800f, result.y, 0f)
    }

    @Test
    fun `clampPanOffset gibt bei scale 1 keine verschiebung frei`() {
        val result = clampPanOffset(
            canvasSize = IntSize(1000, 800),
            scale = 1f,
            offsetX = 250f,
            offsetY = -120f
        )

        assertEquals(0f, result.x, 0f)
        assertEquals(0f, result.y, 0f)
    }

    @Test
    fun `formatZoomScale verwendet festen punkt mit einer nachkommastelle`() {
        assertEquals("2.0", formatZoomScale(2f))
        assertEquals("1.3", formatZoomScale(1.26f))
    }
}

