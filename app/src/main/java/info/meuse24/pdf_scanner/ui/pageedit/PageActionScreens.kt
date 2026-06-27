package info.meuse24.pdf_scanner.ui.pageedit

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import info.meuse24.pdf_scanner.R

@Composable
fun RotatePagesScreen(
    onNavigateBack: () -> Unit,
    viewModel: PageSelectionViewModel = hiltViewModel()
) {
    val record by viewModel.record.collectAsStateWithLifecycle()
    val pages by viewModel.pages.collectAsStateWithLifecycle()
    val selectedPages by viewModel.selectedPages.collectAsStateWithLifecycle()
    val saveAsCopy by viewModel.saveAsCopy.collectAsStateWithLifecycle()
    val loading by viewModel.loading.collectAsStateWithLifecycle()
    val editLoading by viewModel.editLoading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val success by viewModel.success.collectAsStateWithLifecycle()
    var rotationDegrees by rememberSaveable { mutableIntStateOf(90) }

    LaunchedEffect(success) {
        if (success) onNavigateBack()
    }

    PageSelectionContent(
        pages = pages,
        selectedPages = selectedPages,
        loading = loading,
        selectionSummary = if (selectedPages.isNotEmpty()) {
            pluralStringResource(R.plurals.selection_count, selectedPages.size, selectedPages.size)
        } else {
            null
        },
        saveAsCopy = saveAsCopy,
        allowSaveAsCopy = true,
        onSaveAsCopyChange = viewModel::setSaveAsCopy,
        beforeList = {
            RotationSelector(
                rotationDegrees = rotationDegrees,
                onRotationSelected = { rotationDegrees = it }
            )
        },
        confirmLabel = stringResource(R.string.rotate_apply),
        confirmEnabled = record != null && selectedPages.isNotEmpty() && !loading && !editLoading,
        onTogglePage = viewModel::togglePage,
        onConfirm = { viewModel.rotatePages(rotationDegrees) }
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
fun DeletePagesScreen(
    onNavigateBack: () -> Unit,
    viewModel: PageSelectionViewModel = hiltViewModel()
) {
    val record by viewModel.record.collectAsStateWithLifecycle()
    val pages by viewModel.pages.collectAsStateWithLifecycle()
    val selectedPages by viewModel.selectedPages.collectAsStateWithLifecycle()
    val saveAsCopy by viewModel.saveAsCopy.collectAsStateWithLifecycle()
    val loading by viewModel.loading.collectAsStateWithLifecycle()
    val editLoading by viewModel.editLoading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val success by viewModel.success.collectAsStateWithLifecycle()
    val allPagesSelected = record != null && selectedPages.size >= record!!.pageCount

    LaunchedEffect(success) {
        if (success) onNavigateBack()
    }

    PageSelectionContent(
        pages = pages,
        selectedPages = selectedPages,
        loading = loading,
        selectionSummary = when {
            allPagesSelected -> stringResource(R.string.delete_pages_all_hint)
            selectedPages.isNotEmpty() -> pluralStringResource(
                R.plurals.selection_count,
                selectedPages.size,
                selectedPages.size
            )
            else -> null
        },
        saveAsCopy = saveAsCopy,
        allowSaveAsCopy = true,
        onSaveAsCopyChange = viewModel::setSaveAsCopy,
        confirmLabel = stringResource(R.string.delete_pages_apply),
        confirmEnabled = record != null && selectedPages.isNotEmpty() && !loading && !editLoading && !allPagesSelected,
        onTogglePage = viewModel::togglePage,
        onConfirm = { viewModel.deletePages() }
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
fun ExtractPagesScreen(
    onNavigateBack: () -> Unit,
    viewModel: PageSelectionViewModel = hiltViewModel()
) {
    val record by viewModel.record.collectAsStateWithLifecycle()
    val pages by viewModel.pages.collectAsStateWithLifecycle()
    val selectedPages by viewModel.selectedPages.collectAsStateWithLifecycle()
    val loading by viewModel.loading.collectAsStateWithLifecycle()
    val editLoading by viewModel.editLoading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val success by viewModel.success.collectAsStateWithLifecycle()

    LaunchedEffect(success) {
        if (success) onNavigateBack()
    }

    PageSelectionContent(
        pages = pages,
        selectedPages = selectedPages,
        loading = loading,
        selectionSummary = if (selectedPages.isNotEmpty()) {
            pluralStringResource(R.plurals.selection_count, selectedPages.size, selectedPages.size)
        } else {
            null
        },
        saveAsCopy = true,
        allowSaveAsCopy = false,
        onSaveAsCopyChange = {},
        confirmLabel = stringResource(R.string.extract_pages_apply),
        confirmEnabled = record != null && selectedPages.isNotEmpty() && !loading && !editLoading,
        onTogglePage = viewModel::togglePage,
        onConfirm = { viewModel.extractPages() }
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
fun DuplicatePagesScreen(
    onNavigateBack: () -> Unit,
    viewModel: PageSelectionViewModel = hiltViewModel()
) {
    val record by viewModel.record.collectAsStateWithLifecycle()
    val pages by viewModel.pages.collectAsStateWithLifecycle()
    val selectedPages by viewModel.selectedPages.collectAsStateWithLifecycle()
    val loading by viewModel.loading.collectAsStateWithLifecycle()
    val editLoading by viewModel.editLoading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val success by viewModel.success.collectAsStateWithLifecycle()

    LaunchedEffect(success) {
        if (success) onNavigateBack()
    }

    PageSelectionContent(
        pages = pages,
        selectedPages = selectedPages,
        loading = loading,
        selectionSummary = if (selectedPages.isNotEmpty()) {
            pluralStringResource(R.plurals.selection_count, selectedPages.size, selectedPages.size)
        } else {
            null
        },
        saveAsCopy = true,
        allowSaveAsCopy = false,
        onSaveAsCopyChange = {},
        confirmLabel = stringResource(R.string.duplicate_pages_apply),
        confirmEnabled = record != null && selectedPages.isNotEmpty() && !loading && !editLoading,
        onTogglePage = viewModel::togglePage,
        onConfirm = { viewModel.duplicatePages() }
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
private fun PageSelectionContent(
    pages: List<SelectablePage>,
    selectedPages: Set<Int>,
    loading: Boolean,
    selectionSummary: String?,
    saveAsCopy: Boolean,
    allowSaveAsCopy: Boolean,
    onSaveAsCopyChange: (Boolean) -> Unit,
    confirmLabel: String,
    confirmEnabled: Boolean,
    onTogglePage: (Int) -> Unit,
    onConfirm: () -> Unit,
    beforeList: @Composable (() -> Unit)? = null
) {
    Column(modifier = Modifier.fillMaxSize()) {
        if (allowSaveAsCopy) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.reorder_save_as_copy),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = stringResource(R.string.reorder_overwrite_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
                Switch(checked = saveAsCopy, onCheckedChange = onSaveAsCopyChange)
            }
        }

        if (beforeList != null) {
            beforeList()
        }

        if (selectionSummary != null) {
            Text(
                text = selectionSummary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }

        if (loading) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                items(pages, key = { it.index }) { page ->
                    SelectablePageCard(
                        page = page,
                        selected = page.index in selectedPages,
                        onToggle = { onTogglePage(page.index) }
                    )
                }
            }
        }

        Button(
            onClick = onConfirm,
            enabled = confirmEnabled,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(confirmLabel)
        }
    }
}

@Composable
private fun RotationSelector(
    rotationDegrees: Int,
    onRotationSelected: (Int) -> Unit
) {
    val options = remember {
        listOf(90 to R.string.rotate_option_90, 180 to R.string.rotate_option_180, 270 to R.string.rotate_option_270)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { (degrees, labelRes) ->
            FilterChip(
                selected = rotationDegrees == degrees,
                onClick = { onRotationSelected(degrees) },
                label = { Text(stringResource(labelRes)) }
            )
        }
    }
}

@Composable
private fun SelectablePageCard(
    page: SelectablePage,
    selected: Boolean,
    onToggle: () -> Unit
) {
    Card(
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .fillMaxWidth()
            .clickable(onClick = onToggle),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val bitmap = page.bitmap
            if (bitmap != null) {
                Image(
                    painter = BitmapPainter(bitmap.asImageBitmap()),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    if (!page.isLoaded) {
                        CircularProgressIndicator(Modifier.size(24.dp))
                    }
                }
            }

            Spacer(Modifier.width(12.dp))

            Text(
                text = stringResource(R.string.split_page_label, page.index + 1),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )

            Checkbox(
                checked = selected,
                onCheckedChange = { onToggle() }
            )
        }
    }
}
