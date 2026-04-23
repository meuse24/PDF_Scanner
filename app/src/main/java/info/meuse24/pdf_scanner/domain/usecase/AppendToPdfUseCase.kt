package info.meuse24.pdf_scanner.domain.usecase

import android.net.Uri
import info.meuse24.pdf_scanner.data.local.ScanRecord
import info.meuse24.pdf_scanner.data.repository.ScanRepository
import info.meuse24.pdf_scanner.util.FileUtil
import info.meuse24.pdf_scanner.util.PdfEditor
import java.io.File
import javax.inject.Inject

sealed interface AppendSource {
    data class Scan(val pdfUri: Uri, val pageCount: Int) : AppendSource
    data class Pdf(val pdfUri: Uri) : AppendSource
    data class Images(val uris: List<Uri>, val layout: ImagePageLayout) : AppendSource
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
    private val pdfEditor: PdfEditor,
    private val repository: ScanRepository,
    private val imagePdfBuilder: ImagePdfBuilder
) {
    open suspend operator fun invoke(
        target: ScanRecord,
        source: AppendSource
    ): AppendResult {
        val targetFile = File(target.filepath)
        if (!targetFile.exists()) {
            throw AppendTargetMissingException()
        }
        if (target.isEncrypted || pdfEditor.isPdfEncrypted(targetFile)) {
            throw AppendTargetEncryptedException()
        }

        val sourcePdf = when (source) {
            is AppendSource.Pdf -> fileUtil.copyToTemp(source.pdfUri, ".pdf")
            is AppendSource.Scan -> fileUtil.copyToTemp(source.pdfUri, ".pdf")
            is AppendSource.Images -> imagePdfBuilder.createTempPdf(source.uris, source.layout).pdfFile
        }

        try {
            if (pdfEditor.isPdfEncrypted(sourcePdf)) {
                throw AppendSourceEncryptedException()
            }

            val appendedPageCount = pdfEditor.getPageCount(sourcePdf)
            require(appendedPageCount > 0)

            pdfEditor.mergePdfs(listOf(targetFile, sourcePdf), targetFile)

            val newPageCount = pdfEditor.getPageCount(targetFile)
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
