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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import info.meuse24.pdf_scanner.ui.home.HomeViewModel
import info.meuse24.pdf_scanner.ui.overlay.OverlayActionViewModel

private data class SignatureSizeOption(
    val scaleFraction: Float,
    val labelRes: Int
)

@Composable
fun SignatureScreen(
    onNavigateBack: () -> Unit,
    viewModel: OverlayActionViewModel = hiltViewModel(),
    homeViewModel: HomeViewModel = hiltViewModel()
) {
    val record by viewModel.record.collectAsState()
    val editLoading by homeViewModel.editLoading.collectAsState()
    val completedStrokes = remember { mutableStateListOf<List<Offset>>() }
    val currentStroke = remember { mutableStateListOf<Offset>() }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var selectedPageIndex by remember { mutableStateOf(0) }
    var selectedScale by remember { mutableStateOf(0.24f) }

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
                completedStrokes = completedStrokes,
                currentStroke = currentStroke,
                onSizeChanged = { canvasSize = it }
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = {
                        completedStrokes.clear()
                        currentStroke.clear()
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
                    val activeStroke = currentStroke.toList()
                    val signatureBitmap = createSignatureBitmap(
                        strokes = completedStrokes + listOf(activeStroke).filter { it.isNotEmpty() },
                        canvasSize = canvasSize
                    )
                    homeViewModel.applySignatureStamp(
                        record = currentRecord,
                        signatureBitmap = signatureBitmap,
                        pageIndex = selectedPageIndex,
                        scaleFraction = selectedScale
                    )
                    onNavigateBack()
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
}

@Composable
private fun SignaturePad(
    completedStrokes: MutableList<List<Offset>>,
    currentStroke: MutableList<Offset>,
    onSizeChanged: (IntSize) -> Unit
) {
    val strokeColor = MaterialTheme.colorScheme.onSurface
    val strokeWidthPx = 8f
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
            .pointerInput(completedStrokes.size) {
                detectDragGestures(
                    onDragStart = { offset ->
                        currentStroke.clear()
                        currentStroke.add(offset)
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        currentStroke.add(change.position)
                    },
                    onDragEnd = {
                        if (currentStroke.isNotEmpty()) {
                            completedStrokes.add(currentStroke.toList())
                            currentStroke.clear()
                        }
                    },
                    onDragCancel = {
                        if (currentStroke.isNotEmpty()) {
                            completedStrokes.add(currentStroke.toList())
                            currentStroke.clear()
                        }
                    }
                )
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            allStrokes.forEach { stroke ->
                val path = Path()
                if (stroke.isNotEmpty()) {
                    path.moveTo(stroke.first().x, stroke.first().y)
                    stroke.drop(1).forEach { point -> path.lineTo(point.x, point.y) }
                    drawPath(
                        path = path,
                        color = strokeColor,
                        style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
                    )
                    if (stroke.size == 1) {
                        drawCircle(
                            color = strokeColor,
                            radius = strokeWidthPx / 2f,
                            center = stroke.first()
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
    val points = strokes.flatten()
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
        if (stroke.size == 1) {
            canvas.drawCircle(
                stroke.first().x - left,
                stroke.first().y - top,
                paint.strokeWidth / 2f,
                paint
            )
            return@forEach
        }
        val path = android.graphics.Path().apply {
            moveTo(stroke.first().x - left, stroke.first().y - top)
            stroke.drop(1).forEach { point ->
                lineTo(point.x - left, point.y - top)
            }
        }
        canvas.drawPath(path, paint)
    }
    return bitmap
}
