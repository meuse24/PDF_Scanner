package info.meuse24.pdf_scanner.ui.home.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import info.meuse24.pdf_scanner.domain.model.Document
import info.meuse24.pdf_scanner.ui.components.DocumentEditSheet
import info.meuse24.pdf_scanner.ui.home.LandscapeSelectionTopBar
import info.meuse24.pdf_scanner.ui.theme.PDF_ScannerTheme
import info.meuse24.pdf_scanner.domain.model.ThemeMode

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun ScanItemPreview() {
    PDF_ScannerTheme(dynamicColor = false) {
        ScanItem(
            record = previewDocument,
            isSelected = false,
            onClick = {},
            onCheckboxToggle = {},
            onToggleFavorite = {},
            folderLabel = "Invoices",
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun ScanItemSelectedPreview() {
    PDF_ScannerTheme(dynamicColor = false) {
        ScanItem(
            record = previewDocument.copy(isFavorite = true),
            isSelected = true,
            inSelectionMode = true,
            onClick = {},
            onCheckboxToggle = {},
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Preview(showBackground = true, widthDp = 640, heightDp = 360, name = "ScanItem Landscape Compact")
@Composable
private fun ScanItemLandscapeCompactPreview() {
    PDF_ScannerTheme(dynamicColor = false) {
        ScanItem(
            record = previewDocument.copy(filename = "Bilder_2026-04-24"),
            isSelected = true,
            inSelectionMode = true,
            compact = true,
            onClick = {},
            onCheckboxToggle = {},
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
        )
    }
}

@Preview(showBackground = true, widthDp = 640, heightDp = 360, name = "Selection TopBar Landscape")
@Composable
private fun LandscapeSelectionTopBarPreview() {
    PDF_ScannerTheme(dynamicColor = false) {
        LandscapeSelectionTopBar(
            selectedRecords = listOf(previewDocument, previewDocument.copy(id = 2L)),
            totalCount = 8,
            onClearSelection = {},
            onSelectAll = {},
            onShare = {},
            onExport = {},
            onExportDocx = {},
            onExportOcrText = {},
            onExtractTexts = {},
            onMakeSearchable = {},
            onMerge = {},
            onMoveToFolder = {},
            onDelete = {}
        )
    }
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun BulkActionBarPreview() {
    PDF_ScannerTheme(dynamicColor = false) {
        Column {
            BulkActionBar(
                onShare = {},
                onExport = {},
                onExportDocx = {},
                onExportOcrText = {},
                onExtractTexts = {},
                onMakeSearchable = {},
                onMerge = {},
                onMoveToFolder = {},
                onDelete = {},
                mergeEnabled = true
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun DocumentEditSheetPreview() {
    PDF_ScannerTheme(dynamicColor = false) {
        DocumentEditSheet(
            record = previewDocument,
            onAction = {}
        )
    }
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun ScanItemDarkPreview() {
    PDF_ScannerTheme(themeMode = ThemeMode.DARK, dynamicColor = false) {
        ScanItem(
            record = previewDocument.copy(isFavorite = true),
            isSelected = false,
            onClick = {},
            onCheckboxToggle = {},
            onToggleFavorite = {},
            folderLabel = "Invoices",
            modifier = Modifier.padding(16.dp)
        )
    }
}

private val previewDocument = Document(
    id = 1L,
    filename = "Quarterly invoice with a very long file name.pdf",
    filepath = "/preview/document.pdf",
    timestamp = 1_717_171_717_000,
    pageCount = 8,
    fileSize = 2_450_000,
    isSearchable = true,
    tags = "invoice,contract,bank",
    ocrConfidence = 0.91f,
    folderId = 1L
)
