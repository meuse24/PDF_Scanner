package info.meuse24.pdf_scanner.domain.usecase

import info.meuse24.pdf_scanner.domain.common.OcrAiPromptBuilder
import info.meuse24.pdf_scanner.domain.model.Document
import info.meuse24.pdf_scanner.domain.pdf.PdfTextOps
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.toList
import java.io.File
import javax.inject.Inject

/** Resolves local text for an AI prompt without starting or persisting OCR. */
class ResolveAiPromptTextUseCase @Inject constructor(
    private val pdfTextOps: PdfTextOps
) {
    suspend operator fun invoke(record: Document): String {
        val storedText = OcrAiPromptBuilder.resolveOcrText(
            extractedText = record.extractedText,
            pageTexts = record.pageTexts
        )
        if (storedText.isNotBlank()) return storedText

        val pdfFile = File(record.filepath)
        if (!pdfFile.isFile) return ""

        return try {
            pdfTextOps.extractSearchText(pdfFile)
                .toList()
                .joinToString(separator = "\n\n") { it.text }
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: Exception) {
            ""
        }
    }
}
