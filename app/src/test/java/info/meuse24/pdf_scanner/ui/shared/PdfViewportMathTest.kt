package info.meuse24.pdf_scanner.ui.shared

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import info.meuse24.pdf_scanner.domain.usecase.HighlightRect
import info.meuse24.pdf_scanner.domain.usecase.TextLine
import org.junit.Assert.assertEquals
import org.junit.Test

class PdfViewportMathTest {

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

    @Test
    fun `mapViewportOffsetToCanvasOffset rechnet zoom um die mitte zurueck`() {
        val result = mapViewportOffsetToCanvasOffset(
            offset = Offset(0f, 0f),
            canvasSize = IntSize(1000, 800),
            scale = 2f,
            offsetX = 0f,
            offsetY = 0f
        )

        assertEquals(250f, result.x, 0.001f)
        assertEquals(200f, result.y, 0.001f)
    }

    @Test
    fun `mapViewportOffsetToCanvasOffset beruecksichtigt pan offset`() {
        val result = mapViewportOffsetToCanvasOffset(
            offset = Offset(500f, 400f),
            canvasSize = IntSize(1000, 800),
            scale = 2f,
            offsetX = 120f,
            offsetY = -80f
        )

        assertEquals(440f, result.x, 0.001f)
        assertEquals(440f, result.y, 0.001f)
    }

    @Test
    fun `normalizeViewportPoint liefert dokumentkoordinaten trotz zoom`() {
        val result = normalizeViewportPoint(
            offset = Offset(0f, 0f),
            canvasSize = IntSize(1000, 800),
            scale = 2f,
            offsetX = 0f,
            offsetY = 0f
        )

        assertEquals(0.25f, result.first, 0.001f)
        assertEquals(0.25f, result.second, 0.001f)
    }

    @Test
    fun `snapStrokeToTextLines liefert leer bei fehlender y ueberlappung`() {
        val lines = listOf(
            TextLine(left = 0.1f, top = 0.1f, right = 0.7f, bottom = 0.2f, pageIndex = 0)
        )

        val result = snapStrokeToTextLines(
            stroke = listOf(0.2f to 0.4f, 0.4f to 0.45f),
            textLines = lines
        )

        assertEquals(emptyList<HighlightRect>(), result)
    }

    @Test
    fun `snapStrokeToTextLines liefert genau eine ueberlappende zeile`() {
        val line = TextLine(left = 0.1f, top = 0.1f, right = 0.7f, bottom = 0.2f, pageIndex = 0)

        val result = snapStrokeToTextLines(
            stroke = listOf(0.2f to 0.12f, 0.4f to 0.18f),
            textLines = listOf(line)
        )

        assertEquals(
            listOf(HighlightRect(left = 0.1f, top = 0.1f, right = 0.7f, bottom = 0.2f, pageIndex = 0)),
            result
        )
    }

    @Test
    fun `snapStrokeToTextLines liefert mehrere zeilen bei mehreren ueberlappungen`() {
        val result = snapStrokeToTextLines(
            stroke = listOf(0.2f to 0.12f, 0.4f to 0.32f),
            textLines = listOf(
                TextLine(left = 0.1f, top = 0.1f, right = 0.7f, bottom = 0.2f, pageIndex = 0),
                TextLine(left = 0.15f, top = 0.25f, right = 0.75f, bottom = 0.35f, pageIndex = 0)
            )
        )

        assertEquals(2, result.size)
        assertEquals(0.1f, result[0].top, 0.001f)
        assertEquals(0.25f, result[1].top, 0.001f)
    }

    @Test
    fun `snapStrokeToTextLines liefert leer fuer leeren strich oder leere zeilen`() {
        val line = TextLine(left = 0.1f, top = 0.1f, right = 0.7f, bottom = 0.2f, pageIndex = 0)

        assertEquals(emptyList<HighlightRect>(), snapStrokeToTextLines(emptyList(), listOf(line)))
        assertEquals(emptyList<HighlightRect>(), snapStrokeToTextLines(listOf(0.1f to 0.1f), emptyList()))
    }
}
