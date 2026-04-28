package info.meuse24.pdf_scanner.domain.usecase

import info.meuse24.pdf_scanner.domain.gateway.DocumentFileStore
import info.meuse24.pdf_scanner.domain.gateway.QrCodeReader
import info.meuse24.pdf_scanner.domain.model.Document
import java.io.File
import javax.inject.Inject

class ScanQrCodesUseCase @Inject constructor(
    private val scanner: QrCodeReader,
    private val fileStore: DocumentFileStore
) {
    suspend operator fun invoke(
        record: Document,
        onProgress: (page: Int, total: Int) -> Unit = { _, _ -> }
    ): List<QrCodeResult> {
        if (record.isEncrypted) return emptyList()
        if (!fileStore.exists(record.filepath)) return emptyList()
        val file = File(record.filepath)
        return scanner.scan(file, onProgress)
    }
}

