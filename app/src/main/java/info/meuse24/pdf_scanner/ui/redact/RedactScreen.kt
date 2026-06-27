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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import info.meuse24.pdf_scanner.R
import info.meuse24.pdf_scanner.domain.usecase.RedactionRect
import info.meuse24.pdf_scanner.ui.documentaction.DocumentEditViewModel
import info.meuse24.pdf_scanner.ui.ocr.buildOcrLanguageOptions
import info.meuse24.pdf_scanner.ui.settings.SettingsViewModel
import info.meuse24.pdf_scanner.ui.shared.clampPanOffset
import info.meuse24.pdf_scanner.ui.shared.formatZoomScale
import info.meuse24.pdf_scanner.ui.shared.normalizeViewportPoint
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RedactScreen(
    onNavigateBack: () -> Unit,
    viewModel: DocumentEditViewModel = hiltViewModel()
) {
    val settingsViewModel: SettingsViewModel = hiltViewModel()
    val settings by settingsViewModel.settings.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val pagePreviewUiState by viewModel.pagePreviewUiState.collectAsStateWithLifecycle()
    val record = uiState.record
    val editLoading = uiState.editLoading
    val ocrStatusText = uiState.ocrStatusText
    val error = uiState.error
    val success = uiState.success
    val pageBitmap = pagePreviewUiState.pageBitmap
    val resources = LocalResources.current
    val displayLocale = resources.configuration.locales[0] ?: Locale.getDefault()
    val ocrAutoLabel = stringResource(R.string.ocr_language_auto)
    val ocrLanguages = remember(displayLocale, ocrAutoLabel) { buildOcrLanguageOptions(ocrAutoLabel, displayLocale) }

    LaunchedEffect(success) {
        if (success) onNavigateBack()
    }

    var allRects by rememberSaveable(stateSaver = redactionRectListSaver) {
        mutableStateOf(emptyList<RedactionRect>())
    }
    var currentRect by remember { mutableStateOf<RedactionRect?>(null) }
    var dragStartPoint by remember { mutableStateOf<Pair<Float, Float>?>(null) }
    var selectedPageIndex by rememberSaveable { mutableIntStateOf(0) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var showInstructionsDialog by rememberSaveable { mutableStateOf(true) }
    var showSaveOptionsDialog by rememberSaveable { mutableStateOf(false) }
    var isZoomMode by rememberSaveable { mutableStateOf(false) }
    var makeSearchable by rememberSaveable { mutableStateOf(settings.defaultMakeSearchable) }
    var selectedLanguage by rememberSaveable { mutableStateOf(settings.defaultOcrLanguage) }
    var languageMenuExpanded by remember { mutableStateOf(false) }
    val currentCanvasSize by rememberUpdatedState(canvasSize)

    var zoomScale by rememberSaveable { mutableFloatStateOf(1f) }
    var zoomOffsetX by rememberSaveable { mutableFloatStateOf(0f) }
    var zoomOffsetY by rememberSaveable { mutableFloatStateOf(0f) }
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
        viewModel.loadDocumentPage(selectedPageIndex)
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
    val canvasDescription = buildString {
        append(stringResource(R.string.signature_target_page, selectedPageIndex + 1, currentRecord.pageCount))
        append(". ")
        append(stringResource(R.string.redact_hint))
    }
    val undoActionLabel = stringResource(R.string.highlight_undo_last)
    val clearPageActionLabel = stringResource(R.string.highlight_clear_page)
    val clearAllActionLabel = stringResource(R.string.highlight_reset_all)
    val canvasAccessibilityActions = buildList {
        if (pageRectsForDisplay.isNotEmpty()) {
            add(
                CustomAccessibilityAction(undoActionLabel) {
                    undoLastRect()
                    true
                }
            )
            add(
                CustomAccessibilityAction(clearPageActionLabel) {
                    clearCurrentPage()
                    true
                }
            )
        }
        if (hasAnyRects) {
            add(
                CustomAccessibilityAction(clearAllActionLabel) {
                    resetAllRects()
                    true
                }
            )
        }
    }

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
        RedactModeBar(
            isZoomMode = isZoomMode,
            hasMultiplePages = hasMultiplePages,
            selectedPageIndex = selectedPageIndex,
            pageCount = currentRecord.pageCount,
            zoomScaleLabel = formatZoomScale(zoomScale),
            onModeChange = { isZoomMode = it },
            modifier = Modifier.fillMaxWidth().zIndex(1f)
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .onSizeChanged { canvasSize = it }
                .semantics {
                    contentDescription = canvasDescription
                    customActions = canvasAccessibilityActions
                }
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

        RedactFooter(
            isZoomMode = isZoomMode,
            hasMultiplePages = hasMultiplePages,
            selectedPageIndex = selectedPageIndex,
            pageCount = currentRecord.pageCount,
            hasPageRects = hasPageRects,
            hasAnyRects = hasAnyRects,
            editLoading = editLoading,
            onPreviousPage = { selectedPageIndex = (selectedPageIndex - 1).coerceAtLeast(0) },
            onNextPage = {
                selectedPageIndex = (selectedPageIndex + 1).coerceAtMost(currentRecord.pageCount - 1)
            },
            onResetZoom = resetZoom,
            onUndoLast = undoLastRect,
            onClearPage = clearCurrentPage,
            onClearAll = resetAllRects,
            onApply = { showSaveOptionsDialog = true }
        )
    }

    if (showInstructionsDialog) {
        RedactInstructionsDialog(onDismiss = { showInstructionsDialog = false })
    }

    if (showSaveOptionsDialog) {
        RedactSaveOptionsDialog(
            makeSearchable = makeSearchable,
            selectedLanguage = selectedLanguage,
            displayLanguageFallback = selectedLanguage.uppercase(displayLocale),
            languageMenuExpanded = languageMenuExpanded,
            ocrLanguages = ocrLanguages,
            onMakeSearchableChange = { makeSearchable = it },
            onLanguageMenuExpandedChange = { languageMenuExpanded = it },
            onLanguageSelected = {
                selectedLanguage = it
                languageMenuExpanded = false
            },
            onConfirm = {
                showSaveOptionsDialog = false
                viewModel.applyRedactions(
                    rects = allRects,
                    makeSearchable = makeSearchable,
                    languageCode = selectedLanguage
                )
            },
            onDismiss = { showSaveOptionsDialog = false }
        )
    }

    if (editLoading) {
        RedactProgressDialog(statusText = ocrStatusText)
    }

    error?.let { message ->
        RedactErrorDialog(message = message, onDismiss = viewModel::clearError)
    }
}
