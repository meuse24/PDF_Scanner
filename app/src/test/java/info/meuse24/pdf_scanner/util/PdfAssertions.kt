package info.meuse24.pdf_scanner.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import java.io.File

internal fun assertPdfPageRotations(
    pdfFile: File,
    expectedRotations: List<Int>,
    password: String? = null
) {
    assertEquals(expectedRotations, readPdfPageRotations(pdfFile, password))
}

internal fun assertPdfPageSignatures(
    pdfFile: File,
    expectedSignatures: List<PdfPageSignature>,
    password: String? = null
) {
    assertEquals(expectedSignatures, readPdfPageSignatures(pdfFile, password))
}

internal fun assertPdfEncrypted(editor: PdfEditor, pdfFile: File) {
    assertTrue("Expected encrypted PDF: ${pdfFile.name}", editor.isPdfEncrypted(pdfFile))
}

internal fun assertPdfNotEncrypted(editor: PdfEditor, pdfFile: File) {
    assertFalse("Expected unencrypted PDF: ${pdfFile.name}", editor.isPdfEncrypted(pdfFile))
}
