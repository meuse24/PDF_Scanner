package info.meuse24.pdf_scanner.ui.annotate

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import info.meuse24.pdf_scanner.R
import info.meuse24.pdf_scanner.domain.usecase.AnnotationOval
import info.meuse24.pdf_scanner.domain.usecase.AnnotationRect
import info.meuse24.pdf_scanner.domain.usecase.AnnotationShapeStyle
import info.meuse24.pdf_scanner.domain.usecase.AnnotationStroke
import info.meuse24.pdf_scanner.domain.usecase.AnnotationText
import info.meuse24.pdf_scanner.ui.documentaction.DocumentEditViewModel
import info.meuse24.pdf_scanner.ui.shared.clampPanOffset
import info.meuse24.pdf_scanner.ui.shared.formatZoomScale
import info.meuse24.pdf_scanner.ui.shared.snapStrokeToTextLines
import kotlin.math.abs

@Composable
fun AnnotateScreen(
    onNavigateBack: () -> Unit,
    viewModel: DocumentEditViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val pagePreviewUiState by viewModel.pagePreviewUiState.collectAsStateWithLifecycle()
    val record = uiState.record
    val editLoading = uiState.editLoading
    val error = uiState.error
    val success = uiState.success
    val pageBitmap = pagePreviewUiState.pageBitmap
    val textLines = pagePreviewUiState.textLines

    LaunchedEffect(success) {
        if (success) onNavigateBack()
    }

    var allStrokes by rememberSaveable(stateSaver = annotateStrokeListSaver) { mutableStateOf(emptyList<AnnotationStroke>()) }
    var allRects by rememberSaveable(stateSaver = annotateRectListSaver) { mutableStateOf(emptyList<AnnotationRect>()) }
    var allOvals by rememberSaveable(stateSaver = annotateOvalListSaver) { mutableStateOf(emptyList<AnnotationOval>()) }
    var allComments by rememberSaveable(stateSaver = annotateCommentListSaver) { mutableStateOf(emptyList<AnnotationTextDraft>()) }
    var annotationHistory by rememberSaveable(stateSaver = annotateHistorySaver) { mutableStateOf(emptyList<AnnotationHistoryEntry>()) }
    var currentStroke by rememberSaveable(stateSaver = annotatePointListSaver) { mutableStateOf(emptyList<Pair<Float, Float>>()) }

    var selectedPageIndex by rememberSaveable { mutableStateOf(0) }
    var selectedWidthFraction by rememberSaveable { mutableFloatStateOf(0.025f) }
    var selectedMarkerColor by rememberSaveable { mutableIntStateOf(defaultAnnotationColor()) }
    var selectedOpaqueColor by rememberSaveable { mutableIntStateOf(defaultTextAnnotationColor()) }
    var selectedColor by rememberSaveable { mutableIntStateOf(defaultAnnotationColor()) }
    var selectedTool by rememberSaveable { mutableStateOf(AnnotateTool.MARK) }
    var selectedDrawTool by rememberSaveable { mutableStateOf(AnnotateTool.MARK) }
    var selectedAnnotation by rememberSaveable(stateSaver = annotationSelectionSaver) { mutableStateOf<AnnotationSelection?>(null) }
    var isSnapMode by rememberSaveable { mutableStateOf(false) }
    var showInstructionsDialog by rememberSaveable { mutableStateOf(true) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    var zoomScale by rememberSaveable { mutableFloatStateOf(1f) }
    var zoomOffsetX by rememberSaveable { mutableFloatStateOf(0f) }
    var zoomOffsetY by rememberSaveable { mutableFloatStateOf(0f) }
    var activePointerCount by remember { mutableIntStateOf(0) }

    var currentShapeDraft by remember { mutableStateOf<AnnotationShapeDraft?>(null) }
    var showCommentDialog by remember { mutableStateOf(false) }
    var pendingCommentAnchor by remember { mutableStateOf<Pair<Float, Float>?>(null) }
    var editingCommentIndex by remember { mutableStateOf(-1) }
    var commentDialogText by remember { mutableStateOf("") }

    val elementStateSnapshot = AnnotationElementState(
        strokes = allStrokes,
        rects = allRects,
        ovals = allOvals,
        comments = allComments
    )

    val syncSelection: (AnnotationSelection?, AnnotationElementState) -> AnnotationSelection? = { selection, state ->
        selection?.takeIf { selectionExists(it, state) }
    }
    val applySelectionState: (AnnotationSelection?, AnnotationElementState) -> Unit = { selection, state ->
        val nextSelection = syncSelection(selection, state)
        selectedAnnotation = nextSelection
        nextSelection?.let {
            selectionColor(it, state)?.let { color -> selectedColor = color }
            selectionWidthFraction(it, state)?.let { width -> selectedWidthFraction = width }
        }
    }
    val setElementState: (AnnotationElementState, AnnotationSelection?) -> Unit = { state, selection ->
        allStrokes = state.strokes
        allRects = state.rects
        allOvals = state.ovals
        allComments = state.comments
        applySelectionState(selection, state)
    }
    val clearPageHistory = {
        annotationHistory = annotationHistory.filter { it.pageIndex != selectedPageIndex }
    }

    val currentCanvasSize by rememberUpdatedState(canvasSize)
    val currentZoomScale by rememberUpdatedState(zoomScale)
    val currentZoomOffsetX by rememberUpdatedState(zoomOffsetX)
    val currentZoomOffsetY by rememberUpdatedState(zoomOffsetY)
    val currentSelectedPageIndex by rememberUpdatedState(selectedPageIndex)
    val currentSelectedWidthFraction by rememberUpdatedState(selectedWidthFraction)
    val currentSelectedColor by rememberUpdatedState(selectedColor)
    val currentSelectedAnnotation by rememberUpdatedState(selectedAnnotation)
    val currentTextLines by rememberUpdatedState(textLines)
    val currentElementState by rememberUpdatedState(elementStateSnapshot)
    // pointerInput keeps long-lived gesture coroutines; this indirection forces selection changes
    // to resolve against the latest editor snapshot instead of the snapshot from the last tool rebuild.
    val currentSelectionSync by rememberUpdatedState<(AnnotationSelection?) -> Unit> { selection ->
        applySelectionState(selection, currentElementState)
    }

    val resetZoom = {
        zoomScale = 1f
        zoomOffsetX = 0f
        zoomOffsetY = 0f
    }
    val clearCurrentPage = {
        allStrokes = allStrokes.filter { it.pageIndex != selectedPageIndex }
        allRects = allRects.filter { it.pageIndex != selectedPageIndex }
        allOvals = allOvals.filter { it.pageIndex != selectedPageIndex }
        allComments = allComments.filter { it.pageIndex != selectedPageIndex }
        annotationHistory = annotationHistory.filter { it.pageIndex != selectedPageIndex }
        selectedAnnotation = null
        currentStroke = emptyList()
        currentShapeDraft = null
    }
    val resetAllAnnotations = {
        allStrokes = emptyList()
        allRects = emptyList()
        allOvals = emptyList()
        allComments = emptyList()
        annotationHistory = emptyList()
        selectedAnnotation = null
        currentStroke = emptyList()
        currentShapeDraft = null
    }
    val undoLastAnnotation = {
        selectedAnnotation = null
        currentStroke = emptyList()
        currentShapeDraft = null
        val lastEntryIndex = annotationHistory.indexOfLast { it.pageIndex == selectedPageIndex }
        if (lastEntryIndex >= 0) {
            when (annotationHistory[lastEntryIndex].kind) {
                AnnotationHistoryKind.STROKE -> allStrokes = removeLastStrokeForPage(allStrokes, selectedPageIndex)
                AnnotationHistoryKind.RECT -> allRects = removeLastRectForPage(allRects, selectedPageIndex)
                AnnotationHistoryKind.OVAL -> allOvals = removeLastOvalForPage(allOvals, selectedPageIndex)
                AnnotationHistoryKind.COMMENT -> allComments = removeLastCommentForPage(allComments, selectedPageIndex)
            }
            annotationHistory = annotationHistory.toMutableList().apply { removeAt(lastEntryIndex) }
        }
    }

    val transformableState = rememberTransformableState { zoomChange, panChange, _ ->
        val newScale = (currentZoomScale * zoomChange).coerceIn(1f, 8f)
        zoomScale = newScale
        val clamped = clampPanOffset(
            canvasSize = currentCanvasSize,
            scale = newScale,
            offsetX = currentZoomOffsetX + panChange.x,
            offsetY = currentZoomOffsetY + panChange.y
        )
        zoomOffsetX = clamped.x
        zoomOffsetY = clamped.y
    }

    val currentRecord = record
    if (currentRecord == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    LaunchedEffect(currentRecord.pageCount) {
        if (selectedPageIndex >= currentRecord.pageCount) selectedPageIndex = 0
    }
    LaunchedEffect(selectedTool) {
        if (selectedTool != AnnotateTool.ZOOM) selectedDrawTool = selectedTool
        if (selectedAnnotation == null) {
            selectedColor = when {
                selectedTool == AnnotateTool.MARK -> selectedMarkerColor
                selectedTool.usesOpaqueAnnotationColor() -> selectedOpaqueColor
                else -> selectedColor
            }
        }
        currentStroke = emptyList()
        currentShapeDraft = null
    }
    LaunchedEffect(selectedAnnotation, allStrokes, allRects, allOvals, allComments) {
        selectedAnnotation?.let { selection ->
            selectionColor(selection, elementStateSnapshot)?.let { color -> selectedColor = color }
            selectionWidthFraction(selection, elementStateSnapshot)?.let { width -> selectedWidthFraction = width }
        }
    }
    LaunchedEffect(selectedPageIndex, currentRecord.id) {
        viewModel.loadDocumentPage(selectedPageIndex)
        resetZoom()
        selectedAnnotation = null
        currentStroke = emptyList()
        currentShapeDraft = null
    }

    val bitmap = pageBitmap
    val aspectRatio = bitmap?.let { it.width.toFloat() / it.height.toFloat() } ?: (210f / 297f)
    val pageStrokes = allStrokes.filter { it.pageIndex == selectedPageIndex }
    val pageRects = allRects.filter { it.pageIndex == selectedPageIndex }
    val pageOvals = allOvals.filter { it.pageIndex == selectedPageIndex }
    val pageComments = allComments.filter { it.pageIndex == selectedPageIndex }
    val hasPageAnnotations = pageStrokes.isNotEmpty() || pageRects.isNotEmpty() || pageOvals.isNotEmpty() ||
        pageComments.isNotEmpty() || currentStroke.isNotEmpty() || currentShapeDraft != null
    val hasAnyAnnotations = allStrokes.isNotEmpty() || allRects.isNotEmpty() || allOvals.isNotEmpty() ||
        allComments.isNotEmpty() || currentStroke.isNotEmpty() || currentShapeDraft != null
    val isZoomTool = selectedTool == AnnotateTool.ZOOM
    val selectedDrawSpec = selectedDrawTool.spec()
    val selectionLabelRes = selectionKindLabelRes(selectedAnnotation)
    val selectionFrame = selectionFrame(selectedAnnotation, elementStateSnapshot)
    val showOpaqueColorPreview = when (selectedAnnotation?.kind) {
        AnnotationHistoryKind.RECT,
        AnnotationHistoryKind.OVAL,
        AnnotationHistoryKind.COMMENT -> true
        AnnotationHistoryKind.STROKE -> false
        null -> selectedTool.usesOpaqueAnnotationColor()
    }
    val widthLabelRes = when (selectedAnnotation?.kind) {
        AnnotationHistoryKind.COMMENT -> R.string.annotate_text_size
        AnnotationHistoryKind.STROKE, AnnotationHistoryKind.RECT, AnnotationHistoryKind.OVAL -> R.string.highlight_stroke_width
        null -> if (selectedTool == AnnotateTool.TEXT) R.string.annotate_text_size else R.string.highlight_stroke_width
    }

    val (pdfOrigin, pdfSize) = remember(canvasSize, aspectRatio) {
        val cw = canvasSize.width.toFloat()
        val ch = canvasSize.height.toFloat()
        if (cw <= 0f || ch <= 0f) return@remember Offset.Zero to Size.Zero
        if (aspectRatio >= cw / ch) {
            val h = cw / aspectRatio
            Offset(0f, (ch - h) / 2f) to Size(cw, h)
        } else {
            val w = ch * aspectRatio
            Offset((cw - w) / 2f, 0f) to Size(w, ch)
        }
    }
    val currentPdfOrigin by rememberUpdatedState(pdfOrigin)
    val currentPdfSize by rememberUpdatedState(pdfSize)

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        AnnotateToolbar(
            isZoomTool = isZoomTool,
            selectedTool = selectedTool,
            selectedDrawSpec = selectedDrawSpec,
            isSearchable = currentRecord.isSearchable,
            isSnapMode = isSnapMode,
            selectedColor = selectedColor,
            showOpaqueColorPreview = showOpaqueColorPreview,
            selectedWidthFraction = selectedWidthFraction,
            widthLabelRes = widthLabelRes,
            zoomResetEnabled = zoomScale > 1f || abs(zoomOffsetX) > 0.5f || abs(zoomOffsetY) > 0.5f,
            onToolSelected = { selectedTool = it },
            onOpenZoom = { selectedTool = AnnotateTool.ZOOM },
            onExitZoom = { selectedTool = selectedDrawTool },
            onResetZoom = resetZoom,
            onSnapModeChanged = { isSnapMode = it },
            onColorSelected = {
                val resolvedColor = when {
                    selectedAnnotation?.kind == AnnotationHistoryKind.STROKE || selectedTool == AnnotateTool.MARK -> it.toMarkerColor()
                    else -> it.toOpaqueColor()
                }
                selectedColor = resolvedColor
                when (selectedAnnotation?.kind) {
                    AnnotationHistoryKind.STROKE -> selectedMarkerColor = resolvedColor
                    AnnotationHistoryKind.RECT,
                    AnnotationHistoryKind.OVAL,
                    AnnotationHistoryKind.COMMENT -> selectedOpaqueColor = resolvedColor
                    null -> {
                        if (selectedTool == AnnotateTool.MARK) {
                            selectedMarkerColor = resolvedColor
                        } else if (selectedTool.usesOpaqueAnnotationColor()) {
                            selectedOpaqueColor = resolvedColor
                        }
                    }
                }
                selectedAnnotation?.let { selection ->
                    setElementState(applySelectionColor(selection, resolvedColor, elementStateSnapshot), selection)
                    clearPageHistory()
                }
            },
            onWidthSelected = {
                selectedWidthFraction = it
                selectedAnnotation?.let { selection ->
                    setElementState(applySelectionWidth(selection, it, elementStateSnapshot), selection)
                    clearPageHistory()
                }
            }
        )

        Box(
            modifier = Modifier.fillMaxWidth().weight(1f).background(MaterialTheme.colorScheme.surfaceContainerLow)
                .onSizeChanged { canvasSize = it }
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val pressedPointers = awaitPointerEvent(PointerEventPass.Initial).changes.count { it.pressed }
                            if (activePointerCount != pressedPointers) activePointerCount = pressedPointers
                        }
                    }
                }
                .transformable(
                    state = transformableState,
                    canPan = { activePointerCount > 1 },
                    enabled = true
                )
                .pointerInput(selectedTool, isSnapMode) {
                    when (selectedTool) {
                        AnnotateTool.MARK -> detectMarkGestures(
                            currentCanvasSize = { currentCanvasSize },
                            currentZoomScale = { currentZoomScale },
                            currentZoomOffsetX = { currentZoomOffsetX },
                            currentZoomOffsetY = { currentZoomOffsetY },
                            currentPdfOrigin = { currentPdfOrigin },
                            currentPdfSize = { currentPdfSize },
                            selectedPageIndex = currentSelectedPageIndex,
                            currentState = { currentElementState },
                            onStateChanged = { setElementState(it, currentSelectedAnnotation) },
                            onSelectionChanged = { currentSelectionSync(it) },
                            currentStrokePoints = { currentStroke },
                            onStrokeChanged = { currentStroke = it },
                            onCommit = { points ->
                                val snappedRects = if (isSnapMode) {
                                    snapStrokeToTextLines(points, currentTextLines.filter { it.pageIndex == currentSelectedPageIndex }).map {
                                        AnnotationRect(it.left, it.top, it.right, it.bottom, it.pageIndex, currentSelectedColor.toMarkerColor(), AnnotationShapeStyle.FILLED, currentSelectedWidthFraction)
                                    }
                                } else emptyList()
                                if (snappedRects.isNotEmpty()) {
                                    allRects = allRects + snappedRects
                                    selectedAnnotation = null
                                    annotationHistory = annotationHistory + snappedRects.map { AnnotationHistoryEntry(it.pageIndex, AnnotationHistoryKind.RECT) }
                                } else {
                                    allStrokes = allStrokes + AnnotationStroke(points, currentSelectedPageIndex, currentSelectedWidthFraction, currentSelectedColor.toMarkerColor())
                                    selectedAnnotation = null
                                    annotationHistory = annotationHistory + AnnotationHistoryEntry(currentSelectedPageIndex, AnnotationHistoryKind.STROKE)
                                }
                            }
                        )
                        AnnotateTool.TEXT -> detectTextGestures(
                            currentCanvasSize = { currentCanvasSize },
                            currentZoomScale = { currentZoomScale },
                            currentZoomOffsetX = { currentZoomOffsetX },
                            currentZoomOffsetY = { currentZoomOffsetY },
                            currentPdfOrigin = { currentPdfOrigin },
                            currentPdfSize = { currentPdfSize },
                            currentSelectedPageIndex, currentState = { currentElementState },
                            onStateChanged = { setElementState(it, currentSelectedAnnotation) },
                            onSelectionChanged = { currentSelectionSync(it) },
                            onAddRequested = { anchor -> editingCommentIndex = -1; pendingCommentAnchor = anchor; commentDialogText = ""; showCommentDialog = true },
                        )
                        AnnotateTool.RECT_FILLED, AnnotateTool.RECT_FRAME, AnnotateTool.OVAL_FILLED, AnnotateTool.OVAL_FRAME ->
                            detectShapeGestures(
                                currentCanvasSize = { currentCanvasSize },
                                currentZoomScale = { currentZoomScale },
                                currentZoomOffsetX = { currentZoomOffsetX },
                                currentZoomOffsetY = { currentZoomOffsetY },
                                currentPdfOrigin = { currentPdfOrigin },
                                currentPdfSize = { currentPdfSize },
                                selectedTool, currentSelectedPageIndex,
                                currentState = { currentElementState },
                                onStateChanged = { setElementState(it, currentSelectedAnnotation) },
                                onSelectionChanged = { currentSelectionSync(it) },
                                currentDraft = { currentShapeDraft },
                                onDraftChanged = { currentShapeDraft = it },
                                onCommit = { draft ->
                                    when (val shape = draft.toPreviewShape(currentSelectedColor.toOpaqueColor(), currentSelectedWidthFraction)) {
                                        is AnnotationRect -> {
                                            allRects = allRects + shape
                                            selectedAnnotation = null
                                            annotationHistory = annotationHistory + AnnotationHistoryEntry(shape.pageIndex, AnnotationHistoryKind.RECT)
                                        }
                                        is AnnotationOval -> {
                                            allOvals = allOvals + shape
                                            selectedAnnotation = null
                                            annotationHistory = annotationHistory + AnnotationHistoryEntry(shape.pageIndex, AnnotationHistoryKind.OVAL)
                                        }
                                    }
                                },
                                onTapCommit = { tool, center ->
                                    when (tool) {
                                        AnnotateTool.RECT_FILLED, AnnotateTool.RECT_FRAME -> {
                                            val rect = createTapRect(
                                                pageIndex = currentSelectedPageIndex,
                                                center = center,
                                                widthFraction = currentSelectedWidthFraction,
                                                color = currentSelectedColor.toOpaqueColor(),
                                                filled = tool == AnnotateTool.RECT_FILLED
                                            )
                                            allRects = allRects + rect
                                            selectedAnnotation = null
                                            annotationHistory = annotationHistory + AnnotationHistoryEntry(currentSelectedPageIndex, AnnotationHistoryKind.RECT)
                                        }
                                        AnnotateTool.OVAL_FILLED, AnnotateTool.OVAL_FRAME -> {
                                            val oval = createTapOval(
                                                pageIndex = currentSelectedPageIndex,
                                                center = center,
                                                widthFraction = currentSelectedWidthFraction,
                                                color = currentSelectedColor.toOpaqueColor(),
                                                filled = tool == AnnotateTool.OVAL_FILLED
                                            )
                                            allOvals = allOvals + oval
                                            selectedAnnotation = null
                                            annotationHistory = annotationHistory + AnnotationHistoryEntry(currentSelectedPageIndex, AnnotationHistoryKind.OVAL)
                                        }
                                        else -> Unit
                                    }
                                }
                            )
                        AnnotateTool.ZOOM -> Unit
                    }
                }
        ) {
            AnnotateCanvasContent(
                bitmap = bitmap,
                selectedPageIndex = selectedPageIndex,
                zoomScale = zoomScale,
                zoomOffsetX = zoomOffsetX,
                zoomOffsetY = zoomOffsetY,
                pdfOrigin = pdfOrigin,
                pdfSize = pdfSize,
                pageRects = pageRects,
                pageOvals = pageOvals,
                pageStrokes = pageStrokes,
                pageComments = pageComments,
                currentShapeDraft = currentShapeDraft,
                selectedColor = selectedColor,
                selectedWidthFraction = selectedWidthFraction,
                currentStroke = currentStroke,
                selectionFrame = selectionFrame
            )
        }

        AnnotateFooter(
            selectedPageIndex = selectedPageIndex,
            pageCount = currentRecord.pageCount,
            onPageSelected = { selectedPageIndex = it },
            selectedTool = selectedTool,
            zoomScale = zoomScale,
            isZoomTool = isZoomTool,
            hasPageAnnotations = hasPageAnnotations,
            hasAnyAnnotations = hasAnyAnnotations,
            selectionLabelRes = selectionLabelRes,
            showEditText = selectedAnnotation?.kind == AnnotationHistoryKind.COMMENT,
            onUndo = undoLastAnnotation,
            onClearPage = clearCurrentPage,
            onResetAll = resetAllAnnotations,
            onEditText = {
                val selection = selectedAnnotation
                val text = selection?.let { selectionText(it, elementStateSnapshot) }
                if (selection != null && text != null) {
                    editingCommentIndex = selection.index
                    commentDialogText = text
                    showCommentDialog = true
                }
            },
            onDelete = {
                selectedAnnotation?.let { selection ->
                    setElementState(deleteSelection(selection, elementStateSnapshot), null)
                    clearPageHistory()
                }
            },
            onClearSelection = { selectedAnnotation = null }
        )

        Button(
            onClick = {
                val finalStrokes = allStrokes + if (currentStroke.isNotEmpty()) listOf(AnnotationStroke(currentStroke, selectedPageIndex, selectedWidthFraction, selectedColor.toMarkerColor())) else emptyList()
                val finalShape = currentShapeDraft?.takeIf { it.pageIndex == selectedPageIndex }
                    ?.toPreviewShape(selectedColor.toOpaqueColor(), selectedWidthFraction)
                val finalRects = when (finalShape) {
                    is AnnotationRect -> allRects + finalShape
                    else -> allRects
                }
                val finalOvals = when (finalShape) {
                    is AnnotationOval -> allOvals + finalShape
                    else -> allOvals
                }
                val finalComments = allComments.map { AnnotationText(it.pageIndex, it.anchorX, it.anchorY, it.text, it.fontSizeFraction, it.color) }
                viewModel.applyAnnotations(finalStrokes, finalRects, finalOvals, finalComments)
            },
            enabled = hasAnyAnnotations && !editLoading,
            modifier = Modifier.fillMaxWidth()
        ) { Text(stringResource(R.string.annotate_apply)) }
    }

    AnnotateDialogs(
        showInstructionsDialog = showInstructionsDialog,
        onDismissInstructions = { showInstructionsDialog = false },
        showCommentDialog = showCommentDialog,
        isEditing = editingCommentIndex >= 0,
        commentDialogText = commentDialogText,
        onCommentTextChanged = { if (it.length <= 160) commentDialogText = it },
        onDismissComment = { showCommentDialog = false; commentDialogText = ""; pendingCommentAnchor = null; editingCommentIndex = -1 },
        onConfirmComment = {
            val text = commentDialogText.trim()
            if (text.isNotEmpty()) {
                if (editingCommentIndex >= 0) {
                    val selection = AnnotationSelection(AnnotationHistoryKind.COMMENT, editingCommentIndex)
                    setElementState(applySelectionText(selection, text, elementStateSnapshot), selection)
                    clearPageHistory()
                } else {
                    pendingCommentAnchor?.let { anchor ->
                        allComments = allComments + AnnotationTextDraft(
                            selectedPageIndex,
                            anchor.first,
                            anchor.second,
                            text,
                            commentFontSizeFromWidth(selectedWidthFraction),
                            selectedColor.toOpaqueColor()
                        )
                        selectedAnnotation = null
                        annotationHistory = annotationHistory + AnnotationHistoryEntry(selectedPageIndex, AnnotationHistoryKind.COMMENT)
                    }
                }
            }
            showCommentDialog = false; commentDialogText = ""; pendingCommentAnchor = null; editingCommentIndex = -1
        },
        onDeleteComment = {
            if (editingCommentIndex >= 0) {
                setElementState(deleteSelection(AnnotationSelection(AnnotationHistoryKind.COMMENT, editingCommentIndex), elementStateSnapshot), null)
                clearPageHistory()
            }
            showCommentDialog = false; commentDialogText = ""; pendingCommentAnchor = null; editingCommentIndex = -1
        },
        editLoading = editLoading,
        error = error,
        clearError = viewModel::clearError
    )
}

