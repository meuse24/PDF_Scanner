package info.meuse24.pdf_scanner.domain.usecase

import info.meuse24.pdf_scanner.domain.common.reconstructTable
import info.meuse24.pdf_scanner.domain.gateway.OcrPositionedTextExtractor
import info.meuse24.pdf_scanner.domain.model.Document
import info.meuse24.pdf_scanner.domain.model.OcrPipelineStatus
import info.meuse24.pdf_scanner.domain.model.TableExtractionResult
import javax.inject.Inject

class NoTableFoundException : Exception()

open class ExtractTableUseCase @Inject constructor(
    private val ocrPositionedTextExtractor: OcrPositionedTextExtractor
) {
    open suspend operator fun invoke(
        document: Document,
        languageCode: String,
        onStatus: (OcrPipelineStatus) -> Unit = {},
        onProgress: (current: Int, total: Int) -> Unit = { _, _ -> }
    ): TableExtractionResult {
        val pages = ocrPositionedTextExtractor.extract(document, languageCode, onStatus, onProgress)
        val tablesByPage = pages.associate { it.pageIndex to reconstructTable(it) }
        if (tablesByPage.values.none { it != null }) throw NoTableFoundException()
        return TableExtractionResult(tablesByPage)
    }
}
