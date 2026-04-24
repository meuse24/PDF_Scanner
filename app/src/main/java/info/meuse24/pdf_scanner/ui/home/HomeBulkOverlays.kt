package info.meuse24.pdf_scanner.ui.home

import androidx.compose.runtime.Composable
import info.meuse24.pdf_scanner.domain.model.Document
import info.meuse24.pdf_scanner.domain.model.Folder
import info.meuse24.pdf_scanner.ui.home.components.HomeBulkDeleteDialog
import info.meuse24.pdf_scanner.ui.home.components.HomeBulkLanguageDialog
import info.meuse24.pdf_scanner.ui.home.components.HomeDeleteDialog
import info.meuse24.pdf_scanner.ui.home.components.FolderPickerSheet
import info.meuse24.pdf_scanner.ui.home.components.HomeRenameDialog
import info.meuse24.pdf_scanner.ui.home.components.MergeDialog

@Composable
internal fun HomeBulkOverlays(
    scans: List<Document>,
    selectedIds: Set<Long>,
    onSelectedIdsChange: (Set<Long>) -> Unit,
    pendingDeleteRecord: Document?,
    onPendingDeleteRecordChange: (Document?) -> Unit,
    showBulkDeleteConfirm: Boolean,
    onShowBulkDeleteConfirmChange: (Boolean) -> Unit,
    showMergeDialog: Boolean,
    onShowMergeDialogChange: (Boolean) -> Unit,
    mergeFilenameInput: String,
    onMergeFilenameInputChange: (String) -> Unit,
    recordToRename: Document?,
    onRecordToRenameChange: (Document?) -> Unit,
    renameInput: String,
    onRenameInputChange: (String) -> Unit,
    showFolderPicker: Boolean,
    onShowFolderPickerChange: (Boolean) -> Unit,
    folders: List<Folder>,
    showBulkLangDialog: Boolean,
    onShowBulkLangDialogChange: (Boolean) -> Unit,
    bulkLangMenuExpanded: Boolean,
    onBulkLangMenuExpandedChange: (Boolean) -> Unit,
    selectedBulkLang: String,
    onSelectedBulkLangChange: (String) -> Unit,
    bulkLangForSearchable: Boolean,
    ocrLanguages: List<Pair<String, String>>,
    viewModel: HomeViewModel
) {
    pendingDeleteRecord?.let { record ->
        HomeDeleteDialog(
            record = record,
            onConfirm = {
                viewModel.deleteScan(record)
                onSelectedIdsChange(selectedIds - record.id)
                onPendingDeleteRecordChange(null)
            },
            onDismiss = { onPendingDeleteRecordChange(null) }
        )
    }

    if (showBulkDeleteConfirm) {
        HomeBulkDeleteDialog(
            selectedCount = selectedIds.size,
            onConfirm = {
                viewModel.deleteScans(scans.filter { it.id in selectedIds })
                onSelectedIdsChange(emptySet())
                onShowBulkDeleteConfirmChange(false)
            },
            onDismiss = { onShowBulkDeleteConfirmChange(false) }
        )
    }

    if (showMergeDialog) {
        val selectedRecords = scans.filter { it.id in selectedIds }
        MergeDialog(
            filename = mergeFilenameInput,
            onFilenameChange = onMergeFilenameInputChange,
            records = selectedRecords,
            onConfirm = {
                if (mergeFilenameInput.isNotBlank()) {
                    viewModel.mergePdfs(selectedRecords, mergeFilenameInput.trim())
                    onSelectedIdsChange(emptySet())
                    onShowMergeDialogChange(false)
                }
            },
            onDismiss = { onShowMergeDialogChange(false) }
        )
    }

    recordToRename?.let { record ->
        HomeRenameDialog(
            input = renameInput,
            onInputChange = onRenameInputChange,
            onConfirm = {
                viewModel.renameScan(record, renameInput)
                onRecordToRenameChange(null)
            },
            onDismiss = { onRecordToRenameChange(null) }
        )
    }

    if (showFolderPicker) {
        FolderPickerSheet(
            folders = folders,
            onMoveToFolder = { folderId ->
                viewModel.moveScansToFolder(selectedIds, folderId)
                onSelectedIdsChange(emptySet())
                onShowFolderPickerChange(false)
            },
            onDismiss = { onShowFolderPickerChange(false) }
        )
    }

    if (showBulkLangDialog) {
        val bulkRecords = scans.filter { it.id in selectedIds }
        HomeBulkLanguageDialog(
            expanded = bulkLangMenuExpanded,
            languageCode = selectedBulkLang,
            languages = ocrLanguages,
            onExpandedChange = onBulkLangMenuExpandedChange,
            onLanguageSelected = {
                onSelectedBulkLangChange(it)
                onBulkLangMenuExpandedChange(false)
            },
            onConfirm = {
                onShowBulkLangDialogChange(false)
                if (bulkLangForSearchable) {
                    viewModel.makeSearchableScans(bulkRecords, selectedBulkLang)
                    onSelectedIdsChange(emptySet())
                } else {
                    viewModel.extractTexts(bulkRecords, selectedBulkLang)
                }
            },
            onDismiss = { onShowBulkLangDialogChange(false) }
        )
    }
}
