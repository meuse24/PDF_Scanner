package info.meuse24.pdf_scanner.ui.annotate

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Edit
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import info.meuse24.pdf_scanner.domain.usecase.HIGHLIGHT_ALPHA
import info.meuse24.pdf_scanner.domain.usecase.HIGHLIGHT_COLOR_BLUE
import info.meuse24.pdf_scanner.domain.usecase.HIGHLIGHT_COLOR_GREEN
import info.meuse24.pdf_scanner.domain.usecase.HIGHLIGHT_COLOR_RED
import info.meuse24.pdf_scanner.R
import info.meuse24.pdf_scanner.domain.usecase.HighlightRect
import info.meuse24.pdf_scanner.domain.usecase.HighlightStroke
import info.meuse24.pdf_scanner.domain.usecase.TextComment
import info.meuse24.pdf_scanner.ui.documentaction.DocumentEditViewModel
import info.meuse24.pdf_scanner.ui.highlight.clampPanOffset
import info.meuse24.pdf_scanner.ui.highlight.formatZoomScale
import info.meuse24.pdf_scanner.ui.highlight.normalizeViewportPoint
import info.meuse24.pdf_scanner.ui.highlight.removeLastRectForPage
import info.meuse24.pdf_scanner.ui.highlight.removeLastStrokeForPage
import info.meuse24.pdf_scanner.ui.highlight.snapStrokeToTextLines

private enum class AnnotateMode { MARK, WRITE, ZOOM }

private data class TextCommentDraft(
    val pageIndex: Int,
    val anchorX: Float,
    val anchorY: Float,
    val text: String,
    val fontSizeFraction: Float
)

private data class MarkerWidthOption(
    val fraction: Float,
    val labelRes: Int
)

private val markerWidthOptions = listOf(
    MarkerWidthOption(0.015f, R.string.highlight_stroke_thin),
    MarkerWidthOption(0.025f, R.string.highlight_stroke_medium),
    MarkerWidthOption(0.04f, R.string.highlight_stroke_thick)
)

private val annotatePointListSaver = listSaver<List<Pair<Float, Float>>, Float>(
    save = { points -> points.flatMap { listOf(it.first, it.second) } },
    restore = { values ->
        values.chunked(2).mapNotNull { pair ->
            if (pair.size == 2) pair[0] to pair[1] else null
        }
    }
)

private val annotateStrokeListSaver = listSaver<List<HighlightStroke>, List<Float>>(
    save = { strokes ->
        strokes.map { stroke ->
            listOf(
                stroke.pageIndex.toFloat(),
                stroke.strokeWidthFraction,
                stroke.points.size.toFloat()
            ) + stroke.points.flatMap { listOf(it.first, it.second) }
        }
    },
    restore = { values ->
        values.mapNotNull { savedStroke ->
            if (savedStroke.size < 3) return@mapNotNull null
            val pageIndex = savedStroke[0].toInt()
            val strokeWidthFraction = savedStroke[1]
            val pointCount = savedStroke[2].toInt().coerceAtLeast(0)
            val pointValues = savedStroke.drop(3)
            if (pointValues.size < pointCount * 2) return@mapNotNull null
            val points = pointValues
                .take(pointCount * 2)
                .chunked(2)
                .mapNotNull { pair ->
                    if (pair.size == 2) pair[0] to pair[1] else null
                }
            HighlightStroke(points = points, pageIndex = pageIndex, strokeWidthFraction = strokeWidthFraction)
        }
    }
)

private val annotateRectListSaver = listSaver<List<HighlightRect>, List<Float>>(
    save = { rects ->
        rects.map { rect ->
            listOf(rect.pageIndex.toFloat(), rect.left, rect.top, rect.right, rect.bottom)
        }
    },
    restore = { values ->
        values.mapNotNull { savedRect ->
            if (savedRect.size != 5) return@mapNotNull null
            HighlightRect(
                pageIndex = savedRect[0].toInt(),
                left = savedRect[1], top = savedRect[2],
                right = savedRect[3], bottom = savedRect[4]
            )
        }
    }
)

