package info.meuse24.pdf_scanner.ui.trash

import android.graphics.BitmapFactory
import android.text.format.Formatter
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import info.meuse24.pdf_scanner.R
import info.meuse24.pdf_scanner.data.local.ScanRecord
import info.meuse24.pdf_scanner.util.TrashConstants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DateFormat
import java.util.Date
import java.util.concurrent.TimeUnit
import kotlin.math.ceil

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrashScreen(
    viewModel: TrashViewModel = hiltViewModel()
) {
    val scans by viewModel.trashedScans.collectAsStateWithLifecycle()
    val loading by viewModel.loading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val success by viewModel.success.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var selectedIds by remember { mutableStateOf(emptySet<Long>()) }
    var pendingPurge by remember { mutableStateOf<List<ScanRecord>>(emptyList()) }
    var showEmptyConfirm by remember { mutableStateOf(false) }

    val selectedRecords = scans.filter { it.id in selectedIds }

    LaunchedEffect(success) {
        val message = success ?: return@LaunchedEffect
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        viewModel.clearSuccess()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            scans.isEmpty() -> TrashEmptyState()
            else -> {
                Column(modifier = Modifier.fillMaxSize()) {
                    TrashActionRow(
                        selectedCount = selectedIds.size,
                        totalCount = scans.size,
                        onClearSelection = { selectedIds = emptySet() },
                        onSelectAll = { selectedIds = scans.map { it.id }.toSet() },
                        onRestoreSelected = {
                            viewModel.restore(selectedRecords)
                            selectedIds = emptySet()
                        },
                        onPurgeSelected = { pendingPurge = selectedRecords },
                        onEmptyTrash = { showEmptyConfirm = true }
                    )
                    LazyColumn(
                        contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 96.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        items(scans, key = { it.id }) { record ->
                            TrashItem(
                                record = record,
                                selected = record.id in selectedIds,
                                onSelectionToggle = {
                                    selectedIds = if (record.id in selectedIds) {
                                        selectedIds - record.id
                                    } else {
                                        selectedIds + record.id
                                    }
                                },
                                onRestore = { viewModel.restore(listOf(record)) },
                                onPurge = { pendingPurge = listOf(record) }
                            )
                        }
                    }
                }
            }
        }

        if (loading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
    }

    if (pendingPurge.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { pendingPurge = emptyList() },
            title = { Text(stringResource(R.string.trash_confirm_purge_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.trash_confirm_purge_message,
                        pendingPurge.size
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.purge(pendingPurge)
                        selectedIds = selectedIds - pendingPurge.map { it.id }.toSet()
                        pendingPurge = emptyList()
                    }
                ) {
                    Text(
                        stringResource(R.string.action_delete_forever),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingPurge = emptyList() }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    if (showEmptyConfirm) {
        AlertDialog(
            onDismissRequest = { showEmptyConfirm = false },
            title = { Text(stringResource(R.string.trash_confirm_empty_title)) },
            text = { Text(stringResource(R.string.trash_confirm_empty_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.emptyTrash()
                        selectedIds = emptySet()
                        showEmptyConfirm = false
                    }
                ) {
                    Text(
                        stringResource(R.string.action_empty_trash),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showEmptyConfirm = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
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

@Composable
private fun TrashActionRow(
    selectedCount: Int,
    totalCount: Int,
    onClearSelection: () -> Unit,
    onSelectAll: () -> Unit,
    onRestoreSelected: () -> Unit,
    onPurgeSelected: () -> Unit,
    onEmptyTrash: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (selectedCount > 0) {
            Text(
                text = stringResource(R.string.selection_count_fraction, selectedCount, totalCount),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onClearSelection) {
                Text(stringResource(R.string.action_cancel))
            }
            IconButton(onClick = onRestoreSelected) {
                Icon(Icons.Default.Restore, stringResource(R.string.action_restore))
            }
            IconButton(onClick = onPurgeSelected) {
                Icon(Icons.Default.DeleteForever, stringResource(R.string.action_delete_forever))
            }
        } else {
            Text(
                text = stringResource(R.string.trash_retention_hint, TrashConstants.RETENTION_DAYS),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onSelectAll) {
                Text(stringResource(R.string.action_select_all))
            }
            TextButton(onClick = onEmptyTrash) {
                Text(
                    stringResource(R.string.action_empty_trash),
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TrashItem(
    record: ScanRecord,
    selected: Boolean,
    onSelectionToggle: () -> Unit,
    onRestore: () -> Unit,
    onPurge: () -> Unit
) {
    var showSheet by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val resources = LocalResources.current
    val thumbnail = rememberTrashThumbnail(record.thumbnailPath)
    val sizeStr = remember(record.fileSize, context) {
        Formatter.formatShortFileSize(context, record.fileSize.coerceAtLeast(0L))
    }
    val deletedDate = remember(record.deletedAt) {
        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
            .format(Date(record.deletedAt ?: 0L))
    }
    val remainingDays = remember(record.deletedAt) {
        val deletedAt = record.deletedAt ?: return@remember 0
        val remaining = deletedAt + TrashConstants.RETENTION_MILLIS - System.currentTimeMillis()
        ceil(remaining.coerceAtLeast(0L).toDouble() / TimeUnit.DAYS.toMillis(1).toDouble())
            .toInt()
            .coerceAtLeast(0)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelectionToggle),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            }
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (thumbnail != null) {
                Image(
                    painter = BitmapPainter(thumbnail),
                    contentDescription = stringResource(R.string.cd_pdf_document),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
            } else {
                Icon(
                    Icons.Default.PictureAsPdf,
                    contentDescription = stringResource(R.string.cd_pdf_document),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(44.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = record.filename,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.trash_item_meta, deletedDate, record.pageCount, sizeStr),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.outline
                )
                Text(
                    text = resources.getQuantityString(
                        R.plurals.trash_days_remaining,
                        remainingDays,
                        remainingDays
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            IconButton(onClick = { showSheet = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.action_more))
            }
            Checkbox(checked = selected, onCheckedChange = { onSelectionToggle() })
        }
    }

    if (showSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp)
            ) {
                TrashSheetItem(Icons.Default.Restore, R.string.action_restore) {
                    showSheet = false
                    onRestore()
                }
                HorizontalDivider(modifier = Modifier.padding(horizontal = 24.dp))
                TrashSheetItem(Icons.Default.DeleteForever, R.string.action_delete_forever) {
                    showSheet = false
                    onPurge()
                }
            }
        }
    }
}

@Composable
private fun TrashSheetItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    labelRes: Int,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null)
        Spacer(Modifier.width(16.dp))
        Text(stringResource(labelRes), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun TrashEmptyState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.DeleteForever,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(56.dp)
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.trash_empty_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.trash_empty_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

@Composable
private fun rememberTrashThumbnail(thumbnailPath: String?): ImageBitmap? {
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
