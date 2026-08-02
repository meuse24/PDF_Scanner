package info.meuse24.pdf_scanner.ui.tableexport

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import info.meuse24.pdf_scanner.R
import info.meuse24.pdf_scanner.domain.model.CsvDelimiter
import info.meuse24.pdf_scanner.domain.model.DelimitedTextDialect
import info.meuse24.pdf_scanner.domain.model.OcrPipelineStatus
import info.meuse24.pdf_scanner.ui.components.LocalAppSnackbarHostState
import info.meuse24.pdf_scanner.ui.ocr.buildOcrLanguageOptions
import info.meuse24.pdf_scanner.ui.ocr.defaultOcrLanguage
import info.meuse24.pdf_scanner.util.buildCsvShareIntent
import java.util.Locale

private data class CellEditTarget(
    val pageIndex: Int,
    val rowIndex: Int,
    val columnIndex: Int,
    val text: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TableExportScreen(
    viewModel: TableExportViewModel = hiltViewModel()
) {
    val loading by viewModel.loading.collectAsStateWithLifecycle()
    val ocrStatus by viewModel.ocrStatus.collectAsStateWithLifecycle()
    val pageProgress by viewModel.pageProgress.collectAsStateWithLifecycle()
    val pages by viewModel.pages.collectAsStateWithLifecycle()
    val selectedPageIndex by viewModel.selectedPageIndex.collectAsStateWithLifecycle()
    val dialect by viewModel.dialect.collectAsStateWithLifecycle()
    val noTableMessage by viewModel.noTableMessage.collectAsStateWithLifecycle()
    val exporting by viewModel.exporting.collectAsStateWithLifecycle()
    val success by viewModel.success.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val pendingDraft by viewModel.pendingDraft.collectAsStateWithLifecycle()
    val shareRequest by viewModel.shareRequest.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val snackbarHostState = LocalAppSnackbarHostState.current
    val resources = LocalResources.current
    val displayLocale = resources.configuration.locales[0]
    val ocrLanguages = remember(displayLocale) {
        buildOcrLanguageOptions(
            autoLabel = resources.getString(R.string.ocr_language_auto),
            displayLocale = displayLocale
        )
    }
    var selectedLanguage by rememberSaveable { mutableStateOf(defaultOcrLanguage()) }
    var editTarget by remember { mutableStateOf<CellEditTarget?>(null) }
    val shareChooserTitle = stringResource(R.string.action_share_text)

    LaunchedEffect(success) {
        success?.let { message ->
            snackbarHostState?.showSnackbar(message)
            viewModel.clearSuccess()
        }
    }

    LaunchedEffect(shareRequest) {
        val request = shareRequest ?: return@LaunchedEffect
        buildCsvShareIntent(context, request.files, request.dialect.mimeType)?.let { intent ->
            context.startActivity(Intent.createChooser(intent, shareChooserTitle))
        }
        viewModel.onShareRequestHandled()
    }

    pendingDraft?.let {
        RestoreDraftDialog(
            onContinue = viewModel::continueDraft,
            onDiscard = { viewModel.discardDraftAndReextract(selectedLanguage) }
        )
    }

    error?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::clearError,
            title = { Text(stringResource(R.string.error_title)) },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = viewModel::clearError) { Text(stringResource(R.string.action_ok)) }
            }
        )
    }

    editTarget?.let { target ->
        CellEditDialog(
            initialText = target.text,
            onConfirm = { newText ->
                viewModel.editCell(target.pageIndex, target.rowIndex, target.columnIndex, newText)
                editTarget = null
            },
            onDismiss = { editTarget = null }
        )
    }

    Scaffold { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            when {
                loading -> LoadingContent(ocrStatus, pageProgress)
                noTableMessage != null -> NoTableContent(
                    message = noTableMessage.orEmpty(),
                    languages = ocrLanguages,
                    selectedLanguage = selectedLanguage,
                    onLanguageChange = { selectedLanguage = it },
                    onRetry = { viewModel.reExtract(selectedLanguage) }
                )
                pages.isEmpty() -> Unit
                else -> {
                    val currentPage = pages.getOrNull(selectedPageIndex) ?: pages.first()
                    if (pages.size > 1) {
                        PrimaryScrollableTabRow(selectedTabIndex = selectedPageIndex) {
                            pages.forEachIndexed { index, page ->
                                Tab(
                                    selected = index == selectedPageIndex,
                                    onClick = { viewModel.selectPage(index) },
                                    text = { Text(stringResource(R.string.table_export_page_tab, page.pageIndex + 1)) }
                                )
                            }
                        }
                    }
                    ExportControlsRow(
                        dialect = dialect,
                        pageSelectedForExport = currentPage.selectedForExport,
                        onDelimiterChange = viewModel::selectDelimiter,
                        onProtectFormulasChange = viewModel::setProtectFormulas,
                        onPageSelectedChange = { viewModel.togglePageSelectedForExport(currentPage.pageIndex, it) },
                        onReset = { viewModel.resetPage(currentPage.pageIndex) }
                    )
                    if (currentPage.hasIssues) {
                        Text(
                            text = stringResource(R.string.table_export_quality_warning),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                        )
                    }
                    TableGrid(
                        page = currentPage,
                        onCellClick = { rowIndex, columnIndex ->
                            editTarget = CellEditTarget(
                                pageIndex = currentPage.pageIndex,
                                rowIndex = rowIndex,
                                columnIndex = columnIndex,
                                text = currentPage.rows[rowIndex].cells[columnIndex].text
                            )
                        },
                        onRowIncludedChange = { rowIndex, included ->
                            viewModel.toggleRowIncluded(currentPage.pageIndex, rowIndex, included)
                        },
                        modifier = Modifier.weight(1f)
                    )
                    HorizontalDivider()
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Beide Buttons teilen sich die Breite fair auf (Modifier.weight(1f)):
                        // ohne Gewichtung kann der lange erste Button-Text (z. B. "In Downloads
                        // speichern") dem zweiten Button auf schmalen Geraeten so wenig Restbreite
                        // lassen, dass sein Text buchstabenweise umbricht (auf echtem Geraet
                        // beobachtet). maxLines/Ellipsis schuetzen zusaetzlich vor noch laengeren
                        // Uebersetzungen in anderen Locales.
                        Button(
                            onClick = viewModel::saveToDownloads,
                            enabled = !exporting && pages.any { it.selectedForExport },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null)
                            Text(
                                text = stringResource(R.string.table_export_save_downloads),
                                modifier = Modifier.padding(start = 8.dp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        OutlinedButton(
                            onClick = viewModel::shareFiles,
                            enabled = !exporting && pages.any { it.selectedForExport },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null)
                            Text(
                                text = stringResource(R.string.action_share_text),
                                modifier = Modifier.padding(start = 8.dp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LoadingContent(ocrStatus: OcrPipelineStatus?, pageProgress: Pair<Int, Int>?) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator()
        Box(modifier = Modifier.padding(top = 16.dp)) {
            val statusText = when (ocrStatus) {
                OcrPipelineStatus.PreparingModel -> stringResource(R.string.table_export_status_preparing_model)
                OcrPipelineStatus.DownloadingModel -> stringResource(R.string.table_export_status_downloading_model)
                OcrPipelineStatus.InstallingModel -> stringResource(R.string.table_export_status_installing_model)
                null -> pageProgress?.let { (current, total) ->
                    stringResource(R.string.table_export_status_page_progress, current, total)
                } ?: stringResource(R.string.table_export_status_analyzing)
            }
            Text(text = statusText, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NoTableContent(
    message: String,
    languages: List<Pair<String, String>>,
    selectedLanguage: String,
    onLanguageChange: (String) -> Unit,
    onRetry: () -> Unit
) {
    var menuExpanded by rememberSaveable { mutableStateOf(false) }
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = message, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 16.dp))
        ExposedDropdownMenuBox(expanded = menuExpanded, onExpandedChange = { menuExpanded = it }) {
            OutlinedTextField(
                value = languages.find { it.first == selectedLanguage }?.second ?: selectedLanguage,
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(R.string.dialog_ocr_language)) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = menuExpanded) },
                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth()
            )
            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                languages.forEach { (code, label) ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = { onLanguageChange(code); menuExpanded = false }
                    )
                }
            }
        }
        Button(onClick = onRetry, modifier = Modifier.padding(top = 16.dp)) {
            Text(stringResource(R.string.table_export_retry))
        }
    }
}

@Composable
private fun RestoreDraftDialog(onContinue: () -> Unit, onDiscard: () -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text(stringResource(R.string.table_export_restore_draft_title)) },
        text = { Text(stringResource(R.string.table_export_restore_draft_message)) },
        confirmButton = {
            TextButton(onClick = onContinue) { Text(stringResource(R.string.table_export_restore_draft_continue)) }
        },
        dismissButton = {
            TextButton(onClick = onDiscard) { Text(stringResource(R.string.table_export_restore_draft_discard)) }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExportControlsRow(
    dialect: DelimitedTextDialect,
    pageSelectedForExport: Boolean,
    onDelimiterChange: (CsvDelimiter) -> Unit,
    onProtectFormulasChange: (Boolean) -> Unit,
    onPageSelectedChange: (Boolean) -> Unit,
    onReset: () -> Unit
) {
    var delimiterMenuExpanded by rememberSaveable { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Checkbox(checked = pageSelectedForExport, onCheckedChange = onPageSelectedChange)
            Text(
                text = stringResource(R.string.table_export_include_page),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onReset) {
                Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.table_export_reset_page))
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ExposedDropdownMenuBox(
                expanded = delimiterMenuExpanded,
                onExpandedChange = { delimiterMenuExpanded = it },
                modifier = Modifier.weight(1f)
            ) {
                OutlinedTextField(
                    value = delimiterLabel(dialect.delimiter),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.table_export_delimiter_label)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = delimiterMenuExpanded) },
                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                    modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth()
                )
                DropdownMenu(expanded = delimiterMenuExpanded, onDismissRequest = { delimiterMenuExpanded = false }) {
                    CsvDelimiter.entries.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(delimiterLabel(option)) },
                            onClick = { onDelimiterChange(option); delimiterMenuExpanded = false }
                        )
                    }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = stringResource(R.string.table_export_protect_formulas), modifier = Modifier.weight(1f))
            Switch(checked = dialect.protectFormulas, onCheckedChange = onProtectFormulasChange)
        }
    }
}

