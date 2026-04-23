package info.meuse24.pdf_scanner.domain.usecase

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import info.meuse24.pdf_scanner.util.PdfEditor
import info.meuse24.pdf_scanner.util.StorageProvider
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class ImagePdfBuildResult(
    val pdfFile: File,
    val pageCount: Int,
    val skippedCount: Int
)

@Singleton
open class ImagePdfBuilder @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val pdfEditor: PdfEditor,
    private val storageProvider: StorageProvider
) {

    open suspend fun createPdf(
        imageUris: List<Uri>,
        layout: ImagePageLayout,
        outputFile: File
    ): ImagePdfBuildResult {
        require(imageUris.isNotEmpty())

        val imageBytes: List<ByteArray?> = imageUris.map { uri ->
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            }.getOrNull()
        }

        val skippedCount = imageBytes.count { it == null }
        require(skippedCount < imageUris.size)

        try {
            pdfEditor.createPdfFromImages(imageBytes, layout, outputFile)
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
        imageUris: List<Uri>,
        layout: ImagePageLayout
    ): ImagePdfBuildResult {
        val outputFile = File.createTempFile("append_images_", ".pdf", storageProvider.tempDir())
        return createPdf(imageUris, layout, outputFile)
    }
}
