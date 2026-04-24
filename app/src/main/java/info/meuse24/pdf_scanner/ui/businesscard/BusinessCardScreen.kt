package info.meuse24.pdf_scanner.ui.businesscard

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import info.meuse24.pdf_scanner.R

@Composable
fun BusinessCardScreen(
    onNavigateBack: () -> Unit,
    viewModel: BusinessCardViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val shareVCardLabel = stringResource(R.string.business_card_share_vcard)
    val noContactAppMessage = stringResource(R.string.business_card_no_contact_app)

    LaunchedEffect(uiState.externalAction) {
        val action = uiState.externalAction ?: return@LaunchedEffect
        val intent = when (action) {
            is BusinessCardExternalAction.AddToContacts -> Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(action.uri, "text/x-vcard")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            is BusinessCardExternalAction.ShareVCard -> Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/x-vcard"
                    putExtra(Intent.EXTRA_STREAM, action.uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                },
                shareVCardLabel
            )
        }

        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            viewModel.reportError(noContactAppMessage)
        } finally {
            viewModel.consumeExternalAction()
        }
    }

    if (uiState.loading && uiState.record == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.business_card_title),
                style = MaterialTheme.typography.headlineSmall
            )
            Text(
                text = stringResource(R.string.business_card_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedTextField(
                value = uiState.fullName,
                onValueChange = viewModel::updateFullName,
                label = { Text(stringResource(R.string.business_card_name)) },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = uiState.organization,
                onValueChange = viewModel::updateOrganization,
                label = { Text(stringResource(R.string.business_card_organization)) },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = uiState.jobTitle,
                onValueChange = viewModel::updateJobTitle,
                label = { Text(stringResource(R.string.business_card_job_title)) },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = uiState.emails,
                onValueChange = viewModel::updateEmails,
                label = { Text(stringResource(R.string.business_card_emails)) },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = uiState.phones,
                onValueChange = viewModel::updatePhones,
                label = { Text(stringResource(R.string.business_card_phones)) },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = uiState.urls,
                onValueChange = viewModel::updateUrls,
                label = { Text(stringResource(R.string.business_card_urls)) },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = uiState.address,
                onValueChange = viewModel::updateAddress,
                label = { Text(stringResource(R.string.business_card_address)) },
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = viewModel::addToContacts,
                enabled = !uiState.loading,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.business_card_add_to_contacts))
            }
            TextButton(
                onClick = viewModel::shareVCard,
                enabled = !uiState.loading,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.business_card_share_vcard))
            }
            TextButton(
                onClick = onNavigateBack,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    }

    if (uiState.loading) {
        AlertDialog(
            onDismissRequest = {},
            title = null,
            text = {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            },
            confirmButton = {}
        )
    }

    uiState.error?.let { message ->
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
