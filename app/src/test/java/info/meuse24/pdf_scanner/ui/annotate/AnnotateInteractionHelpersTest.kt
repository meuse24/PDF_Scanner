package info.meuse24.pdf_scanner.ui.annotate

import info.meuse24.pdf_scanner.domain.usecase.AnnotationOval
import info.meuse24.pdf_scanner.domain.usecase.AnnotationRect
import info.meuse24.pdf_scanner.domain.usecase.AnnotationShapeStyle
import info.meuse24.pdf_scanner.domain.usecase.AnnotationStroke
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AnnotateInteractionHelpersTest {

    @Test
    fun `hitTestSelection priorisiert kommentar vor anderen elementen`() {
        val state = AnnotationElementState(
            strokes = listOf(
                AnnotationStroke(
                    points = listOf(0.2f to 0.2f, 0.4f to 0.2f),
                    pageIndex = 0
                )
            ),
            rects = listOf(
                AnnotationRect(
                    left = 0.1f,
                    top = 0.1f,
                    right = 0.5f,
                    bottom = 0.4f,
                    pageIndex = 0
                )
            ),
            ovals = emptyList(),
            comments = listOf(
                AnnotationTextDraft(
                    pageIndex = 0,
                    anchorX = 0.22f,
                    anchorY = 0.22f,
                    text = "note",
                    fontSizeFraction = 0.03f,
                    color = defaultAnnotationColor()
                )
            )
        )

        val selection = hitTestSelection(0, 0.22f to 0.22f, state)

        assertNotNull(selection)
        assertEquals(AnnotationHistoryKind.COMMENT, selection!!.kind)
    }

    @Test
    fun `moveSelectionBy verschiebt stroke und klemmt an seitenrand`() {
        val state = AnnotationElementState(
            strokes = listOf(
                AnnotationStroke(
                    points = listOf(0.9f to 0.9f, 0.95f to 0.95f),
                    pageIndex = 0
                )
            ),
            rects = emptyList(),
            ovals = emptyList(),
            comments = emptyList()
        )

        val moved = moveSelectionBy(
            selection = AnnotationSelection(AnnotationHistoryKind.STROKE, 0),
            deltaX = 0.2f,
            deltaY = 0.2f,
            state = state
        )

        assertEquals(1f, moved.strokes.single().points.last().first, 0.0001f)
        assertEquals(1f, moved.strokes.single().points.last().second, 0.0001f)
    }

    @Test
    fun `moveSelectionBy verschiebt rect konsistent`() {
        val state = AnnotationElementState(
            strokes = emptyList(),
            rects = listOf(
                AnnotationRect(
                    left = 0.2f,
                    top = 0.3f,
                    right = 0.4f,
                    bottom = 0.5f,
                    pageIndex = 0
                )
            ),
            ovals = emptyList(),
            comments = emptyList()
        )

        val moved = moveSelectionBy(
            selection = AnnotationSelection(AnnotationHistoryKind.RECT, 0),
            deltaX = 0.1f,
            deltaY = -0.1f,
            state = state
        )

        val rect = moved.rects.single()
        assertEquals(0.3f, rect.left, 0.0001f)
        assertEquals(0.2f, rect.top, 0.0001f)
        assertEquals(0.5f, rect.right, 0.0001f)
        assertEquals(0.4f, rect.bottom, 0.0001f)
    }

    @Test
    fun `createTapRect erzeugt gefuelltes rect um den tap punkt`() {
        val rect = createTapRect(
            pageIndex = 0,
            center = 0.5f to 0.5f,
            widthFraction = 0.025f,
            color = defaultAnnotationColor(),
            filled = true
        )

        assertTrue(rect.left < 0.5f)
        assertTrue(rect.right > 0.5f)
        assertEquals(AnnotationShapeStyle.FILLED, rect.style)
    }

    @Test
    fun `createTapOval erzeugt kontur oval um den tap punkt`() {
        val oval = createTapOval(
            pageIndex = 0,
            center = 0.5f to 0.5f,
            widthFraction = 0.025f,
            color = defaultAnnotationColor(),
            filled = false
        )

        assertTrue(oval.left < 0.5f)
        assertTrue(oval.right > 0.5f)
        assertEquals(AnnotationShapeStyle.FRAME, oval.style)
    }

    @Test
    fun `hitTestSelection erkennt oval`() {
        val state = AnnotationElementState(
            strokes = emptyList(),
            rects = emptyList(),
            ovals = listOf(
                AnnotationOval(
                    left = 0.3f,
                    top = 0.3f,
                    right = 0.6f,
                    bottom = 0.6f,
                    pageIndex = 0,
                    style = AnnotationShapeStyle.FRAME
                )
            ),
            comments = emptyList()
        )

        val selection = hitTestSelection(0, 0.45f to 0.45f, state)

        assertNotNull(selection)
        assertEquals(AnnotationHistoryKind.OVAL, selection!!.kind)
    }

    @Test
    fun `hitTestSelection erkennt kommentar ueber textflaeche`() {
        val state = AnnotationElementState(
            strokes = emptyList(),
            rects = emptyList(),
            ovals = emptyList(),
            comments = listOf(
                AnnotationTextDraft(
                    pageIndex = 0,
                    anchorX = 0.2f,
                    anchorY = 0.3f,
                    text = "Kommentar",
                    fontSizeFraction = 0.03f,
                    color = defaultAnnotationColor()
                )
            )
        )

        val selection = hitTestSelection(0, 0.31f to 0.29f, state)

        assertNotNull(selection)
        assertEquals(AnnotationHistoryKind.COMMENT, selection!!.kind)
    }

    @Test
    fun `applySelectionColor aktualisiert ausgewaehlten kommentar`() {
        val state = AnnotationElementState(
            strokes = emptyList(),
            rects = emptyList(),
            ovals = emptyList(),
            comments = listOf(
                AnnotationTextDraft(
                    pageIndex = 0,
                    anchorX = 0.2f,
                    anchorY = 0.3f,
                    text = "Kommentar",
                    fontSizeFraction = 0.03f,
                    color = defaultAnnotationColor()
                )
            )
        )

        val updated = applySelectionColor(
            selection = AnnotationSelection(AnnotationHistoryKind.COMMENT, 0),
            color = 0x660091EA,
            state = state
        )

        assertEquals(0xFF0091EA.toInt(), updated.comments.single().color)
    }

    @Test
    fun `applySelectionWidth aktualisiert kommentar ueber width mapping`() {
        val state = AnnotationElementState(
            strokes = emptyList(),
            rects = emptyList(),
            ovals = emptyList(),
            comments = listOf(
                AnnotationTextDraft(
                    pageIndex = 0,
                    anchorX = 0.2f,
                    anchorY = 0.3f,
                    text = "Kommentar",
                    fontSizeFraction = 0.028f,
                    color = defaultAnnotationColor()
                )
            )
        )

        val updated = applySelectionWidth(
            selection = AnnotationSelection(AnnotationHistoryKind.COMMENT, 0),
            widthFraction = 0.04f,
            state = state
        )

        assertEquals(commentFontSizeFromWidth(0.04f), updated.comments.single().fontSizeFraction, 0.0001f)
        assertEquals(
            0.04f,
            selectionWidthFraction(AnnotationSelection(AnnotationHistoryKind.COMMENT, 0), updated) ?: -1f,
            0.0001f
        )
    }

    @Test
    fun `deleteSelection entfernt ausgewaehltes rect`() {
        val state = AnnotationElementState(
            strokes = emptyList(),
            rects = listOf(
                AnnotationRect(left = 0.1f, top = 0.1f, right = 0.2f, bottom = 0.2f, pageIndex = 0),
                AnnotationRect(left = 0.3f, top = 0.3f, right = 0.4f, bottom = 0.4f, pageIndex = 0)
            ),
            ovals = emptyList(),
            comments = emptyList()
        )

        val updated = deleteSelection(AnnotationSelection(AnnotationHistoryKind.RECT, 0), state)

        assertEquals(1, updated.rects.size)
        assertEquals(0.3f, updated.rects.single().left, 0.0001f)
    }

    @Test
    fun `selectionFrame liefert henkel fuer stroke`() {
        val state = AnnotationElementState(
            strokes = listOf(
                AnnotationStroke(
                    points = listOf(0.2f to 0.2f, 0.4f to 0.4f),
                    pageIndex = 0
                )
            ),
            rects = emptyList(),
            ovals = emptyList(),
            comments = emptyList()
        )

        val frame = selectionFrame(AnnotationSelection(AnnotationHistoryKind.STROKE, 0), state)

        assertNotNull(frame)
        assertTrue(frame!!.handleX >= frame.right - 0.0001f)
        assertTrue(frame.handleY <= frame.top + 0.0001f)
    }
}
