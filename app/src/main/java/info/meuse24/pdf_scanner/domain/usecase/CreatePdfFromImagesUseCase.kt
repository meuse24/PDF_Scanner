package info.meuse24.pdf_scanner.domain.usecase

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import info.meuse24.pdf_scanner.data.local.ScanRecord
import info.meuse24.pdf_scanner.data.repository.ScanRepository
import info.meuse24.pdf_scanner.util.PdfEditor
import info.meuse24.pdf_scanner.util.resolveUniqueFilename
import java.io.File
import javax.inject.Inject

data class CreatePdfFromImagesResult(
    val baseName: String,
    val skippedCount: Int
)

/**
 * Konvertiert eine Liste von Bild-URIs in ein neues PDF.
 *
 * - URIs werden im UseCase per ContentResolver zu ByteArrays aufgelöst,
 *   damit PdfEditor keine Android-Abhängigkeit erhält.
 * - Nicht lesbare URIs (IOException, null-Stream) ergeben null-Einträge im
 *   ByteArray-Array → PdfEditor erzeugt für diese Slots leere Zellen.
 * - Sind alle Bilder unlesbar, wird eine Exception geworfen.
 */
open class CreatePdfFromImagesUseCase @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val pdfEditor: PdfEditor,
    private val repository: ScanRepository
) {
    open suspend operator fun invoke(
        imageUris: List<Uri>,
        filename: String,
        layout: ImagePageLayout,
        scansDir: File
    ): CreatePdfFromImagesResult {
        require(imageUris.isNotEmpty()) { "Bildliste darf nicht leer sein" }

        val imageBytes: List<ByteArray?> = imageUris.map { uri ->
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            }.getOrNull()
        }

        val skippedCount = imageBytes.count { it == null }
        require(skippedCount < imageUris.size) { "Kein Bild konnte gelesen werden" }

        val baseName = resolveUniqueFilename(scansDir, filename)
        val destFile = File(scansDir, "$baseName.pdf")
        pdfEditor.createPdfFromImages(imageBytes, layout, destFile)

        val thumbFile = File(scansDir, "$baseName.jpg")
        pdfEditor.generateThumbnail(destFile, thumbFile)

        val pageCount = calculatePageCount(imageUris.size, layout.imagesPerPage)
        repository.saveScan(
            ScanRecord(
                filename = baseName,
                filepath = destFile.absolutePath,
                timestamp = System.currentTimeMillis(),
                pageCount = pageCount,
                fileSize = destFile.length(),
                thumbnailPath = thumbFile.takeIf { it.exists() }?.absolutePath
            )
        )
        return CreatePdfFromImagesResult(baseName, skippedCount)
    }

    private fun calculatePageCount(imageCount: Int, imagesPerPage: Int): Int =
        (imageCount + imagesPerPage - 1) / imagesPerPage
}
