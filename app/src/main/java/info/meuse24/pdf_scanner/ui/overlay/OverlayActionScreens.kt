package info.meuse24.pdf_scanner.ui.overlay

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import info.meuse24.pdf_scanner.R
import info.meuse24.pdf_scanner.ui.components.ActionScreenContent
import info.meuse24.pdf_scanner.ui.home.HomeViewModel

@Composable
fun PageNumbersScreen(
    onNavigateBack: () -> Unit,
    viewModel: ScanDetailViewModel = hiltViewModel(),
    homeViewModel: HomeViewModel = hiltViewModel()
) {
    val record by viewModel.record.collectAsState()
    val editLoading by homeViewModel.editLoading.collectAsState()

    ActionScreenContent(
        record = record,
        title = stringResource(R.string.page_numbers_description),
        body = stringResource(R.string.page_numbers_details),
        confirmLabel = stringResource(R.string.page_numbers_apply),
        confirmEnabled = record != null && !editLoading,
        onConfirm = {
            val currentRecord = record ?: return@ActionScreenContent
            homeViewModel.addPageNumbers(currentRecord)
            onNavigateBack()
        }
    )
}

@Composable
fun TextWatermarkScreen(
    onNavigateBack: () -> Unit,
    viewModel: ScanDetailViewModel = hiltViewModel(),
    homeViewModel: HomeViewModel = hiltViewModel()
) {
    val record by viewModel.record.collectAsState()
    val editLoading by homeViewModel.editLoading.collectAsState()
    var watermarkText by rememberSaveable { mutableStateOf("") }

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
        onConfirm = {
            val currentRecord = record ?: return@ActionScreenContent
            homeViewModel.applyTextWatermark(currentRecord, watermarkText)
            onNavigateBack()
        }
    )
}
