package info.meuse24.pdf_scanner.domain.usecase

import info.meuse24.pdf_scanner.domain.common.linesToParagraphs
import info.meuse24.pdf_scanner.domain.gateway.DocxBuilder
import info.meuse24.pdf_scanner.domain.gateway.DocxDocument
import info.meuse24.pdf_scanner.domain.gateway.DocxPage
import info.meuse24.pdf_scanner.domain.gateway.DownloadsStorage
import info.meuse24.pdf_scanner.domain.model.Document
import javax.inject.Inject

class NoExportableTextException : Exception("No exportable text")

class ExportDocxUseCase @Inject constructor(
    private val downloadsStorage: DownloadsStorage,
    private val docxBuilder: DocxBuilder
) {
    suspend operator fun invoke(documents: List<Document>): String {
        if (documents.isEmpty()) throw NoExportableTextException()

        val exportableDocuments = documents.filter { it.hasDocxExportContent() }
        if (exportableDocuments.isEmpty()) throw NoExportableTextException()

        val docxDocument = DocxDocument(
            title = exportTitle(exportableDocuments),
            pages = exportableDocuments.flatMap { it.toDocxPages(includeDocumentHeading = exportableDocuments.size > 1) }
        )
        val filename = if (exportableDocuments.size == 1) {
            "${exportableDocuments.first().filename}.docx"
        } else {
            "docx_export_${System.currentTimeMillis()}.docx"
        }

        val entry = downloadsStorage.writeDownload(
            displayName = filename,
            mimeType = DOCX_MIME_TYPE
        ) { output ->
            docxBuilder.writeDocx(docxDocument, output)
        }
        return entry.displayName
    }
}

private fun Document.hasDocxExportContent(): Boolean =
    pageTexts.any { it.isNotBlank() } || !extractedText.isNullOrBlank()

private fun Document.toDocxPages(includeDocumentHeading: Boolean): List<DocxPage> {
    val nonBlankPageTexts = pageTexts.map { it.trim() }.filter { it.isNotBlank() }
    if (nonBlankPageTexts.isNotEmpty()) {
        return nonBlankPageTexts.mapIndexed { index, pageText ->
            val heading = when {
                includeDocumentHeading && index == 0 -> filename
                nonBlankPageTexts.size > 1 -> "Page ${index + 1}"
                else -> null
            }
            DocxPage(
                heading = heading,
                paragraphs = linesToParagraphs(pageText.lines())
            )
        }
    }

    return listOf(
        DocxPage(
            heading = filename.takeIf { includeDocumentHeading },
            paragraphs = linesToParagraphs(extractedText.orEmpty().lines())
        )
    )
}

private fun exportTitle(documents: List<Document>): String? =
    if (documents.size == 1) documents.first().filename else "DOCX export"

private const val DOCX_MIME_TYPE = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
