package info.meuse24.pdf_scanner.ui.highlight

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import info.meuse24.pdf_scanner.domain.usecase.HIGHLIGHT_ALPHA
import info.meuse24.pdf_scanner.domain.usecase.HIGHLIGHT_COLOR_BLUE
import info.meuse24.pdf_scanner.domain.usecase.HIGHLIGHT_COLOR_GREEN
import info.meuse24.pdf_scanner.domain.usecase.HIGHLIGHT_COLOR_RED
import info.meuse24.pdf_scanner.R
import info.meuse24.pdf_scanner.domain.usecase.HighlightRect
import info.meuse24.pdf_scanner.domain.usecase.HighlightStroke
import info.meuse24.pdf_scanner.domain.usecase.TextLine
import info.meuse24.pdf_scanner.ui.documentaction.DocumentEditViewModel
import java.util.Locale
import kotlin.math.min

private data class MarkerWidthOption(
    val fraction: Float,
    val labelRes: Int
)

private val markerWidthOptions = listOf(
    MarkerWidthOption(0.015f, R.string.highlight_stroke_thin),
    MarkerWidthOption(0.025f, R.string.highlight_stroke_medium),
    MarkerWidthOption(0.04f, R.string.highlight_stroke_thick)
)

private val highlightPointListSaver = listSaver<List<Pair<Float, Float>>, Float>(
    save = { points -> points.flatMap { listOf(it.first, it.second) } },
    restore = { values ->
        values.chunked(2).mapNotNull { pair ->
            if (pair.size == 2) pair[0] to pair[1] else null
        }
    }
)

private val highlightStrokeListSaver = listSaver<List<HighlightStroke>, List<Float>>(
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
            HighlightStroke(
                points = points,
                pageIndex = pageIndex,
                strokeWidthFraction = strokeWidthFraction
            )
        }
    }
)

