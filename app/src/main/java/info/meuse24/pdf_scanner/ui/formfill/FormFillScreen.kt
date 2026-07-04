package info.meuse24.pdf_scanner.ui.formfill

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import info.meuse24.pdf_scanner.R
import info.meuse24.pdf_scanner.domain.model.FormField
import info.meuse24.pdf_scanner.domain.model.FormFieldType
import info.meuse24.pdf_scanner.domain.model.FormFieldValue
import info.meuse24.pdf_scanner.ui.components.LocalAppSnackbarHostState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FormFillScreen(
    onNavigateBack: () -> Unit,
    viewModel: FormFillViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = LocalAppSnackbarHostState.current

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let { message ->
            snackbarHostState?.showSnackbar(message)
            if (state.pageBitmap != null) viewModel.clearError()
        }
    }
    LaunchedEffect(state.saved) {
        if (state.saved) onNavigateBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.form_fill_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_navigate_back)
                        )
                    }
                }
            )
        },
        bottomBar = {
            if (!state.loading && state.fields.isNotEmpty()) {
                FormFillBottomBar(
                    state = state,
                    onPrevious = { viewModel.showPage(state.currentPageIndex - 1) },
                    onNext = { viewModel.showPage(state.currentPageIndex + 1) },
                    onReset = viewModel::resetValues,
                    onFlattenChange = viewModel::setFlatten,
                    onSave = viewModel::save
                )
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding(),
            contentAlignment = Alignment.Center
        ) {
            when {
                state.loading -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.form_fill_loading))
                }
                state.fields.isEmpty() -> Text(
                    text = state.errorMessage ?: stringResource(R.string.form_fill_no_fields),
                    modifier = Modifier.padding(24.dp)
                )
                state.errorMessage != null -> Text(
                    text = state.errorMessage!!,
                    modifier = Modifier.padding(24.dp)
                )
                state.pageBitmap == null -> CircularProgressIndicator()
                else -> FormPage(
                    state = state,
                    onSingleValueChange = viewModel::updateSingleValue,
                    onListValueToggle = viewModel::toggleListValue
                )
            }

            if (state.saving) {
                Surface(
                    modifier = Modifier.fillMaxSize().alpha(0.72f),
                    color = MaterialTheme.colorScheme.scrim
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            }
        }
    }
}

@Composable
private fun FormPage(
    state: FormFillUiState,
    onSingleValueChange: (String, String) -> Unit,
    onListValueToggle: (String, String) -> Unit
) {
    val bitmap = requireNotNull(state.pageBitmap)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(bitmap.width.toFloat() / bitmap.height.toFloat())
        ) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.FillBounds
            )
            state.currentPageFields.forEach { field ->
                val rect = field.rectNormalized
                FormFieldOverlay(
                    field = field,
                    value = state.values[field.fullyQualifiedName] ?: field.value,
                    onSingleValueChange = { onSingleValueChange(field.fullyQualifiedName, it) },
                    onListValueToggle = { onListValueToggle(field.fullyQualifiedName, it) },
                    modifier = Modifier
                        .offset(x = maxWidth * rect.left, y = maxHeight * rect.top)
                        .width(maxWidth * (rect.right - rect.left))
                        .height(maxHeight * (rect.bottom - rect.top))
                )
            }
        }
    }
}

