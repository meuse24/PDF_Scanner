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
import androidx.compose.material.icons.automirrored.filled.CallMerge
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.automirrored.filled.ManageSearch
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.automirrored.filled.TextSnippet
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SwapVert
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
                    HelpAction(Icons.Default.PhotoCamera, R.string.nav_start_scanner, R.string.help_desc_start_scanner),
                    HelpAction(Icons.AutoMirrored.Filled.Help, R.string.nav_help, R.string.help_desc_help),
                    HelpAction(Icons.Default.Info, R.string.nav_info, R.string.help_desc_info),
                    HelpAction(Icons.Default.PrivacyTip, R.string.nav_privacy, R.string.help_desc_privacy)
                )
            ),
            HelpSection(
                icon = Icons.Default.Share,
                titleRes = R.string.help_group_selection_menu,
                actions = listOf(
                    HelpAction(Icons.Default.Share, R.string.cd_share, R.string.help_desc_share),
                    HelpAction(Icons.Default.Download, R.string.action_export, R.string.help_desc_export),
                    HelpAction(Icons.AutoMirrored.Filled.CallMerge, R.string.cd_merge, R.string.help_item_merge),
                    HelpAction(Icons.AutoMirrored.Filled.TextSnippet, R.string.cd_extract_text, R.string.help_item_extract_text),
                    HelpAction(Icons.AutoMirrored.Filled.ManageSearch, R.string.cd_make_searchable, R.string.help_item_make_searchable),
                    HelpAction(Icons.Default.Delete, R.string.cd_delete, R.string.help_item_delete)
                )
            ),
            HelpSection(
                icon = Icons.Default.SwapVert,
                titleRes = R.string.help_section_pdf_pages,
                actions = listOf(
                    HelpAction(Icons.Default.ContentCut, R.string.action_split, R.string.help_item_split),
                    HelpAction(Icons.Default.SwapVert, R.string.action_reorder, R.string.help_item_reorder)
                )
            ),
            HelpSection(
                icon = Icons.AutoMirrored.Filled.RotateRight,
                titleRes = R.string.help_section_pdf_page_changes,
                actions = listOf(
                    HelpAction(Icons.AutoMirrored.Filled.RotateRight, R.string.action_rotate, R.string.help_item_rotate),
                    HelpAction(Icons.Default.Delete, R.string.action_delete_pages, R.string.help_item_delete_pages),
                    HelpAction(Icons.Default.PictureAsPdf, R.string.action_extract_pages, R.string.help_item_extract_pages),
                    HelpAction(Icons.Default.ContentCopy, R.string.action_duplicate_pages, R.string.help_item_duplicate_pages)
                )
            ),
            HelpSection(
                icon = Icons.Default.FormatListNumbered,
                titleRes = R.string.help_section_pdf_marks,
                actions = listOf(
                    HelpAction(Icons.Default.FormatListNumbered, R.string.action_page_numbers, R.string.help_item_page_numbers),
                    HelpAction(Icons.AutoMirrored.Filled.BrandingWatermark, R.string.action_text_watermark, R.string.help_item_watermark)
                )
            ),
            HelpSection(
                icon = Icons.Default.PictureAsPdf,
                titleRes = R.string.help_section_pdf_output,
                actions = listOf(
                    HelpAction(Icons.Default.Compress, R.string.action_compress_pdf, R.string.help_item_compress_pdf),
                    HelpAction(Icons.Default.Lock, R.string.action_protect_pdf, R.string.help_item_protect_pdf),
                    HelpAction(Icons.Default.LockOpen, R.string.action_unlock_pdf, R.string.help_item_unlock_pdf),
                    HelpAction(Icons.Default.Draw, R.string.action_sign_pdf, R.string.help_item_sign_pdf)
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
    val actionTargetIndices = remember(helpSections) {
        var nextIndex = 1
        buildMap {
            helpSections.forEach { section ->
                nextIndex += 1 // GroupHeader überspringen
                section.actions.forEach { action ->
                    put(action.titleRes, nextIndex++)
                }
            }
        }
    }

    val showJumpToContents by remember {
        derivedStateOf { lazyListState.firstVisibleItemIndex >= 1 }
    }

    fun scrollTo(titleRes: Int) {
        actionTargetIndices[titleRes]?.let { index ->
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
                TableOfContents(sections = helpSections, onActionClick = ::scrollTo)
            }

            // ── Kapitel-Inhalt ────────────────────────────────────────────────
            helpSections.forEach { section ->
                item(key = "header_${section.titleRes}") {
                    GroupHeader(section.titleRes)
                }
                items(section.actions, key = { it.titleRes }) { action ->
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
    onActionClick: (Int) -> Unit
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
                TocSectionBlock(section = section, onActionClick = onActionClick)
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
    onActionClick: (Int) -> Unit
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
                onClick = { onActionClick(action.titleRes) }
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
