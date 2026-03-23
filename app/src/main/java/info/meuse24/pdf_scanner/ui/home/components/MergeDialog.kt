package info.meuse24.pdf_scanner.ui.home.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import info.meuse24.pdf_scanner.R
import info.meuse24.pdf_scanner.data.local.ScanRecord

@Composable
internal fun MergeDialog(
    filename:         String,
    onFilenameChange: (String) -> Unit,
    records:          List<ScanRecord>,
    onConfirm:        () -> Unit,
    onDismiss:        () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title   = { Text(stringResource(R.string.merge_dialog_title)) },
        text    = {
            Column {
                OutlinedTextField(
                    value         = filename,
                    onValueChange = onFilenameChange,
                    label         = { Text(stringResource(R.string.merge_dialog_filename_label)) },
                    singleLine    = true,
                    modifier      = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(R.string.merge_dialog_order_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
                Spacer(Modifier.height(8.dp))
                records.forEachIndexed { index, record ->
                    Text(
                        "${index + 1}. ${record.filename}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick  = onConfirm,
                enabled  = filename.isNotBlank()
            ) { Text(stringResource(R.string.merge_dialog_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}
