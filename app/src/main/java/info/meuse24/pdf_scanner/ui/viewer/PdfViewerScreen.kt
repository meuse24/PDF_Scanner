package info.meuse24.pdf_scanner.ui.viewer

import android.content.Intent
import android.print.PrintManager
import android.text.format.Formatter
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.BrandingWatermark
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.BorderColor
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FindInPage
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.InvertColors
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.NoEncryption
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import info.meuse24.pdf_scanner.R
import info.meuse24.pdf_scanner.data.local.ScanRecord
import info.meuse24.pdf_scanner.ui.shared.clampPanOffset
import info.meuse24.pdf_scanner.util.PdfPrintAdapter
import info.meuse24.pdf_scanner.util.buildPdfShareIntent
import info.meuse24.pdf_scanner.util.openPdfExternally

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfViewerScreen(
    onNavigateBack: () -> Unit,
    onNavigateToSplit: (Long) -> Unit,
    onNavigateToReorder: (Long) -> Unit,
    onNavigateToRotate: (Long) -> Unit,
    onNavigateToDeletePages: (Long) -> Unit,
    onNavigateToExtractPages: (Long) -> Unit,
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

        LaunchedEffect(listState, state.pageCount, viewportWidthPx) {
            snapshotFlow {
                listState.layoutInfo.visibleItemsInfo.map { it.index }
            }.collect { visible ->
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
                        items = List(state.pageCount) { it },
                        key = { pageIndex -> "${state.record?.filepath}_${state.record?.fileSize}_$pageIndex" }
                    ) { pageIndex ->
                        PdfPageCard(
                            pageIndex = pageIndex,
                            pageState = state.pages[pageIndex],
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
                            context.getSystemService(PrintManager::class.java)?.print(
                                record.filename,
                                PdfPrintAdapter(
                                    file = java.io.File(record.filepath),
                                    jobName = record.filename,
                                    pageCount = record.pageCount
                                ),
                                null
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
                onZoomChanged = { scale ->
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
                ViewerEditSheet(
                    record = record,
                    onActionSelected = { action ->
                        editSheetVisible = false
                        when (action) {
                            ViewerEditAction.Split -> onNavigateToSplit(record.id)
                            ViewerEditAction.Reorder -> onNavigateToReorder(record.id)
                            ViewerEditAction.Rotate -> onNavigateToRotate(record.id)
                            ViewerEditAction.DeletePages -> onNavigateToDeletePages(record.id)
                            ViewerEditAction.ExtractPages -> onNavigateToExtractPages(record.id)
                            ViewerEditAction.DuplicatePages -> onNavigateToDuplicatePages(record.id)
                            ViewerEditAction.PageNumbers -> onNavigateToPageNumbers(record.id)
                            ViewerEditAction.TextWatermark -> onNavigateToTextWatermark(record.id)
                            ViewerEditAction.CompressPdf -> onNavigateToCompressPdf(record.id)
                            ViewerEditAction.ProtectPdf -> onNavigateToProtectPdf(record.id)
                            ViewerEditAction.UnlockPdf -> onNavigateToUnlockPdf(record.id)
                            ViewerEditAction.Signature -> onNavigateToSignature(record.id)
                            ViewerEditAction.RemoveTextLayer -> onNavigateToRemoveTextLayer(record.id)
                            ViewerEditAction.RemovePassword -> onNavigateToRemovePassword(record.id)
                            ViewerEditAction.RestrictUsage -> onNavigateToRestrictUsage(record.id)
                            ViewerEditAction.Annotate -> onNavigateToAnnotate(record.id)
                            ViewerEditAction.Redact -> onNavigateToRedact(record.id)
                            ViewerEditAction.Grayscale -> onNavigateToGrayscale(record.id)
                            ViewerEditAction.PdfMetadata -> onNavigateToPdfMetadata(record.id)
                            ViewerEditAction.ScanQrCodes -> onNavigateToQrScan(record.id)
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
    record: ScanRecord?,
    onRetry: () -> Unit,
    onNavigateBack: () -> Unit,
    onOpenExternal: (ScanRecord) -> Unit
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
private fun PdfPageCard(
    pageIndex: Int,
    pageState: PdfViewerPageState?,
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
    record: ScanRecord,
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
    onZoomChanged: (Float) -> Unit
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
        onZoomChanged(newScale)
    }

    LaunchedEffect(pageIndex) {
        onZoomChanged(2f)
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
            if (viewportWidthPx > 0) onZoomChanged(scale)
        }
    }
}

@Composable
private fun ViewerEditSheet(
    record: ScanRecord,
    onActionSelected: (ViewerEditAction) -> Unit
) {
    val context = LocalContext.current
    val notEncrypted = !record.isEncrypted
    val multiPage = record.pageCount >= 2
    val sizeStr = remember(record.fileSize, context) {
        Formatter.formatShortFileSize(context, record.fileSize.coerceAtLeast(0L))
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(bottom = 16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp)
        ) {
            Text(
                text = record.filename,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = stringResource(R.string.sheet_header_meta, record.pageCount, sizeStr),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
        HorizontalDivider()

        SheetSection(R.string.sheet_section_document)
        SheetItem(Icons.Default.Info, R.string.action_pdf_metadata, true) {
            onActionSelected(ViewerEditAction.PdfMetadata)
        }

        SheetSection(R.string.sheet_section_pages)
        SheetItem(Icons.Default.SwapVert, R.string.action_reorder, notEncrypted && multiPage) {
            onActionSelected(ViewerEditAction.Reorder)
        }
        SheetItem(Icons.AutoMirrored.Filled.RotateRight, R.string.action_rotate, notEncrypted) {
            onActionSelected(ViewerEditAction.Rotate)
        }
        SheetItem(Icons.Default.ContentCut, R.string.action_split, notEncrypted && multiPage) {
            onActionSelected(ViewerEditAction.Split)
        }
        SheetItem(Icons.Default.Image, R.string.action_extract_pages, notEncrypted && multiPage) {
            onActionSelected(ViewerEditAction.ExtractPages)
        }
        SheetItem(Icons.Default.ContentCopy, R.string.action_duplicate_pages, true) {
            onActionSelected(ViewerEditAction.DuplicatePages)
        }
        SheetItem(Icons.Default.Delete, R.string.action_delete_pages, notEncrypted && multiPage) {
            onActionSelected(ViewerEditAction.DeletePages)
        }

        SheetSection(R.string.sheet_section_edit)
        SheetItem(Icons.Default.BorderColor, R.string.action_annotate_pdf, notEncrypted) {
            onActionSelected(ViewerEditAction.Annotate)
        }
        SheetItem(Icons.Default.Draw, R.string.action_sign_pdf, notEncrypted) {
            onActionSelected(ViewerEditAction.Signature)
        }
        SheetItem(Icons.Default.FormatListNumbered, R.string.action_page_numbers, notEncrypted) {
            onActionSelected(ViewerEditAction.PageNumbers)
        }
        SheetItem(Icons.AutoMirrored.Filled.BrandingWatermark, R.string.action_text_watermark, notEncrypted) {
            onActionSelected(ViewerEditAction.TextWatermark)
        }
        SheetItem(Icons.Default.Block, R.string.action_redact_pdf, notEncrypted) {
            onActionSelected(ViewerEditAction.Redact)
        }

        SheetSection(R.string.sheet_section_analyse)
        SheetItem(Icons.Default.QrCodeScanner, R.string.action_scan_qr_codes, notEncrypted) {
            onActionSelected(ViewerEditAction.ScanQrCodes)
        }
        if (record.isSearchable && notEncrypted) {
            SheetItem(Icons.Default.FindInPage, R.string.action_remove_text_layer, true) {
                onActionSelected(ViewerEditAction.RemoveTextLayer)
            }
        }

        SheetSection(R.string.sheet_section_export)
        SheetItem(Icons.Default.InvertColors, R.string.action_grayscale_pdf, notEncrypted) {
            onActionSelected(ViewerEditAction.Grayscale)
        }
        SheetItem(Icons.Default.Compress, R.string.action_compress_pdf, notEncrypted && !record.isSearchable) {
            onActionSelected(ViewerEditAction.CompressPdf)
        }

        SheetSection(R.string.sheet_section_security)
        SheetItem(Icons.Default.Lock, R.string.action_protect_pdf, notEncrypted) {
            onActionSelected(ViewerEditAction.ProtectPdf)
        }
        SheetItem(Icons.Default.AdminPanelSettings, R.string.action_restrict_usage, notEncrypted) {
            onActionSelected(ViewerEditAction.RestrictUsage)
        }
        SheetItem(Icons.Default.LockOpen, R.string.action_unlock_pdf, record.isEncrypted) {
            onActionSelected(ViewerEditAction.UnlockPdf)
        }
        SheetItem(Icons.Default.NoEncryption, R.string.action_remove_password, record.isEncrypted) {
            onActionSelected(ViewerEditAction.RemovePassword)
        }
    }
}

@Composable
private fun SheetSection(titleRes: Int) {
    Text(
        text = stringResource(titleRes).uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 4.dp)
    )
}

@Composable
private fun SheetItem(
    icon: ImageVector,
    textRes: Int,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val contentColor = if (enabled) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(16.dp))
        Text(text = stringResource(textRes), style = MaterialTheme.typography.bodyMedium, color = contentColor)
    }
}

private enum class ViewerEditAction {
    Split,
    Reorder,
    Rotate,
    DeletePages,
    ExtractPages,
    DuplicatePages,
    PageNumbers,
    TextWatermark,
    CompressPdf,
    ProtectPdf,
    UnlockPdf,
    Signature,
    RemoveTextLayer,
    RemovePassword,
    RestrictUsage,
    Annotate,
    Redact,
    Grayscale,
    PdfMetadata,
    ScanQrCodes
}
