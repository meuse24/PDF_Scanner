package info.meuse24.pdf_scanner.ui.documentaction

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import info.meuse24.pdf_scanner.R
import info.meuse24.pdf_scanner.domain.usecase.PdfCompressionPreset
import info.meuse24.pdf_scanner.ui.components.ActionScreenContent
import info.meuse24.pdf_scanner.ui.home.HomeViewModel
import info.meuse24.pdf_scanner.ui.overlay.ScanDetailViewModel

@Composable
fun CompressPdfScreen(
    onNavigateBack: () -> Unit,
    viewModel: ScanDetailViewModel = hiltViewModel(),
    homeViewModel: HomeViewModel = hiltViewModel()
) {
    val record by viewModel.record.collectAsState()
    val editLoading by homeViewModel.editLoading.collectAsState()
    var preset by rememberSaveable { mutableStateOf(PdfCompressionPreset.MEDIUM.name) }

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
        onConfirm = {
            val currentRecord = record ?: return@ActionScreenContent
            homeViewModel.compressPdf(currentRecord, PdfCompressionPreset.valueOf(preset))
            onNavigateBack()
        }
    )
}

@Composable
fun ProtectPdfScreen(
    onNavigateBack: () -> Unit,
    viewModel: ScanDetailViewModel = hiltViewModel(),
    homeViewModel: HomeViewModel = hiltViewModel()
) {
    val record by viewModel.record.collectAsState()
    val editLoading by homeViewModel.editLoading.collectAsState()
    var password by rememberSaveable { mutableStateOf("") }
    var confirmPassword by rememberSaveable { mutableStateOf("") }
    val passwordsMatch = password.isNotBlank() && password == confirmPassword

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
        onConfirm = {
            val currentRecord = record ?: return@ActionScreenContent
            homeViewModel.protectPdf(currentRecord, password)
            onNavigateBack()
        }
    )
}

@Composable
fun UnlockPdfScreen(
    onNavigateBack: () -> Unit,
    viewModel: ScanDetailViewModel = hiltViewModel(),
    homeViewModel: HomeViewModel = hiltViewModel()
) {
    val record by viewModel.record.collectAsState()
    val editLoading by homeViewModel.editLoading.collectAsState()
    var password by rememberSaveable { mutableStateOf("") }

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
        onConfirm = {
            val currentRecord = record ?: return@ActionScreenContent
            homeViewModel.unlockPdf(currentRecord, password)
            onNavigateBack()
        }
    )
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
