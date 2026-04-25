package info.meuse24.pdf_scanner.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import info.meuse24.pdf_scanner.R
import info.meuse24.pdf_scanner.ui.components.LocalAppSnackbarHostState
import info.meuse24.pdf_scanner.ui.ocr.buildOcrLanguageOptions
import info.meuse24.pdf_scanner.ui.theme.ThemeMode
import info.meuse24.pdf_scanner.util.AppSettings
import info.meuse24.pdf_scanner.util.AppSortOrder
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: AppSettings,
    onThemeModeChange: (ThemeMode) -> Unit,
    onM24AnimationEnabledChange: (Boolean) -> Unit,
    onDefaultMakeSearchableChange: (Boolean) -> Unit,
    onDefaultOcrLanguageChange: (String) -> Unit,
    onDefaultSortOrderChange: (AppSortOrder) -> Unit,
    onTrashUndoSnackbarSecondsChange: (Int) -> Unit,
    onAppLockEnabledChange: (Boolean) -> Unit,
    onAppLockTimeoutSecondsChange: (Int) -> Unit,
    transientError: String?,
    onTransientErrorConsumed: () -> Unit
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
    val trashUndoSnackbarOptions = remember {
        listOf(5, 10, 15, 30, 60)
    }
    val timeoutOptions = remember {
        listOf(0, 15, 30, 60, 300)
    }

    LaunchedEffect(transientError) {
        if (transientError != null) {
            snackbarHostState?.showSnackbar(transientError)
            onTransientErrorConsumed()
        }
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SettingsSectionCard(
                icon = Icons.Default.Palette,
                title = stringResource(R.string.settings_appearance_title)
            ) {
                Text(
                    text = stringResource(R.string.settings_theme_mode_label),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(8.dp))
                ThemeModeOptionRow(
                    label = stringResource(R.string.theme_mode_system),
                    selected = settings.themeMode == ThemeMode.SYSTEM,
                    onClick = { onThemeModeChange(ThemeMode.SYSTEM) }
                )
                ThemeModeOptionRow(
                    label = stringResource(R.string.theme_mode_light),
                    selected = settings.themeMode == ThemeMode.LIGHT,
                    onClick = { onThemeModeChange(ThemeMode.LIGHT) }
                )
                ThemeModeOptionRow(
                    label = stringResource(R.string.theme_mode_dark),
                    selected = settings.themeMode == ThemeMode.DARK,
                    onClick = { onThemeModeChange(ThemeMode.DARK) }
                )
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.settings_m24_animation_label),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = settings.m24AnimationEnabled,
                        onCheckedChange = onM24AnimationEnabledChange
                    )
                }
            }
        }

        item {
            SettingsSectionCard(
                icon = Icons.Default.Search,
                title = stringResource(R.string.settings_scan_ocr_title)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.settings_default_make_searchable_label),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = settings.defaultMakeSearchable,
                        onCheckedChange = onDefaultMakeSearchableChange
                    )
                }

                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.settings_default_ocr_language_label),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(8.dp))
                ExposedDropdownMenuBox(
                    expanded = languageMenuExpanded,
                    onExpandedChange = { languageMenuExpanded = it }
                ) {
                    OutlinedTextField(
                        value = ocrLanguages.find { it.first == settings.defaultOcrLanguage }?.second
                            ?: settings.defaultOcrLanguage.uppercase(displayLocale),
                        onValueChange = {},
                        readOnly = true,
                        singleLine = true,
                        label = { Text(stringResource(R.string.dialog_ocr_language)) },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = languageMenuExpanded)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                    )
                    DropdownMenu(
                        expanded = languageMenuExpanded,
                        onDismissRequest = { languageMenuExpanded = false }
                    ) {
                        ocrLanguages.forEach { (code, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    onDefaultOcrLanguageChange(code)
                                    languageMenuExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        }

        item {
            SettingsSectionCard(
                icon = Icons.AutoMirrored.Filled.Sort,
                title = stringResource(R.string.settings_archive_title)
            ) {
                Text(
                    text = stringResource(R.string.settings_default_sort_order_label),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(8.dp))
                SortOrderOptionRow(
                    label = stringResource(R.string.sort_by_date),
                    selected = settings.defaultSortOrder == AppSortOrder.BY_DATE,
                    onClick = { onDefaultSortOrderChange(AppSortOrder.BY_DATE) }
                )
                SortOrderOptionRow(
                    label = stringResource(R.string.sort_by_name),
                    selected = settings.defaultSortOrder == AppSortOrder.BY_NAME,
                    onClick = { onDefaultSortOrderChange(AppSortOrder.BY_NAME) }
                )
                SortOrderOptionRow(
                    label = stringResource(R.string.sort_by_size),
                    selected = settings.defaultSortOrder == AppSortOrder.BY_SIZE,
                    onClick = { onDefaultSortOrderChange(AppSortOrder.BY_SIZE) }
                )

                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.settings_trash_undo_duration_label),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(8.dp))
                ExposedDropdownMenuBox(
                    expanded = trashUndoSnackbarExpanded,
                    onExpandedChange = { trashUndoSnackbarExpanded = it }
                ) {
                    OutlinedTextField(
                        value = formatTrashUndoDurationLabel(settings.trashUndoSnackbarSeconds),
                        onValueChange = {},
                        readOnly = true,
                        singleLine = true,
                        label = { Text(stringResource(R.string.settings_trash_undo_duration_label)) },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = trashUndoSnackbarExpanded)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                    )
                    DropdownMenu(
                        expanded = trashUndoSnackbarExpanded,
                        onDismissRequest = { trashUndoSnackbarExpanded = false }
                    ) {
                        trashUndoSnackbarOptions.forEach { seconds ->
                            DropdownMenuItem(
                                text = { Text(formatTrashUndoDurationLabel(seconds)) },
                                onClick = {
                                    onTrashUndoSnackbarSecondsChange(seconds)
                                    trashUndoSnackbarExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        }

        item {
            SettingsSectionCard(
                icon = Icons.Default.Lock,
                title = stringResource(R.string.settings_security_title)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.settings_app_lock_label),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = settings.appLockEnabled,
                        onCheckedChange = onAppLockEnabledChange
                    )
                }

                if (settings.appLockEnabled) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.settings_app_lock_timeout_label),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(8.dp))
                    ExposedDropdownMenuBox(
                        expanded = appLockTimeoutExpanded,
                        onExpandedChange = { appLockTimeoutExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = formatTimeoutLabel(settings.appLockTimeoutSeconds),
                            onValueChange = {},
                            readOnly = true,
                            singleLine = true,
                            label = { Text(stringResource(R.string.settings_app_lock_timeout_label)) },
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = appLockTimeoutExpanded)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                        )
                        DropdownMenu(
                            expanded = appLockTimeoutExpanded,
                            onDismissRequest = { appLockTimeoutExpanded = false }
                        ) {
                            timeoutOptions.forEach { seconds ->
                                DropdownMenuItem(
                                    text = { Text(formatTimeoutLabel(seconds)) },
                                    onClick = {
                                        onAppLockTimeoutSecondsChange(seconds)
                                        appLockTimeoutExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
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
private fun ThemeModeOptionRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun SortOrderOptionRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun SettingsSectionCard(
    icon: ImageVector,
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(Modifier.width(14.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}
