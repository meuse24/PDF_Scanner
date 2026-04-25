package info.meuse24.pdf_scanner.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MergeType
import androidx.compose.material.icons.automirrored.filled.TextSnippet
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FindInPage
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import info.meuse24.pdf_scanner.domain.model.Document
import info.meuse24.pdf_scanner.ui.home.components.BulkActionBar
import info.meuse24.pdf_scanner.R
import androidx.compose.ui.unit.dp

@Composable
internal fun HomeSelectionBar(
    selectedRecords: List<Document>,
    onShare: () -> Unit,
    onExport: () -> Unit,
    onExtractTexts: () -> Unit,
    onMakeSearchable: () -> Unit,
    onMerge: () -> Unit,
    onMoveToFolder: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    BulkActionBar(
        onShare = onShare,
        onExport = onExport,
        onExtractTexts = onExtractTexts,
        extractEnabled = selectedRecords.isNotEmpty(),
        onMakeSearchable = onMakeSearchable,
        makeSearchableEnabled = true,
        onMerge = onMerge,
        mergeEnabled = selectedRecords.size >= 2,
        onMoveToFolder = onMoveToFolder,
        onDelete = onDelete,
        modifier = modifier
    )
}

@Composable
internal fun LandscapeSelectionTopBar(
    selectedRecords: List<Document>,
    totalCount: Int,
    onClearSelection: () -> Unit,
    onSelectAll: () -> Unit,
    onShare: () -> Unit,
    onExport: () -> Unit,
    onExtractTexts: () -> Unit,
    onMakeSearchable: () -> Unit,
    onMerge: () -> Unit,
    onMoveToFolder: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var ocrMenuExpanded by remember { mutableStateOf(false) }
    var moreMenuExpanded by remember { mutableStateOf(false) }
    val hasSelection = selectedRecords.isNotEmpty()
    val mergeEnabled = selectedRecords.size >= 2

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClearSelection) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.action_cancel))
            }
            Text(
                text = stringResource(R.string.selection_count, selectedRecords.size),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            SelectionTopBarAction(
                icon = Icons.Default.SelectAll,
                labelRes = R.string.action_select_all,
                enabled = selectedRecords.size < totalCount,
                onClick = onSelectAll
            )
            SelectionTopBarAction(
                icon = Icons.Default.Share,
                labelRes = R.string.label_bulk_share,
                enabled = hasSelection,
                onClick = onShare
            )
            SelectionTopBarAction(
                icon = Icons.Default.FolderOpen,
                labelRes = R.string.label_bulk_move_folder,
                enabled = hasSelection,
                onClick = onMoveToFolder
            )
            Box {
                SelectionTopBarAction(
                    icon = Icons.Default.FindInPage,
                    labelRes = R.string.label_bulk_searchable,
                    enabled = hasSelection,
                    onClick = { ocrMenuExpanded = true }
                )
                DropdownMenu(
                    expanded = ocrMenuExpanded,
                    onDismissRequest = { ocrMenuExpanded = false }
                ) {
                    SelectionDropdownItem(
                        icon = Icons.AutoMirrored.Filled.TextSnippet,
                        labelRes = R.string.label_bulk_extract,
                        enabled = hasSelection,
                        onClick = {
                            ocrMenuExpanded = false
                            onExtractTexts()
                        }
                    )
                    SelectionDropdownItem(
                        icon = Icons.Default.FindInPage,
                        labelRes = R.string.label_bulk_searchable,
                        enabled = hasSelection,
                        onClick = {
                            ocrMenuExpanded = false
                            onMakeSearchable()
                        }
                    )
                }
            }
            Box {
                SelectionTopBarAction(
                    icon = Icons.Default.MoreVert,
                    labelRes = R.string.label_bulk_more,
                    enabled = hasSelection,
                    onClick = { moreMenuExpanded = true }
                )
                DropdownMenu(
                    expanded = moreMenuExpanded,
                    onDismissRequest = { moreMenuExpanded = false }
                ) {
                    SelectionDropdownItem(
                        icon = Icons.Default.Download,
                        labelRes = R.string.label_bulk_export,
                        enabled = hasSelection,
                        onClick = {
                            moreMenuExpanded = false
                            onExport()
                        }
                    )
                    if (mergeEnabled) {
                        SelectionDropdownItem(
                            icon = Icons.AutoMirrored.Filled.MergeType,
                            labelRes = R.string.label_bulk_merge,
                            onClick = {
                                moreMenuExpanded = false
                                onMerge()
                            }
                        )
                    }
                    HorizontalDivider()
                    SelectionDropdownItem(
                        icon = Icons.Default.Delete,
                        labelRes = R.string.label_bulk_delete,
                        enabled = hasSelection,
                        tint = MaterialTheme.colorScheme.error,
                        onClick = {
                            moreMenuExpanded = false
                            onDelete()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SelectionTopBarAction(
    icon: ImageVector,
    labelRes: Int,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    IconButton(
        enabled = enabled,
        onClick = onClick
    ) {
        Icon(icon, contentDescription = stringResource(labelRes))
    }
}

@Composable
private fun SelectionDropdownItem(
    icon: ImageVector,
    labelRes: Int,
    enabled: Boolean = true,
    tint: Color = Color.Unspecified,
    onClick: () -> Unit
) {
    val color = if (tint == Color.Unspecified) MaterialTheme.colorScheme.onSurface else tint
    DropdownMenuItem(
        enabled = enabled,
        leadingIcon = {
            Icon(
                icon,
                contentDescription = null,
                tint = if (enabled) color else color.copy(alpha = 0.38f)
            )
        },
        text = {
            Text(
                text = stringResource(labelRes),
                color = if (enabled) color else color.copy(alpha = 0.38f)
            )
        },
        onClick = onClick
    )
}