private val highlightRectListSaver = listSaver<List<HighlightRect>, List<Float>>(
    save = { rects ->
        rects.map { rect ->
            listOf(
                rect.pageIndex.toFloat(),
                rect.left,
                rect.top,
                rect.right,
                rect.bottom
            )
        }
    },
    restore = { values ->
        values.mapNotNull { savedRect ->
            if (savedRect.size != 5) return@mapNotNull null
            HighlightRect(
                pageIndex = savedRect[0].toInt(),
                left = savedRect[1],
                top = savedRect[2],
                right = savedRect[3],
                bottom = savedRect[4]
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
fun HighlightScreen(
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

    var allStrokes by rememberSaveable(stateSaver = highlightStrokeListSaver) {
        mutableStateOf(emptyList<HighlightStroke>())
    }
    var allRects by rememberSaveable(stateSaver = highlightRectListSaver) {
        mutableStateOf(emptyList<HighlightRect>())
    }
    var currentStroke by rememberSaveable(stateSaver = highlightPointListSaver) {
        mutableStateOf(emptyList<Pair<Float, Float>>())
    }
    var selectedPageIndex by rememberSaveable { mutableStateOf(0) }
    var selectedWidthFraction by rememberSaveable { mutableFloatStateOf(0.025f) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var showInstructionsDialog by rememberSaveable { mutableStateOf(true) }
    val currentCanvasSize by rememberUpdatedState(canvasSize)

    var zoomScale by rememberSaveable { mutableFloatStateOf(1f) }
    var zoomOffsetX by rememberSaveable { mutableFloatStateOf(0f) }
    var zoomOffsetY by rememberSaveable { mutableFloatStateOf(0f) }
    var isZoomMode by rememberSaveable { mutableStateOf(false) }
    var isSnapMode by rememberSaveable { mutableStateOf(false) }
    val currentZoomScale by rememberUpdatedState(zoomScale)
    val currentZoomOffsetX by rememberUpdatedState(zoomOffsetX)
    val currentZoomOffsetY by rememberUpdatedState(zoomOffsetY)
    val currentSelectedPageIndex by rememberUpdatedState(selectedPageIndex)
    val currentSelectedWidthFraction by rememberUpdatedState(selectedWidthFraction)

    val resetZoom = {
        zoomScale = 1f
        zoomOffsetX = 0f
        zoomOffsetY = 0f
    }
    val clearCurrentPage = {
        allStrokes = allStrokes.filter { it.pageIndex != selectedPageIndex }
        allRects = allRects.filter { it.pageIndex != selectedPageIndex }
        currentStroke = emptyList()
    }
    val resetAllMarks = {
        allStrokes = emptyList()
        allRects = emptyList()
        currentStroke = emptyList()
    }
    val undoLastMark = {
        currentStroke = emptyList()
        val updatedRects = removeLastRectForPage(allRects, selectedPageIndex)
        if (updatedRects !== allRects) {
            allRects = updatedRects
        } else {
            allStrokes = removeLastStrokeForPage(allStrokes, selectedPageIndex)
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
        if (selectedPageIndex >= currentRecord.pageCount) {
            selectedPageIndex = 0
        }
    }

    LaunchedEffect(selectedPageIndex, currentRecord.id) {
        viewModel.loadHighlightPage(selectedPageIndex)
        resetZoom()
    }

    val bitmap = pageBitmap
    val aspectRatio = bitmap?.let { it.width.toFloat() / it.height.toFloat() } ?: (210f / 297f)
    val pageStrokesForDisplay = allStrokes.filter { it.pageIndex == selectedPageIndex }
    val pageRectsForDisplay = allRects.filter { it.pageIndex == selectedPageIndex }
    val hasPageMarks =
        pageStrokesForDisplay.isNotEmpty() || pageRectsForDisplay.isNotEmpty() || currentStroke.isNotEmpty()
    val hasAnyMarks = allStrokes.isNotEmpty() || allRects.isNotEmpty() || currentStroke.isNotEmpty()
    val hasMultiplePages = currentRecord.pageCount > 1
    val density = LocalDensity.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // ── Kompakte Toolbar: Modus-Chips + Seite/Zoom-Info ────────────
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = 1.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = !isZoomMode,
                        onClick = { isZoomMode = false },
                        label = { Text(stringResource(R.string.highlight_mode_draw)) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    )
                    FilterChip(
                        selected = isZoomMode,
                        onClick = { isZoomMode = true },
                        label = { Text(stringResource(R.string.highlight_mode_pan)) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.ZoomIn,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    )
                    if (currentRecord.isSearchable && !isZoomMode) {
                        FilterChip(
                            selected = isSnapMode,
                            onClick = { isSnapMode = !isSnapMode },
                            label = { Text(stringResource(R.string.highlight_mode_snap)) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.TextFields,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        )
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (hasMultiplePages) {
                        Text(
                            text = "${selectedPageIndex + 1}/${currentRecord.pageCount}",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
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
        }

        // ── PDF-Canvas (füllt verfügbaren Platz, weißer Hintergrund) ───
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            val maxWidthPx = constraints.maxWidth.toFloat()
            val maxHeightPx = constraints.maxHeight.toFloat()
            val fittedWidthPx = min(maxWidthPx, maxHeightPx * aspectRatio)
            val fittedHeightPx = fittedWidthPx / aspectRatio
            val fittedWidthDp = with(density) { fittedWidthPx.toDp() }
            val fittedHeightDp = with(density) { fittedHeightPx.toDp() }

            Box(
                modifier = Modifier
                    .size(fittedWidthDp, fittedHeightDp)
                    .shadow(elevation = 4.dp, shape = RoundedCornerShape(4.dp))
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.White)
                    .onSizeChanged { canvasSize = it }
                    .transformable(state = transformableState, enabled = isZoomMode)
                    .pointerInput(isZoomMode) {
                        if (!isZoomMode) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    currentStroke = listOf(
                                        normalizeViewportPoint(
                                            offset = offset,
                                            canvasSize = currentCanvasSize,
                                            scale = currentZoomScale,
                                            offsetX = currentZoomOffsetX,
                                            offsetY = currentZoomOffsetY
                                        )
                                    )
                                },
                                onDrag = { change, _ ->
                                    change.consume()
                                    currentStroke = currentStroke + normalizeViewportPoint(
                                        offset = change.position,
                                        canvasSize = currentCanvasSize,
                                        scale = currentZoomScale,
                                        offsetX = currentZoomOffsetX,
                                        offsetY = currentZoomOffsetY
                                    )
                                },
                                onDragEnd = {
                                    if (currentStroke.isNotEmpty()) {
                                        val snappedRects = if (isSnapMode) {
                                            snapStrokeToTextLines(
                                                stroke = currentStroke,
                                                textLines = textLines.filter {
                                                    it.pageIndex == currentSelectedPageIndex
                                                }
                                            )
                                        } else {
                                            emptyList()
                                        }
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
                                onDragCancel = {
                                    currentStroke = emptyList()
                                }
                            )
                        }
                    }
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = zoomScale
                            scaleY = zoomScale
                            translationX = zoomOffsetX
                            translationY = zoomOffsetY
                        }
                ) {
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.FillBounds,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.White),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }

                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val allForPage = pageStrokesForDisplay + if (currentStroke.isNotEmpty()) {
                            listOf(
                                HighlightStroke(
                                    points = currentStroke,
                                    pageIndex = selectedPageIndex,
                                    strokeWidthFraction = selectedWidthFraction
                                )
                            )
                        } else {
                            emptyList()
                        }

                        pageRectsForDisplay.forEach { rect ->
                            drawRect(
                                color = highlightRectYellow,
                                topLeft = Offset(rect.left * size.width, rect.top * size.height),
                                size = Size(
                                    width = (rect.right - rect.left) * size.width,
                                    height = (rect.bottom - rect.top) * size.height
                                )
                            )
                        }

                        allForPage.forEach { stroke ->
                            if (stroke.points.isEmpty()) return@forEach
                            val strokeWidthPx =
                                size.width * stroke.strokeWidthFraction.coerceIn(0.005f, 0.1f)
                            val path = Path()
                            val first =
                                denormalizePoint(stroke.points.first(), size.width, size.height)
                            path.moveTo(first.x, first.y)
                            stroke.points.drop(1).forEach { (nx, ny) ->
                                val pt = denormalizePoint(Pair(nx, ny), size.width, size.height)
                                path.lineTo(pt.x, pt.y)
                            }
                            drawPath(
                                path = path,
                                color = highlightYellow,
                                style = Stroke(
                                    width = strokeWidthPx,
                                    cap = StrokeCap.Round,
                                    join = StrokeJoin.Round
                                )
                            )
                            if (stroke.points.size == 1) {
                                drawCircle(
                                    color = highlightYellow,
                                    radius = strokeWidthPx / 2f,
                                    center = first
                                )
                            }
                        }
                    }
                }
            }
        }

        // ── Kompakte Steuerleiste ───────────────────────────────────────
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
                // Seitennavigation (nur bei mehrseitigen PDFs)
                if (hasMultiplePages) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                selectedPageIndex = (selectedPageIndex - 1).coerceAtLeast(0)
                            },
                            enabled = selectedPageIndex > 0,
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.signature_page_previous),
                                maxLines = 1
                            )
                        }
                        OutlinedButton(
                            onClick = {
                                selectedPageIndex =
                                    (selectedPageIndex + 1).coerceAtMost(currentRecord.pageCount - 1)
                            },
                            enabled = selectedPageIndex < currentRecord.pageCount - 1,
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.signature_page_next),
                                maxLines = 1
                            )
                        }
                    }
                }

                // Aktions-Buttons je nach Modus
                if (isZoomMode) {
                    OutlinedButton(
                        onClick = resetZoom,
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                    ) {
                        Text(stringResource(R.string.highlight_zoom_reset_button))
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedButton(
                            onClick = undoLastMark,
                            enabled = hasPageMarks,
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.highlight_undo_last),
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center,
                                maxLines = 1
                            )
                        }
                        OutlinedButton(
                            onClick = clearCurrentPage,
                            enabled = hasPageMarks,
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.highlight_clear_page),
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center,
                                maxLines = 1
                            )
                        }
                        OutlinedButton(
                            onClick = resetAllMarks,
                            enabled = hasAnyMarks,
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.highlight_reset_all),
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center,
                                maxLines = 1
                            )
                        }
                    }

                    // Markierungsbreite
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

        // ── Speichern-Button ───────────────────────────────────────────
        Button(
            onClick = {
                val finalStrokes = allStrokes + if (currentStroke.isNotEmpty()) {
                    listOf(
                        HighlightStroke(
                            points = currentStroke,
                            pageIndex = selectedPageIndex,
                            strokeWidthFraction = selectedWidthFraction
                        )
                    )
                } else {
                    emptyList()
                }
                viewModel.applyHighlight(finalStrokes, allRects)
            },
            enabled = hasAnyMarks && !editLoading,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.highlight_apply))
        }
    }

    if (showInstructionsDialog) {
        AlertDialog(
            onDismissRequest = { showInstructionsDialog = false },
            title = { Text(stringResource(R.string.highlight_description)) },
            text = { Text(stringResource(R.string.highlight_details)) },
            confirmButton = {
                TextButton(onClick = { showInstructionsDialog = false }) {
                    Text(stringResource(R.string.action_ok))
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

internal fun removeLastStrokeForPage(
    strokes: List<HighlightStroke>,
    pageIndex: Int
): List<HighlightStroke> {
    val lastIndex = strokes.indexOfLast { it.pageIndex == pageIndex }
    if (lastIndex == -1) return strokes
    return strokes.toMutableList().apply { removeAt(lastIndex) }
}

internal fun removeLastRectForPage(
    rects: List<HighlightRect>,
    pageIndex: Int
): List<HighlightRect> {
    val lastIndex = rects.indexOfLast { it.pageIndex == pageIndex }
    if (lastIndex == -1) return rects
    return rects.toMutableList().apply { removeAt(lastIndex) }
}

internal fun snapStrokeToTextLines(
    stroke: List<Pair<Float, Float>>,
    textLines: List<TextLine>
): List<HighlightRect> {
    if (stroke.isEmpty() || textLines.isEmpty()) return emptyList()

    val minY = stroke.minOf { it.second }
    val maxY = stroke.maxOf { it.second }

    return textLines.filter { line ->
        line.top <= maxY && line.bottom >= minY
    }.distinctBy { line ->
        listOf(line.pageIndex, line.left, line.top, line.right, line.bottom)
    }.map { line ->
        HighlightRect(
            left = line.left,
            top = line.top,
            right = line.right,
            bottom = line.bottom,
            pageIndex = line.pageIndex
        )
    }
}

internal fun normalizeViewportPoint(
    offset: Offset,
    canvasSize: IntSize,
    scale: Float,
    offsetX: Float,
    offsetY: Float
): Pair<Float, Float> {
    val contentOffset = mapViewportOffsetToCanvasOffset(
        offset = offset,
        canvasSize = canvasSize,
        scale = scale,
        offsetX = offsetX,
        offsetY = offsetY
    )
    return normalizePoint(contentOffset, canvasSize)
}

internal fun mapViewportOffsetToCanvasOffset(
    offset: Offset,
    canvasSize: IntSize,
    scale: Float,
    offsetX: Float,
    offsetY: Float
): Offset {
    if (canvasSize == IntSize.Zero) return Offset.Zero
    val width = canvasSize.width.toFloat()
    val height = canvasSize.height.toFloat()
    val clampedScale = scale.coerceAtLeast(1f)
    val centerX = width / 2f
    val centerY = height / 2f
    val unscaledX = centerX + ((offset.x - offsetX) - centerX) / clampedScale
    val unscaledY = centerY + ((offset.y - offsetY) - centerY) / clampedScale
    return Offset(
        x = unscaledX.coerceIn(0f, width),
        y = unscaledY.coerceIn(0f, height)
    )
}

private fun normalizePoint(offset: Offset, canvasSize: IntSize): Pair<Float, Float> {
    if (canvasSize == IntSize.Zero) return Pair(0f, 0f)
    return Pair(
        (offset.x / canvasSize.width.toFloat()).coerceIn(0f, 1f),
        (offset.y / canvasSize.height.toFloat()).coerceIn(0f, 1f)
    )
}

internal fun clampPanOffset(
    canvasSize: IntSize,
    scale: Float,
    offsetX: Float,
    offsetY: Float
): Offset {
    val maxX = canvasSize.width * (scale - 1f) / 2f
    val maxY = canvasSize.height * (scale - 1f) / 2f
    return Offset(
        x = offsetX.coerceIn(-maxX, maxX),
        y = offsetY.coerceIn(-maxY, maxY)
    )
}

internal fun formatZoomScale(scale: Float): String = String.format(Locale.US, "%.1f", scale)

private fun denormalizePoint(point: Pair<Float, Float>, width: Float, height: Float): Offset {
    return Offset(point.first * width, point.second * height)
}
