package info.meuse24.pdf_scanner.domain.model

import java.util.Calendar

data class PdfMetadata(
    val title: String?,
    val author: String?,
    val creator: String?,
    val subject: String?,
    val keywords: String?,
    val creationDate: Calendar?,
    val modificationDate: Calendar?
)
