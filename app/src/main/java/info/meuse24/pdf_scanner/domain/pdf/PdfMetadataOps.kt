package info.meuse24.pdf_scanner.domain.pdf

import info.meuse24.pdf_scanner.domain.model.PdfMetadata
import info.meuse24.pdf_scanner.domain.model.PdfPageSizeCategory
import info.meuse24.pdf_scanner.domain.model.PageNumberSettings
import java.io.File

interface PdfMetadataOps {
    fun addPageNumbers(
        input: File,
        outputDir: File,
        settings: PageNumberSettings
    ): File

    fun applyTextWatermark(input: File, outputDir: File, text: String): File
    fun readMetadata(input: File): PdfMetadata

    /**
     * Classifies the PDF page sizes for print-warning decisions.
     *
     * Default: [PdfPageSizeCategory.UNIFORM_STANDARD]. Implementations that cannot inspect
     * page geometry intentionally suppress the custom-page-size warning.
     */
    fun classifyPageSizes(pdfFile: File): PdfPageSizeCategory = PdfPageSizeCategory.UNIFORM_STANDARD
    fun updateMetadata(input: File, metadata: PdfMetadata): File
    fun applySignatureStamp(
        input: File,
        outputDir: File,
        signatureBitmap: Any,
        pageIndex: Int,
        scaleFraction: Float
    ): File
}
