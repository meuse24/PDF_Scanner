package info.meuse24.pdf_scanner.data.mapper

import info.meuse24.pdf_scanner.data.local.ScanListItem
import info.meuse24.pdf_scanner.data.local.ScanRecord
import info.meuse24.pdf_scanner.domain.model.hasDocxExportText
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentMappersTest {
    @Test
    fun `scan list item preserves stored ocr text flag for docx visibility`() {
        val document = scanListItem(hasStoredOcrText = true).toDomain()

        assertTrue(document.hasDocxExportText())
    }

    @Test
    fun `scan list item without stored ocr text is not docx visible`() {
        val document = scanListItem(hasStoredOcrText = false).toDomain()

        assertFalse(document.hasDocxExportText())
    }

    @Test
    fun `full scan record derives stored ocr text flag from extracted text`() {
        val document = scanRecord(
            extractedText = "OCR text",
            ocrPageTextJson = null
        ).toDomain()

        assertTrue(document.hasDocxExportText())
    }

    private fun scanListItem(hasStoredOcrText: Boolean) = ScanListItem(
        id = 1L,
        filename = "scan",
        filepath = "/tmp/scan.pdf",
        timestamp = 0L,
        pageCount = 1,
        fileSize = 0L,
        hasStoredOcrText = hasStoredOcrText
    )

    private fun scanRecord(
        extractedText: String?,
        ocrPageTextJson: String?
    ) = ScanRecord(
        id = 1L,
        filename = "scan",
        filepath = "/tmp/scan.pdf",
        timestamp = 0L,
        pageCount = 1,
        fileSize = 0L,
        extractedText = extractedText,
        ocrPageTextJson = ocrPageTextJson
    )
}
