package info.meuse24.pdf_scanner.domain.usecase

import android.graphics.Bitmap
import info.meuse24.pdf_scanner.domain.model.Document
import info.meuse24.pdf_scanner.domain.pdf.PdfMetadataOps
import info.meuse24.pdf_scanner.domain.service.ScanArtifactPersister
import java.io.File
import javax.inject.Inject

class ApplySignatureStampUseCase @Inject constructor(
    private val pdfMetadataOps: PdfMetadataOps,
    private val persister: ScanArtifactPersister
) {
    suspend operator fun invoke(
        record: Document,
        signatureBitmap: Bitmap,
        pageIndex: Int,
        scaleFraction: Float,
        scansDir: File
    ): String {
        val resultFile = pdfMetadataOps.applySignatureStamp(
            input = File(record.filepath),
            outputDir = scansDir,
            signatureBitmap = signatureBitmap,
            pageIndex = pageIndex,
            scaleFraction = scaleFraction
        )
        persister.persistDerivedFrom(record, resultFile, scansDir)
        return resultFile.nameWithoutExtension
    }
}

