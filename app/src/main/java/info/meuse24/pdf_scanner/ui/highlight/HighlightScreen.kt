package info.meuse24.pdf_scanner.ui.highlight

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import info.meuse24.pdf_scanner.R
import info.meuse24.pdf_scanner.domain.usecase.HighlightStroke
import info.meuse24.pdf_scanner.ui.documentaction.DocumentEditViewModel
import java.util.Locale

private data class MarkerWidthOption(
    val fraction: Float,
    val labelRes: Int
)

private val markerWidthOptions = listOf(
    MarkerWidthOption(0.015f, R.string.highlight_stroke_thin),
    MarkerWidthOption(0.025f, R.string.highlight_stroke_medium),
    MarkerWidthOption(0.04f, R.string.highlight_stroke_thick)
)

private val highlightYellow = Color(1f, 0.86f, 0f, 0.5f)

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

    LaunchedEffect(success) {
        if (success) onNavigateBack()
    }

    var allStrokes by remember { mutableStateOf(emptyList<HighlightStroke>()) }
    var currentStroke by remember { mutableStateOf(emptyList<Pair<Float, Float>>()) }
    var selectedPageIndex by rememberSaveable { mutableStateOf(0) }
    var selectedWidthFraction by rememberSaveable { mutableStateOf(0.025f) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    val currentCanvasSize by rememberUpdatedState(canvasSize)

    // Zoom state
    var zoomScale by rememberSaveable { mutableFloatStateOf(1f) }
    var zoomOffsetX by rememberSaveable { mutableFloatStateOf(0f) }
    var zoomOffsetY by rememberSaveable { mutableFloatStateOf(0f) }
    var isZoomMode by rememberSaveable { mutableStateOf(false) }
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

    if (selectedPageIndex >= currentRecord.pageCount) selectedPageIndex = 0

    LaunchedEffect(selectedPageIndex, currentRecord.id) {
        viewModel.loadHighlightPage(selectedPageIndex)
        // Zoom beim Seitenwechsel zurücksetzen
        resetZoom()
    }

    val bitmap = pageBitmap
    val aspectRatio = bitmap?.let { it.width.toFloat() / it.height.toFloat() } ?: (210f / 297f)
    val pageStrokesForDisplay = allStrokes.filter { it.pageIndex == selectedPageIndex }
    val hasAnyStroke = allStrokes.isNotEmpty() || currentStroke.isNotEmpty()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = stringResource(R.string.highlight_description),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
        item {
            Text(
                text = stringResource(R.string.highlight_details),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline
            )
        }
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = currentRecord.filename,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(
                            R.string.highlight_target_page,
                            selectedPageIndex + 1,
                            currentRecord.pageCount
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }
        item {
            // Modus-Umschalter und Zoom-Steuerung
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = !isZoomMode,
                    onClick = { isZoomMode = false },
                    label = { Text(stringResource(R.string.highlight_mode_draw)) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = null
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
                            contentDescription = null
                        )
                    }
                )
                if (zoomScale > 1.01f) {
                    Spacer(Modifier.weight(1f))
                    TextButton(
                        onClick = resetZoom
                    ) {
                        Text(
                            stringResource(
                                R.string.highlight_zoom_reset,
                                formatZoomScale(zoomScale)
                            )
                        )
                    }
                }
            }
        }
        item {
            // Äußerer Box: legt Größe + Aspect-Ratio fest und clippt den gezoomten Inhalt
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(aspectRatio)
                    .clip(RoundedCornerShape(8.dp))
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant,
                        shape = RoundedCornerShape(8.dp)
                    )
                    .onSizeChanged { canvasSize = it }
            ) {
                // Innerer Box: trägt Zoom-Transformation, Gesten und Zeichnen
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = zoomScale
                            scaleY = zoomScale
                            translationX = zoomOffsetX
                            translationY = zoomOffsetY
                        }
                        .transformable(state = transformableState, enabled = isZoomMode)
                        .pointerInput(isZoomMode) {
                            if (!isZoomMode) {
                                detectDragGestures(
                                    onDragStart = { offset ->
                                        currentStroke =
                                            listOf(normalizePoint(offset, currentCanvasSize))
                                    },
                                    onDrag = { change, _ ->
                                        change.consume()
                                        currentStroke =
                                            currentStroke + normalizePoint(
                                                change.position,
                                                currentCanvasSize
                                            )
                                    },
                                    onDragEnd = {
                                        if (currentStroke.isNotEmpty()) {
                                            allStrokes = allStrokes + HighlightStroke(
                                                points = currentStroke,
                                                pageIndex = currentSelectedPageIndex,
                                                strokeWidthFraction = currentSelectedWidthFraction
                                            )
                                            currentStroke = emptyList()
                                        }
                                    },
                                    onDragCancel = {
                                        if (currentStroke.isNotEmpty()) {
                                            allStrokes = allStrokes + HighlightStroke(
                                                points = currentStroke,
                                                pageIndex = currentSelectedPageIndex,
                                                strokeWidthFraction = currentSelectedWidthFraction
                                            )
                                            currentStroke = emptyList()
                                        }
                                    }
                                )
                            }
                        }
                ) {
                    // Hintergrund: PDF-Seite oder Lade-Indikator
                    if (bitmap != null) {
                        androidx.compose.foundation.Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.FillBounds,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.surfaceContainerLow),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }

                    // Overlay: Marker-Striche
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val allForPage = pageStrokesForDisplay + if (currentStroke.isNotEmpty()) {
                            listOf(
                                HighlightStroke(
                                    currentStroke,
                                    selectedPageIndex,
                                    selectedWidthFraction
                                )
                            )
                        } else emptyList()

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
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = {
                        allStrokes =
                            allStrokes.filter { it.pageIndex != selectedPageIndex }
                        currentStroke = emptyList()
                    }
                ) {
                    Text(stringResource(R.string.highlight_clear_page))
                }
                Text(
                    text = stringResource(R.string.highlight_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.align(Alignment.CenterVertically)
                )
            }
        }
        item {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(
                        R.string.highlight_target_page,
                        selectedPageIndex + 1,
                        currentRecord.pageCount
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    OutlinedButton(
                        onClick = { selectedPageIndex = (selectedPageIndex - 1).coerceAtLeast(0) },
                        enabled = selectedPageIndex > 0
                    ) {
                        Text(stringResource(R.string.signature_page_previous))
                    }
                    OutlinedButton(
                        onClick = {
                            selectedPageIndex =
                                (selectedPageIndex + 1).coerceAtMost(currentRecord.pageCount - 1)
                        },
                        enabled = selectedPageIndex < currentRecord.pageCount - 1
                    ) {
                        Text(stringResource(R.string.signature_page_next))
                    }
                }
            }
        }
        item {
            Column {
                Text(
                    text = stringResource(R.string.highlight_stroke_width),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
        item {
            Button(
                onClick = {
                    val finalStrokes = allStrokes + if (currentStroke.isNotEmpty()) {
                        listOf(
                            HighlightStroke(
                                currentStroke,
                                selectedPageIndex,
                                selectedWidthFraction
                            )
                        )
                    } else emptyList()
                    viewModel.applyHighlight(finalStrokes)
                },
                enabled = hasAnyStroke && !editLoading,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.highlight_apply))
            }
        }
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
