package info.meuse24.pdf_scanner.ui.overlay

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import info.meuse24.pdf_scanner.R
import info.meuse24.pdf_scanner.ui.components.ActionScreenContent
import info.meuse24.pdf_scanner.ui.documentaction.DocumentEditViewModel

@Composable
fun PageNumbersScreen(
    onNavigateBack: () -> Unit,
    viewModel: DocumentEditViewModel = hiltViewModel()
) {
    val record by viewModel.record.collectAsStateWithLifecycle()
    val editLoading by viewModel.editLoading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val success by viewModel.success.collectAsStateWithLifecycle()

    LaunchedEffect(success) {
        if (success) onNavigateBack()
    }

    ActionScreenContent(
        record = record,
        title = stringResource(R.string.page_numbers_description),
        body = stringResource(R.string.page_numbers_details),
        confirmLabel = stringResource(R.string.page_numbers_apply),
        confirmEnabled = record != null && !editLoading,
        onConfirm = { viewModel.addPageNumbers() }
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
fun TextWatermarkScreen(
    onNavigateBack: () -> Unit,
    viewModel: DocumentEditViewModel = hiltViewModel()
) {
    val record by viewModel.record.collectAsStateWithLifecycle()
    val editLoading by viewModel.editLoading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val success by viewModel.success.collectAsStateWithLifecycle()
    var watermarkText by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(success) {
        if (success) onNavigateBack()
    }

    ActionScreenContent(
        record = record,
        title = stringResource(R.string.watermark_description),
        body = stringResource(R.string.watermark_details),
        form = {
            OutlinedTextField(
                value = watermarkText,
                onValueChange = { watermarkText = it },
                label = { Text(stringResource(R.string.watermark_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmLabel = stringResource(R.string.watermark_apply),
        confirmEnabled = record != null && watermarkText.isNotBlank() && !editLoading,
        onConfirm = { viewModel.applyTextWatermark(watermarkText) }
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
