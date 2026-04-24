package info.meuse24.pdf_scanner.ui.viewer

import android.content.Intent
import android.graphics.BitmapFactory
import android.print.PrintManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
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
import info.meuse24.pdf_scanner.domain.model.Document
import info.meuse24.pdf_scanner.ui.components.DocumentEditSheet
import info.meuse24.pdf_scanner.ui.components.ScanAction
import info.meuse24.pdf_scanner.ui.shared.clampPanOffset
import info.meuse24.pdf_scanner.util.PdfPrintHelper
import info.meuse24.pdf_scanner.util.buildPdfShareIntent
import info.meuse24.pdf_scanner.util.openPdfExternally
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
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
    viewModel: PdfViewerViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val errorNoPdfViewer = stringResource(R.string.error_no_pdf_viewer)
    val shareTitle = stringResource(R.string.share_pdf_title)
    val listState = rememberLazyListState()
    var editSheetVisible by remember { mutableStateOf(false) }
    var zoomPageIndex by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(state.transientMessage) {
        val message = state.transientMessage ?: return@LaunchedEffect
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        viewModel.clearTransientMessage()
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val viewportWidthPx = with(density) { maxWidth.roundToPx() }.coerceAtLeast(64)
        val recordThumbnail = rememberViewerThumbnail(state.record?.thumbnailPath)

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
                        Toast.makeText(context, errorNoPdfViewer, Toast.LENGTH_SHORT).show()
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
                        Toast.makeText(context, errorNoPdfViewer, Toast.LENGTH_SHORT).show()
                    }
                }
            )
            else -> {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
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
                            onClick = { zoomPageIndex = pageIndex }
                        )
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
                        onPrint = {
                            PdfPrintHelper.print(
                                context = context,
                                pdf = java.io.File(record.filepath),
                                jobName = record.filename,
                                pageCount = record.pageCount
                            )
                        },
                        onOpenExternal = {
                            if (!openPdfExternally(context, record)) {
                                Toast.makeText(context, errorNoPdfViewer, Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )
                }
            }
        }

        val zoomIndex = zoomPageIndex
        if (zoomIndex != null) {
            PdfZoomOverlay(
                pageIndex = zoomIndex,
                pageCount = state.pageCount,
                pageState = state.pages[zoomIndex],
                viewportWidthPx = viewportWidthPx,
                onClose = { zoomPageIndex = null },
                onPrefetchZoom = {
                    viewModel.prefetchZoomRender(zoomIndex, viewportWidthPx)
                },
                onZoomScaleChanged = { scale ->
                    viewModel.requestZoomRender(zoomIndex, viewportWidthPx, scale)
                }
            )
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
                            ScanAction.ExportAsJpg,
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

@Composable
private fun PdfPageCard(
    pageIndex: Int,
    pageState: PdfViewerPageState?,
    thumbnail: ImageBitmap?,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(pageState?.aspectRatio ?: PDF_VIEWER_DEFAULT_ASPECT_RATIO)
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick),
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

@Composable
private fun PdfZoomOverlay(
    pageIndex: Int,
    pageCount: Int,
    pageState: PdfViewerPageState?,
    viewportWidthPx: Int,
    onClose: () -> Unit,
    onPrefetchZoom: () -> Unit,
    onZoomScaleChanged: (Float) -> Unit
) {
    BackHandler(onBack = onClose)

    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    var scale by remember(pageIndex) { mutableFloatStateOf(1f) }
    var offsetX by remember(pageIndex) { mutableFloatStateOf(0f) }
    var offsetY by remember(pageIndex) { mutableFloatStateOf(0f) }
    val transformableState = rememberTransformableState { zoomChange, panChange, _ ->
        val newScale = (scale * zoomChange).coerceIn(1f, 5f)
        val clamped = clampPanOffset(
            canvasSize = containerSize,
            scale = newScale,
            offsetX = offsetX + panChange.x,
            offsetY = offsetY + panChange.y
        )
        scale = newScale
        offsetX = clamped.x
        offsetY = clamped.y
        onZoomScaleChanged(newScale)
    }

    LaunchedEffect(pageIndex, viewportWidthPx) {
        if (viewportWidthPx > 0) onPrefetchZoom()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { containerSize = it }
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.82f))
            .padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        val bitmap = pageState?.bitmap
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = stringResource(
                    R.string.pdf_viewer_page_content_description,
                    pageIndex + 1
                ),
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .transformable(transformableState)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offsetX
                        translationY = offsetY
                    }
                    .padding(vertical = 44.dp)
            )
        } else {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.inversePrimary)
        }

        Surface(
            modifier = Modifier.align(Alignment.TopCenter),
            shape = RoundedCornerShape(999.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
        ) {
            Text(
                text = stringResource(R.string.pdf_viewer_page_indicator, pageIndex + 1, pageCount),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
            )
        }

        IconButton(
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .background(
                    MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                    RoundedCornerShape(999.dp)
                )
        ) {
            Icon(Icons.Default.Close, stringResource(R.string.pdf_viewer_close_zoom))
        }

        LaunchedEffect(viewportWidthPx) {
            if (viewportWidthPx > 0) onZoomScaleChanged(scale)
        }
    }
}

