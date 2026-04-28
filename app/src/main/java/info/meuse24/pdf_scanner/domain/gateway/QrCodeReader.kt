package info.meuse24.pdf_scanner.domain.gateway

import info.meuse24.pdf_scanner.domain.usecase.QrCodeResult
import info.meuse24.pdf_scanner.domain.model.OcrPipelineStatus
import java.io.File

interface QrCodeReader {
    suspend fun scan(
        pdfFile: File,
        onProgress: (page: Int, total: Int) -> Unit = { _, _ -> },
        onStatus: (OcrPipelineStatus) -> Unit = {}
    ): List<QrCodeResult>
}
