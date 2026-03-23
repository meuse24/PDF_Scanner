package info.meuse24.pdf_scanner.ui.overlay

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import info.meuse24.pdf_scanner.R
import info.meuse24.pdf_scanner.data.local.ScanRecord
import info.meuse24.pdf_scanner.ui.home.HomeViewModel
import java.io.File

@Composable
fun PageNumbersScreen(
    onNavigateBack: () -> Unit,
    viewModel: OverlayActionViewModel = hiltViewModel(),
    homeViewModel: HomeViewModel = hiltViewModel()
) {
    val record by viewModel.record.collectAsState()
    val editLoading by homeViewModel.editLoading.collectAsState()

    OverlayActionContent(
        record = record,
        title = stringResource(R.string.page_numbers_description),
        body = stringResource(R.string.page_numbers_details),
        actionContent = {},
        confirmLabel = stringResource(R.string.page_numbers_apply),
        confirmEnabled = record != null && !editLoading,
        onConfirm = {
            val currentRecord = record ?: return@OverlayActionContent
            homeViewModel.addPageNumbers(currentRecord)
            onNavigateBack()
        }
    )
}

@Composable
fun TextWatermarkScreen(
    onNavigateBack: () -> Unit,
    viewModel: OverlayActionViewModel = hiltViewModel(),
    homeViewModel: HomeViewModel = hiltViewModel()
) {
    val record by viewModel.record.collectAsState()
    val editLoading by homeViewModel.editLoading.collectAsState()
    var watermarkText by rememberSaveable { mutableStateOf("") }

    OverlayActionContent(
        record = record,
        title = stringResource(R.string.watermark_description),
        body = stringResource(R.string.watermark_details),
        actionContent = {
            OutlinedTextField(
                value = watermarkText,
                onValueChange = { watermarkText = it },
                label = { Text(stringResource(R.string.watermark_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmLabel = stringResource(R.string.watermark_apply),
        confirmEnabled = record != null && watermarkText.isNotBlank() && !editLoading,
        onConfirm = {
            val currentRecord = record ?: return@OverlayActionContent
            homeViewModel.applyTextWatermark(currentRecord, watermarkText)
            onNavigateBack()
        }
    )
}

@Composable
private fun OverlayActionContent(
    record: ScanRecord?,
    title: String,
    body: String,
    actionContent: @Composable () -> Unit,
    confirmLabel: String,
    confirmEnabled: Boolean,
    onConfirm: () -> Unit
) {
    if (record == null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
        item {
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline
            )
        }
        item {
            DocumentPreviewCard(record)
        }
        item { actionContent() }
        item {
            Button(
                onClick = onConfirm,
                enabled = confirmEnabled,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(confirmLabel)
            }
        }
    }
}

@Composable
private fun DocumentPreviewCard(record: ScanRecord) {
    val thumbnail by produceState<ImageBitmap?>(initialValue = null, key1 = record.thumbnailPath) {
        value = record.thumbnailPath
            ?.let(::File)
            ?.takeIf { it.exists() }
            ?.let { file ->
                BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap()
            }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            val image = thumbnail
            if (image != null) {
                Image(
                    painter = BitmapPainter(image),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .clip(MaterialTheme.shapes.medium)
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PictureAsPdf,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp)
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = record.filename,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.overlay_document_info, record.pageCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}
