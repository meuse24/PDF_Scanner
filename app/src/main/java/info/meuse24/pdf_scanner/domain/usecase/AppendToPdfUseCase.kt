package info.meuse24.pdf_scanner.domain.usecase

import android.net.Uri
import info.meuse24.pdf_scanner.domain.model.Document
import info.meuse24.pdf_scanner.domain.pdf.PdfRenderingOps
import info.meuse24.pdf_scanner.domain.pdf.PdfSecurityOps
import info.meuse24.pdf_scanner.domain.pdf.PdfStructureOps
import info.meuse24.pdf_scanner.domain.repository.DocumentRepository
import info.meuse24.pdf_scanner.util.FileUtil
import java.io.File
import javax.inject.Inject

sealed interface AppendSource {
    data class Scan(val pdfUri: Uri, val pageCount: Int) : AppendSource
    data class Pdf(val pdfUri: Uri) : AppendSource
    data class Images(val uris: List<Uri>, val options: ImagePdfOptions) : AppendSource
}

data class AppendResult(
    val pageCount: Int,
    val fileSize: Long,
    val appendedPageCount: Int
)

class AppendTargetMissingException : IllegalStateException()

class AppendTargetEncryptedException : IllegalStateException()

class AppendSourceEncryptedException : IllegalStateException()

open class AppendToPdfUseCase @Inject constructor(
    private val fileUtil: FileUtil,
    private val pdfEditor: PdfStructureOps,
    private val repository: DocumentRepository,
    private val imagePdfBuilder: ImagePdfBuilder,
    private val pdfSecurityOps: PdfSecurityOps = pdfEditor as PdfSecurityOps,
    private val pdfRenderingOps: PdfRenderingOps = pdfEditor as PdfRenderingOps
) {
    open suspend operator fun invoke(
        target: Document,
        source: AppendSource
    ): AppendResult {
        val targetFile = File(target.filepath)
        if (!targetFile.exists()) {
            throw AppendTargetMissingException()
        }
        if (target.isEncrypted || pdfSecurityOps.isPdfEncrypted(targetFile)) {
            throw AppendTargetEncryptedException()
        }

        val sourcePdf = when (source) {
            is AppendSource.Pdf -> fileUtil.copyToTemp(source.pdfUri, ".pdf")
            is AppendSource.Scan -> fileUtil.copyToTemp(source.pdfUri, ".pdf")
            is AppendSource.Images -> imagePdfBuilder.createTempPdf(source.uris, source.options).pdfFile
        }

        try {
            if (pdfSecurityOps.isPdfEncrypted(sourcePdf)) {
                throw AppendSourceEncryptedException()
            }

            val appendedPageCount = pdfRenderingOps.getPageCount(sourcePdf)
            require(appendedPageCount > 0)

            pdfEditor.mergePdfs(listOf(targetFile, sourcePdf), targetFile)

            val newPageCount = pdfRenderingOps.getPageCount(targetFile)
            val newFileSize = targetFile.length()
            repository.invalidateAfterAppend(
                id = target.id,
                fileSize = newFileSize,
                pageCount = newPageCount
            )

            return AppendResult(
                pageCount = newPageCount,
                fileSize = newFileSize,
                appendedPageCount = appendedPageCount
            )
        } finally {
            sourcePdf.delete()
        }
    }
}

