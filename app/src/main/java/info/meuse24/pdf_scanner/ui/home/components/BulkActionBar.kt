package info.meuse24.pdf_scanner.ui.home.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMerge
import androidx.compose.material.icons.automirrored.filled.ManageSearch
import androidx.compose.material.icons.automirrored.filled.TextSnippet
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import info.meuse24.pdf_scanner.R

@Composable
internal fun BulkActionBar(
    onShare:               () -> Unit,
    onExport:              () -> Unit,
    onExtractTexts:        () -> Unit,
    onMakeSearchable:      () -> Unit,
    onMerge:               () -> Unit,
    onDelete:              () -> Unit,
    extractEnabled:        Boolean  = true,
    makeSearchableEnabled: Boolean  = true,
    mergeEnabled:          Boolean  = false,
    modifier:              Modifier = Modifier
) {
    Surface(
        modifier        = modifier.fillMaxWidth(),
        shadowElevation = 8.dp,
        color           = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Row(
            modifier              = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            IconButton(onClick = onShare) {
                Icon(Icons.Default.Share, contentDescription = stringResource(R.string.cd_share))
            }
            IconButton(onClick = onExport) {
                Icon(Icons.Default.Download, contentDescription = stringResource(R.string.action_export))
            }
            IconButton(onClick = onMerge, enabled = mergeEnabled) {
                Icon(
                    Icons.AutoMirrored.Filled.CallMerge,
                    contentDescription = stringResource(R.string.cd_merge)
                )
            }
            IconButton(onClick = onExtractTexts, enabled = extractEnabled) {
                Icon(
                    Icons.AutoMirrored.Filled.TextSnippet,
                    contentDescription = stringResource(R.string.cd_extract_text)
                )
            }
            IconButton(onClick = onMakeSearchable) {
                Icon(
                    Icons.AutoMirrored.Filled.ManageSearch,
                    contentDescription = stringResource(R.string.cd_make_searchable)
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = stringResource(R.string.cd_delete),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
