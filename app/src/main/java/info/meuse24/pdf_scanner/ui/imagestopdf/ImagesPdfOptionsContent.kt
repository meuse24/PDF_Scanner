package info.meuse24.pdf_scanner.ui.imagestopdf

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContactPage
import androidx.compose.material.icons.filled.CropSquare
import info.meuse24.pdf_scanner.R
import info.meuse24.pdf_scanner.domain.model.PdfPageOrientation
import info.meuse24.pdf_scanner.domain.model.PdfPageSetup
import info.meuse24.pdf_scanner.domain.model.PdfPageSizePreset
import info.meuse24.pdf_scanner.domain.usecase.ImagePageLayout
import info.meuse24.pdf_scanner.domain.usecase.ImagePdfPageMode
import info.meuse24.pdf_scanner.ui.components.PdfPageSetupSection
import info.meuse24.pdf_scanner.ui.components.PdfPageSizeSegmentedRow

@Composable
fun ImagesPdfOptionsContent(
    imageUris: List<Uri>,
    selectedLayout: ImagePageLayout,
    onLayoutSelected: (ImagePageLayout) -> Unit,
    pageMode: ImagePdfPageMode,
    onPageModeSelected: (ImagePdfPageMode) -> Unit,
    pageSetup: PdfPageSetup,
    onPageSetupChange: (PdfPageSetup) -> Unit,
    actionLabel: String,
    actionEnabled: Boolean,
    actionInProgress: Boolean,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
    topContent: @Composable (() -> Unit)? = null
) {
    Column(modifier = modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            itemsIndexed(imageUris, key = { index, _ -> index }) { _, uri ->
                UriThumbnail(uri = uri, modifier = Modifier.size(110.dp))
            }
        }

        Surface(
            tonalElevation = 4.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.images_to_pdf_image_count, imageUris.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    topContent?.invoke()

                    PageModeSection(
                        pageMode = pageMode,
                        onPageModeSelected = onPageModeSelected
                    )

                    AnimatedVisibility(
                        visible = pageMode == ImagePdfPageMode.FIXED_PAGE,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = stringResource(R.string.images_to_pdf_layout_label),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                SingleChoiceSegmentedButtonRow(modifier = Modifier.weight(1f)) {
                                    ImagePageLayout.entries.forEachIndexed { index, layout ->
                                        SegmentedButton(
                                            selected = selectedLayout == layout,
                                            onClick = { onLayoutSelected(layout) },
                                            shape = SegmentedButtonDefaults.itemShape(
                                                index = index,
                                                count = ImagePageLayout.entries.size
                                            ),
                                            label = {
                                                Text(
                                                    text = when (layout) {
                                                        ImagePageLayout.SINGLE -> stringResource(R.string.images_to_pdf_layout_single)
                                                        ImagePageLayout.TWO_PER_PAGE -> stringResource(R.string.images_to_pdf_layout_two)
                                                        ImagePageLayout.FOUR_PER_PAGE -> stringResource(R.string.images_to_pdf_layout_four)
                                                    }
                                                )
                                            }
                                        )
                                    }
                                }
                                LayoutPreviewCanvas(
                                    layout = selectedLayout,
                                    modifier = if (pageSetup.orientation == PdfPageOrientation.LANDSCAPE) {
                                        Modifier.size(width = 50.dp, height = 36.dp)
                                    } else {
                                        Modifier.size(width = 36.dp, height = 50.dp)
                                    }
                                )
                            }
                        }
                    }

                    AnimatedVisibility(
                        visible = pageMode == ImagePdfPageMode.PHOTO_PAGE,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            MaxPageSizeRow(
                                selected = pageSetup.sizePreset,
                                onSelected = { onPageSetupChange(pageSetup.copy(sizePreset = it)) },
                                modifier = Modifier.weight(1f)
                            )
                            PhotoPagePreview(modifier = Modifier.size(width = 42.dp, height = 54.dp))
                        }
                    }

                    AnimatedVisibility(
                        visible = pageMode == ImagePdfPageMode.FIXED_PAGE,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        PdfPageSetupSection(
                            setup = pageSetup,
                            onSetupChange = onPageSetupChange
                        )
                    }

                    Button(
                        onClick = onAction,
                        enabled = actionEnabled,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (actionInProgress) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(
                            text = actionLabel,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PageModeSection(
    pageMode: ImagePdfPageMode,
    onPageModeSelected: (ImagePdfPageMode) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = stringResource(R.string.images_to_pdf_mode_label),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            ImagePdfPageMode.entries.forEachIndexed { index, mode ->
                SegmentedButton(
                    selected = pageMode == mode,
                    onClick = { onPageModeSelected(mode) },
                    shape = SegmentedButtonDefaults.itemShape(
                        index = index,
                        count = ImagePdfPageMode.entries.size
                    ),
                    icon = {
                        Icon(
                            imageVector = when (mode) {
                                ImagePdfPageMode.FIXED_PAGE -> Icons.Filled.ContactPage
                                ImagePdfPageMode.PHOTO_PAGE -> Icons.Filled.CropSquare
                            },
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    label = {
                        Text(
                            text = when (mode) {
                                ImagePdfPageMode.FIXED_PAGE -> stringResource(R.string.images_to_pdf_mode_fixed)
                                ImagePdfPageMode.PHOTO_PAGE -> stringResource(R.string.images_to_pdf_mode_photo)
                            }
                        )
                    }
                )
            }
        }
        Text(
            text = when (pageMode) {
                ImagePdfPageMode.FIXED_PAGE -> stringResource(R.string.images_to_pdf_mode_fixed_supporting_text)
                ImagePdfPageMode.PHOTO_PAGE -> stringResource(R.string.images_to_pdf_mode_photo_supporting_text)
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun MaxPageSizeRow(
    selected: PdfPageSizePreset,
    onSelected: (PdfPageSizePreset) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = stringResource(R.string.images_to_pdf_page_size_max_label),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        PdfPageSizeSegmentedRow(
            selected = selected,
            onSelected = onSelected
        )
    }
}

@Composable
private fun PhotoPagePreview(modifier: Modifier = Modifier) {
    val pageColor = MaterialTheme.colorScheme.surfaceVariant
    val borderColor = MaterialTheme.colorScheme.outline
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(3.dp))
                .background(MaterialTheme.colorScheme.surface)
        ) {
            val previewW = size.width * 0.76f
            val previewH = previewW * 4f / 3f
            val left = (size.width - previewW) / 2f
            val top = (size.height - previewH) / 2f
            drawRect(
                pageColor,
                topLeft = Offset(left, top),
                size = Size(previewW, previewH)
            )
            drawRect(
                borderColor,
                topLeft = Offset(left, top),
                size = Size(previewW, previewH),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 0.75.dp.toPx())
            )
        }
        Icon(
            imageVector = Icons.Filled.CropSquare,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun UriThumbnail(uri: Uri, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bitmap by produceState<android.graphics.Bitmap?>(initialValue = null, key1 = uri) {
        value = runCatching {
            val sampleSize = context.contentResolver.openInputStream(uri)?.use { stream ->
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeStream(stream, null, options)
                maxOf(options.outWidth / 220, options.outHeight / 220, 1)
            } ?: 2
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
                BitmapFactory.decodeStream(stream, null, options)
            }
        }.getOrNull()
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        bitmap?.let { bmp ->
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun LayoutPreviewCanvas(layout: ImagePageLayout, modifier: Modifier = Modifier) {
    val cellColor = MaterialTheme.colorScheme.surfaceVariant
    val borderColor = MaterialTheme.colorScheme.outline
    Canvas(
        modifier = modifier
            .clip(RoundedCornerShape(2.dp))
            .background(MaterialTheme.colorScheme.surface)
    ) {
        val margin = size.width * 0.08f
        val gap = size.width * 0.04f
        val areaW = size.width - 2 * margin
        val areaH = size.height - 2 * margin

        fun drawCell(x: Float, y: Float, w: Float, h: Float) {
            drawRect(cellColor, topLeft = Offset(x, y), size = Size(w, h))
            drawRect(
                borderColor,
                topLeft = Offset(x, y),
                size = Size(w, h),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 0.5.dp.toPx())
            )
        }

        when (layout) {
            ImagePageLayout.SINGLE -> drawCell(margin, margin, areaW, areaH)
            ImagePageLayout.TWO_PER_PAGE -> {
                val h = (areaH - gap) / 2f
                drawCell(margin, margin, areaW, h)
                drawCell(margin, margin + h + gap, areaW, h)
            }
            ImagePageLayout.FOUR_PER_PAGE -> {
                val w = (areaW - gap) / 2f
                val h = (areaH - gap) / 2f
                drawCell(margin, margin, w, h)
                drawCell(margin + w + gap, margin, w, h)
                drawCell(margin, margin + h + gap, w, h)
                drawCell(margin + w + gap, margin + h + gap, w, h)
            }
        }
    }
}
