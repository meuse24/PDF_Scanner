package info.meuse24.pdf_scanner.ui.viewer

import android.content.Intent
import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FindInPage
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import info.meuse24.pdf_scanner.R
import info.meuse24.pdf_scanner.ui.components.LocalAppSnackbarHostState
import info.meuse24.pdf_scanner.domain.model.Document
import info.meuse24.pdf_scanner.ui.components.DocumentEditSheet
import info.meuse24.pdf_scanner.ui.components.PrintPageSizeWarningDialog
import info.meuse24.pdf_scanner.ui.components.ScanAction
import info.meuse24.pdf_scanner.ui.shared.clampPanOffset
import info.meuse24.pdf_scanner.util.PdfPrintHelper
import info.meuse24.pdf_scanner.util.buildPdfShareIntent
import info.meuse24.pdf_scanner.util.openPdfExternally
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.Offset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfViewerScreen(
    onNavigateBack: () -> Unit,
    onNavigateToSplit: (Long) -> Unit,
    onNavigateToReorder: (Long) -> Unit,
    onNavigateToRotate: (Long) -> Unit,
    onNavigateToDeletePages: (Long) -> Unit,
    onNavigateToExtractPages: (Long) -> Unit,
    onNavigateToAppendPages: (Long) -> Unit,
    onNavigateToDuplicatePages: (Long) -> Unit,
    onNavigateToPageNumbers: (Long) -> Unit,
    onNavigateToTextWatermark: (Long) -> Unit,
    onNavigateToCompressPdf: (Long) -> Unit,
    onNavigateToProtectPdf: (Long) -> Unit,
    onNavigateToUnlockPdf: (Long) -> Unit,
    onNavigateToSignature: (Long) -> Unit,
    onNavigateToRemoveTextLayer: (Long) -> Unit,
    onNavigateToRemovePassword: (Long) -> Unit,
    onNavigateToRestrictUsage: (Long) -> Unit,
    onNavigateToAnnotate: (Long) -> Unit,
    onNavigateToRedact: (Long) -> Unit,
    onNavigateToGrayscale: (Long) -> Unit,
    onNavigateToPdfMetadata: (Long) -> Unit,
    onNavigateToQrScan: (Long) -> Unit,
    onNavigateToBusinessCard: (Long) -> Unit,
    onNavigateToTranslation: (Long) -> Unit,
    viewModel: PdfViewerViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = LocalAppSnackbarHostState.current
    val scope = rememberCoroutineScope()
    val pendingPrintDocument by viewModel.pendingPrintDocument.collectAsStateWithLifecycle()
    val errorNoPdfViewer = stringResource(R.string.error_no_pdf_viewer)
    val shareTitle = stringResource(R.string.share_pdf_title)
    val listState = rememberLazyListState()
    var editSheetVisible by remember { mutableStateOf(false) }
    var inlineViewerSize by rememberSaveable(stateSaver = intSizeSaver) { mutableStateOf(IntSize.Zero) }
    var inlineScale by rememberSaveable(state.record?.filepath, state.record?.fileSize) {
        mutableFloatStateOf(state.zoomScale.coerceIn(1f, PDF_VIEWER_MAX_ZOOM_SCALE))
    }
    var inlineOffsetX by rememberSaveable(state.record?.filepath, state.record?.fileSize) {
        mutableFloatStateOf(0f)
    }
    var inlineOffsetY by rememberSaveable(state.record?.filepath, state.record?.fileSize) {
        mutableFloatStateOf(0f)
    }
    var activePointerCount by remember { mutableIntStateOf(0) }

    fun showMessage(message: String) {
        val hostState = snackbarHostState ?: return
        scope.launch { hostState.showSnackbar(message) }
    }

    fun printRecord(record: Document) {
        PdfPrintHelper.print(
            context = context,
            pdf = File(record.filepath),
            jobName = record.filename,
            pageCount = record.pageCount
        )
    }

    LaunchedEffect(viewModel) {
        viewModel.printRequests.collect(::printRecord)
    }

    if (pendingPrintDocument != null) {
        PrintPageSizeWarningDialog(
            onConfirm = viewModel::confirmPrintWarning,
            onDismiss = viewModel::dismissPrintWarning
        )
    }

    LaunchedEffect(state.transientMessage) {
        val message = state.transientMessage ?: return@LaunchedEffect
        snackbarHostState?.showSnackbar(message)
        viewModel.clearTransientMessage()
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val viewportWidthPx = with(density) { maxWidth.roundToPx() }.coerceAtLeast(64)
        val contentPaddingStartPx = with(density) { 16.dp.toPx() }
        val recordThumbnail = rememberViewerThumbnail(state.record?.thumbnailPath)
        val inlineTransformableState = rememberTransformableState { zoomChange, panChange, _ ->
            val newScale = (inlineScale * zoomChange).coerceIn(1f, PDF_VIEWER_MAX_ZOOM_SCALE)
            // Clamp existing offset to new scale first — scale-out shrinks maxY, which must not
            // produce a spurious unusedY that scrolls the list without a real pan gesture.
            val scaleClamped = clampPanOffset(inlineViewerSize, newScale, inlineOffsetX, inlineOffsetY)
            val rawY = scaleClamped.y + panChange.y
            val finalClamped = clampPanOffset(inlineViewerSize, newScale, scaleClamped.x + panChange.x, rawY)
            val unusedY = rawY - finalClamped.y
            inlineScale = newScale
            inlineOffsetX = finalClamped.x
            inlineOffsetY = finalClamped.y
            if (unusedY != 0f) {
                listState.dispatchRawDelta(-unusedY / newScale)
            }
            viewModel.requestVisibleZoomRender(viewportWidthPx, newScale)
        }

        BackHandler(enabled = inlineScale > 1f) {
            inlineScale = 1f
            inlineOffsetX = 0f
            inlineOffsetY = 0f
            viewModel.setZoomScale(1f)
        }

        LaunchedEffect(viewportWidthPx, state.pageCount) {
            if (state.pageCount > 0 && viewportWidthPx > 0 && inlineScale > 1f) {
                viewModel.requestVisibleZoomRender(viewportWidthPx, inlineScale)
            }
        }

        LaunchedEffect(listState, state.pageCount, viewportWidthPx) {
            snapshotFlow {
                listState.layoutInfo.visibleItemsInfo.map { it.index }
            }.distinctUntilChanged().collect { visible ->
                viewModel.onVisiblePagesChanged(visible, viewportWidthPx)
            }
        }

        when {
            state.loading -> ViewerLoadingState()
            state.errorMessage != null -> ViewerErrorState(
                message = state.errorMessage!!,
                record = state.record,
                onRetry = viewModel::retry,
                onNavigateBack = onNavigateBack,
                onOpenExternal = { record ->
                    if (!openPdfExternally(context, record)) {
                        showMessage(errorNoPdfViewer)
                    }
                }
            )
            state.pageCount == 0 -> ViewerErrorState(
                message = stringResource(R.string.pdf_viewer_file_corrupted),
                record = state.record,
                onRetry = viewModel::retry,
                onNavigateBack = onNavigateBack,
                onOpenExternal = { record ->
                    if (!openPdfExternally(context, record)) {
                        showMessage(errorNoPdfViewer)
                    }
                }
            )
            else -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .onSizeChanged { inlineViewerSize = it }
                        .pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                    activePointerCount = event.changes.count { it.pressed }
                                }
                            }
                        }
                        .transformable(
                            state = inlineTransformableState,
                            canPan = { inlineScale > 1f }
                        )
                ) {
                    LazyColumn(
                        state = listState,
                        userScrollEnabled = inlineScale <= 1f && activePointerCount < 2,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = inlineScale
                                scaleY = inlineScale
                                translationX = inlineOffsetX
                                translationY = inlineOffsetY
                                clip = true
                            },
                        contentPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 96.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        items(
                            count = state.pageCount,
                            key = { pageIndex -> "${state.record?.filepath}_${state.record?.fileSize}_$pageIndex" }
                        ) { pageIndex ->
                            PdfPageCard(
                                pageIndex = pageIndex,
                                pageState = state.pages[pageIndex],
                                thumbnail = if (pageIndex == 0) recordThumbnail else null,
                                onDoubleClick = { tapOffset ->
                                    if (inlineScale > 1f) {
                                        inlineScale = 1f
                                        inlineOffsetX = 0f
                                        inlineOffsetY = 0f
                                        viewModel.setZoomScale(1f)
                                    } else {
                                        val newScale = 2f
                                        val itemOffset = listState.layoutInfo.visibleItemsInfo
                                            .firstOrNull { it.index == pageIndex }?.offset?.toFloat() ?: 0f
                                        val tapX = contentPaddingStartPx + tapOffset.x
                                        val tapY = itemOffset + tapOffset.y
                                        val viewW = inlineViewerSize.width.toFloat()
                                        val viewH = inlineViewerSize.height.toFloat()
                                        val rawOffsetX = (viewW / 2f - tapX) * newScale
                                        val rawOffsetY = (viewH / 2f - tapY) * newScale
                                        val clamped = clampPanOffset(inlineViewerSize, newScale, rawOffsetX, rawOffsetY)
                                        inlineScale = newScale
                                        inlineOffsetX = clamped.x
                                        inlineOffsetY = clamped.y
                                        viewModel.requestVisibleZoomRender(viewportWidthPx, newScale)
                                    }
                                }
                            )
                        }
                    }
                }

                PageIndicator(
                    currentPage = state.currentPageIndex + 1,
                    pageCount = state.pageCount,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 10.dp)
                )

                state.record?.let { record ->
                    ViewerActionBar(
                        record = record,
                        onEdit = { editSheetVisible = true },
                        onShare = {
                            val shareIntent = buildPdfShareIntent(context, listOf(record))
                            if (shareIntent != null) {
                                context.startActivity(Intent.createChooser(shareIntent, shareTitle))
                            }
                        },
                        onExport = viewModel::exportCurrentPdf,
                        onPrint = { viewModel.requestPrint(record) },
                        onOpenExternal = {
                            if (!openPdfExternally(context, record)) {
                                showMessage(errorNoPdfViewer)
                            }
                        },
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )
                }
            }
        }
    }

    if (editSheetVisible) {
        state.record?.let { record ->
            ModalBottomSheet(
                onDismissRequest = { editSheetVisible = false },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ) {
                DocumentEditSheet(
                    record = record,
                    showRenameAction = false,
                    showPrintAction = false,
                    showExportAsJpgAction = false,
                    showTextExportActions = false,
                    onAction = { action ->
                        editSheetVisible = false
                        when (action) {
                            ScanAction.Split -> onNavigateToSplit(record.id)
                            ScanAction.Reorder -> onNavigateToReorder(record.id)
                            ScanAction.Rotate -> onNavigateToRotate(record.id)
                            ScanAction.DeletePages -> onNavigateToDeletePages(record.id)
                            ScanAction.ExtractPages -> onNavigateToExtractPages(record.id)
                            ScanAction.AppendPages -> onNavigateToAppendPages(record.id)
                            ScanAction.DuplicatePages -> onNavigateToDuplicatePages(record.id)
                            ScanAction.PageNumbers -> onNavigateToPageNumbers(record.id)
                            ScanAction.TextWatermark -> onNavigateToTextWatermark(record.id)
                            ScanAction.CompressPdf -> onNavigateToCompressPdf(record.id)
                            ScanAction.ProtectPdf -> onNavigateToProtectPdf(record.id)
                            ScanAction.UnlockPdf -> onNavigateToUnlockPdf(record.id)
                            ScanAction.Signature -> onNavigateToSignature(record.id)
                            ScanAction.RemoveTextLayer -> onNavigateToRemoveTextLayer(record.id)
                            ScanAction.RemovePassword -> onNavigateToRemovePassword(record.id)
                            ScanAction.RestrictUsage -> onNavigateToRestrictUsage(record.id)
                            ScanAction.Annotate -> onNavigateToAnnotate(record.id)
                            ScanAction.Redact -> onNavigateToRedact(record.id)
                            ScanAction.Grayscale -> onNavigateToGrayscale(record.id)
                            ScanAction.PdfMetadata -> onNavigateToPdfMetadata(record.id)
                            ScanAction.ScanQrCodes -> onNavigateToQrScan(record.id)
                            ScanAction.ScanBusinessCard -> onNavigateToBusinessCard(record.id)
                            ScanAction.TranslateText -> onNavigateToTranslation(record.id)
                            ScanAction.ExportAsJpg,
                            ScanAction.ExportDocx,
                            ScanAction.ExportOcrText,
                            ScanAction.Print,
                            ScanAction.Rename -> Unit
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun ViewerLoadingState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.pdf_viewer_loading),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ViewerErrorState(
    message: String,
    record: Document?,
    onRetry: () -> Unit,
    onNavigateBack: () -> Unit,
    onOpenExternal: (Document) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.FindInPage,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(56.dp)
        )
        Spacer(Modifier.height(18.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton(onClick = onNavigateBack) {
                Text(stringResource(R.string.pdf_viewer_back_to_archive))
            }
            Button(onClick = onRetry) {
                Text(stringResource(R.string.pdf_viewer_retry))
            }
        }
        if (record != null) {
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = { onOpenExternal(record) }) {
                Text(stringResource(R.string.action_open_external))
            }
        }
    }
}

@Composable
private fun rememberViewerThumbnail(thumbnailPath: String?): ImageBitmap? {
    val thumbnail by produceState<ImageBitmap?>(initialValue = null, key1 = thumbnailPath) {
        value = withContext(Dispatchers.IO) {
            val path = thumbnailPath ?: return@withContext null
            val file = File(path)
            if (!file.exists()) return@withContext null
            BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap()
        }
    }
    return thumbnail
}

private val intSizeSaver = listSaver<IntSize, Int>(
    save = { listOf(it.width, it.height) },
    restore = { values ->
        IntSize(
            width = values.getOrNull(0) ?: 0,
            height = values.getOrNull(1) ?: 0
        )
    }
)

@Composable
private fun PdfPageCard(
    pageIndex: Int,
    pageState: PdfViewerPageState?,
    thumbnail: ImageBitmap?,
    onDoubleClick: (Offset) -> Unit = {}
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(pageState?.aspectRatio ?: PDF_VIEWER_DEFAULT_ASPECT_RATIO)
            .clip(RoundedCornerShape(6.dp))
            .pointerInput(onDoubleClick) {
                detectTapGestures(onDoubleTap = { offset -> onDoubleClick(offset) })
            },
        shape = RoundedCornerShape(6.dp),
        color = Color.White,
        shadowElevation = 2.dp
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            val bitmap = pageState?.bitmap
            when {
                bitmap != null -> {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = stringResource(
                            R.string.pdf_viewer_page_content_description,
                            pageIndex + 1
                        ),
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                pageState?.errorMessage != null -> {
                    Text(
                        text = pageState.errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(24.dp)
                    )
                }
                thumbnail != null -> {
                    Image(
                        painter = BitmapPainter(thumbnail),
                        contentDescription = stringResource(R.string.cd_pdf_document),
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                else -> {
                    CircularProgressIndicator()
                }
            }
        }
    }
}

@Composable
private fun PageIndicator(
    currentPage: Int,
    pageCount: Int,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.92f),
        shadowElevation = 1.dp
    ) {
        Text(
            text = stringResource(R.string.pdf_viewer_page_indicator, currentPage, pageCount),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun ViewerActionBar(
    record: Document,
    onEdit: () -> Unit,
    onShare: () -> Unit,
    onExport: () -> Unit,
    onPrint: () -> Unit,
    onOpenExternal: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.96f),
        shadowElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ViewerActionButton(Icons.Default.Edit, R.string.action_edit_pdf, onEdit)
            ViewerActionButton(Icons.Default.Share, R.string.cd_share, onShare)
            ViewerActionButton(Icons.Default.Download, R.string.action_export, onExport)
            ViewerActionButton(Icons.Default.Print, R.string.action_print_pdf, onPrint, enabled = !record.isEncrypted)
            ViewerActionButton(Icons.AutoMirrored.Filled.OpenInNew, R.string.action_open_external, onOpenExternal)
        }
    }
}

@Composable
private fun ViewerActionButton(
    icon: ImageVector,
    labelRes: Int,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    FilledTonalIconButton(onClick = onClick, enabled = enabled) {
        Icon(
            imageVector = icon,
            contentDescription = stringResource(labelRes)
        )
    }
}

