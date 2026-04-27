package info.meuse24.pdf_scanner.ui.home.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import info.meuse24.pdf_scanner.R
import info.meuse24.pdf_scanner.domain.model.Document
import info.meuse24.pdf_scanner.domain.model.Folder
import info.meuse24.pdf_scanner.ui.components.ScanAction
import info.meuse24.pdf_scanner.ui.home.SortOrder
import info.meuse24.pdf_scanner.ui.home.sortOrderLabel

@Composable
internal fun HomeArchiveContent(
    scans: List<Document>,
    filteredScans: List<Document>,
    folders: List<Folder>,
    currentFolder: Folder?,
    favoritesFilter: Boolean,
    currentTagKey: String?,
    availableTagKeys: List<String>,
    searchQuery: String,
    sortOrder: SortOrder,
    selectedIds: Set<Long>,
    isSelectionMode: Boolean,
    listState: LazyListState,
    onSearchQueryChange: (String) -> Unit,
    onSortOrderSelected: (SortOrder) -> Unit,
    onShowAllDocuments: () -> Unit,
    onTagSelected: (String) -> Unit,
    onClearSelection: () -> Unit,
    onSelectAll: () -> Unit,
    onSelectionToggle: (Document) -> Unit,
    onOpenRecord: (Document) -> Unit,
    onToggleFavorite: (Document) -> Unit,
    onAction: (Document, ScanAction) -> Unit,
    modifier: Modifier = Modifier,
    isLandscapeCompact: Boolean = false
) {
    val folderNamesById = remember(folders) { folders.associateBy({ it.id }, { it.name }) }
    val showSearchBar by remember(listState, searchQuery, sortOrder, filteredScans.size) {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val hasMoreItemsBelow =
                layoutInfo.visibleItemsInfo.lastOrNull()?.index?.let { it < layoutInfo.totalItemsCount - 1 } == true
            val hasScrollableArchive =
                listState.firstVisibleItemIndex > 0 ||
                    listState.firstVisibleItemScrollOffset > 0 ||
                    hasMoreItemsBelow

            searchQuery.isNotEmpty() || sortOrder != SortOrder.ByDate || hasScrollableArchive
        }
    }

    if (isSelectionMode && !isLandscapeCompact) {
        SelectionTitleBar(
            count = selectedIds.size,
            total = filteredScans.size,
            onClearSelection = onClearSelection,
            onSelectAll = onSelectAll
        )
    } else {
        Column {
            if (favoritesFilter || currentFolder != null) {
                Text(
                    text = currentFolder?.name ?: stringResource(R.string.folder_favorites),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }
            if (availableTagKeys.isNotEmpty()) {
                TagFilterRow(
                    currentTagKey = currentTagKey,
                    availableTagKeys = availableTagKeys,
                    onShowAllDocuments = onShowAllDocuments,
                    onTagSelected = onTagSelected
                )
            }
            if (showSearchBar) {
                HomeSearchBar(
                    searchQuery = searchQuery,
                    sortOrder = sortOrder,
                    onSearchQueryChange = onSearchQueryChange,
                    onSortOrderSelected = onSortOrderSelected
                )
            }
        }
    }

    when {
        scans.isEmpty() -> EmptyStateContent(modifier = modifier)
        filteredScans.isEmpty() && searchQuery.isNotEmpty() -> {
            Box(
                modifier = modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    stringResource(R.string.search_no_results, searchQuery),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
        else -> {
            LazyColumn(
                state = listState,
                modifier = modifier,
                contentPadding = PaddingValues(
                    top = if (isLandscapeCompact) 4.dp else 8.dp,
                    bottom = when {
                        isLandscapeCompact -> 8.dp
                        isSelectionMode -> 80.dp
                        else -> 88.dp
                    }
                )
            ) {
                itemsIndexed(filteredScans, key = { _, it -> it.id }) { index, record ->
                    val isSelected = record.id in selectedIds
                    ScanItem(
                        record = record,
                        isSelected = isSelected,
                        inSelectionMode = isSelectionMode,
                        compact = isLandscapeCompact,
                        modifier = Modifier.padding(
                            horizontal = 16.dp,
                            vertical = if (isLandscapeCompact) 2.dp else 4.dp
                        ),
                        onClick = {
                            if (isSelectionMode) onSelectionToggle(record) else onOpenRecord(record)
                        },
                        onCheckboxToggle = { onSelectionToggle(record) },
                        onToggleFavorite = { onToggleFavorite(record) },
                        folderLabel = record.folderId?.let(folderNamesById::get),
                        onAction = { action -> onAction(record, action) }
                    )
                    if (index < filteredScans.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 28.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TagFilterRow(
    currentTagKey: String?,
    availableTagKeys: List<String>,
    onShowAllDocuments: () -> Unit,
    onTagSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
        LazyRow(
            modifier = modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 1.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                FilterChip(
                    selected = currentTagKey == null,
                    onClick = onShowAllDocuments,
                    label = { Text(stringResource(R.string.filter_tag_all)) },
                    modifier = Modifier.height(32.dp)
                )
            }
            items(availableTagKeys) { tagKey ->
                FilterChip(
                    selected = currentTagKey == tagKey,
                    onClick = { onTagSelected(tagKey) },
                    label = { Text(tagLabel(tagKey)) },
                    modifier = Modifier.height(32.dp)
                )
            }
        }
    }
}

@Composable
private fun tagLabel(tagKey: String): String = when (tagKey) {
    "invoice" -> stringResource(R.string.filter_tag_invoice)
    "contract" -> stringResource(R.string.filter_tag_contract)
    "insurance" -> stringResource(R.string.filter_tag_insurance)
    "certificate" -> stringResource(R.string.filter_tag_certificate)
    "bank" -> stringResource(R.string.filter_tag_bank)
    "delivery" -> stringResource(R.string.filter_tag_delivery)
    else -> tagKey
}

@Composable
private fun HomeSearchBar(
    searchQuery: String,
    sortOrder: SortOrder,
    onSearchQueryChange: (String) -> Unit,
    onSortOrderSelected: (SortOrder) -> Unit,
    modifier: Modifier = Modifier
) {
    var sortMenuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            placeholder = { Text(stringResource(R.string.search_placeholder)) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = if (searchQuery.isNotEmpty()) {
                {
                    IconButton(onClick = { onSearchQueryChange("") }) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = stringResource(R.string.cd_search_clear)
                        )
                    }
                }
            } else {
                null
            },
            singleLine = true,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(8.dp))
        Box {
            IconButton(onClick = { sortMenuExpanded = true }) {
                Icon(
                    Icons.AutoMirrored.Filled.Sort,
                    contentDescription = stringResource(
                        R.string.cd_sort_documents,
                        stringResource(sortOrderLabel(sortOrder))
                    )
                )
            }
            DropdownMenu(
                expanded = sortMenuExpanded,
                onDismissRequest = { sortMenuExpanded = false }
            ) {
                SortOrder.entries.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(stringResource(sortOrderLabel(option))) },
                        onClick = {
                            onSortOrderSelected(option)
                            sortMenuExpanded = false
                        }
                    )
                }
            }
        }
    }
}