private val annotateCommentListSaver = listSaver<List<TextCommentDraft>, List<String>>(
    save = { comments ->
        comments.map { c ->
            listOf(c.pageIndex.toString(), c.anchorX.toString(), c.anchorY.toString(),
                   c.fontSizeFraction.toString(), c.text)
        }
    },
    restore = { values ->
        values.mapNotNull { saved ->
            if (saved.size < 5) return@mapNotNull null
            TextCommentDraft(
                pageIndex  = saved[0].toIntOrNull()   ?: return@mapNotNull null,
                anchorX    = saved[1].toFloatOrNull() ?: return@mapNotNull null,
                anchorY    = saved[2].toFloatOrNull() ?: return@mapNotNull null,
                fontSizeFraction = saved[3].toFloatOrNull() ?: return@mapNotNull null,
                text       = saved[4]
            )
        }
    }
)

private val highlightYellow = Color(
    red = HIGHLIGHT_COLOR_RED / 255f,
    green = HIGHLIGHT_COLOR_GREEN / 255f,
    blue = HIGHLIGHT_COLOR_BLUE / 255f,
    alpha = HIGHLIGHT_ALPHA
)

private val highlightRectYellow = Color(
    red = HIGHLIGHT_COLOR_RED / 255f,
    green = HIGHLIGHT_COLOR_GREEN / 255f,
    blue = HIGHLIGHT_COLOR_BLUE / 255f,
    alpha = 0.3f
)

