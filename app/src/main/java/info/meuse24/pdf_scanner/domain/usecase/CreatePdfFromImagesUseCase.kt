package info.meuse24.pdf_scanner.domain.usecase

import android.net.Uri
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
    private val imagePdfBuilder: ImagePdfBuilder,
    private val pdfEditor: PdfEditor,
    private val repository: ScanRepository
) {
    open suspend operator fun invoke(
        imageUris: List<Uri>,
        filename: String,
        layout: ImagePageLayout,
        scansDir: File
    ): CreatePdfFromImagesResult {
        val baseName = resolveUniqueFilename(scansDir, filename)
        val destFile = File(scansDir, "$baseName.pdf")
        val buildResult = imagePdfBuilder.createPdf(imageUris, layout, destFile)

        val thumbFile = File(scansDir, "$baseName.jpg")
        pdfEditor.generateThumbnail(destFile, thumbFile)

        repository.saveScan(
            ScanRecord(
                filename = baseName,
                filepath = destFile.absolutePath,
                timestamp = System.currentTimeMillis(),
                pageCount = buildResult.pageCount,
                fileSize = destFile.length(),
                thumbnailPath = thumbFile.takeIf { it.exists() }?.absolutePath
            )
        )
        return CreatePdfFromImagesResult(baseName, buildResult.skippedCount)
    }
}
