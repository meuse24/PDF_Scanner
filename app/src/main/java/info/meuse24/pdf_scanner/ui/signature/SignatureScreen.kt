package info.meuse24.pdf_scanner.ui.signature

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import info.meuse24.pdf_scanner.R
import info.meuse24.pdf_scanner.ui.documentaction.DocumentEditViewModel

private data class SignatureSizeOption(
    val scaleFraction: Float,
    val labelRes: Int
)

private val offsetListSaver = listSaver<List<Offset>, Float>(
    save = { offsets -> offsets.flatMap { listOf(it.x, it.y) } },
    restore = { values ->
        values.chunked(2).mapNotNull { pair ->
            if (pair.size == 2) Offset(pair[0], pair[1]) else null
        }
    }
)

private val strokeListSaver = listSaver<List<List<Offset>>, List<Float>>(
    save = { strokes -> strokes.map { stroke -> stroke.flatMap { listOf(it.x, it.y) } } },
    restore = { values ->
        values.map { savedStroke ->
            savedStroke.chunked(2).mapNotNull { pair ->
                if (pair.size == 2) Offset(pair[0], pair[1]) else null
            }
        }
    }
)

@Composable
fun SignatureScreen(
    onNavigateBack: () -> Unit,
    viewModel: DocumentEditViewModel = hiltViewModel()
) {
    val record by viewModel.record.collectAsState()
    val editLoading by viewModel.editLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val success by viewModel.success.collectAsState()

    LaunchedEffect(success) {
        if (success) onNavigateBack()
    }
    val completedStrokesState = rememberSaveable(stateSaver = strokeListSaver) {
        mutableStateOf(emptyList<List<Offset>>())
    }
    val currentStrokeState = rememberSaveable(stateSaver = offsetListSaver) {
        mutableStateOf(emptyList<Offset>())
    }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var selectedPageIndex by rememberSaveable { mutableStateOf(0) }
    var selectedScale by rememberSaveable { mutableStateOf(0.24f) }
    val currentCanvasSize by rememberUpdatedState(canvasSize)

    val completedStrokes = completedStrokesState.value
    val currentStroke = currentStrokeState.value

    if (record == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val currentRecord = record!!
    if (selectedPageIndex >= currentRecord.pageCount) {
        selectedPageIndex = 0
    }
    val sizeOptions = listOf(
        SignatureSizeOption(0.18f, R.string.signature_size_small),
        SignatureSizeOption(0.24f, R.string.signature_size_medium),
        SignatureSizeOption(0.32f, R.string.signature_size_large)
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = stringResource(R.string.signature_description),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
        item {
            Text(
                text = stringResource(R.string.signature_details),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline
            )
        }
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = currentRecord.filename,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.signature_target_page, selectedPageIndex + 1, currentRecord.pageCount),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }
        item {
            SignaturePad(
                completedStrokesState = completedStrokesState,
                currentStrokeState = currentStrokeState,
                canvasSize = currentCanvasSize,
                onSizeChanged = { canvasSize = it }
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = {
                        completedStrokesState.value = emptyList()
                        currentStrokeState.value = emptyList()
                    }
                ) {
                    Text(stringResource(R.string.signature_clear))
                }
                Text(
                    text = stringResource(R.string.signature_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.align(Alignment.CenterVertically)
                )
            }
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = { selectedPageIndex = (selectedPageIndex - 1).coerceAtLeast(0) },
                    enabled = selectedPageIndex > 0
                ) {
                    Text(stringResource(R.string.signature_page_previous))
                }
                Text(
                    text = stringResource(R.string.signature_target_page, selectedPageIndex + 1, currentRecord.pageCount),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                OutlinedButton(
                    onClick = { selectedPageIndex = (selectedPageIndex + 1).coerceAtMost(currentRecord.pageCount - 1) },
                    enabled = selectedPageIndex < currentRecord.pageCount - 1
                ) {
                    Text(stringResource(R.string.signature_page_next))
                }
            }
        }
        item {
            Column {
                Text(
                    text = stringResource(R.string.signature_size_label),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    sizeOptions.forEach { option ->
                        FilterChip(
                            selected = selectedScale == option.scaleFraction,
                            onClick = { selectedScale = option.scaleFraction },
                            label = { Text(stringResource(option.labelRes)) }
                        )
                    }
                }
            }
        }
        item {
            Button(
                onClick = {
                    val activeStroke = currentStroke
                    val signatureBitmap = createSignatureBitmap(
                        strokes = completedStrokes + listOf(activeStroke).filter { it.isNotEmpty() },
                        canvasSize = canvasSize
                    )
                    viewModel.applySignatureStamp(
                        signatureBitmap = signatureBitmap,
                        pageIndex = selectedPageIndex,
                        scaleFraction = selectedScale
                    )
                },
                enabled = (completedStrokes.isNotEmpty() || currentStroke.isNotEmpty()) &&
                    canvasSize != IntSize.Zero &&
                    !editLoading,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.signature_apply))
            }
        }
    }

    if (editLoading) {
        AlertDialog(
            onDismissRequest = {},
            title = null,
            text  = {
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
            title            = { Text(stringResource(R.string.error_title)) },
            text             = { Text(msg) },
            confirmButton    = {
                TextButton(onClick = viewModel::clearError) {
                    Text(stringResource(R.string.action_ok))
                }
            }
        )
    }
}

