package info.meuse24.pdf_scanner.ui.help

import androidx.annotation.StringRes
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.BrandingWatermark
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.automirrored.filled.MergeType
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.automirrored.filled.TextSnippet
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.InvertColors
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.FindInPage
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.BorderColor
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.NoEncryption
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PostAdd
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import info.meuse24.pdf_scanner.R
import kotlinx.coroutines.launch

private data class HelpAction(
    val icon: ImageVector,
    @param:StringRes val titleRes: Int,
    @param:StringRes val descriptionRes: Int
)

private data class HelpSection(
    val icon: ImageVector,
    @param:StringRes val titleRes: Int,
    val actions: List<HelpAction>
)

@Composable
fun HelpScreen() {
    val helpSections = remember {
        listOf(
            HelpSection(
                icon = Icons.Default.Menu,
                titleRes = R.string.help_group_drawer_menu,
                actions = listOf(
                    HelpAction(Icons.Default.FolderOpen, R.string.nav_archive, R.string.help_desc_archive),
                    HelpAction(Icons.Default.Delete, R.string.nav_trash, R.string.trash_empty_body),
                    HelpAction(Icons.AutoMirrored.Filled.Help, R.string.nav_help, R.string.help_desc_help),
                    HelpAction(Icons.Default.Info, R.string.nav_info, R.string.help_desc_info),
                    HelpAction(Icons.Default.PrivacyTip, R.string.nav_privacy, R.string.help_desc_privacy)
                )
            ),
            HelpSection(
                icon = Icons.Default.CameraAlt,
                titleRes = R.string.help_section_add_documents,
                actions = listOf(
                    HelpAction(Icons.Default.CameraAlt, R.string.add_document_scan_title, R.string.help_item_add_document_scan),
                    HelpAction(Icons.Default.UploadFile, R.string.add_document_import_title, R.string.help_item_add_document_import),
                    HelpAction(Icons.Default.Image, R.string.images_to_pdf_title, R.string.images_to_pdf_description),
                    HelpAction(Icons.Default.Share, R.string.share_pdf_title, R.string.help_item_external_share),
                    HelpAction(Icons.Default.CameraAlt, R.string.help_title_app_shortcuts, R.string.help_item_app_shortcuts)
                )
            ),
            HelpSection(
                icon = Icons.Default.FolderOpen,
                titleRes = R.string.help_section_archive_search,
                actions = listOf(
                    HelpAction(Icons.Default.PictureAsPdf, R.string.action_open_document, R.string.help_item_open),
                    HelpAction(Icons.Default.CheckBox, R.string.action_select_documents, R.string.help_item_select),
                    HelpAction(Icons.Default.Search, R.string.help_section_search, R.string.help_item_search),
                    HelpAction(Icons.Default.FolderOpen, R.string.folder_manage, R.string.help_item_folders)
                )
            ),
            HelpSection(
                icon = Icons.Default.Share,
                titleRes = R.string.help_group_selection_menu,
                actions = listOf(
                    HelpAction(Icons.Default.Share, R.string.cd_share, R.string.help_desc_share),
                    HelpAction(Icons.Default.FolderOpen, R.string.label_bulk_move_folder, R.string.help_item_folders),
                    HelpAction(Icons.AutoMirrored.Filled.TextSnippet, R.string.cd_extract_text, R.string.help_item_extract_text),
                    HelpAction(Icons.Default.FindInPage, R.string.cd_make_searchable, R.string.help_item_make_searchable),
                    HelpAction(Icons.Default.Delete, R.string.cd_delete, R.string.help_item_delete),
                    HelpAction(Icons.Default.Download, R.string.action_export, R.string.help_desc_export),
                    HelpAction(Icons.AutoMirrored.Filled.TextSnippet, R.string.docx_export_action, R.string.help_item_export_docx),
                    HelpAction(Icons.AutoMirrored.Filled.MergeType, R.string.cd_merge, R.string.help_item_merge)
                )
            ),
            HelpSection(
                icon = Icons.Default.Info,
                titleRes = R.string.sheet_section_document,
                actions = listOf(
                    HelpAction(Icons.Default.DriveFileRenameOutline, R.string.action_rename,       R.string.help_item_rename),
                    HelpAction(Icons.Default.Info,                   R.string.action_pdf_metadata, R.string.help_item_pdf_metadata),
                    HelpAction(Icons.Default.FindInPage,             R.string.hash_action_calculate, R.string.help_item_sha256),
                    HelpAction(Icons.Default.Print,                  R.string.action_print_pdf,    R.string.help_item_print_pdf)
                )
            ),
            HelpSection(
                icon = Icons.Default.SwapVert,
                titleRes = R.string.sheet_section_pages,
                actions = listOf(
                    HelpAction(Icons.Default.SwapVert,                R.string.action_reorder,         R.string.help_item_reorder),
                    HelpAction(Icons.AutoMirrored.Filled.RotateRight, R.string.action_rotate,          R.string.help_item_rotate),
                    HelpAction(Icons.Default.PostAdd,                 R.string.action_append_pages,    R.string.append_pages_body),
                    HelpAction(Icons.Default.PictureAsPdf,            R.string.action_extract_pages,   R.string.help_item_extract_pages),
                    HelpAction(Icons.Default.ContentCopy,             R.string.action_duplicate_pages, R.string.help_item_duplicate_pages),
                    HelpAction(Icons.Default.ContentCut,              R.string.action_split,           R.string.help_item_split),
                    HelpAction(Icons.Default.Delete,                  R.string.action_delete_pages,    R.string.help_item_delete_pages)
                )
            ),
            HelpSection(
                icon = Icons.Default.BorderColor,
                titleRes = R.string.sheet_section_edit,
                actions = listOf(
                    HelpAction(Icons.Default.BorderColor,                       R.string.action_annotate_pdf,   R.string.help_item_annotate_pdf),
                    HelpAction(Icons.Default.Draw,                              R.string.action_sign_pdf,       R.string.help_item_sign_pdf),
                    HelpAction(Icons.Default.FormatListNumbered,               R.string.action_page_numbers,  R.string.help_item_page_numbers),
                    HelpAction(Icons.AutoMirrored.Filled.BrandingWatermark,    R.string.action_text_watermark, R.string.help_item_watermark),
                    HelpAction(Icons.Default.Block,                             R.string.action_redact_pdf,     R.string.help_item_redact_pdf)
                )
            ),
            HelpSection(
                icon = Icons.Default.QrCodeScanner,
                titleRes = R.string.sheet_section_analyse,
                actions = listOf(
                    HelpAction(Icons.Default.QrCodeScanner, R.string.action_scan_qr_codes,    R.string.help_item_scan_qr_codes),
                    HelpAction(Icons.Default.PictureAsPdf,  R.string.action_scan_business_card, R.string.help_item_business_card),
                    HelpAction(Icons.Default.FindInPage,    R.string.action_remove_text_layer, R.string.help_item_remove_text_layer),
                    HelpAction(Icons.Default.Language,      R.string.dialog_ocr_language,      R.string.help_item_ocr_language),
                    HelpAction(Icons.Default.Translate,     R.string.translate_action_label,   R.string.help_item_translate)
                )
            ),
            HelpSection(
                icon = Icons.Default.Print,
                titleRes = R.string.sheet_section_export,
                actions = listOf(
                    HelpAction(Icons.Default.Image, R.string.action_export_as_jpg, R.string.help_item_export_pages_to_folder),
                    HelpAction(Icons.AutoMirrored.Filled.TextSnippet, R.string.docx_export_action, R.string.help_item_export_docx),
                    HelpAction(Icons.Default.InvertColors, R.string.action_grayscale_pdf, R.string.help_item_grayscale_pdf),
                    HelpAction(Icons.Default.Compress,     R.string.action_compress_pdf,  R.string.help_item_compress_pdf)
                )
            ),
            HelpSection(
                icon = Icons.Default.Lock,
                titleRes = R.string.sheet_section_security,
                actions = listOf(
                    HelpAction(Icons.Default.Lock,               R.string.settings_app_lock_label, R.string.help_item_app_lock),
                    HelpAction(Icons.Default.Lock,               R.string.backup_group_title,      R.string.help_item_encrypted_backup),
                    HelpAction(Icons.Default.Lock,               R.string.action_protect_pdf,     R.string.help_item_protect_pdf),
                    HelpAction(Icons.Default.AdminPanelSettings, R.string.action_restrict_usage,  R.string.help_item_restrict_usage),
                    HelpAction(Icons.Default.LockOpen,           R.string.action_unlock_pdf,      R.string.help_item_unlock_pdf),
                    HelpAction(Icons.Default.NoEncryption,       R.string.action_remove_password, R.string.help_item_remove_password)
                )
            )
        )
    }

    val lazyListState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // LazyColumn-Struktur:
    //   index 0             → TableOfContents (gesamtes IHV als ein einziges Item)
    //   index 1             → GroupHeader[Abschnitt 0]
    //   index 2..N          → ActionCards[Abschnitt 0]
    //   index N+1           → GroupHeader[Abschnitt 1]
    //   ...
    // Damit: nextIndex = 1 (ein Item vor dem ersten GroupHeader: das IHV-Item).
    // Composite-Key "sectionTitleRes_actionTitleRes" verhindert Kollisionen bei gleichen titleRes
    val actionTargetIndices = remember(helpSections) {
        var nextIndex = 1
        buildMap<String, Int> {
            helpSections.forEach { section ->
                nextIndex += 1 // GroupHeader überspringen
                section.actions.forEach { action ->
                    put("${section.titleRes}_${action.titleRes}", nextIndex++)
                }
            }
        }
    }

    val showJumpToContents by remember {
        derivedStateOf { lazyListState.firstVisibleItemIndex >= 1 }
    }

    fun scrollTo(key: String) {
        actionTargetIndices[key]?.let { index ->
            scope.launch { lazyListState.animateScrollToItem(index) }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = lazyListState,
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── Inhaltsverzeichnis (ein einziges Item) ────────────────────────
            item(key = "toc") {
                TableOfContents(sections = helpSections, onSectionActionClick = ::scrollTo)
            }

            // ── Kapitel-Inhalt ────────────────────────────────────────────────
            helpSections.forEach { section ->
                item(key = "header_${section.titleRes}") {
                    GroupHeader(section.titleRes)
                }
                items(section.actions, key = { "${section.titleRes}_${it.titleRes}" }) { action ->
                    ActionCard(action)
                }
            }

            item { Spacer(Modifier.height(8.dp)) }
        }

        if (showJumpToContents) {
            ExtendedFloatingActionButton(
                onClick = { scope.launch { lazyListState.animateScrollToItem(0) } },
                icon = { Icon(Icons.Default.KeyboardArrowUp, contentDescription = null) },
                text = { Text(stringResource(R.string.help_back_to_contents)) },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
            )
        }
    }
}

