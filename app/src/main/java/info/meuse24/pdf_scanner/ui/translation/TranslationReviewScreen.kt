package info.meuse24.pdf_scanner.ui.translation

import android.content.ClipData
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.toClipEntry
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import info.meuse24.pdf_scanner.R
import info.meuse24.pdf_scanner.ui.components.LocalAppSnackbarHostState
import info.meuse24.pdf_scanner.ui.ocr.displayLanguageName
import kotlinx.coroutines.launch
import java.util.Locale

private val TRANSLATE_LANGUAGES = listOf("en", "de", "es", "fr", "pt", "ru", "ar", "hi", "zh", "ja")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TranslationReviewScreen(
    viewModel: TranslationReviewViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val resources = LocalResources.current
    val snackbarHostState = LocalAppSnackbarHostState.current
    val coroutineScope = rememberCoroutineScope()
    val displayLocale = resources.configuration.locales[0] ?: Locale.getDefault()

    val copiedMessage = stringResource(R.string.translate_copied_message)
    val shareLabel = stringResource(R.string.action_share_text)

    val languageOptions = remember(displayLocale) {
        buildTranslateLanguageOptions(displayLocale)
    }

    var sourceLanguage by rememberSaveable { mutableStateOf(TranslationReviewViewModel.DEFAULT_SOURCE) }
    var targetLanguage by rememberSaveable { mutableStateOf(TranslationReviewViewModel.DEFAULT_TARGET) }


    LaunchedEffect(state.record?.id) {
        val lang = state.record?.ocrLanguage
            ?.takeIf { it.isNotBlank() && it != "und" && it in TRANSLATE_LANGUAGES }
        if (lang != null) {
            sourceLanguage = lang
            if (targetLanguage == lang) {
                targetLanguage = if (lang == "en") "de" else "en"
            }
        }
    }
    var sourceMenuExpanded by rememberSaveable { mutableStateOf(false) }
    var targetMenuExpanded by rememberSaveable { mutableStateOf(false) }

    val fullTranslation = state.result?.fullText.orEmpty()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val record = state.record
                    if (record != null) {
                        Text(record.filename, style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = stringResource(R.string.overlay_document_info, record.pageCount),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        CircularProgressIndicator()
                    }
                }
            }
        }

        item {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.translate_settings_title),
                        style = MaterialTheme.typography.titleSmall
                    )

                    ExposedDropdownMenuBox(
                        expanded = sourceMenuExpanded,
                        onExpandedChange = { sourceMenuExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = languageOptions.find { it.first == sourceLanguage }?.second ?: sourceLanguage,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(R.string.translate_source_language)) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = sourceMenuExpanded) },
                            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                            modifier = Modifier
                                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                                .fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = sourceMenuExpanded,
                            onDismissRequest = { sourceMenuExpanded = false }
                        ) {
                            languageOptions.forEach { (code, label) ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = {
                                        sourceLanguage = code
                                        sourceMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    ExposedDropdownMenuBox(
                        expanded = targetMenuExpanded,
                        onExpandedChange = { targetMenuExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = languageOptions.find { it.first == targetLanguage }?.second ?: targetLanguage,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(R.string.translate_target_language)) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = targetMenuExpanded) },
                            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                            modifier = Modifier
                                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                                .fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = targetMenuExpanded,
                            onDismissRequest = { targetMenuExpanded = false }
                        ) {
                            languageOptions.forEach { (code, label) ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = {
                                        targetLanguage = code
                                        targetMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    val progress = state.progress
                    val errorMsg = state.error
                    when {
                        state.isLoading && progress != null -> {
                            Text(
                                text = stringResource(R.string.translate_progress, progress.first, progress.second),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            CircularProgressIndicator()
                        }
                        state.isLoading -> {
                            Text(
                                text = stringResource(R.string.translate_downloading_model),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            CircularProgressIndicator()
                        }
                        errorMsg != null -> {
                            Text(
                                text = errorMsg,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                viewModel.clearError()
                                viewModel.translate(sourceLanguage, targetLanguage)
                            },
                            enabled = state.record != null && !state.isLoading
                        ) {
                            Icon(Icons.Default.Translate, contentDescription = null)
                            Text(
                                text = stringResource(R.string.translate_action_button),
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                        OutlinedButton(
                            onClick = {
                                coroutineScope.launch {
                                    clipboard.setClipEntry(
                                        ClipData.newPlainText("", fullTranslation).toClipEntry()
                                    )
                                    snackbarHostState?.showSnackbar(copiedMessage)
                                }
                            },
                            enabled = fullTranslation.isNotBlank()
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null)
                            Text(
                                text = stringResource(R.string.translate_copy_all),
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                        OutlinedButton(
                            onClick = {
                                context.startActivity(
                                    Intent.createChooser(
                                        Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_TEXT, fullTranslation)
                                        },
                                        shareLabel
                                    )
                                )
                            },
                            enabled = fullTranslation.isNotBlank()
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null)
                            Text(
                                text = stringResource(R.string.action_share_text),
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }
                }
            }
        }

        item {
            val result = state.result
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.translate_result_title),
                        style = MaterialTheme.typography.titleSmall
                    )
                    if (result == null && !state.isLoading) {
                        Text(
                            text = stringResource(R.string.translate_no_result),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else if (result != null) {
                        SelectionContainer {
                            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                result.pageTranslations.forEachIndexed { index, translation ->
                                    val sourcePageIndex = result.sourcePageIndices
                                        .getOrElse(index) { index }
                                    val originalText = state.record?.pageTexts
                                        ?.getOrNull(sourcePageIndex)
                                        ?: state.record?.extractedText.orEmpty()

                                    if (result.pageTranslations.size > 1) {
                                        val unknownLabel = stringResource(R.string.ocr_review_language_unknown)
                                        val srcDisplay = displayLanguageName(
                                            languageCode = result.sourceLanguage,
                                            displayLocale = displayLocale,
                                            unknownLabel = unknownLabel
                                        )
                                        val tgtDisplay = displayLanguageName(
                                            languageCode = result.targetLanguage,
                                            displayLocale = displayLocale,
                                            unknownLabel = unknownLabel
                                        )
                                        Text(
                                            text = stringResource(
                                                R.string.translate_page_header,
                                                sourcePageIndex + 1,
                                                srcDisplay,
                                                tgtDisplay
                                            ),
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }

                                    if (originalText.isNotBlank()) {
                                        Text(
                                            text = stringResource(R.string.translate_original_label),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = originalText,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        HorizontalDivider()
                                    }

                                    Text(
                                        text = stringResource(R.string.translate_translation_label),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = translation,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun buildTranslateLanguageOptions(displayLocale: Locale): List<Pair<String, String>> =
    TRANSLATE_LANGUAGES.map { code ->
        val name = Locale.forLanguageTag(code).getDisplayLanguage(displayLocale)
            .replaceFirstChar { c -> if (c.isLowerCase()) c.titlecase(displayLocale) else c.toString() }
        code to name
    }
