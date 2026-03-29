package info.meuse24.pdf_scanner.ui.annotate

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.unit.IntSize
import info.meuse24.pdf_scanner.ui.shared.normalizeViewportPoint

internal suspend fun PointerInputScope.detectMarkGestures(
    canvasSize: IntSize,
    zoomScale: Float,
    zoomOffsetX: Float,
    zoomOffsetY: Float,
    pdfOrigin: Offset,
    pdfSize: Size,
    selectedPageIndex: Int,
    currentState: () -> AnnotationElementState,
    onStateChanged: (AnnotationElementState) -> Unit,
    onSelectionChanged: (AnnotationSelection?) -> Unit,
    currentStrokePoints: () -> List<Pair<Float, Float>>,
    onStrokeChanged: (List<Pair<Float, Float>>) -> Unit,
    onCommit: (List<Pair<Float, Float>>) -> Unit
) {
    awaitEachGesture {
        var startChange: PointerInputChange? = null
        while (startChange == null) {
            val event = awaitPointerEvent()
            startChange = event.changes.firstOrNull { !it.previousPressed && it.pressed }
        }
        val pointerId = startChange.id
        val startNorm = normalizeViewportPoint(startChange.position, canvasSize, zoomScale, zoomOffsetX, zoomOffsetY, pdfOrigin, pdfSize)
        val selection = hitTestSelection(selectedPageIndex, startNorm, currentState())
        if (selection != null) onSelectionChanged(selection)

        var previousNorm = startNorm
        var movedEnough = false
        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == pointerId } ?: break
            if (change.previousPressed && !change.pressed) break
            if (!change.pressed) continue

            val currentNorm = normalizeViewportPoint(change.position, canvasSize, zoomScale, zoomOffsetX, zoomOffsetY, pdfOrigin, pdfSize)
            val deltaX = currentNorm.first - previousNorm.first
            val deltaY = currentNorm.second - previousNorm.second
            val totalX = currentNorm.first - startNorm.first
            val totalY = currentNorm.second - startNorm.second
            if (totalX * totalX + totalY * totalY > 0.0009f) movedEnough = true

            if (movedEnough && selection != null) {
                change.consume()
                onStateChanged(moveSelectionBy(selection, deltaX, deltaY, currentState()))
            } else if (selection == null) {
                change.consume()
                val nextStroke = if (currentStrokePoints().isEmpty()) {
                    listOf(startNorm, currentNorm)
                } else {
                    currentStrokePoints() + currentNorm
                }
                onStrokeChanged(nextStroke)
            }
            previousNorm = currentNorm
        }

        if (selection == null) {
            onSelectionChanged(null)
            if (movedEnough) {
                val points = currentStrokePoints()
                if (points.isNotEmpty()) onCommit(points)
                onStrokeChanged(emptyList())
            } else {
                onCommit(listOf(startNorm))
                onStrokeChanged(emptyList())
            }
        }
    }
}

