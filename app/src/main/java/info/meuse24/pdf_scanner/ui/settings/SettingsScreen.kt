package info.meuse24.pdf_scanner.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import info.meuse24.pdf_scanner.R
import info.meuse24.pdf_scanner.ui.components.LocalAppSnackbarHostState
import info.meuse24.pdf_scanner.ui.ocr.buildOcrLanguageOptions
import info.meuse24.pdf_scanner.domain.model.ThemeMode
import info.meuse24.pdf_scanner.domain.model.AppSettings
import info.meuse24.pdf_scanner.domain.model.AppSortOrder
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: AppSettings,
    onThemeModeChange: (ThemeMode) -> Unit,
    onM24AnimationEnabledChange: (Boolean) -> Unit,
    onDefaultMakeSearchableChange: (Boolean) -> Unit,
    onAutoTaggingEnabledChange: (Boolean) -> Unit,
    onDefaultOcrLanguageChange: (String) -> Unit,
    onRetroTagDocuments: () -> Unit,
    onDefaultSortOrderChange: (AppSortOrder) -> Unit,
    onTrashUndoSnackbarSecondsChange: (Int) -> Unit,
    onAppLockEnabledChange: (Boolean) -> Unit,
    onAppLockTimeoutSecondsChange: (Int) -> Unit,
    transientSuccess: String?,
    transientError: String?,
    onTransientSuccessConsumed: () -> Unit,
    onTransientErrorConsumed: () -> Unit,
    backupSection: @Composable () -> Unit = {}
) {
    val snackbarHostState = LocalAppSnackbarHostState.current
    val resources = LocalResources.current
    val displayLocale = resources.configuration.locales[0] ?: Locale.getDefault()
    val ocrAutoLabel = stringResource(R.string.ocr_language_auto)
    val ocrLanguages = remember(displayLocale, ocrAutoLabel) {
        buildOcrLanguageOptions(ocrAutoLabel, displayLocale)
    }
    var languageMenuExpanded by remember { mutableStateOf(false) }
    var trashUndoSnackbarExpanded by remember { mutableStateOf(false) }
    var appLockTimeoutExpanded by remember { mutableStateOf(false) }
    val trashUndoSnackbarOptions = remember { listOf(5, 10, 15, 30, 60) }
    val timeoutOptions = remember { listOf(0, 15, 30, 60, 300) }

    LaunchedEffect(transientSuccess) {
        if (transientSuccess != null) {
            snackbarHostState?.showSnackbar(transientSuccess)
            onTransientSuccessConsumed()
        }
    }

    LaunchedEffect(transientError) {
        if (transientError != null) {
            snackbarHostState?.showSnackbar(transientError)
            onTransientErrorConsumed()
        }
    }

    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item {
            SettingsGroup(
                icon = Icons.Default.Palette,
                title = stringResource(R.string.settings_appearance_title)
            ) {
                SegmentedPreference(
                    title = stringResource(R.string.settings_theme_mode_label),
                    options = listOf(
                        ThemeMode.SYSTEM to stringResource(R.string.theme_mode_system),
                        ThemeMode.LIGHT to stringResource(R.string.theme_mode_light),
                        ThemeMode.DARK to stringResource(R.string.theme_mode_dark)
                    ),
                    selected = settings.themeMode,
                    onSelected = onThemeModeChange
                )
                SettingsDivider()
                SettingsSwitchRow(
                    title = stringResource(R.string.settings_m24_animation_label),
                    checked = settings.m24AnimationEnabled,
                    onCheckedChange = onM24AnimationEnabledChange
                )
            }
        }

        item {
            SettingsGroup(
                icon = Icons.Default.Search,
                title = stringResource(R.string.settings_scan_ocr_title)
            ) {
                SettingsSwitchRow(
                    title = stringResource(R.string.settings_default_make_searchable_label),
                    checked = settings.defaultMakeSearchable,
                    onCheckedChange = onDefaultMakeSearchableChange
                )
                SettingsDivider()
                SettingsSwitchRow(
                    title = stringResource(R.string.settings_auto_tagging_label),
                    checked = settings.autoTaggingEnabled,
                    onCheckedChange = onAutoTaggingEnabledChange
                )
                SettingsDivider()
                DropdownPreference(
                    title = stringResource(R.string.settings_default_ocr_language_label),
                    value = ocrLanguages.find { it.first == settings.defaultOcrLanguage }?.second
                        ?: settings.defaultOcrLanguage.uppercase(displayLocale),
                    expanded = languageMenuExpanded,
                    onExpandedChange = { languageMenuExpanded = it },
                    options = ocrLanguages.map { it.first to it.second },
                    onOptionSelected = { code ->
                        onDefaultOcrLanguageChange(code)
                        languageMenuExpanded = false
                    }
                )
            }
        }

        item {
            SettingsGroup(
                icon = Icons.AutoMirrored.Filled.Sort,
                title = stringResource(R.string.settings_archive_title)
            ) {
                SegmentedPreference(
                    title = stringResource(R.string.settings_default_sort_order_label),
                    options = listOf(
                        AppSortOrder.BY_DATE to stringResource(R.string.sort_by_date),
                        AppSortOrder.BY_NAME to stringResource(R.string.sort_by_name),
                        AppSortOrder.BY_SIZE to stringResource(R.string.sort_by_size)
                    ),
                    selected = settings.defaultSortOrder,
                    onSelected = onDefaultSortOrderChange
                )
                SettingsDivider()
                DropdownPreference(
                    title = stringResource(R.string.settings_trash_undo_duration_label),
                    value = formatTrashUndoDurationLabel(settings.trashUndoSnackbarSeconds),
                    expanded = trashUndoSnackbarExpanded,
                    onExpandedChange = { trashUndoSnackbarExpanded = it },
                    options = trashUndoSnackbarOptions.map { it to formatTrashUndoDurationLabel(it) },
                    onOptionSelected = { seconds ->
                        onTrashUndoSnackbarSecondsChange(seconds)
                        trashUndoSnackbarExpanded = false
                    }
                )
                SettingsDivider()
                PreferenceActionRow {
                    FilledTonalButton(
                        onClick = onRetroTagDocuments,
                        enabled = settings.autoTaggingEnabled,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = stringResource(R.string.settings_retro_tag_action),
                            maxLines = 2,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }

        item {
            SettingsGroup(
                icon = Icons.Default.Lock,
                title = stringResource(R.string.settings_security_title)
            ) {
                SettingsSwitchRow(
                    title = stringResource(R.string.settings_app_lock_label),
                    checked = settings.appLockEnabled,
                    onCheckedChange = onAppLockEnabledChange
                )

                if (settings.appLockEnabled) {
                    SettingsDivider()
                    DropdownPreference(
                        title = stringResource(R.string.settings_app_lock_timeout_label),
                        value = formatTimeoutLabel(settings.appLockTimeoutSeconds),
                        expanded = appLockTimeoutExpanded,
                        onExpandedChange = { appLockTimeoutExpanded = it },
                        options = timeoutOptions.map { it to formatTimeoutLabel(it) },
                        onOptionSelected = { seconds ->
                            onAppLockTimeoutSecondsChange(seconds)
                            appLockTimeoutExpanded = false
                        }
                    )
                }
            }
        }

        item {
            backupSection()
        }
    }
}

@Composable
private fun formatTimeoutLabel(seconds: Int): String = when (seconds) {
    0 -> stringResource(R.string.settings_app_lock_timeout_immediately)
    15 -> stringResource(R.string.settings_app_lock_timeout_15s)
    30 -> stringResource(R.string.settings_app_lock_timeout_30s)
    60 -> stringResource(R.string.settings_app_lock_timeout_1m)
    300 -> stringResource(R.string.settings_app_lock_timeout_5m)
    else -> stringResource(R.string.settings_app_lock_timeout_custom, seconds)
}

@Composable
private fun formatTrashUndoDurationLabel(seconds: Int): String =
    stringResource(R.string.settings_trash_undo_duration_seconds, seconds)

@Composable
private fun SettingsGroup(
    icon: ImageVector,
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader(icon = icon, title = title)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = 1.dp
        ) {
            Column(content = content)
        }
    }
}

@Composable
private fun SectionHeader(
    icon: ImageVector,
    title: String
) {
    Row(
        modifier = Modifier.padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    ListItem(
        headlineContent = {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge
            )
        },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange
            )
        },
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
    )
}

@Composable
private fun <T> SegmentedPreference(
    title: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelected: (T) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        PreferenceTitle(title)
        Spacer(Modifier.height(10.dp))
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, option ->
                SegmentedButton(
                    selected = option.first == selected,
                    onClick = { onSelected(option.first) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size)
                ) {
                    Text(
                        text = option.second,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> DropdownPreference(
    title: String,
    value: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    options: List<Pair<T, String>>,
    onOptionSelected: (T) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        PreferenceTitle(title)
        Spacer(Modifier.height(8.dp))
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = onExpandedChange
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = {},
                readOnly = true,
                singleLine = true,
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
            )
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { onExpandedChange(false) }
            ) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.second) },
                        onClick = { onOptionSelected(option.first) }
                    )
                }
            }
        }
    }
}

@Composable
private fun PreferenceActionRow(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        content()
    }
}

@Composable
private fun PreferenceTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface
    )
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)
    )
}
