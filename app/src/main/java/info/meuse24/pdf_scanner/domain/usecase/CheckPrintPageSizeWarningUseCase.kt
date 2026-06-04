package info.meuse24.pdf_scanner.domain.usecase

import info.meuse24.pdf_scanner.domain.gateway.DispatcherProvider
import info.meuse24.pdf_scanner.domain.model.Document
import info.meuse24.pdf_scanner.domain.model.PdfPageSizeCategory
import info.meuse24.pdf_scanner.domain.pdf.PdfMetadataOps
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.withContext

class CheckPrintPageSizeWarningUseCase @Inject constructor(
    private val pdfMetadataOps: PdfMetadataOps,
    private val dispatcherProvider: DispatcherProvider
) {
    suspend operator fun invoke(record: Document): Boolean =
        withContext(dispatcherProvider.io) {
            runCatching {
                pdfMetadataOps.classifyPageSizes(File(record.filepath))
            }.getOrDefault(PdfPageSizeCategory.UNIFORM_STANDARD) != PdfPageSizeCategory.UNIFORM_STANDARD
        }
}
