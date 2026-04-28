package info.meuse24.pdf_scanner.domain.gateway

import java.io.File
import java.io.OutputStream

interface PdfPageJpgRenderer {
    fun renderPages(
        pdfFile: File,
        onPage: (pageIndex: Int, pageCount: Int, writeJpeg: (OutputStream) -> Unit) -> Unit
    )
}
