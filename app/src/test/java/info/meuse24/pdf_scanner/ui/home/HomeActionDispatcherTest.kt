package info.meuse24.pdf_scanner.ui.home

import info.meuse24.pdf_scanner.domain.model.Document
import info.meuse24.pdf_scanner.ui.components.ScanAction
import org.junit.Assert.assertSame
import org.junit.Test

class HomeActionDispatcherTest {

    @Test
    fun `calculate sha256 forwards complete document`() {
        val document = Document(
            id = 17L,
            filename = "invoice",
            filepath = "/scans/invoice.pdf",
            timestamp = 0L,
            pageCount = 1,
            fileSize = 42L
        )
        var receivedDocument: Document? = null

        dispatchHomeScanAction(
            record = document,
            action = ScanAction.CalculateSha256,
            navigator = navigator(
                onCalculateSha256 = { receivedDocument = it }
            )
        )

        assertSame(document, receivedDocument)
    }

    @Test
    fun `export table csv forwards scan id`() {
        val document = Document(
            id = 23L,
            filename = "invoice",
            filepath = "/scans/invoice.pdf",
            timestamp = 0L,
            pageCount = 1,
            fileSize = 42L
        )
        var receivedScanId: Long? = null

        dispatchHomeScanAction(
            record = document,
            action = ScanAction.ExportTableCsv,
            navigator = navigator(
                onCalculateSha256 = {},
                onExportTableCsv = { receivedScanId = it }
            )
        )

        assertSame(document.id, receivedScanId)
    }

    private fun navigator(
        onCalculateSha256: (Document) -> Unit,
        onExportTableCsv: (Long) -> Unit = {}
    ) = HomeScanActionNavigator(
        onSplit = {},
        onReorder = {},
        onRotate = {},
        onDeletePages = {},
        onExtractPages = {},
        onAppendPages = {},
        onDuplicatePages = {},
        onPageNumbers = {},
        onTextWatermark = {},
        onCompressPdf = {},
        onProtectPdf = {},
        onUnlockPdf = {},
        onSignature = {},
        onRemoveTextLayer = {},
        onRemovePassword = {},
        onRestrictUsage = {},
        onAnnotate = {},
        onRedact = {},
        onGrayscale = {},
        onPdfMetadata = {},
        onQrScan = {},
        onBusinessCard = {},
        onTranslateText = {},
        onExportTableCsv = onExportTableCsv,
        onExportAsJpg = {},
        onExportDocx = {},
        onExportOcrText = {},
        onPrint = {},
        onRename = {},
        onCalculateSha256 = onCalculateSha256
    )
}
