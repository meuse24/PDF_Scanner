package info.meuse24.pdf_scanner.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import info.meuse24.pdf_scanner.domain.model.Document
import info.meuse24.pdf_scanner.ui.home.components.BulkActionBar

@Composable
internal fun HomeSelectionBar(
    selectedRecords: List<Document>,
    onShare: () -> Unit,
    onExport: () -> Unit,
    onExtractTexts: () -> Unit,
    onMakeSearchable: () -> Unit,
    onMerge: () -> Unit,
    onMoveToFolder: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    BulkActionBar(
        onShare = onShare,
        onExport = onExport,
        onExtractTexts = onExtractTexts,
        extractEnabled = selectedRecords.isNotEmpty(),
        onMakeSearchable = onMakeSearchable,
        makeSearchableEnabled = true,
        onMerge = onMerge,
        mergeEnabled = selectedRecords.size >= 2,
        onMoveToFolder = onMoveToFolder,
        onDelete = onDelete,
        modifier = modifier
    )
}
