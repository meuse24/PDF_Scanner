package info.meuse24.pdf_scanner.ui.home

import info.meuse24.pdf_scanner.domain.model.Document
import info.meuse24.pdf_scanner.ui.components.ScanAction

internal data class HomeScanActionNavigator(
    val onSplit: (Long) -> Unit,
    val onReorder: (Long) -> Unit,
    val onRotate: (Long) -> Unit,
    val onDeletePages: (Long) -> Unit,
    val onExtractPages: (Long) -> Unit,
    val onAppendPages: (Long) -> Unit,
    val onDuplicatePages: (Long) -> Unit,
    val onPageNumbers: (Long) -> Unit,
    val onTextWatermark: (Long) -> Unit,
    val onCompressPdf: (Long) -> Unit,
    val onProtectPdf: (Long) -> Unit,
    val onUnlockPdf: (Long) -> Unit,
    val onSignature: (Long) -> Unit,
    val onRemoveTextLayer: (Long) -> Unit,
    val onRemovePassword: (Long) -> Unit,
    val onRestrictUsage: (Long) -> Unit,
    val onAnnotate: (Long) -> Unit,
    val onRedact: (Long) -> Unit,
    val onGrayscale: (Long) -> Unit,
    val onPdfMetadata: (Long) -> Unit,
    val onQrScan: (Long) -> Unit,
    val onBusinessCard: (Long) -> Unit,
    val onTranslateText: (Long) -> Unit,
    val onExportAsJpg: (Document) -> Unit,
    val onExportDocx: (Document) -> Unit,
    val onExportOcrText: (Document) -> Unit,
    val onPrint: (Document) -> Unit,
    val onRename: (Document) -> Unit,
    val onCalculateSha256: (Document) -> Unit
)

internal fun dispatchHomeScanAction(
    record: Document,
    action: ScanAction,
    navigator: HomeScanActionNavigator
) {
    when (action) {
        ScanAction.Split -> navigator.onSplit(record.id)
        ScanAction.Reorder -> navigator.onReorder(record.id)
        ScanAction.Rotate -> navigator.onRotate(record.id)
        ScanAction.DeletePages -> navigator.onDeletePages(record.id)
        ScanAction.ExtractPages -> navigator.onExtractPages(record.id)
        ScanAction.AppendPages -> navigator.onAppendPages(record.id)
        ScanAction.DuplicatePages -> navigator.onDuplicatePages(record.id)
        ScanAction.PageNumbers -> navigator.onPageNumbers(record.id)
        ScanAction.TextWatermark -> navigator.onTextWatermark(record.id)
        ScanAction.CompressPdf -> navigator.onCompressPdf(record.id)
        ScanAction.ProtectPdf -> navigator.onProtectPdf(record.id)
        ScanAction.UnlockPdf -> navigator.onUnlockPdf(record.id)
        ScanAction.Signature -> navigator.onSignature(record.id)
        ScanAction.RemoveTextLayer -> navigator.onRemoveTextLayer(record.id)
        ScanAction.RemovePassword -> navigator.onRemovePassword(record.id)
        ScanAction.RestrictUsage -> navigator.onRestrictUsage(record.id)
        ScanAction.ExportAsJpg -> navigator.onExportAsJpg(record)
        ScanAction.ExportDocx -> navigator.onExportDocx(record)
        ScanAction.ExportOcrText -> navigator.onExportOcrText(record)
        ScanAction.Annotate -> navigator.onAnnotate(record.id)
        ScanAction.Redact -> navigator.onRedact(record.id)
        ScanAction.Grayscale -> navigator.onGrayscale(record.id)
        ScanAction.PdfMetadata -> navigator.onPdfMetadata(record.id)
        ScanAction.TranslateText -> navigator.onTranslateText(record.id)
        ScanAction.ScanQrCodes -> navigator.onQrScan(record.id)
        ScanAction.ScanBusinessCard -> navigator.onBusinessCard(record.id)
        ScanAction.Print -> navigator.onPrint(record)
        ScanAction.Rename -> navigator.onRename(record)
        ScanAction.CalculateSha256 -> navigator.onCalculateSha256(record)
    }
}
