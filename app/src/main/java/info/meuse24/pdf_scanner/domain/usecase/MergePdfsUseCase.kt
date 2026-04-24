package info.meuse24.pdf_scanner.domain.usecase

import info.meuse24.pdf_scanner.domain.model.Document
import info.meuse24.pdf_scanner.domain.pdf.PdfStructureOps
import info.meuse24.pdf_scanner.domain.service.ScanArtifactPersister
import info.meuse24.pdf_scanner.util.resolveUniqueFilename
import java.io.File
import javax.inject.Inject

/**
 * Führt mehrere PDFs zu einer neuen Datei zusammen und legt einen DB-Eintrag an.
 * @return der finale Dateiname (ohne Extension) des erstellten PDFs
 */
class MergePdfsUseCase @Inject constructor(
    private val pdfStructureOps: PdfStructureOps,
    private val persister: ScanArtifactPersister
) {
    suspend operator fun invoke(
        records: List<Document>,
        outputFilename: String,
        scansDir: File
    ): String {
        val baseName = resolveUniqueFilename(scansDir, outputFilename)
        val destFile = File(scansDir, "$baseName.pdf")
        pdfStructureOps.mergePdfs(records.map { File(it.filepath) }, destFile)

        persister.persistDerived(destFile, scansDir, records.sumOf { it.pageCount })
        return baseName
    }
}

