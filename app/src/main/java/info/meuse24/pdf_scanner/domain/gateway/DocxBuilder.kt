package info.meuse24.pdf_scanner.domain.gateway

import java.io.OutputStream

interface DocxBuilder {
    fun writeDocx(doc: DocxDocument, out: OutputStream)
}

data class DocxDocument(
    val title: String?,
    val pages: List<DocxPage>
)

data class DocxPage(
    val heading: String?,
    val paragraphs: List<String>
)