@Composable
private fun AnnotateToolbar(
    isZoomTool: Boolean,
    selectedTool: AnnotateTool,
    selectedDrawSpec: AnnotateToolSpec,
    isSearchable: Boolean,
    isSnapMode: Boolean,
    selectedColor: Int,
    showOpaqueColorPreview: Boolean,
    selectedWidthFraction: Float,
    widthLabelRes: Int,
    zoomResetEnabled: Boolean,
    onToolSelected: (AnnotateTool) -> Unit,
    onOpenZoom: () -> Unit,
    onExitZoom: () -> Unit,
    onResetZoom: () -> Unit,
    onSnapModeChanged: (Boolean) -> Unit,
    onColorSelected: (Int) -> Unit,
    onWidthSelected: (Float) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().zIndex(1f),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp
    ) {
        if (isZoomTool) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = stringResource(R.string.annotate_mode_zoom_title),
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = stringResource(R.string.annotate_mode_zoom_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                AnnotationToolbarIconButton(
                    icon = selectedDrawSpec.icon,
                    contentDescriptionRes = R.string.annotate_mode_edit_back,
                    onClick = onExitZoom
                )
                AnnotationToolbarIconButton(
                    icon = Icons.Default.Refresh,
                    contentDescriptionRes = R.string.highlight_zoom_reset_button,
                    enabled = zoomResetEnabled,
                    onClick = onResetZoom
                )
            }
        } else {
            Column(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AnnotationToolDropdown(
                        selectedTool = selectedTool,
                        onToolSelected = onToolSelected,
                        modifier = Modifier.weight(1f)
                    )
                    AnnotationToolbarIconButton(
                        icon = Icons.Default.ZoomIn,
                        contentDescriptionRes = R.string.annotate_tool_zoom,
                        onClick = onOpenZoom
                    )
                    if (isSearchable && selectedTool == AnnotateTool.MARK) {
                        FilterChip(
                            selected = isSnapMode,
                            onClick = { onSnapModeChanged(!isSnapMode) },
                            label = { Text(stringResource(R.string.highlight_mode_snap)) },
                            leadingIcon = { Icon(Icons.Default.TextFields, null, modifier = Modifier.size(16.dp)) }
                        )
                    }
                }
                AnnotationAttributeBar(
                    selectedColor = selectedColor,
                    showOpaqueColorPreview = showOpaqueColorPreview,
                    selectedWidthFraction = selectedWidthFraction,
                    widthLabelRes = widthLabelRes,
                    onColorSelected = onColorSelected,
                    onWidthSelected = onWidthSelected
                )
            }
        }
    }
}