@Composable
fun AnnotateScreen(
    onNavigateBack: () -> Unit,
    viewModel: DocumentEditViewModel = hiltViewModel()
) {
    val record by viewModel.record.collectAsState()
    val editLoading by viewModel.editLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val success by viewModel.success.collectAsState()
    val pageBitmap by viewModel.highlightPageBitmap.collectAsState()
    val textLines by viewModel.textLines.collectAsState()

    LaunchedEffect(success) {
        if (success) onNavigateBack()
    }

    var allStrokes by rememberSaveable(stateSaver = annotateStrokeListSaver) {
        mutableStateOf(emptyList<HighlightStroke>())
    }
    var allRects by rememberSaveable(stateSaver = annotateRectListSaver) {
        mutableStateOf(emptyList<HighlightRect>())
    }
    var currentStroke by rememberSaveable(stateSaver = annotatePointListSaver) {
        mutableStateOf(emptyList<Pair<Float, Float>>())
    }
    var allComments by rememberSaveable(stateSaver = annotateCommentListSaver) {
        mutableStateOf(emptyList<TextCommentDraft>())
    }

    var selectedPageIndex by rememberSaveable { mutableStateOf(0) }
    var selectedWidthFraction by rememberSaveable { mutableFloatStateOf(0.025f) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var showInstructionsDialog by rememberSaveable { mutableStateOf(true) }
    val currentCanvasSize by rememberUpdatedState(canvasSize)

    var annotateMode by rememberSaveable { mutableStateOf(AnnotateMode.MARK) }
    var isSnapMode by rememberSaveable { mutableStateOf(false) }

    var zoomScale by rememberSaveable { mutableFloatStateOf(1f) }
    var zoomOffsetX by rememberSaveable { mutableFloatStateOf(0f) }
    var zoomOffsetY by rememberSaveable { mutableFloatStateOf(0f) }
    val currentZoomScale by rememberUpdatedState(zoomScale)
    val currentZoomOffsetX by rememberUpdatedState(zoomOffsetX)
    val currentZoomOffsetY by rememberUpdatedState(zoomOffsetY)
    val currentSelectedPageIndex by rememberUpdatedState(selectedPageIndex)
    val currentSelectedWidthFraction by rememberUpdatedState(selectedWidthFraction)

    // Kommentar-Dialog-State
    var showCommentDialog by remember { mutableStateOf(false) }
    var pendingCommentAnchor by remember { mutableStateOf<Pair<Float, Float>?>(null) }
    var editingCommentIndex by remember { mutableStateOf<Int>(-1) }
    var commentDialogText by remember { mutableStateOf("") }


    val resetZoom = {
        zoomScale = 1f; zoomOffsetX = 0f; zoomOffsetY = 0f
    }
    val clearCurrentPage = {
        allStrokes = allStrokes.filter { it.pageIndex != selectedPageIndex }
        allRects = allRects.filter { it.pageIndex != selectedPageIndex }
        allComments = allComments.filter { it.pageIndex != selectedPageIndex }
        currentStroke = emptyList()
    }
    val resetAllMarks = {
        allStrokes = emptyList(); allRects = emptyList()
        allComments = emptyList(); currentStroke = emptyList()
    }
    val undoLastAnnotation = {
        currentStroke = emptyList()
        // Reihenfolge: zuerst letzter Kommentar, dann letztes Rect, dann letzter Stroke
        val lastCommentIdx = allComments.indexOfLast { it.pageIndex == selectedPageIndex }
        if (lastCommentIdx >= 0) {
            allComments = allComments.toMutableList().apply { removeAt(lastCommentIdx) }
        } else {
            val updatedRects = removeLastRectForPage(allRects, selectedPageIndex)
            if (updatedRects !== allRects) {
                allRects = updatedRects
            } else {
                allStrokes = removeLastStrokeForPage(allStrokes, selectedPageIndex)
            }
        }
    }

    val transformableState = rememberTransformableState { zoomChange, panChange, _ ->
        val newScale = (currentZoomScale * zoomChange).coerceIn(1f, 8f)
        zoomScale = newScale
        val clampedOffset = clampPanOffset(
            canvasSize = currentCanvasSize,
            scale = newScale,
            offsetX = currentZoomOffsetX + panChange.x,
            offsetY = currentZoomOffsetY + panChange.y
        )
        zoomOffsetX = clampedOffset.x
        zoomOffsetY = clampedOffset.y
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

    LaunchedEffect(selectedPageIndex, currentRecord.id) {
        viewModel.loadHighlightPage(selectedPageIndex)
        resetZoom()
    }

    val bitmap = pageBitmap
    val aspectRatio = bitmap?.let { it.width.toFloat() / it.height.toFloat() } ?: (210f / 297f)
    val pageStrokesForDisplay = allStrokes.filter { it.pageIndex == selectedPageIndex }
    val pageRectsForDisplay = allRects.filter { it.pageIndex == selectedPageIndex }
    val pageCommentsForDisplay = allComments.filter { it.pageIndex == selectedPageIndex }
    val hasPageAnnotations = pageStrokesForDisplay.isNotEmpty() || pageRectsForDisplay.isNotEmpty() ||
        pageCommentsForDisplay.isNotEmpty() || currentStroke.isNotEmpty()
    val hasAnyAnnotations = allStrokes.isNotEmpty() || allRects.isNotEmpty() ||
        allComments.isNotEmpty() || currentStroke.isNotEmpty()
    val hasMultiplePages = currentRecord.pageCount > 1
    val isZoomMode = annotateMode == AnnotateMode.ZOOM

    val (pdfInCanvasOrigin, pdfInCanvasSize) = remember(canvasSize, aspectRatio) {
        val cw = canvasSize.width.toFloat()
        val ch = canvasSize.height.toFloat()
        if (cw <= 0f || ch <= 0f) return@remember Offset.Zero to Size.Zero
        val ar = aspectRatio.coerceAtLeast(0.01f)
        if (ar >= cw / ch) {
            val h = cw / ar
            Offset(0f, (ch - h) / 2f) to Size(cw, h)
        } else {
            val w = ch * ar
            Offset((cw - w) / 2f, 0f) to Size(w, ch)
        }
    }
    val currentPdfOrigin by rememberUpdatedState(pdfInCanvasOrigin)
    val currentPdfSize by rememberUpdatedState(pdfInCanvasSize)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Toolbar: Modus-Chips
        Surface(
            modifier = Modifier.fillMaxWidth().zIndex(1f),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = 1.dp
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = annotateMode == AnnotateMode.MARK,
                        onClick = { annotateMode = AnnotateMode.MARK },
                        label = { Text(stringResource(R.string.annotate_mode_mark)) },
                        leadingIcon = {
                            Icon(Icons.Default.Edit, null, modifier = Modifier.size(16.dp))
                        }
                    )
                    FilterChip(
                        selected = annotateMode == AnnotateMode.WRITE,
                        onClick = { annotateMode = AnnotateMode.WRITE },
                        label = { Text(stringResource(R.string.annotate_mode_write)) },
                        leadingIcon = {
                            Icon(Icons.Default.Create, null, modifier = Modifier.size(16.dp))
                        }
                    )
                    FilterChip(
                        selected = annotateMode == AnnotateMode.ZOOM,
                        onClick = { annotateMode = AnnotateMode.ZOOM },
                        label = { Text(stringResource(R.string.highlight_mode_pan)) },
                        leadingIcon = {
                            Icon(Icons.Default.ZoomIn, null, modifier = Modifier.size(16.dp))
                        }
                    )
                    if (currentRecord.isSearchable && annotateMode == AnnotateMode.MARK) {
                        FilterChip(
                            selected = isSnapMode,
                            onClick = { isSnapMode = !isSnapMode },
                            label = { Text(stringResource(R.string.highlight_mode_snap)) },
                            leadingIcon = {
                                Icon(Icons.Default.TextFields, null, modifier = Modifier.size(16.dp))
                            }
                        )
                    }
                }
                if (isZoomMode) {
                    Text(
                        text = "${formatZoomScale(zoomScale)}×",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Canvas
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .onSizeChanged { canvasSize = it }
                .transformable(state = transformableState, enabled = isZoomMode)
                .pointerInput(annotateMode) {
                    when (annotateMode) {
                        AnnotateMode.MARK -> detectDragGestures(
                            onDragStart = { offset ->
                                currentStroke = listOf(
                                    normalizeViewportPoint(
                                        offset, currentCanvasSize, currentZoomScale,
                                        currentZoomOffsetX, currentZoomOffsetY,
                                        currentPdfOrigin, currentPdfSize
                                    )
                                )
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                currentStroke = currentStroke + normalizeViewportPoint(
                                    change.position, currentCanvasSize, currentZoomScale,
                                    currentZoomOffsetX, currentZoomOffsetY,
                                    currentPdfOrigin, currentPdfSize
                                )
                            },
                            onDragEnd = {
                                if (currentStroke.isNotEmpty()) {
                                    val snappedRects = if (isSnapMode) {
                                        snapStrokeToTextLines(
                                            stroke = currentStroke,
                                            textLines = textLines.filter { it.pageIndex == currentSelectedPageIndex }
                                        )
                                    } else emptyList()
                                    if (snappedRects.isNotEmpty()) {
                                        allRects = allRects + snappedRects
                                    } else {
                                        allStrokes = allStrokes + HighlightStroke(
                                            points = currentStroke,
                                            pageIndex = currentSelectedPageIndex,
                                            strokeWidthFraction = currentSelectedWidthFraction
                                        )
                                    }
                                    currentStroke = emptyList()
                                }
                            },
                            onDragCancel = { currentStroke = emptyList() }
                        )
                        AnnotateMode.WRITE -> awaitEachGesture {
                            // Down-Event sofort ohne Slop-Warten erfassen:
                            // awaitPointerEvent() ist Member von AwaitPointerEventScope
                            var startChange: androidx.compose.ui.input.pointer.PointerInputChange? = null
                            while (startChange == null) {
                                val ev = awaitPointerEvent()
                                startChange = ev.changes.firstOrNull { !it.previousPressed && it.pressed }
                            }
                            val pointerId = startChange.id
                            val startNorm = normalizeViewportPoint(
                                startChange.position, currentCanvasSize, currentZoomScale,
                                currentZoomOffsetX, currentZoomOffsetY,
                                currentPdfOrigin, currentPdfSize
                            )

                            // Hit-Test: Kommentar-Ankerpunkt in der Nähe?
                            val hitRadius = 0.06f
                            val localComments = allComments.filter { it.pageIndex == currentSelectedPageIndex }
                            val hitLocal = localComments.indexOfFirst { c ->
                                val dx = c.anchorX - startNorm.first
                                val dy = c.anchorY - startNorm.second
                                dx * dx + dy * dy < hitRadius * hitRadius
                            }
                            val hitGlobalIdx = if (hitLocal >= 0) allComments.indexOf(localComments[hitLocal]) else -1

                            var latestNorm = startNorm
                            var movedEnough = false
                            val dragThreshold = 0.0009f  // ~3% Seitenbreite²

                            // Pointer verfolgen bis Finger losgelassen
                            while (true) {
                                val ev = awaitPointerEvent()
                                val change = ev.changes.firstOrNull { it.id == pointerId } ?: break
                                if (change.previousPressed && !change.pressed) break  // UP-Event
                                if (!change.pressed) continue

                                latestNorm = normalizeViewportPoint(
                                    change.position, currentCanvasSize, currentZoomScale,
                                    currentZoomOffsetX, currentZoomOffsetY,
                                    currentPdfOrigin, currentPdfSize
                                )
                                val dx = latestNorm.first - startNorm.first
                                val dy = latestNorm.second - startNorm.second
                                if (dx * dx + dy * dy > dragThreshold) movedEnough = true

                                if (movedEnough && hitGlobalIdx >= 0) {
                                    change.consume()
                                    allComments = allComments.toMutableList().apply {
                                        val old = get(hitGlobalIdx)
                                        set(hitGlobalIdx, old.copy(anchorX = latestNorm.first, anchorY = latestNorm.second))
                                    }
                                }
                            }

                            // Finger losgelassen: Tap-Semantik wenn nicht verschoben
                            if (!movedEnough) {
                                if (hitGlobalIdx >= 0) {
                                    editingCommentIndex = hitGlobalIdx
                                    commentDialogText = allComments[hitGlobalIdx].text
                                    showCommentDialog = true
                                } else {
                                    editingCommentIndex = -1
                                    pendingCommentAnchor = startNorm
                                    commentDialogText = ""
                                    showCommentDialog = true
                                }
                            }
                        }
                        AnnotateMode.ZOOM -> {} // handled by transformable
                    }
                }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = zoomScale; scaleY = zoomScale
                        translationX = zoomOffsetX; translationY = zoomOffsetY
                        clip = true
                    }
            ) {
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = stringResource(
                            R.string.annotate_page_content_description,
                            selectedPageIndex + 1
                        ),
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }

                Canvas(modifier = Modifier.fillMaxSize()) {
                    val pdX = pdfInCanvasOrigin.x
                    val pdY = pdfInCanvasOrigin.y
                    val pdW = pdfInCanvasSize.width.coerceAtLeast(1f)
                    val pdH = pdfInCanvasSize.height.coerceAtLeast(1f)

                    val allForPage = pageStrokesForDisplay + if (currentStroke.isNotEmpty()) {
                        listOf(HighlightStroke(currentStroke, selectedPageIndex, selectedWidthFraction))
                    } else emptyList()

                    pageRectsForDisplay.forEach { rect ->
                        drawRect(
                            color = highlightRectYellow,
                            topLeft = Offset(pdX + rect.left * pdW, pdY + rect.top * pdH),
                            size = Size((rect.right - rect.left) * pdW, (rect.bottom - rect.top) * pdH)
                        )
                    }

                    allForPage.forEach { stroke ->
                        if (stroke.points.isEmpty()) return@forEach
                        val strokeWidthPx = pdW * stroke.strokeWidthFraction.coerceIn(0.005f, 0.1f)
                        val path = Path()
                        val first = Offset(pdX + stroke.points.first().first * pdW, pdY + stroke.points.first().second * pdH)
                        path.moveTo(first.x, first.y)
                        stroke.points.drop(1).forEach { (nx, ny) ->
                            path.lineTo(pdX + nx * pdW, pdY + ny * pdH)
                        }
                        drawPath(
                            path = path,
                            color = highlightYellow,
                            style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round, join = StrokeJoin.Round)
                        )
                        if (stroke.points.size == 1) {
                            drawCircle(color = highlightYellow, radius = strokeWidthPx / 2f, center = first)
                        }
                    }

                    // Textkommentare anzeigen
                    pageCommentsForDisplay.forEach { comment ->
                        val anchorX = pdX + comment.anchorX * pdW
                        val anchorY = pdY + comment.anchorY * pdH
                        val textSizePx = (pdW * comment.fontSizeFraction).coerceIn(10f, 72f)

                        val paint = android.graphics.Paint().apply {
                            color = android.graphics.Color.argb(220, 30, 30, 30)
                            textSize = textSizePx
                            isAntiAlias = true
                        }
                        val bgPaint = android.graphics.Paint().apply {
                            color = android.graphics.Color.argb(180, 255, 255, 200)
                        }
                        val lines = comment.text.split("\n")
                        val lineHeight = textSizePx * 1.3f
                        val maxWidth = lines.maxOfOrNull { paint.measureText(it) } ?: 0f
                        val bgRect = android.graphics.RectF(
                            anchorX - 3f, anchorY - textSizePx - 2f,
                            anchorX + maxWidth + 6f, anchorY + lineHeight * (lines.size - 1) + 4f
                        )
                        drawContext.canvas.nativeCanvas.drawRoundRect(bgRect, 4f, 4f, bgPaint)
                        lines.forEachIndexed { i, line ->
                            drawContext.canvas.nativeCanvas.drawText(
                                line, anchorX, anchorY + lineHeight * i, paint
                            )
                        }
                    }

                    // Schreibmodus-Hint: kleiner Ankerpunkt-Indikator
                    if (annotateMode == AnnotateMode.WRITE) {
                        pageCommentsForDisplay.forEach { comment ->
                            val cx = pdX + comment.anchorX * pdW
                            val cy = pdY + comment.anchorY * pdH
                            drawCircle(
                                color = Color(0xFF1565C0),
                                radius = 5f,
                                center = Offset(cx, cy)
                            )
                        }
                    }
                }
            }
        }

        // Steuerleiste
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
                if (hasMultiplePages) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = { selectedPageIndex = (selectedPageIndex - 1).coerceAtLeast(0) },
                            enabled = selectedPageIndex > 0,
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                        ) {
                            Text(stringResource(R.string.annotate_page_previous_short), maxLines = 1)
                        }
                        Text(
                            text = stringResource(
                                R.string.annotate_page_indicator,
                                selectedPageIndex + 1,
                                currentRecord.pageCount
                            ),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(0.8f)
                        )
                        OutlinedButton(
                            onClick = { selectedPageIndex = (selectedPageIndex + 1).coerceAtMost(currentRecord.pageCount - 1) },
                            enabled = selectedPageIndex < currentRecord.pageCount - 1,
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                        ) {
                            Text(stringResource(R.string.annotate_page_next_short), maxLines = 1)
                        }
                    }
                }

                when (annotateMode) {
                    AnnotateMode.ZOOM -> {
                        OutlinedButton(
                            onClick = resetZoom,
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                        ) {
                            Text(stringResource(R.string.highlight_zoom_reset_button))
                        }
                    }
                    AnnotateMode.WRITE -> {
                        Text(
                            text = stringResource(R.string.annotate_write_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            OutlinedButton(
                                onClick = undoLastAnnotation,
                                enabled = hasPageAnnotations,
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp)
                            ) {
                                Text(stringResource(R.string.highlight_undo_last), maxLines = 1, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                            }
                            OutlinedButton(
                                onClick = clearCurrentPage,
                                enabled = hasPageAnnotations,
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp)
                            ) {
                                Text(stringResource(R.string.highlight_clear_page), maxLines = 1, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                            }
                            OutlinedButton(
                                onClick = resetAllMarks,
                                enabled = hasAnyAnnotations,
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp)
                            ) {
                                Text(stringResource(R.string.highlight_reset_all), maxLines = 1, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.annotate_text_size),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )
                            markerWidthOptions.forEach { option ->
                                FilterChip(
                                    selected = selectedWidthFraction == option.fraction,
                                    onClick = { selectedWidthFraction = option.fraction },
                                    label = { Text(stringResource(option.labelRes)) }
                                )
                            }
                        }
                    }
                    AnnotateMode.MARK -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            OutlinedButton(
                                onClick = undoLastAnnotation,
                                enabled = hasPageAnnotations,
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp)
                            ) {
                                Text(stringResource(R.string.highlight_undo_last), maxLines = 1, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                            }
                            OutlinedButton(
                                onClick = clearCurrentPage,
                                enabled = hasPageAnnotations,
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp)
                            ) {
                                Text(stringResource(R.string.highlight_clear_page), maxLines = 1, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                            }
                            OutlinedButton(
                                onClick = resetAllMarks,
                                enabled = hasAnyAnnotations,
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp)
                            ) {
                                Text(stringResource(R.string.highlight_reset_all), maxLines = 1, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.highlight_stroke_width),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )
                            markerWidthOptions.forEach { option ->
                                FilterChip(
                                    selected = selectedWidthFraction == option.fraction,
                                    onClick = { selectedWidthFraction = option.fraction },
                                    label = { Text(stringResource(option.labelRes)) }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Speichern-Button
        Button(
            onClick = {
                val finalStrokes = allStrokes + if (currentStroke.isNotEmpty()) {
                    listOf(HighlightStroke(currentStroke, selectedPageIndex, selectedWidthFraction))
                } else emptyList()
                val finalComments = allComments.map { draft ->
                    TextComment(
                        pageIndex = draft.pageIndex,
                        anchorX = draft.anchorX,
                        anchorY = draft.anchorY,
                        text = draft.text,
                        fontSizeFraction = draft.fontSizeFraction
                    )
                }
                viewModel.applyAnnotations(finalStrokes, allRects, finalComments)
            },
            enabled = hasAnyAnnotations && !editLoading,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.annotate_apply))
        }
    }

    // Instructions-Dialog
    if (showInstructionsDialog) {
        AlertDialog(
            onDismissRequest = { showInstructionsDialog = false },
            title = { Text(stringResource(R.string.annotate_description)) },
            text = { Text(stringResource(R.string.annotate_details)) },
            confirmButton = {
                TextButton(onClick = { showInstructionsDialog = false }) {
                    Text(stringResource(R.string.action_ok))
                }
            }
        )
    }

    // Kommentar-Dialog
    if (showCommentDialog) {
        val isEditing = editingCommentIndex >= 0
        AlertDialog(
            onDismissRequest = { showCommentDialog = false; commentDialogText = "" },
            title = {
                Text(stringResource(if (isEditing) R.string.annotate_comment_edit_title else R.string.annotate_comment_title))
            },
            text = {
                OutlinedTextField(
                    value = commentDialogText,
                    onValueChange = { if (it.length <= 160) commentDialogText = it },
                    label = { Text(stringResource(R.string.annotate_comment_hint)) },
                    minLines = 2,
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val text = commentDialogText.trim()
                        if (text.isNotEmpty()) {
                            if (isEditing) {
                                allComments = allComments.toMutableList().apply {
                                    set(editingCommentIndex, get(editingCommentIndex).copy(text = text))
                                }
                            } else {
                                val anchor = pendingCommentAnchor
                                if (anchor != null) {
                                    allComments = allComments + TextCommentDraft(
                                        pageIndex = selectedPageIndex,
                                        anchorX = anchor.first,
                                        anchorY = anchor.second,
                                        text = text,
                                        fontSizeFraction = selectedWidthFraction + 0.003f
                                    )
                                }
                            }
                        }
                        showCommentDialog = false
                        commentDialogText = ""
                        pendingCommentAnchor = null
                    }
                ) {
                    Text(stringResource(R.string.annotate_comment_add))
                }
            },
            dismissButton = {
                Row {
                    if (isEditing) {
                        TextButton(
                            onClick = {
                                allComments = allComments.toMutableList().apply { removeAt(editingCommentIndex) }
                                showCommentDialog = false
                                commentDialogText = ""
                            }
                        ) {
                            Text(stringResource(R.string.annotate_comment_delete))
                        }
                    }
                    TextButton(
                        onClick = { showCommentDialog = false; commentDialogText = "" }
                    ) {
                        Text(stringResource(R.string.action_cancel))
                    }
                }
            }
        )
    }

    // Loading-Dialog
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

    // Error-Dialog
    error?.let { msg ->
        AlertDialog(
            onDismissRequest = viewModel::clearError,
            title = { Text(stringResource(R.string.error_title)) },
            text = { Text(msg) },
            confirmButton = {
                TextButton(onClick = viewModel::clearError) {
                    Text(stringResource(R.string.action_ok))
                }
            }
        )
    }
}
