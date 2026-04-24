package info.meuse24.pdf_scanner.ui.home.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TextSnippet
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FindInPage
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.automirrored.filled.MergeType
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import info.meuse24.pdf_scanner.R

@Composable
internal fun BulkActionBar(
    onShare:               () -> Unit,
    onExport:              () -> Unit,
    onExtractTexts:        () -> Unit,
    onMakeSearchable:      () -> Unit,
    onMerge:               () -> Unit,
    onMoveToFolder:        () -> Unit,
    onDelete:              () -> Unit,
    extractEnabled:        Boolean  = true,
    makeSearchableEnabled: Boolean  = true,
    mergeEnabled:          Boolean  = false,
    modifier:              Modifier = Modifier
) {
    var ocrSheetVisible by remember { mutableStateOf(false) }

    Surface(
        modifier        = modifier.fillMaxWidth(),
        shadowElevation = 8.dp,
        color           = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Row(
            modifier              = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            BulkAction(
                icon     = Icons.Default.Share,
                labelRes = R.string.label_bulk_share,
                onClick  = onShare
            )
            BulkAction(
                icon = Icons.Default.FolderOpen,
                labelRes = R.string.label_bulk_move_folder,
                onClick = onMoveToFolder
            )
            BulkAction(
                icon     = Icons.Default.FindInPage,
                labelRes = R.string.label_bulk_searchable,
                enabled  = extractEnabled || makeSearchableEnabled,
                onClick  = { ocrSheetVisible = true }
            )
            BulkAction(
                icon     = Icons.Default.Delete,
                labelRes = R.string.label_bulk_delete,
                tint     = MaterialTheme.colorScheme.error,
                onClick  = onDelete
            )
            if (mergeEnabled) {
                BulkAction(
                    icon = Icons.AutoMirrored.Filled.MergeType,
                    labelRes = R.string.label_bulk_merge,
                    onClick = onMerge
                )
            } else {
                BulkAction(
                    icon = Icons.Default.Download,
                    labelRes = R.string.action_export,
                    onClick = onExport
                )
            }
        }
    }

    if (ocrSheetVisible) {
        BulkActionSheet(
            onDismiss = { ocrSheetVisible = false }
        ) {
            BulkSheetItem(
                icon = Icons.AutoMirrored.Filled.TextSnippet,
                labelRes = R.string.cd_extract_text,
                enabled = extractEnabled,
                onClick = {
                    ocrSheetVisible = false
                    onExtractTexts()
                }
            )
            BulkSheetItem(
                icon = Icons.Default.FindInPage,
                labelRes = R.string.cd_make_searchable,
                enabled = makeSearchableEnabled,
                onClick = {
                    ocrSheetVisible = false
                    onMakeSearchable()
                }
            )
        }
    }
}

@Composable
private fun BulkAction(
    icon:    ImageVector,
    labelRes: Int,
    enabled: Boolean = true,
    tint:    Color   = Color.Unspecified,
    onClick: () -> Unit
) {
    val effectiveTint = if (tint == Color.Unspecified) LocalContentColor.current else tint
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .alpha(if (enabled) 1f else 0.38f)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Icon(
            imageVector        = icon,
            contentDescription = null,
            tint               = effectiveTint,
            modifier           = Modifier.size(24.dp)
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text      = stringResource(labelRes),
            style     = MaterialTheme.typography.labelSmall,
            color     = effectiveTint,
            textAlign = TextAlign.Center,
            maxLines  = 1,
            overflow  = TextOverflow.Ellipsis
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BulkActionSheet(
    onDismiss: () -> Unit,
    content: @Composable () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(bottom = 16.dp)) {
            content()
        }
    }
}

@Composable
private fun BulkSheetItem(
    icon: ImageVector,
    labelRes: Int,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val contentColor = MaterialTheme.colorScheme.onSurface.let {
        if (enabled) it else it.copy(alpha = 0.38f)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(24.dp)
        )
        Spacer(Modifier.size(16.dp))
        Text(
            text = stringResource(labelRes),
            style = MaterialTheme.typography.bodyMedium,
            color = contentColor
        )
    }
}
