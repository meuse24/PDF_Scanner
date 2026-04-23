package info.meuse24.pdf_scanner.ui.ocr

import android.content.ClipData
import android.content.Intent
import android.widget.Toast
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
import androidx.compose.material3.DropdownMenu
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
import info.meuse24.pdf_scanner.util.OcrQuality
import info.meuse24.pdf_scanner.util.toQualityPercent
import java.util.Locale
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OcrReviewScreen(
    viewModel: OcrReviewViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val resources = LocalResources.current
    val coroutineScope = rememberCoroutineScope()
    val copiedMessage = stringResource(R.string.ocr_copied)
    val shareTextLabel = stringResource(R.string.action_share_text)
    val displayLocale = resources.configuration.locales[0] ?: Locale.getDefault()
    val ocrLanguages = remember(displayLocale) {
        buildOcrLanguageOptions(
            autoLabel = resources.getString(R.string.ocr_language_auto),
            displayLocale = displayLocale
        )
    }
    var selectedLanguage by rememberSaveable(state.recognizedLanguage) {
        mutableStateOf(state.recognizedLanguage ?: OCR_LANGUAGE_AUTO)
    }
    var languageMenuExpanded by rememberSaveable { mutableStateOf(false) }

    val record = state.record
    val displayText = state.text.orEmpty()

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
                    when {
                        record != null -> {
                            Text(
                                text = record.filename,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = stringResource(R.string.overlay_document_info, record.pageCount),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        state.loading -> {
                            CircularProgressIndicator()
                        }
                        else -> {
                            Text(
                                text = stringResource(R.string.ocr_review_missing_record),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        }

        item {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = stringResource(R.string.ocr_review_quality_title),
                        style = MaterialTheme.typography.titleSmall
                    )
                    OcrQualityBadge(
                        quality = state.quality,
                        percent = state.confidence.toQualityPercent()
                    )
                    HorizontalDivider()
                    Text(
                        text = stringResource(R.string.ocr_review_language_title),
                        style = MaterialTheme.typography.labelLarge
                    )
                    Text(
                        text = displayLanguageName(
                            languageCode = state.recognizedLanguage,
                            displayLocale = displayLocale,
                            unknownLabel = stringResource(R.string.ocr_review_language_unknown)
                        ),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (state.quality == OcrQuality.LOW) {
                        Text(
                            text = stringResource(R.string.ocr_review_low_warning),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    state.error?.let { message ->
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
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
                        text = stringResource(R.string.ocr_review_actions_title),
                        style = MaterialTheme.typography.titleSmall
                    )
                    ExposedDropdownMenuBox(
                        expanded = languageMenuExpanded,
                        onExpandedChange = { languageMenuExpanded = it }
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
                            onDismissRequest = { languageMenuExpanded = false }
                        ) {
                            ocrLanguages.forEach { (code, label) ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = {
                                        selectedLanguage = code
                                        languageMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                viewModel.clearError()
                                viewModel.reExtract(selectedLanguage)
                            },
                            enabled = record != null && !state.loading
                        ) {
                            Icon(Icons.Default.Translate, contentDescription = null)
                            Text(
                                text = stringResource(R.string.ocr_review_run_ocr),
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                        OutlinedButton(
                            onClick = {
                                coroutineScope.launch {
                                    clipboard.setClipEntry(ClipData.newPlainText("", displayText).toClipEntry())
                                    Toast.makeText(context, copiedMessage, Toast.LENGTH_SHORT).show()
                                }
                            },
                            enabled = displayText.isNotBlank()
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null)
                            Text(
                                text = stringResource(R.string.action_copy),
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                        OutlinedButton(
                            onClick = {
                                context.startActivity(
                                    Intent.createChooser(
                                        Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_TEXT, displayText)
                                        },
                                        shareTextLabel
                                    )
                                )
                            },
                            enabled = displayText.isNotBlank()
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
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.ocr_result_title),
                        style = MaterialTheme.typography.titleSmall
                    )
                    when {
                        state.loading && state.pageTexts.isEmpty() -> {
                            CircularProgressIndicator()
                        }
                        state.pageTexts.isEmpty() -> {
                            Text(
                                text = state.error ?: stringResource(R.string.ocr_review_no_text),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        else -> {
                            SelectionContainer {
                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    state.pageTexts.forEachIndexed { index, pageText ->
                                        if (state.pageTexts.size > 1) {
                                            Text(
                                                text = stringResource(R.string.ocr_review_page_header, index + 1),
                                                style = MaterialTheme.typography.labelLarge,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Text(
                                            text = pageText,
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
}

private fun displayLanguageName(
    languageCode: String?,
    displayLocale: Locale,
    unknownLabel: String
): String {
    if (languageCode.isNullOrBlank()) return unknownLabel
    val displayName = Locale.forLanguageTag(languageCode)
        .getDisplayLanguage(displayLocale)
        .trim()
    return if (displayName.isBlank()) {
        languageCode.uppercase(displayLocale)
    } else {
        displayName.replaceFirstChar { char ->
            if (char.isLowerCase()) char.titlecase(displayLocale) else char.toString()
        }
    }
}