@Composable
private fun AnnotateCanvasContent(
    bitmap: Bitmap?,
    selectedPageIndex: Int,
    zoomScale: Float,
    zoomOffsetX: Float,
    zoomOffsetY: Float,
    pdfOrigin: Offset,
    pdfSize: Size,
    pageRects: List<AnnotationRect>,
    pageOvals: List<AnnotationOval>,
    pageStrokes: List<AnnotationStroke>,
    pageComments: List<AnnotationTextDraft>,
    currentShapeDraft: AnnotationShapeDraft?,
    selectedColor: Int,
    selectedWidthFraction: Float,
    currentStroke: List<Pair<Float, Float>>,
    selectionFrame: AnnotationSelectionFrame?
) {
    Box(
        modifier = Modifier.fillMaxSize().graphicsLayer {
            scaleX = zoomScale
            scaleY = zoomScale
            translationX = zoomOffsetX
            translationY = zoomOffsetY
            clip = true
        }
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = stringResource(R.string.annotate_page_content_description, selectedPageIndex + 1),
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        Canvas(modifier = Modifier.fillMaxSize()) {
            val pdfX = pdfOrigin.x
            val pdfY = pdfOrigin.y
            val pdfWidth = pdfSize.width.coerceAtLeast(1f)
            val pdfHeight = pdfSize.height.coerceAtLeast(1f)
            pageRects.forEach { drawAnnotationRectPreview(it, pdfX, pdfY, pdfWidth, pdfHeight) }
            pageOvals.forEach { drawAnnotationOvalPreview(it, pdfX, pdfY, pdfWidth, pdfHeight) }
            pageStrokes.forEach { drawAnnotationStrokePreview(it, pdfX, pdfY, pdfWidth, pdfHeight) }
            pageComments.forEach { drawAnnotationTextPreview(it, pdfX, pdfY, pdfWidth, pdfHeight) }
            currentShapeDraft
                ?.takeIf { it.pageIndex == selectedPageIndex }
                ?.toPreviewShape(selectedColor, selectedWidthFraction)
                ?.let {
                    when (it) {
                        is AnnotationRect -> drawAnnotationRectPreview(it, pdfX, pdfY, pdfWidth, pdfHeight)
                        is AnnotationOval -> drawAnnotationOvalPreview(it, pdfX, pdfY, pdfWidth, pdfHeight)
                    }
                }
            if (currentStroke.isNotEmpty()) {
                drawAnnotationStrokePreview(
                    AnnotationStroke(currentStroke, selectedPageIndex, selectedWidthFraction, selectedColor),
                    pdfX,
                    pdfY,
                    pdfWidth,
                    pdfHeight
                )
            }
            selectionFrame?.let { drawSelectionFramePreview(it, pdfX, pdfY, pdfWidth, pdfHeight) }
        }
    }
}

