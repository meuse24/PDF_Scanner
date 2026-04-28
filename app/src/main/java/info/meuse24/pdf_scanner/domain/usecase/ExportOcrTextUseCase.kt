package info.meuse24.pdf_scanner.domain.usecase

import info.meuse24.pdf_scanner.domain.model.Document
import info.meuse24.pdf_scanner.domain.gateway.DownloadsStorage
import javax.inject.Inject

class ExportOcrTextUseCase @Inject constructor(
    private val downloadsStorage: DownloadsStorage
) {
    suspend operator fun invoke(documents: List<Document>): String {
        require(documents.isNotEmpty())
        val exportText = buildExportText(documents)
        val filename = if (documents.size == 1) {
            "${documents.first().filename}_ocr.txt"
        } else {
            "ocr_export_${System.currentTimeMillis()}.txt"
        }

        val entry = downloadsStorage.writeDownload(
            displayName = filename,
            mimeType = "text/plain"
        ) { output ->
            output.writer(Charsets.UTF_8).use { writer ->
                writer.write(exportText)
            }
        }
        return entry.displayName
    }

    internal fun buildExportText(documents: List<Document>): String = buildString {
        documents.forEachIndexed { index, document ->
            if (documents.size > 1) {
                appendLine("=== ${document.filename} ===")
                appendLine()
            }
            val text = document.extractedText
            if (text.isNullOrBlank()) {
                appendLine("[Kein OCR-Text vorhanden]")
            } else {
                append(text)
            }
            if (index < documents.lastIndex) {
                appendLine()
                appendLine()
            }
        }
    }
}
