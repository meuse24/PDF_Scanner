package info.meuse24.pdf_scanner.domain.usecase

import info.meuse24.pdf_scanner.domain.model.Document
import info.meuse24.pdf_scanner.domain.pdf.PdfRenderingOps
import info.meuse24.pdf_scanner.domain.pdf.PdfStructureOps
import info.meuse24.pdf_scanner.domain.service.ScanArtifactPersister
import java.io.File
import javax.inject.Inject

class DuplicatePagesUseCase @Inject constructor(
    private val pdfEditor: PdfStructureOps,
    private val persister: ScanArtifactPersister,
    private val pdfRenderingOps: PdfRenderingOps = pdfEditor as PdfRenderingOps
) {
    suspend operator fun invoke(
        record: Document,
        pageIndexes: List<Int>,
        scansDir: File
    ): String {
        val resultFile = pdfEditor.duplicatePages(File(record.filepath), scansDir, pageIndexes)
        persister.persistDerivedFrom(record, resultFile, scansDir, pdfRenderingOps.getPageCount(resultFile))
        return resultFile.nameWithoutExtension
    }
}