@Composable
private fun AnnotateFooter(
    selectedPageIndex: Int,
    pageCount: Int,
    onPageSelected: (Int) -> Unit,
    selectedTool: AnnotateTool,
    zoomScale: Float,
    isZoomTool: Boolean,
    hasPageAnnotations: Boolean,
    hasAnyAnnotations: Boolean,
    selectionLabelRes: Int?,
    showEditText: Boolean,
    onUndo: () -> Unit,
    onClearPage: () -> Unit,
    onResetAll: () -> Unit,
    onEditText: () -> Unit,
    onDelete: () -> Unit,
    onClearSelection: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (pageCount > 1) {
                PageNavigationBar(selectedPageIndex, pageCount, onPageSelected)
            }
            AnnotateModeHint(selectedTool = selectedTool, zoomScale = zoomScale)
            if (!isZoomTool) {
                AnnotationActionIconBar(
                    hasPageAnnotations = hasPageAnnotations,
                    hasAnyAnnotations = hasAnyAnnotations,
                    selectionLabelRes = selectionLabelRes,
                    showEditText = showEditText,
                    onUndo = onUndo,
                    onClearPage = onClearPage,
                    onResetAll = onResetAll,
                    onEditText = onEditText,
                    onDelete = onDelete,
                    onClearSelection = onClearSelection
                )
            }
        }
    }
}

