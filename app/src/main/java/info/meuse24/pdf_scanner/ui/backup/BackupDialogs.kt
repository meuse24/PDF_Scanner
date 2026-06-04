package info.meuse24.pdf_scanner.ui.backup

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.foundation.text.KeyboardOptions
import info.meuse24.pdf_scanner.R
import info.meuse24.pdf_scanner.domain.backup.BackupExportOptions
import info.meuse24.pdf_scanner.domain.backup.BackupRestorePreview
import java.text.DateFormat
import java.util.Date

@Composable
internal fun BackupExportOptionsDialog(
    initialOptions: BackupExportOptions,
    onConfirm: (BackupExportOptions) -> Unit,
    onDismiss: () -> Unit
) {
    var includeTrash by remember { mutableStateOf(initialOptions.includeTrash) }
    var includeOcr by remember { mutableStateOf(initialOptions.includeOcrText) }
    var includeThumbnails by remember { mutableStateOf(initialOptions.includeThumbnails) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.backup_options_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                BackupOptionRow(
                    label = stringResource(R.string.backup_option_include_ocr),
                    checked = includeOcr,
                    onCheckedChange = { includeOcr = it }
                )
                BackupOptionRow(
                    label = stringResource(R.string.backup_option_include_thumbnails),
                    checked = includeThumbnails,
                    onCheckedChange = { includeThumbnails = it }
                )
                BackupOptionRow(
                    label = stringResource(R.string.backup_option_include_trash),
                    checked = includeTrash,
                    onCheckedChange = { includeTrash = it }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(
                    BackupExportOptions(
                        includeTrash = includeTrash,
                        includeOcrText = includeOcr,
                        includeThumbnails = includeThumbnails
                    )
                )
            }) { Text(stringResource(R.string.backup_options_continue)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.backup_dialog_cancel)) }
        }
    )
}

@Composable
private fun BackupOptionRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, onValueChange = onCheckedChange, role = Role.Checkbox),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Spacer(Modifier.width(4.dp))
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
internal fun BackupExportPassphraseDialog(
    errorRes: Int?,
    onConfirm: (passphrase: String, repeated: String) -> Unit,
    onDismiss: () -> Unit
) {
    var passphrase by remember { mutableStateOf("") }
    var repeated by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.backup_export_passphrase_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = stringResource(R.string.backup_export_passphrase_message),
                    style = MaterialTheme.typography.bodyMedium
                )
                PassphraseField(
                    value = passphrase,
                    onValueChange = { passphrase = it },
                    label = stringResource(R.string.backup_passphrase_label),
                    visible = visible,
                    onToggleVisible = { visible = !visible }
                )
                PassphraseField(
                    value = repeated,
                    onValueChange = { repeated = it },
                    label = stringResource(R.string.backup_passphrase_repeat_label),
                    visible = visible,
                    onToggleVisible = { visible = !visible }
                )
                if (errorRes != null) {
                    Text(
                        text = stringResource(errorRes),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(passphrase, repeated) },
                enabled = passphrase.isNotEmpty() && repeated.isNotEmpty()
            ) { Text(stringResource(R.string.backup_export_passphrase_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.backup_dialog_cancel)) }
        }
    )
}

@Composable
internal fun BackupRestorePassphraseDialog(
    errorRes: Int?,
    onConfirm: (passphrase: String) -> Unit,
    onDismiss: () -> Unit
) {
    var passphrase by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.backup_restore_passphrase_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PassphraseField(
                    value = passphrase,
                    onValueChange = { passphrase = it },
                    label = stringResource(R.string.backup_passphrase_label),
                    visible = visible,
                    onToggleVisible = { visible = !visible }
                )
                if (errorRes != null) {
                    Text(
                        text = stringResource(errorRes),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(passphrase) },
                enabled = passphrase.isNotEmpty()
            ) { Text(stringResource(R.string.backup_restore_passphrase_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.backup_dialog_cancel)) }
        }
    )
}

@Composable
private fun PassphraseField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    visible: Boolean,
    onToggleVisible: () -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        trailingIcon = {
            IconButton(onClick = onToggleVisible) {
                Icon(
                    imageVector = if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                    contentDescription = stringResource(
                        if (visible) R.string.backup_passphrase_hide else R.string.backup_passphrase_show
                    )
                )
            }
        },
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
internal fun BackupRestoreSummaryDialog(
    preview: BackupRestorePreview,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val size = remember(preview.totalBytes) { Formatter.formatShortFileSize(context, preview.totalBytes) }
    val created = remember(preview.createdAtEpochMillis) {
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(preview.createdAtEpochMillis))
    }
    val appVersion = preview.appVersionName ?: stringResource(R.string.backup_summary_app_version_unknown)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.backup_summary_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.backup_summary_documents, preview.documentCount))
                Text(stringResource(R.string.backup_summary_folders, preview.folderCount))
                Text(stringResource(R.string.backup_summary_size, size))
                Text(stringResource(R.string.backup_summary_created, created))
                Text(stringResource(R.string.backup_summary_app_version, appVersion))
                if (preview.includesTrash) {
                    Text(stringResource(R.string.backup_summary_includes_trash))
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.backup_summary_merge_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.backup_summary_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.backup_dialog_cancel)) }
        }
    )
}

@Composable
internal fun BackupProgressDialog(
    operation: BackupOperationState,
    onCancel: () -> Unit
) {
    val title = stringResource(
        when (operation.kind) {
            BackupOperationKind.EXPORT -> R.string.backup_progress_export_title
            BackupOperationKind.RESTORE_PREPARE -> R.string.backup_progress_restore_prepare_title
            BackupOperationKind.RESTORE -> R.string.backup_progress_restore_title
        }
    )
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp))
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(text = title, style = MaterialTheme.typography.titleMedium)
                        if (operation.detail != null) {
                            Text(
                                text = operation.detail,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                if (operation.cancellable) {
                    TextButton(onClick = onCancel, modifier = Modifier.align(Alignment.End)) {
                        Text(stringResource(R.string.backup_dialog_cancel))
                    }
                }
            }
        }
    }
}
