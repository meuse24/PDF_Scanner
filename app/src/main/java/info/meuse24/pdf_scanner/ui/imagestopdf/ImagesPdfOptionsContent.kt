package info.meuse24.pdf_scanner.ui.imagestopdf

import android.graphics.BitmapFactory
import android.net.Uri
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
import androidx.compose.ui.unit.dp
import info.meuse24.pdf_scanner.R
import info.meuse24.pdf_scanner.domain.usecase.ImagePageLayout

@Composable
fun ImagesPdfOptionsContent(
    imageUris: List<Uri>,
    selectedLayout: ImagePageLayout,
    onLayoutSelected: (ImagePageLayout) -> Unit,
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
                                modifier = Modifier.size(width = 36.dp, height = 50.dp)
                            )
                        }
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
                        Text(actionLabel)
                    }
                }
            }
        }
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
