package info.meuse24.pdf_scanner.domain.gateway

import info.meuse24.pdf_scanner.domain.model.OcrPipelineStatus
import info.meuse24.pdf_scanner.domain.usecase.SearchableResult
import java.io.File

interface SearchablePdfGenerator {
    suspend fun makeSearchable(
        inputFile: File,
        languageCode: String,
        onProgress: (Int, Int) -> Unit = { _, _ -> },
        onStatus: (OcrPipelineStatus) -> Unit = {}
    ): SearchableResult
}
