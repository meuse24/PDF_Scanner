package info.meuse24.pdf_scanner.ui.sync

import android.Manifest
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import info.meuse24.pdf_scanner.R
import info.meuse24.pdf_scanner.domain.model.LocalSyncSession
import info.meuse24.pdf_scanner.domain.model.LocalSyncState
import info.meuse24.pdf_scanner.ui.components.LocalAppSnackbarHostState

/**
 * No own Scaffold/TopAppBar here on purpose: the shared shell in AppNavigation.kt already
 * renders the back button + title (see AppBarTitle.kt) for this route, same as Settings/
 * Privacy/Help/Info. Adding another TopAppBar here would double up the app bar.
 */
@Composable
fun LocalSyncScreen(viewModel: LocalSyncViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = LocalAppSnackbarHostState.current

    // The foreground service posts an ongoing notification (with the Stop action) while
    // the server runs; without this, Android 13+ silently drops that notification and the
    // user has no visible way to stop the connection short of reopening this screen.
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { }

    fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let { message ->
            snackbarHostState?.showSnackbar(message)
            viewModel.clearError()
        }
    }

    if (state.showFirstUseNotice) {
        AlertDialog(
            onDismissRequest = viewModel::onFirstUseCancelled,
            title = { Text(stringResource(R.string.local_sync_first_use_title)) },
            text = { Text(stringResource(R.string.local_sync_first_use_message)) },
            confirmButton = {
                Button(onClick = {
                    requestNotificationPermissionIfNeeded()
                    viewModel.onFirstUseConfirmed()
                }) {
                    Text(stringResource(R.string.local_sync_first_use_confirm))
                }
            },
            dismissButton = {
                OutlinedButton(onClick = viewModel::onFirstUseCancelled) {
                    Text(stringResource(R.string.local_sync_first_use_cancel))
                }
            }
        )
    }

    LocalSyncContent(
        state = state.state,
        onConnect = {
            requestNotificationPermissionIfNeeded()
            viewModel.onConnectClicked()
        },
        onDisconnect = viewModel::onDisconnectClicked
    )
}

@Composable
private fun LocalSyncContent(
    state: LocalSyncState,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        when (state) {
            is LocalSyncState.Stopped, is LocalSyncState.Error -> {
                Button(onClick = onConnect) {
                    Text(stringResource(R.string.local_sync_start_button))
                }
            }
            LocalSyncState.Starting -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.local_sync_starting))
                }
            }
            is LocalSyncState.Running -> {
                RunningContent(session = state.session, onDisconnect = onDisconnect)
            }
        }
    }
}

@Composable
private fun RunningContent(session: LocalSyncSession, onDisconnect: () -> Unit) {
    val context = LocalContext.current
    val shareTitle = stringResource(R.string.cd_share)
    val shareText = stringResource(R.string.local_sync_share_text, session.url, session.pin)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = stringResource(R.string.local_sync_instructions),
            style = MaterialTheme.typography.bodyMedium
        )
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp)
            ) {
                Text(
                    text = session.url,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center
                )
                IconButton(
                    onClick = {
                        val sendIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, shareText)
                        }
                        context.startActivity(Intent.createChooser(sendIntent, shareTitle))
                    }
                ) {
                    Icon(Icons.Default.Share, contentDescription = shareTitle)
                }
            }
        }
        Text(
            text = "${stringResource(R.string.local_sync_pin_label)}: ${session.pin}",
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = onDisconnect) {
            Text(stringResource(R.string.local_sync_stop_button))
        }
    }
}
