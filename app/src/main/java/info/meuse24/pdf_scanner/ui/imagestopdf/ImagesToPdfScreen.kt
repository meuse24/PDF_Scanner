package info.meuse24.pdf_scanner.ui.imagestopdf

import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import info.meuse24.pdf_scanner.R
import info.meuse24.pdf_scanner.domain.usecase.ImagePageLayout
import info.meuse24.pdf_scanner.ui.components.LocalAppSnackbarHostState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

@Composable
fun ImagesToPdfScreen(
    imageUris: List<Uri>,
    onNavigateBack: () -> Unit,
    viewModel: ImagesToPdfViewModel = hiltViewModel()
) {
    val snackbarHostState = LocalAppSnackbarHostState.current
    val scope = rememberCoroutineScope()
    val editLoading by viewModel.editLoading.collectAsStateWithLifecycle()
    val error       by viewModel.error.collectAsStateWithLifecycle()
    val success     by viewModel.success.collectAsStateWithLifecycle()
    val skippedCount by viewModel.skippedCount.collectAsStateWithLifecycle()
    val pageSetup by viewModel.pageSetup.collectAsStateWithLifecycle()

    val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    val defaultFilename = stringResource(R.string.images_to_pdf_filename_default, today)
    val skippedMessage = if (skippedCount > 0) {
        stringResource(R.string.images_to_pdf_unreadable_skipped, skippedCount)
    } else {
        null
    }
    var filename by rememberSaveable(today) {
        mutableStateOf(defaultFilename)
    }
    var selectedLayout by rememberSaveable { mutableStateOf(ImagePageLayout.TWO_PER_PAGE) }

    LaunchedEffect(success) {
        if (success) {
            if (skippedMessage != null) {
                scope.launch { snackbarHostState?.showSnackbar(skippedMessage) }
            }
            onNavigateBack()
        }
    }

    LaunchedEffect(error) {
        val message = error
        if (message != null) {
            snackbarHostState?.showSnackbar(message)
            viewModel.clearError()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        ImagesPdfOptionsContent(
            imageUris = imageUris,
            selectedLayout = selectedLayout,
            onLayoutSelected = { selectedLayout = it },
            pageSetup = pageSetup,
            onPageSetupChange = viewModel::updatePageSetup,
            actionLabel = stringResource(R.string.images_to_pdf_action),
            actionEnabled = imageUris.isNotEmpty() && !editLoading,
            actionInProgress = editLoading,
            onAction = {
                val effectiveName = filename.trim().ifBlank {
                    defaultFilename
                }
                viewModel.createPdf(imageUris, effectiveName, selectedLayout)
            },
            modifier = Modifier.fillMaxSize(),
            topContent = {
                OutlinedTextField(
                    value = filename,
                    onValueChange = { filename = it },
                    label = { Text(stringResource(R.string.images_to_pdf_filename_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        )
    }
}
