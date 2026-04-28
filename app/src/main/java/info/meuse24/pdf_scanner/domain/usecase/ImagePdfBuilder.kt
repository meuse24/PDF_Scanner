package info.meuse24.pdf_scanner.domain.usecase

import info.meuse24.pdf_scanner.domain.pdf.PdfImageRenderer
import info.meuse24.pdf_scanner.domain.pdf.PdfRenderingOps
import info.meuse24.pdf_scanner.domain.gateway.StorageProvider
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class ImagePdfBuildResult(
    val pdfFile: File,
    val pageCount: Int,
    val skippedCount: Int
)

private const val IMAGE_PDF_MAX_SOURCE_DIMENSION = 3000

@Singleton
open class ImagePdfBuilder @Inject constructor(
    private val imageRenderer: PdfImageRenderer,
    private val pdfEditor: PdfRenderingOps,
    private val storageProvider: StorageProvider
) {

    open suspend fun createPdf(
        imageUris: List<Any>,
        options: ImagePdfOptions,
        outputFile: File
    ): ImagePdfBuildResult {
        require(imageUris.isNotEmpty())

        val imageBytes: List<ByteArray?> = imageUris.map { uri ->
            imageRenderer.decodeBitmapBytes(uri, IMAGE_PDF_MAX_SOURCE_DIMENSION)
        }

        val skippedCount = imageBytes.count { it == null }
        require(skippedCount < imageUris.size)

        try {
            pdfEditor.createPdfFromImages(imageBytes, options, outputFile)
            val pageCount = pdfEditor.getPageCount(outputFile)
            return ImagePdfBuildResult(
                pdfFile = outputFile,
                pageCount = pageCount,
                skippedCount = skippedCount
            )
        } catch (exception: Exception) {
            outputFile.delete()
            throw exception
        }
    }

    open suspend fun createTempPdf(
        imageUris: List<Any>,
        options: ImagePdfOptions
    ): ImagePdfBuildResult {
        val outputFile = File.createTempFile("append_images_", ".pdf", storageProvider.tempDir())
        return createPdf(imageUris, options, outputFile)
    }
}