@Composable
private fun AnnotateModeHint(
    selectedTool: AnnotateTool,
    zoomScale: Float
) {
    val hintText = when (selectedTool) {
        AnnotateTool.ZOOM -> "${formatZoomScale(zoomScale)}×"
        AnnotateTool.TEXT -> stringResource(R.string.annotate_write_hint)
        else -> stringResource(R.string.annotate_selected_hint)
    }
    Text(
        text = hintText,
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun PageNavigationBar(
    selectedPageIndex: Int,
    pageCount: Int,
    onPageSelected: (Int) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedButton(
            onClick = { onPageSelected((selectedPageIndex - 1).coerceAtLeast(0)) },
            enabled = selectedPageIndex > 0,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
        ) { Text(stringResource(R.string.annotate_page_previous_short), maxLines = 1) }
        Text(
            text = stringResource(R.string.annotate_page_indicator, selectedPageIndex + 1, pageCount),
            modifier = Modifier.weight(0.8f),
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedButton(
            onClick = { onPageSelected((selectedPageIndex + 1).coerceAtMost(pageCount - 1)) },
            enabled = selectedPageIndex < pageCount - 1,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
        ) { Text(stringResource(R.string.annotate_page_next_short), maxLines = 1) }
    }
}

@Composable
private fun AnnotateDialogs(
    showInstructionsDialog: Boolean,
    onDismissInstructions: () -> Unit,
    showCommentDialog: Boolean,
    isEditing: Boolean,
    commentDialogText: String,
    onCommentTextChanged: (String) -> Unit,
    onDismissComment: () -> Unit,
    onConfirmComment: () -> Unit,
    onDeleteComment: () -> Unit,
    editLoading: Boolean,
    error: String?,
    clearError: () -> Unit
) {
    if (showInstructionsDialog) {
        AlertDialog(
            onDismissRequest = onDismissInstructions,
            title = { Text(stringResource(R.string.annotate_description)) },
            text = { Text(stringResource(R.string.annotate_details)) },
            confirmButton = {
                TextButton(onClick = onDismissInstructions) {
                    Text(stringResource(R.string.action_ok))
                }
            }
        )
    }

    if (showCommentDialog) {
        AlertDialog(
            onDismissRequest = onDismissComment,
            title = { Text(stringResource(if (isEditing) R.string.annotate_comment_edit_title else R.string.annotate_comment_title)) },
            text = {
                OutlinedTextField(
                    value = commentDialogText,
                    onValueChange = onCommentTextChanged,
                    label = { Text(stringResource(R.string.annotate_comment_hint)) },
                    minLines = 2,
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = onConfirmComment) {
                    Text(stringResource(R.string.annotate_comment_add))
                }
            },
            dismissButton = {
                Row {
                    if (isEditing) {
                        TextButton(onClick = onDeleteComment) {
                            Text(stringResource(R.string.annotate_comment_delete))
                        }
                    }
                    TextButton(onClick = onDismissComment) {
                        Text(stringResource(R.string.action_cancel))
                    }
                }
            }
        )
    }

    if (editLoading) {
        AlertDialog(
            onDismissRequest = {},
            title = null,
            text = {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            },
            confirmButton = {}
        )
    }

    error?.let { message ->
        AlertDialog(
            onDismissRequest = clearError,
            title = { Text(stringResource(R.string.error_title)) },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = clearError) {
                    Text(stringResource(R.string.action_ok))
                }
            }
        )
    }
}
