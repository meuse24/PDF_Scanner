package info.meuse24.pdf_scanner.domain.usecase

import info.meuse24.pdf_scanner.domain.model.Document
import info.meuse24.pdf_scanner.domain.pdf.PdfRenderingOps
import info.meuse24.pdf_scanner.domain.pdf.PdfStructureOps
import info.meuse24.pdf_scanner.domain.service.ScanArtifactPersister
import java.io.File
import javax.inject.Inject

/**
 * Teilt ein PDF an den angegebenen Seitenindizes auf.
 * Für jede erzeugte Datei wird ein neuer DB-Eintrag angelegt.
 * @return Anzahl der erzeugten Teile
 */
class SplitPdfUseCase @Inject constructor(
    private val pdfEditor: PdfStructureOps,
    private val persister: ScanArtifactPersister,
    private val pdfRenderingOps: PdfRenderingOps = pdfEditor as PdfRenderingOps
) {
    suspend operator fun invoke(
        record: Document,
        splitAtPages: List<Int>,
        scansDir: File
    ): Int {
        val parts = pdfEditor.splitPdf(File(record.filepath), scansDir, splitAtPages)
        persister.persistDerivedAll(parts, scansDir, pdfRenderingOps::getPageCount)
        return parts.size
    }
}

