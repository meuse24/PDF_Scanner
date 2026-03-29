package info.meuse24.pdf_scanner.ui.imagestopdf

import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
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
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import info.meuse24.pdf_scanner.R
import info.meuse24.pdf_scanner.domain.usecase.ImagePageLayout
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ImagesToPdfScreen(
    imageUris: List<Uri>,
    onNavigateBack: () -> Unit,
    viewModel: ImagesToPdfViewModel = hiltViewModel()
) {
    val context     = LocalContext.current
    val editLoading by viewModel.editLoading.collectAsStateWithLifecycle()
    val error       by viewModel.error.collectAsStateWithLifecycle()
    val success     by viewModel.success.collectAsStateWithLifecycle()
    val skippedCount by viewModel.skippedCount.collectAsStateWithLifecycle()

    val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    val defaultFilename = stringResource(R.string.images_to_pdf_filename_default, today)
    val skippedMessage = if (skippedCount > 0) {
        stringResource(R.string.images_to_pdf_unreadable_skipped, skippedCount)
    } else {
        null
    }
    var filename by rememberSaveable(today) {
        mutableStateOf(defaultFilename)
    }
    var selectedLayout by rememberSaveable { mutableStateOf(ImagePageLayout.TWO_PER_PAGE) }

    LaunchedEffect(success) {
        if (success) {
            if (skippedMessage != null) {
                Toast.makeText(
                    context,
                    skippedMessage,
                    Toast.LENGTH_LONG
                ).show()
            }
            onNavigateBack()
        }
    }

    LaunchedEffect(error) {
        if (error != null) {
            Toast.makeText(context, error, Toast.LENGTH_LONG).show()
            viewModel.clearError()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {

        // Bildvorschau-Raster
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

        // Bottom-Panel
        Surface(
            tonalElevation = 4.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.images_to_pdf_image_count, imageUris.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = filename,
                    onValueChange = { filename = it },
                    label = { Text(stringResource(R.string.images_to_pdf_filename_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

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
                                    onClick = { selectedLayout = layout },
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
                    onClick = {
                        val effectiveName = filename.trim().ifBlank {
                            defaultFilename
                        }
                        viewModel.createPdf(imageUris, effectiveName, selectedLayout)
                    },
                    enabled = imageUris.isNotEmpty() && !editLoading,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (editLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(stringResource(R.string.images_to_pdf_action))
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

/** Kleines A4-ähnliches Vorschaubild, das die Zellaufteilung des gewählten Layouts zeigt. */
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
        val gap    = size.width * 0.04f
        val areaW  = size.width - 2 * margin
        val areaH  = size.height - 2 * margin

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
            ImagePageLayout.SINGLE -> {
                drawCell(margin, margin, areaW, areaH)
            }
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