@Composable
private fun FormFieldOverlay(
    field: FormField,
    value: FormFieldValue,
    onSingleValueChange: (String) -> Unit,
    onListValueToggle: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val description = buildString {
        append(field.label)
        if (field.required) append(", ").append(stringResource(R.string.form_fill_required))
        if (field.readOnly) append(", ").append(stringResource(R.string.form_fill_read_only))
    }
    val fieldModifier = modifier.semantics { contentDescription = description }

    when (field.type) {
        FormFieldType.TEXT,
        FormFieldType.MULTILINE_TEXT -> BasicTextField(
            value = value.singleValue,
            onValueChange = { updated ->
                onSingleValueChange(field.maxLength?.let(updated::take) ?: updated)
            },
            enabled = !field.readOnly,
            singleLine = field.type == FormFieldType.TEXT,
            textStyle = MaterialTheme.typography.bodySmall.copy(
                color = MaterialTheme.colorScheme.onSurface
            ),
            modifier = fieldModifier
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f))
                .border(1.dp, MaterialTheme.colorScheme.primary)
                .padding(horizontal = 3.dp, vertical = 1.dp)
        )
        FormFieldType.CHECKBOX -> {
            val option = field.widgetOptionValue.orEmpty()
            Checkbox(
                checked = option.isNotEmpty() && value.singleValue == option,
                onCheckedChange = if (field.readOnly) {
                    null
                } else {
                    { checked -> onSingleValueChange(if (checked) option else "") }
                },
                modifier = fieldModifier
            )
        }
        FormFieldType.RADIO_GROUP -> {
            val option = field.widgetOptionValue.orEmpty()
            RadioButton(
                selected = option.isNotEmpty() && value.singleValue == option,
                onClick = if (field.readOnly || option.isEmpty()) {
                    null
                } else {
                    { onSingleValueChange(option) }
                },
                modifier = fieldModifier
            )
        }
        FormFieldType.COMBO_BOX,
        FormFieldType.LIST_BOX -> ChoiceField(
            field = field,
            value = value,
            onSingleValueChange = onSingleValueChange,
            onListValueToggle = onListValueToggle,
            modifier = fieldModifier
        )
        FormFieldType.UNSUPPORTED -> Unit
    }
}

@Composable
private fun ChoiceField(
    field: FormField,
    value: FormFieldValue,
    onSingleValueChange: (String) -> Unit,
    onListValueToggle: (String) -> Unit,
    modifier: Modifier
) {
    var expanded by remember(field.id) { mutableStateOf(false) }
    Box(modifier = modifier) {
        val displayedValue = field.options
            .filter { it.value in value.values }
            .joinToString { it.label }
            .ifEmpty { field.label }
        Text(
            text = displayedValue,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 2,
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.94f))
                .border(1.dp, MaterialTheme.colorScheme.primary)
                .clickable(enabled = !field.readOnly) { expanded = true }
                .padding(3.dp)
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            field.options.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (field.multiSelect) {
                                Checkbox(
                                    checked = option.value in value.values,
                                    onCheckedChange = null
                                )
                                Spacer(Modifier.size(8.dp))
                            }
                            Text(option.label)
                        }
                    },
                    onClick = {
                        if (field.multiSelect) {
                            onListValueToggle(option.value)
                        } else {
                            onSingleValueChange(option.value)
                            expanded = false
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun FormFillBottomBar(
    state: FormFillUiState,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onReset: () -> Unit,
    onFlattenChange: (Boolean) -> Unit,
    onSave: () -> Unit
) {
    Surface(shadowElevation = 6.dp) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onPrevious,
                    enabled = state.currentPageIndex > 0
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        stringResource(R.string.form_fill_previous_page)
                    )
                }
                Text(
                    stringResource(
                        R.string.form_fill_page,
                        state.currentPageIndex + 1,
                        state.pageCount
                    )
                )
                IconButton(
                    onClick = onNext,
                    enabled = state.currentPageIndex + 1 < state.pageCount
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        stringResource(R.string.form_fill_next_page)
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onReset) {
                    Icon(Icons.Default.Refresh, stringResource(R.string.form_fill_reset))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.form_fill_flatten),
                        style = MaterialTheme.typography.labelLarge
                    )
                    Text(
                        stringResource(R.string.form_fill_flatten_description),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Switch(checked = state.flatten, onCheckedChange = onFlattenChange)
                Button(onClick = onSave, enabled = !state.saving) {
                    Text(stringResource(R.string.form_fill_save))
                }
            }
        }
    }
}