internal suspend fun PointerInputScope.detectTextGestures(
    canvasSize: IntSize,
    zoomScale: Float,
    zoomOffsetX: Float,
    zoomOffsetY: Float,
    pdfOrigin: Offset,
    pdfSize: Size,
    selectedPageIndex: Int,
    currentState: () -> AnnotationElementState,
    onStateChanged: (AnnotationElementState) -> Unit,
    onSelectionChanged: (AnnotationSelection?) -> Unit,
    onAddRequested: (Pair<Float, Float>) -> Unit
) {
    awaitEachGesture {
        var startChange: PointerInputChange? = null
        while (startChange == null) {
            val event = awaitPointerEvent()
            startChange = event.changes.firstOrNull { !it.previousPressed && it.pressed }
        }
        val pointerId = startChange.id
        val startNorm = normalizeViewportPoint(startChange.position, canvasSize, zoomScale, zoomOffsetX, zoomOffsetY, pdfOrigin, pdfSize)
        val selection = hitTestSelection(selectedPageIndex, startNorm, currentState())
        if (selection != null) onSelectionChanged(selection)

        var previousNorm = startNorm
        var latestNorm = startNorm
        var movedEnough = false
        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == pointerId } ?: break
            if (change.previousPressed && !change.pressed) break
            if (!change.pressed) continue
            latestNorm = normalizeViewportPoint(change.position, canvasSize, zoomScale, zoomOffsetX, zoomOffsetY, pdfOrigin, pdfSize)
            val totalX = latestNorm.first - startNorm.first
            val totalY = latestNorm.second - startNorm.second
            if (totalX * totalX + totalY * totalY > 0.0009f) movedEnough = true
            if (movedEnough && selection != null) {
                change.consume()
                val frameDeltaX = latestNorm.first - previousNorm.first
                val frameDeltaY = latestNorm.second - previousNorm.second
                onStateChanged(moveSelectionBy(selection, frameDeltaX, frameDeltaY, currentState()))
            }
            previousNorm = latestNorm
        }

        if (!movedEnough) {
            if (selection != null) {
                onSelectionChanged(selection)
            } else {
                onSelectionChanged(null)
                onAddRequested(startNorm)
            }
        }
    }
}

internal suspend fun PointerInputScope.detectShapeGestures(
    canvasSize: IntSize,
    zoomScale: Float,
    zoomOffsetX: Float,
    zoomOffsetY: Float,
    pdfOrigin: Offset,
    pdfSize: Size,
    tool: AnnotateTool,
    selectedPageIndex: Int,
    currentState: () -> AnnotationElementState,
    onStateChanged: (AnnotationElementState) -> Unit,
    onSelectionChanged: (AnnotationSelection?) -> Unit,
    currentDraft: () -> AnnotationShapeDraft?,
    onDraftChanged: (AnnotationShapeDraft?) -> Unit,
    onCommit: (AnnotationShapeDraft) -> Unit,
    onTapCommit: (AnnotateTool, Pair<Float, Float>) -> Unit
) {
    awaitEachGesture {
        var startChange: PointerInputChange? = null
        while (startChange == null) {
            val event = awaitPointerEvent()
            startChange = event.changes.firstOrNull { !it.previousPressed && it.pressed }
        }
        val pointerId = startChange.id
        val startNorm = normalizeViewportPoint(startChange.position, canvasSize, zoomScale, zoomOffsetX, zoomOffsetY, pdfOrigin, pdfSize)
        val selection = hitTestSelection(selectedPageIndex, startNorm, currentState())
        if (selection != null) onSelectionChanged(selection)

        var previousNorm = startNorm
        var movedEnough = false
        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == pointerId } ?: break
            if (change.previousPressed && !change.pressed) break
            if (!change.pressed) continue

            val currentNorm = normalizeViewportPoint(change.position, canvasSize, zoomScale, zoomOffsetX, zoomOffsetY, pdfOrigin, pdfSize)
            val deltaX = currentNorm.first - previousNorm.first
            val deltaY = currentNorm.second - previousNorm.second
            val totalX = currentNorm.first - startNorm.first
            val totalY = currentNorm.second - startNorm.second
            if (totalX * totalX + totalY * totalY > 0.0009f) movedEnough = true

            if (movedEnough && selection != null) {
                change.consume()
                onStateChanged(moveSelectionBy(selection, deltaX, deltaY, currentState()))
            } else if (selection == null) {
                change.consume()
                val draft = currentDraft() ?: AnnotationShapeDraft(tool, selectedPageIndex, startNorm, startNorm)
                onDraftChanged(draft.copy(end = currentNorm))
            }
            previousNorm = currentNorm
        }

        if (selection == null) {
            onSelectionChanged(null)
            if (movedEnough) {
                currentDraft()?.let(onCommit)
                onDraftChanged(null)
            } else {
                onTapCommit(tool, startNorm)
                onDraftChanged(null)
            }
        }
    }
}
