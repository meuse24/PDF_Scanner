package info.meuse24.pdf_scanner.ui.documentaction

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Switch
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import info.meuse24.pdf_scanner.R
import info.meuse24.pdf_scanner.domain.usecase.PdfCompressionPreset
import info.meuse24.pdf_scanner.ui.components.ActionScreenContent

@Composable
fun CompressPdfScreen(
    onNavigateBack: () -> Unit,
    viewModel: DocumentEditViewModel = hiltViewModel()
) {
    val record by viewModel.record.collectAsState()
    val editLoading by viewModel.editLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val success by viewModel.success.collectAsState()
    var preset by rememberSaveable { mutableStateOf(PdfCompressionPreset.MEDIUM.name) }

    LaunchedEffect(success) {
        if (success) onNavigateBack()
    }

    ActionScreenContent(
        record = record,
        title = stringResource(R.string.compress_pdf_description),
        body = stringResource(R.string.compress_pdf_details),
        form = {
            CompressionPresetSelector(
                selectedPreset = PdfCompressionPreset.valueOf(preset),
                onSelect = { preset = it.name }
            )
        },
        confirmLabel = stringResource(R.string.compress_pdf_apply),
        confirmEnabled = record != null && !editLoading,
        onConfirm = { viewModel.compressPdf(PdfCompressionPreset.valueOf(preset)) }
    )

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

@Composable
fun ProtectPdfScreen(
    onNavigateBack: () -> Unit,
    viewModel: DocumentEditViewModel = hiltViewModel()
) {
    val record by viewModel.record.collectAsState()
    val editLoading by viewModel.editLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val success by viewModel.success.collectAsState()
    var password by rememberSaveable { mutableStateOf("") }
    var confirmPassword by rememberSaveable { mutableStateOf("") }
    val passwordsMatch = password.isNotBlank() && password == confirmPassword

    LaunchedEffect(success) {
        if (success) onNavigateBack()
    }

    ActionScreenContent(
        record = record,
        title = stringResource(R.string.protect_pdf_description),
        body = stringResource(R.string.protect_pdf_details),
        form = {
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text(stringResource(R.string.password_label)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = confirmPassword,
                onValueChange = { confirmPassword = it },
                label = { Text(stringResource(R.string.password_confirm_label)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )
            if (confirmPassword.isNotEmpty() && !passwordsMatch) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.password_mismatch),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        },
        confirmLabel = stringResource(R.string.protect_pdf_apply),
        confirmEnabled = record != null && passwordsMatch && !editLoading,
        onConfirm = { viewModel.protectPdf(password) }
    )

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

@Composable
fun UnlockPdfScreen(
    onNavigateBack: () -> Unit,
    viewModel: DocumentEditViewModel = hiltViewModel()
) {
    val record by viewModel.record.collectAsState()
    val editLoading by viewModel.editLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val success by viewModel.success.collectAsState()
    var password by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(success) {
        if (success) onNavigateBack()
    }

    ActionScreenContent(
        record = record,
        title = stringResource(R.string.unlock_pdf_description),
        body = stringResource(R.string.unlock_pdf_details),
        form = {
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text(stringResource(R.string.password_label)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmLabel = stringResource(R.string.unlock_pdf_apply),
        confirmEnabled = record != null && password.isNotBlank() && !editLoading,
        onConfirm = { viewModel.unlockPdf(password) }
    )

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

@Composable
fun RemovePasswordScreen(
    onNavigateBack: () -> Unit,
    viewModel: DocumentEditViewModel = hiltViewModel()
) {
    val record by viewModel.record.collectAsState()
    val editLoading by viewModel.editLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val success by viewModel.success.collectAsState()

    LaunchedEffect(success) {
        if (success) onNavigateBack()
    }

    ActionScreenContent(
        record = record,
        title = stringResource(R.string.remove_password_description),
        body = stringResource(R.string.remove_password_details),
        form = {},
        confirmLabel = stringResource(R.string.remove_password_apply),
        confirmEnabled = record != null && !editLoading,
        onConfirm = { viewModel.removePassword() }
    )

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

@Composable
fun RemoveTextLayerScreen(
    onNavigateBack: () -> Unit,
    viewModel: DocumentEditViewModel = hiltViewModel()
) {
    val record by viewModel.record.collectAsState()
    val editLoading by viewModel.editLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val success by viewModel.success.collectAsState()

    LaunchedEffect(success) {
        if (success) onNavigateBack()
    }

    ActionScreenContent(
        record = record,
        title = stringResource(R.string.remove_text_layer_description),
        body = stringResource(R.string.remove_text_layer_details),
        form = {},
        confirmLabel = stringResource(R.string.remove_text_layer_apply),
        confirmEnabled = record != null && !editLoading,
        onConfirm = { viewModel.removeTextLayer() }
    )

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

@Composable
fun RestrictUsageScreen(
    onNavigateBack: () -> Unit,
    viewModel: DocumentEditViewModel = hiltViewModel()
) {
    val record by viewModel.record.collectAsState()
    val editLoading by viewModel.editLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val success by viewModel.success.collectAsState()
    var ownerPassword by rememberSaveable { mutableStateOf("") }
    var confirmPassword by rememberSaveable { mutableStateOf("") }
    var canPrint by rememberSaveable { mutableStateOf(false) }
    var canCopy by rememberSaveable { mutableStateOf(false) }
    var canEdit by rememberSaveable { mutableStateOf(false) }
    val passwordsMatch = ownerPassword.isNotBlank() && ownerPassword == confirmPassword

    LaunchedEffect(success) {
        if (success) onNavigateBack()
    }

    ActionScreenContent(
        record = record,
        title = stringResource(R.string.restrict_usage_description),
        body = stringResource(R.string.restrict_usage_details),
        form = {
            OutlinedTextField(
                value = ownerPassword,
                onValueChange = { ownerPassword = it },
                label = { Text(stringResource(R.string.restrict_usage_owner_password_label)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = confirmPassword,
                onValueChange = { confirmPassword = it },
                label = { Text(stringResource(R.string.password_confirm_label)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )
            if (confirmPassword.isNotEmpty() && !passwordsMatch) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.password_mismatch),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            Spacer(Modifier.height(16.dp))
            UsageRestrictionToggle(
                label = stringResource(R.string.restrict_usage_can_print),
                checked = canPrint,
                onCheckedChange = { canPrint = it }
            )
            UsageRestrictionToggle(
                label = stringResource(R.string.restrict_usage_can_copy),
                checked = canCopy,
                onCheckedChange = { canCopy = it }
            )
            UsageRestrictionToggle(
                label = stringResource(R.string.restrict_usage_can_edit),
                checked = canEdit,
                onCheckedChange = { canEdit = it }
            )
        },
        confirmLabel = stringResource(R.string.restrict_usage_apply),
        confirmEnabled = record != null && passwordsMatch && !editLoading,
        onConfirm = { viewModel.restrictUsage(ownerPassword, canPrint, canCopy, canEdit) }
    )

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

@Composable
private fun UsageRestrictionToggle(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun CompressionPresetSelector(
    selectedPreset: PdfCompressionPreset,
    onSelect: (PdfCompressionPreset) -> Unit
) {
    val options = listOf(
        PdfCompressionPreset.LOW to R.string.compress_pdf_preset_low,
        PdfCompressionPreset.MEDIUM to R.string.compress_pdf_preset_medium,
        PdfCompressionPreset.HIGH to R.string.compress_pdf_preset_high
    )
    Column {
        Text(
            text = stringResource(R.string.compress_pdf_preset_label),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            options.forEach { (preset, labelRes) ->
                FilterChip(
                    selected = selectedPreset == preset,
                    onClick = { onSelect(preset) },
                    label = { Text(stringResource(labelRes)) }
                )
            }
        }
    }
}