@Composable
private fun SignaturePad(
    completedStrokesState: androidx.compose.runtime.MutableState<List<List<Offset>>>,
    currentStrokeState: androidx.compose.runtime.MutableState<List<Offset>>,
    canvasSize: IntSize,
    onSizeChanged: (IntSize) -> Unit
) {
    val strokeColor = MaterialTheme.colorScheme.onSurface
    val strokeWidthPx = 8f
    val completedStrokes = completedStrokesState.value
    val currentStroke = currentStrokeState.value
    val allStrokes = completedStrokes + listOf(currentStroke.toList()).filter { it.isNotEmpty() }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
            .background(MaterialTheme.colorScheme.surface)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(16.dp)
            )
            .onSizeChanged(onSizeChanged)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        currentStrokeState.value = listOf(normalizePoint(offset, canvasSize))
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        currentStrokeState.value =
                            currentStrokeState.value + normalizePoint(change.position, canvasSize)
                    },
                    onDragEnd = {
                        if (currentStrokeState.value.isNotEmpty()) {
                            completedStrokesState.value = completedStrokesState.value + listOf(currentStrokeState.value)
                            currentStrokeState.value = emptyList()
                        }
                    },
                    onDragCancel = {
                        if (currentStrokeState.value.isNotEmpty()) {
                            completedStrokesState.value = completedStrokesState.value + listOf(currentStrokeState.value)
                            currentStrokeState.value = emptyList()
                        }
                    }
                )
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            allStrokes.forEach { stroke ->
                val path = Path()
                if (stroke.isNotEmpty()) {
                    val firstPoint = denormalizePoint(stroke.first(), size.width, size.height)
                    path.moveTo(firstPoint.x, firstPoint.y)
                    stroke.drop(1).forEach { point ->
                        val denormalized = denormalizePoint(point, size.width, size.height)
                        path.lineTo(denormalized.x, denormalized.y)
                    }
                    drawPath(
                        path = path,
                        color = strokeColor,
                        style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
                    )
                    if (stroke.size == 1) {
                        drawCircle(
                            color = strokeColor,
                            radius = strokeWidthPx / 2f,
                            center = denormalizePoint(stroke.first(), size.width, size.height)
                        )
                    }
                }
            }
        }
    }
}

private fun createSignatureBitmap(
    strokes: List<List<Offset>>,
    canvasSize: IntSize
): Bitmap? {
    if (strokes.isEmpty() || canvasSize == IntSize.Zero) return null
    val points = strokes
        .flatten()
        .map { point -> denormalizePoint(point, canvasSize.width.toFloat(), canvasSize.height.toFloat()) }
    if (points.isEmpty()) return null

    val padding = 18f
    val left = (points.minOf { it.x } - padding).coerceAtLeast(0f)
    val top = (points.minOf { it.y } - padding).coerceAtLeast(0f)
    val right = (points.maxOf { it.x } + padding).coerceAtMost(canvasSize.width.toFloat())
    val bottom = (points.maxOf { it.y } + padding).coerceAtMost(canvasSize.height.toFloat())
    val width = (right - left).toInt().coerceAtLeast(1)
    val height = (bottom - top).toInt().coerceAtLeast(1)

    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.BLACK
        style = Paint.Style.STROKE
        strokeWidth = 10f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    strokes.forEach { stroke ->
        if (stroke.isEmpty()) return@forEach
        val denormalizedStroke = stroke.map { point ->
            denormalizePoint(point, canvasSize.width.toFloat(), canvasSize.height.toFloat())
        }
        if (stroke.size == 1) {
            canvas.drawCircle(
                denormalizedStroke.first().x - left,
                denormalizedStroke.first().y - top,
                paint.strokeWidth / 2f,
                paint
            )
            return@forEach
        }
        val path = android.graphics.Path().apply {
            moveTo(denormalizedStroke.first().x - left, denormalizedStroke.first().y - top)
            denormalizedStroke.drop(1).forEach { point ->
                lineTo(point.x - left, point.y - top)
            }
        }
        canvas.drawPath(path, paint)
    }
    return bitmap
}

private fun normalizePoint(offset: Offset, canvasSize: IntSize): Offset {
    if (canvasSize == IntSize.Zero) return Offset.Zero
    return Offset(
        x = (offset.x / canvasSize.width.toFloat()).coerceIn(0f, 1f),
        y = (offset.y / canvasSize.height.toFloat()).coerceIn(0f, 1f)
    )
}

private fun denormalizePoint(offset: Offset, width: Float, height: Float): Offset {
    return Offset(offset.x * width, offset.y * height)
}
