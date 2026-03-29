package info.meuse24.pdf_scanner.ui.redact

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import info.meuse24.pdf_scanner.R
import info.meuse24.pdf_scanner.domain.usecase.RedactionRect
import info.meuse24.pdf_scanner.ui.documentaction.DocumentEditViewModel
import info.meuse24.pdf_scanner.ui.highlight.clampPanOffset
import info.meuse24.pdf_scanner.ui.highlight.formatZoomScale
import info.meuse24.pdf_scanner.ui.highlight.normalizeViewportPoint
import info.meuse24.pdf_scanner.ui.ocr.buildOcrLanguageOptions
import info.meuse24.pdf_scanner.ui.ocr.defaultOcrLanguage
import java.util.Locale

private const val MIN_REDACTION_SIDE = 0.003f

private val redactionRectListSaver = listSaver<List<RedactionRect>, List<Float>>(
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
            RedactionRect(
                pageIndex = savedRect[0].toInt(),
                left = savedRect[1],
                top = savedRect[2],
                right = savedRect[3],
                bottom = savedRect[4]
            )
        }
    }
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RedactScreen(
    onNavigateBack: () -> Unit,
    viewModel: DocumentEditViewModel = hiltViewModel()
) {
    val record by viewModel.record.collectAsStateWithLifecycle()
    val editLoading by viewModel.editLoading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val success by viewModel.success.collectAsStateWithLifecycle()
    val pageBitmap by viewModel.highlightPageBitmap.collectAsStateWithLifecycle()
    val resources = LocalResources.current
    val displayLocale = resources.configuration.locales[0] ?: Locale.getDefault()
    val ocrLanguages = remember(displayLocale) { buildOcrLanguageOptions(displayLocale) }

    LaunchedEffect(success) {
        if (success) onNavigateBack()
    }

    var allRects by rememberSaveable(stateSaver = redactionRectListSaver) {
        mutableStateOf(emptyList<RedactionRect>())
    }
    var currentRect by remember { mutableStateOf<RedactionRect?>(null) }
    var dragStartPoint by remember { mutableStateOf<Pair<Float, Float>?>(null) }
    var selectedPageIndex by rememberSaveable { mutableStateOf(0) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var showInstructionsDialog by rememberSaveable { mutableStateOf(true) }
    var showSaveOptionsDialog by rememberSaveable { mutableStateOf(false) }
    var isZoomMode by rememberSaveable { mutableStateOf(false) }
    var makeSearchable by rememberSaveable { mutableStateOf(false) }
    var selectedLanguage by rememberSaveable { mutableStateOf(defaultOcrLanguage()) }
    var languageMenuExpanded by remember { mutableStateOf(false) }
    val currentCanvasSize by rememberUpdatedState(canvasSize)

    var zoomScale by rememberSaveable { mutableStateOf(1f) }
    var zoomOffsetX by rememberSaveable { mutableStateOf(0f) }
    var zoomOffsetY by rememberSaveable { mutableStateOf(0f) }
    val currentZoomScale by rememberUpdatedState(zoomScale)
    val currentZoomOffsetX by rememberUpdatedState(zoomOffsetX)
    val currentZoomOffsetY by rememberUpdatedState(zoomOffsetY)
    val currentSelectedPageIndex by rememberUpdatedState(selectedPageIndex)

    val resetZoom = {
        zoomScale = 1f
        zoomOffsetX = 0f
        zoomOffsetY = 0f
    }
    val clearCurrentPage = {
        allRects = allRects.filter { it.pageIndex != selectedPageIndex }
        currentRect = null
        dragStartPoint = null
    }
    val resetAllRects = {
        allRects = emptyList()
        currentRect = null
        dragStartPoint = null
    }
    val undoLastRect = {
        currentRect = null
        dragStartPoint = null
        val lastIndex = allRects.indexOfLast { it.pageIndex == selectedPageIndex }
        if (lastIndex >= 0) {
            allRects = allRects.toMutableList().apply { removeAt(lastIndex) }
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
        currentRect = null
        dragStartPoint = null
        resetZoom()
    }

    val bitmap = pageBitmap
    val aspectRatio = bitmap?.let { it.width.toFloat() / it.height.toFloat() } ?: (210f / 297f)
    val pageRectsForDisplay = allRects.filter { it.pageIndex == selectedPageIndex }
    val draftRect = currentRect
    val hasPageRects = pageRectsForDisplay.isNotEmpty() || draftRect != null
    val hasAnyRects = allRects.isNotEmpty()
    val hasMultiplePages = currentRecord.pageCount > 1

    val (pdfInCanvasOrigin, pdfInCanvasSize) = remember(canvasSize, aspectRatio) {
        val canvasWidth = canvasSize.width.toFloat()
        val canvasHeight = canvasSize.height.toFloat()
        if (canvasWidth <= 0f || canvasHeight <= 0f) return@remember Offset.Zero to Size.Zero
        val safeAspectRatio = aspectRatio.coerceAtLeast(0.01f)
        if (safeAspectRatio >= canvasWidth / canvasHeight) {
            val height = canvasWidth / safeAspectRatio
            Offset(0f, (canvasHeight - height) / 2f) to Size(canvasWidth, height)
        } else {
            val width = canvasHeight * safeAspectRatio
            Offset((canvasWidth - width) / 2f, 0f) to Size(width, canvasHeight)
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
        Surface(
            modifier = Modifier.fillMaxWidth().zIndex(1f),
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
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = !isZoomMode,
                        onClick = { isZoomMode = false },
                        label = { Text(stringResource(R.string.redact_mode_rect)) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.CropSquare,
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

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .onSizeChanged { canvasSize = it }
                .transformable(state = transformableState, enabled = isZoomMode)
                .pointerInput(isZoomMode) {
                    if (!isZoomMode) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                val point = normalizeViewportPoint(
                                    offset = offset,
                                    canvasSize = currentCanvasSize,
                                    scale = currentZoomScale,
                                    offsetX = currentZoomOffsetX,
                                    offsetY = currentZoomOffsetY,
                                    pdfOrigin = currentPdfOrigin,
                                    pdfDisplaySize = currentPdfSize
                                )
                                dragStartPoint = point
                                currentRect = createRedactionRect(point, point, currentSelectedPageIndex)
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                val startPoint = dragStartPoint ?: return@detectDragGestures
                                val currentPoint = normalizeViewportPoint(
                                    offset = change.position,
                                    canvasSize = currentCanvasSize,
                                    scale = currentZoomScale,
                                    offsetX = currentZoomOffsetX,
                                    offsetY = currentZoomOffsetY,
                                    pdfOrigin = currentPdfOrigin,
                                    pdfDisplaySize = currentPdfSize
                                )
                                currentRect = createRedactionRect(
                                    start = startPoint,
                                    end = currentPoint,
                                    pageIndex = currentSelectedPageIndex
                                )
                            },
                            onDragEnd = {
                                val finalized = currentRect
                                if (finalized != null && hasMinimumArea(finalized)) {
                                    allRects = allRects + finalized
                                }
                                currentRect = null
                                dragStartPoint = null
                            },
                            onDragCancel = {
                                currentRect = null
                                dragStartPoint = null
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
                        clip = true
                    }
            ) {
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }

                Canvas(modifier = Modifier.fillMaxSize()) {
                    val pdfX = currentPdfOrigin.x
                    val pdfY = currentPdfOrigin.y
                    val pdfWidth = currentPdfSize.width.coerceAtLeast(1f)
                    val pdfHeight = currentPdfSize.height.coerceAtLeast(1f)
                    val allRectsForPage = pageRectsForDisplay + listOfNotNull(draftRect)

                    allRectsForPage.forEach { rect ->
                        drawRect(
                            color = if (rect == draftRect) DraftRedactionColor else AppliedRedactionColor,
                            topLeft = Offset(
                                x = pdfX + rect.left * pdfWidth,
                                y = pdfY + rect.top * pdfHeight
                            ),
                            size = Size(
                                width = (rect.right - rect.left) * pdfWidth,
                                height = (rect.bottom - rect.top) * pdfHeight
                            )
                        )
                    }
                }
            }
        }

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
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedButton(
                            onClick = { selectedPageIndex = (selectedPageIndex - 1).coerceAtLeast(0) },
                            enabled = selectedPageIndex > 0,
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                        ) {
                            Text(stringResource(R.string.signature_page_previous), maxLines = 1)
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
                            Text(stringResource(R.string.signature_page_next), maxLines = 1)
                        }
                    }
                }

                if (isZoomMode) {
                    OutlinedButton(
                        onClick = resetZoom,
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                    ) {
                        Text(stringResource(R.string.highlight_zoom_reset_button))
                    }
                } else {
                    Text(
                        text = stringResource(R.string.redact_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedButton(
                            onClick = undoLastRect,
                            enabled = hasPageRects,
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
                            enabled = hasPageRects,
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
                            onClick = resetAllRects,
                            enabled = hasAnyRects,
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
                }
            }
        }

        Button(
            onClick = { showSaveOptionsDialog = true },
            enabled = hasAnyRects && !editLoading,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.redact_apply))
        }
    }

    if (showInstructionsDialog) {
        AlertDialog(
            onDismissRequest = { showInstructionsDialog = false },
            title = { Text(stringResource(R.string.redact_description)) },
            text = { Text(stringResource(R.string.redact_details)) },
            confirmButton = {
                TextButton(onClick = { showInstructionsDialog = false }) {
                    Text(stringResource(R.string.action_ok))
                }
            }
        )
    }

    if (showSaveOptionsDialog) {
        AlertDialog(
            onDismissRequest = { showSaveOptionsDialog = false },
            title = { Text(stringResource(R.string.redact_description)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Text(
                            text = stringResource(R.string.redact_copy_warning),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.dialog_searchable_pdf),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = makeSearchable,
                            onCheckedChange = { makeSearchable = it }
                        )
                    }
                    if (makeSearchable) {
                        Text(
                            text = stringResource(R.string.dialog_searchable_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        ExposedDropdownMenuBox(
                            expanded = languageMenuExpanded,
                            onExpandedChange = { languageMenuExpanded = it }
                        ) {
                            OutlinedTextField(
                                value = ocrLanguages.find { it.first == selectedLanguage }?.second
                                    ?: selectedLanguage.uppercase(displayLocale),
                                onValueChange = {},
                                readOnly = true,
                                label = { Text(stringResource(R.string.dialog_ocr_language)) },
                                trailingIcon = {
                                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = languageMenuExpanded)
                                },
                                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                                modifier = Modifier
                                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                                    .fillMaxWidth()
                            )
                            DropdownMenu(
                                expanded = languageMenuExpanded,
                                onDismissRequest = { languageMenuExpanded = false }
                            ) {
                                ocrLanguages.forEach { (code, name) ->
                                    DropdownMenuItem(
                                        text = { Text(name) },
                                        onClick = {
                                            selectedLanguage = code
                                            languageMenuExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showSaveOptionsDialog = false
                        viewModel.applyRedactions(
                            rects = allRects,
                            makeSearchable = makeSearchable,
                            languageCode = selectedLanguage
                        )
                    }
                ) {
                    Text(stringResource(R.string.redact_apply))
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveOptionsDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
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
            onDismissRequest = viewModel::clearError,
            title = { Text(stringResource(R.string.error_title)) },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = viewModel::clearError) {
                    Text(stringResource(R.string.action_ok))
                }
            }
        )
    }
}

private fun createRedactionRect(
    start: Pair<Float, Float>,
    end: Pair<Float, Float>,
    pageIndex: Int
): RedactionRect {
    return RedactionRect(
        left = minOf(start.first, end.first),
        top = minOf(start.second, end.second),
        right = maxOf(start.first, end.first),
        bottom = maxOf(start.second, end.second),
        pageIndex = pageIndex
    )
}

private fun hasMinimumArea(rect: RedactionRect): Boolean {
    return (rect.right - rect.left) >= MIN_REDACTION_SIDE &&
        (rect.bottom - rect.top) >= MIN_REDACTION_SIDE
}

private val AppliedRedactionColor = Color.Black.copy(alpha = 0.82f)
private val DraftRedactionColor = Color.Black.copy(alpha = 0.55f)