// ─── Inhaltsverzeichnis ───────────────────────────────────────────────────────

@Composable
private fun TableOfContents(
    sections: List<HelpSection>,
    onSectionActionClick: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp)) {
            // IHV-Kopfzeile
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.secondary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.FormatListNumbered,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondary,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(Modifier.width(14.dp))
                Text(
                    text = stringResource(R.string.help_section_contents),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f))
            Spacer(Modifier.height(12.dp))

            // Abschnitte
            sections.forEachIndexed { index, section ->
                TocSectionBlock(section = section, onSectionActionClick = onSectionActionClick)
                if (index < sections.size - 1) {
                    Spacer(Modifier.height(8.dp))
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f)
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun TocSectionBlock(
    section: HelpSection,
    onSectionActionClick: (String) -> Unit
) {
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 4.dp)
        ) {
            Icon(
                imageVector = section.icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.secondary
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(section.titleRes),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
        section.actions.forEach { action ->
            TocEntry(
                icon = action.icon,
                title = stringResource(action.titleRes),
                onClick = { onSectionActionClick("${section.titleRes}_${action.titleRes}") }
            )
        }
    }
}

@Composable
private fun TocEntry(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 5.dp, horizontal = 4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.65f)
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer
        )
    }
}

// ─── Kapitel-Inhalt ───────────────────────────────────────────────────────────

@Composable
private fun GroupHeader(@StringRes titleRes: Int) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(18.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.primary)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = stringResource(titleRes),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun ActionCard(action: HelpAction) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            HelpCardHeader(
                icon = action.icon,
                title = stringResource(action.titleRes),
                titleStyle = MaterialTheme.typography.titleMedium,
                titleColor = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(action.descriptionRes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun HelpCardHeader(
    icon: ImageVector,
    title: String,
    titleStyle: TextStyle,
    titleColor: Color
) {
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
        Text(text = title, style = titleStyle, color = titleColor)
    }
}
