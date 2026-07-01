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

    private fun navigator(
        onCalculateSha256: (Document) -> Unit
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
        onExportAsJpg = {},
        onExportDocx = {},
        onExportOcrText = {},
        onPrint = {},
        onRename = {},
        onCalculateSha256 = onCalculateSha256
    )
}
