package info.meuse24.pdf_scanner.domain.pdf

import android.graphics.Bitmap
import info.meuse24.pdf_scanner.domain.usecase.ImagePageLayout
import info.meuse24.pdf_scanner.domain.usecase.PdfCompressionPreset
import java.io.File

interface PdfRenderingOps {
    fun compressPdf(input: File, outputDir: File, preset: PdfCompressionPreset): File
    fun convertToGrayscale(input: File, outputDir: File): File
    fun createPdfFromImages(
        imageBytes: List<ByteArray?>,
        layout: ImagePageLayout,
        outputFile: File
    ): File

    fun getPageCount(pdfFile: File): Int
    fun generateThumbnail(pdfFile: File, outputFile: File): Boolean
    fun renderPageThumbnail(pdfFile: File, pageIndex: Int, maxSizePx: Int): Bitmap?
}