@Composable
private fun delimiterLabel(delimiter: CsvDelimiter): String = when (delimiter) {
    CsvDelimiter.COMMA -> stringResource(R.string.table_export_delimiter_comma)
    CsvDelimiter.SEMICOLON -> stringResource(R.string.table_export_delimiter_semicolon)
    CsvDelimiter.TAB -> stringResource(R.string.table_export_delimiter_tab)
}

@Composable
private fun TableGrid(
    page: EditableTablePage,
    onCellClick: (rowIndex: Int, columnIndex: Int) -> Unit,
    onRowIncludedChange: (rowIndex: Int, included: Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    // Ein gemeinsamer horizontaler Scroll-State für alle Zeilen, sonst laufen sie beim Scrollen
    // auseinander. Spaltenbreiten kommen vorberechnet aus dem ViewModel (kein SubcomposeLayout).
    val horizontalScrollState = rememberScrollState()
    val issueDescription = stringResource(R.string.table_export_cell_issue_description)
    val rowIncludedDescription = stringResource(R.string.table_export_row_included)

    LazyColumn(modifier = modifier.fillMaxWidth()) {
        itemsIndexed(items = page.rows, key = { index, _ -> index }) { rowIndex, row ->
            Row(
                modifier = Modifier.horizontalScroll(horizontalScrollState),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = row.included,
                    onCheckedChange = { onRowIncludedChange(rowIndex, it) },
                    modifier = Modifier.semantics { contentDescription = rowIncludedDescription }
                )
                row.cells.forEachIndexed { columnIndex, cell ->
                    val widthDp = page.columnWidthsDp.getOrElse(columnIndex) { 64 }
                    val hasIssue = cell.issues.isNotEmpty()
                    Box(
                        modifier = Modifier
                            .width(widthDp.dp)
                            .padding(4.dp)
                            .then(
                                if (hasIssue) {
                                    Modifier.semantics { contentDescription = issueDescription }
                                } else {
                                    Modifier
                                }
                            )
                            .clickable { onCellClick(rowIndex, columnIndex) },
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (hasIssue) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(14.dp).padding(end = 2.dp)
                                )
                            }
                            Text(
                                text = cell.text,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
            HorizontalDivider()
        }
    }
}

@Composable
private fun CellEditDialog(
    initialText: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by rememberSaveable(initialText) { mutableStateOf(initialText) }
    // Ersttastatur per Heuristik: sieht der Zellwert strikt numerisch aus, startet der Dialog mit
    // KeyboardType.Decimal; die App schreibt Eingaben oder Trennzeichen nie selbst um.
    val keyboardType = if (looksNumeric(initialText)) KeyboardType.Decimal else KeyboardType.Text

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.table_export_cell_edit_title)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }) { Text(stringResource(R.string.action_ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}

private val numericPattern = Regex("""^[+-]?[0-9][0-9.,\s]*$""")

private fun looksNumeric(text: String): Boolean = text.isNotBlank() && numericPattern.matches(text.trim())
