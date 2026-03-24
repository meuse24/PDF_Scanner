package info.meuse24.pdf_scanner.ui.reorder

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import info.meuse24.pdf_scanner.R
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
fun ReorderScreen(
    onNavigateBack: () -> Unit,
    viewModel:      ReorderViewModel = hiltViewModel()
) {
    val record     by viewModel.record.collectAsState()
    val pages      by viewModel.pages.collectAsState()
    val saveAsCopy by viewModel.saveAsCopy.collectAsState()
    val loading    by viewModel.loading.collectAsState()
    val editLoading by viewModel.editLoading.collectAsState()
    val error      by viewModel.error.collectAsState()
    val success    by viewModel.success.collectAsState()

    LaunchedEffect(success) {
        if (success) onNavigateBack()
    }

    val listState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        viewModel.movePage(from.index, to.index)
    }

    Column(modifier = Modifier.fillMaxSize()) {

        // Save-as-copy toggle
        Row(
            modifier          = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text  = stringResource(R.string.reorder_save_as_copy),
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text  = stringResource(R.string.reorder_overwrite_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
            Switch(
                checked         = saveAsCopy,
                onCheckedChange = { viewModel.setSaveAsCopy(it) }
            )
        }

        if (loading) {
            Box(
                modifier         = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                state          = listState,
                modifier       = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                itemsIndexed(pages, key = { _, page -> page.originalIndex }) { index, page ->
                    ReorderableItem(reorderState, key = page.originalIndex) { isDragging ->
                        val elevation = if (isDragging) 8.dp else 2.dp
                        Card(
                            modifier  = Modifier
                                .padding(horizontal = 16.dp, vertical = 4.dp)
                                .fillMaxWidth(),
                            shape     = RoundedCornerShape(12.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = elevation)
                        ) {
                            Row(
                                modifier          = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Drag handle
                                Icon(
                                    imageVector        = Icons.Default.DragHandle,
                                    contentDescription = stringResource(R.string.cd_drag_handle),
                                    modifier           = Modifier
                                        .draggableHandle()
                                        .size(24.dp),
                                    tint               = MaterialTheme.colorScheme.outline
                                )

                                Spacer(Modifier.width(8.dp))

                                // Thumbnail
                                val bmp = page.bitmap
                                if (bmp != null) {
                                    Image(
                                        painter            = BitmapPainter(bmp.asImageBitmap()),
                                        contentDescription = null,
                                        contentScale       = ContentScale.Crop,
                                        modifier           = Modifier
                                            .size(56.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                    )
                                } else {
                                    Box(
                                        modifier         = Modifier
                                            .size(56.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (!page.isLoaded) CircularProgressIndicator(Modifier.size(24.dp))
                                    }
                                }

                                Spacer(Modifier.width(12.dp))

                                Text(
                                    text       = stringResource(R.string.split_page_label, page.originalIndex + 1),
                                    style      = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    modifier   = Modifier.weight(1f)
                                )

                                // Up/down fallback buttons
                                IconButton(
                                    onClick  = { viewModel.movePageUp(index) },
                                    enabled  = index > 0
                                ) {
                                    Icon(
                                        Icons.Default.KeyboardArrowUp,
                                        contentDescription = stringResource(R.string.cd_page_up)
                                    )
                                }
                                IconButton(
                                    onClick  = { viewModel.movePageDown(index) },
                                    enabled  = index < pages.lastIndex
                                ) {
                                    Icon(
                                        Icons.Default.KeyboardArrowDown,
                                        contentDescription = stringResource(R.string.cd_page_down)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Apply button
        Button(
            onClick  = { viewModel.reorderPages() },
            enabled  = record != null && !loading && !editLoading,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(stringResource(R.string.reorder_apply))
        }
    }

    if (editLoading) {
        AlertDialog(
            onDismissRequest = {},
            title = null,
            text  = {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            },
            confirmButton = {}
        )
    }

    error?.let { msg ->
        AlertDialog(
            onDismissRequest = viewModel::clearError,
            title            = { Text(stringResource(R.string.error_title)) },
            text             = { Text(msg) },
            confirmButton    = {
                TextButton(onClick = viewModel::clearError) {
                    Text(stringResource(R.string.action_ok))
                }
            }
        )
    }
}
