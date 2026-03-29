package info.meuse24.pdf_scanner.ui.home.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import info.meuse24.pdf_scanner.R
import info.meuse24.pdf_scanner.ui.home.PendingImport

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HomeAddDocumentSheet(
    onDismiss: () -> Unit,
    onScanClick: () -> Unit,
    onImportClick: () -> Unit,
    onImagesToPdfClick: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(modifier = Modifier.padding(bottom = 24.dp)) {
            Text(
                text = stringResource(R.string.add_document_sheet_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )
            HomeAddDocumentOption(
                title = stringResource(R.string.add_document_scan_title),
                subtitle = stringResource(R.string.add_document_scan_subtitle),
                icon = { Icon(Icons.Default.CameraAlt, contentDescription = null) },
                onClick = onScanClick,
                modifier = Modifier.fillMaxWidth()
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 24.dp))
            HomeAddDocumentOption(
                title = stringResource(R.string.add_document_import_title),
                subtitle = stringResource(R.string.add_document_import_subtitle),
                icon = { Icon(Icons.Default.UploadFile, contentDescription = null) },
                onClick = onImportClick,
                modifier = Modifier.fillMaxWidth()
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 24.dp))
            HomeAddDocumentOption(
                title = stringResource(R.string.images_to_pdf_add_button),
                subtitle = stringResource(R.string.images_to_pdf_description),
                icon = { Icon(Icons.Default.Image, contentDescription = null) },
                onClick = onImagesToPdfClick,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HomeOcrResultSheet(
    text: String,
    onDismiss: () -> Unit,
    onShare: () -> Unit,
    onCopy: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(stringResource(R.string.ocr_result_title), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(4.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                SelectionContainer {
                    Text(text, style = MaterialTheme.typography.bodyMedium)
                }
            }
            Spacer(Modifier.height(16.dp))
            androidx.compose.foundation.layout.Row(
                horizontalArrangement = Arrangement.End,
                modifier = Modifier.fillMaxWidth()
            ) {
                TextButton(onClick = onShare) {
                    Icon(Icons.Default.Share, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.action_share_text))
                }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = onCopy) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.action_copy))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HomeSaveImportDialog(
    pendingImport: PendingImport?,
    filenameInput: String,
    makeSearchable: Boolean,
    selectedLanguage: String,
    languageMenuExpanded: Boolean,
    ocrLanguages: List<Pair<String, String>>,
    onFilenameChange: (String) -> Unit,
    onMakeSearchableChange: (Boolean) -> Unit,
    onLanguageMenuExpandedChange: (Boolean) -> Unit,
    onLanguageSelected: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_save_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = filenameInput,
                    onValueChange = onFilenameChange,
                    label = { Text(stringResource(R.string.dialog_filename_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                when (pendingImport) {
                    is PendingImport.Scan -> {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = stringResource(
                                R.string.dialog_scan_page_count,
                                pendingImport.result.pdf?.pageCount ?: 0
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                        Spacer(Modifier.height(12.dp))
                        androidx.compose.foundation.layout.Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                stringResource(R.string.dialog_searchable_pdf),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                            Switch(
                                checked = makeSearchable,
                                onCheckedChange = onMakeSearchableChange
                            )
                        }
                    }
                    is PendingImport.File -> {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = stringResource(
                                R.string.dialog_import_selected_file,
                                pendingImport.originalName
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                    null -> Unit
                }
                if (pendingImport is PendingImport.Scan && makeSearchable) {
                    Text(
                        stringResource(R.string.dialog_searchable_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Spacer(Modifier.height(10.dp))
                    ExposedDropdownMenuBox(
                        expanded = languageMenuExpanded,
                        onExpandedChange = onLanguageMenuExpandedChange
                    ) {
                        OutlinedTextField(
                            value = ocrLanguages.find { it.first == selectedLanguage }?.second ?: selectedLanguage,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(R.string.dialog_ocr_language)) },
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = languageMenuExpanded)
                            },
                            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                            modifier = Modifier
                                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                                .fillMaxWidth()
                        )
                        DropdownMenu(
                            expanded = languageMenuExpanded,
                            onDismissRequest = { onLanguageMenuExpandedChange(false) }
                        ) {
                            ocrLanguages.forEach { (code, name) ->
                                DropdownMenuItem(
                                    text = { Text(name) },
                                    onClick = { onLanguageSelected(code) }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}

@Composable
private fun HomeAddDocumentOption(
    title: String,
    subtitle: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    TextButton(
        onClick = onClick,
        modifier = modifier.padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            icon()
            Spacer(Modifier.width(16.dp))
            Column(horizontalAlignment = Alignment.Start) {
                Text(text = title, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}
