package info.meuse24.pdf_scanner.domain.pdf

import info.meuse24.pdf_scanner.domain.model.PdfMetadata
import java.io.File

interface PdfMetadataOps {
    fun addPageNumbers(input: File, outputDir: File): File
    fun applyTextWatermark(input: File, outputDir: File, text: String): File
    fun readMetadata(input: File): PdfMetadata
    fun updateMetadata(input: File, metadata: PdfMetadata): File
    fun applySignatureStamp(
        input: File,
        outputDir: File,
        signatureBitmap: Any,
        pageIndex: Int,
        scaleFraction: Float
    ): File
}
