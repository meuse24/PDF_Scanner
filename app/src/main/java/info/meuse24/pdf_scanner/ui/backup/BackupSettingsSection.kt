package info.meuse24.pdf_scanner.ui.backup

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import info.meuse24.pdf_scanner.R
import info.meuse24.pdf_scanner.domain.backup.BACKUP_MIME_TYPE
import info.meuse24.pdf_scanner.ui.components.LocalAppSnackbarHostState

/**
 * Self-contained "Backup" group for the settings screen. Owns its [BackupViewModel], the SAF
 * launchers (CreateDocument/OpenDocument), all backup dialogs, the progress overlay, and result
 * messaging. Hosted via a slot in SettingsScreen so the screen itself stays presentational.
 */
@Composable
fun BackupSettingsSection(
    viewModel: BackupViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = LocalAppSnackbarHostState.current

    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(BACKUP_MIME_TYPE)
    ) { uri ->
        if (uri != null) {
            viewModel.onExportLocationChosen(uri.toString())
        } else {
            viewModel.onExportLocationCancelled()
        }
    }

    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.onRestoreLocationChosen(uri.toString())
        } else {
            viewModel.onRestoreLocationCancelled()
        }
    }

    LaunchedEffect(uiState.pendingExportFilename) {
        uiState.pendingExportFilename?.let { filename ->
            createDocumentLauncher.launch(filename)
            viewModel.onExportFilenameConsumed()
        }
    }

    LaunchedEffect(uiState.launchOpenDocument) {
        if (uiState.launchOpenDocument) {
            openDocumentLauncher.launch(arrayOf("*/*"))
            viewModel.onOpenDocumentConsumed()
        }
    }

    LaunchedEffect(uiState.message) {
        when (val message = uiState.message) {
            is BackupMessage.Success -> snackbarHostState?.showSnackbar(message.text)
            is BackupMessage.Error -> snackbarHostState?.showSnackbar(message.text)
            null -> Unit
        }
        if (uiState.message != null) viewModel.onMessageConsumed()
    }

    BackupGroupCard(
        onCreateBackup = viewModel::onCreateBackupClicked,
        onRestoreBackup = viewModel::onRestoreBackupClicked
    )

    when (val dialog = uiState.dialog) {
        is BackupDialog.ExportOptions -> BackupExportOptionsDialog(
            initialOptions = dialog.options,
            onConfirm = viewModel::onExportOptionsConfirmed,
            onDismiss = viewModel::onDialogDismissed
        )

        is BackupDialog.ExportPassphrase -> BackupExportPassphraseDialog(
            errorRes = dialog.errorRes,
            onConfirm = viewModel::onExportPassphraseConfirmed,
            onDismiss = viewModel::onDialogDismissed
        )

        is BackupDialog.RestorePassphrase -> BackupRestorePassphraseDialog(
            errorRes = dialog.errorRes,
            onConfirm = viewModel::onRestorePassphraseConfirmed,
            onDismiss = viewModel::onDialogDismissed
        )

        is BackupDialog.RestoreSummary -> BackupRestoreSummaryDialog(
            preview = dialog.preview,
            onConfirm = viewModel::onRestoreConfirmed,
            onDismiss = viewModel::onRestoreSummaryDismissed
        )

        null -> Unit
    }

    uiState.operation?.let { operation ->
        BackupProgressDialog(operation = operation, onCancel = viewModel::onCancelOperation)
    }
}
@Composable
private fun BackupGroupCard(
    onCreateBackup: () -> Unit,
    onRestoreBackup: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.backup_group_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = 1.dp
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.backup_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FilledTonalButton(onClick = onCreateBackup, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(R.string.backup_create_action),
                        maxLines = 2,
                        textAlign = TextAlign.Center
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f))
                OutlinedButton(onClick = onRestoreBackup, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(R.string.backup_restore_action),
                        maxLines = 2,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
