package info.meuse24.pdf_scanner.domain.pdf

data class PdfPageTextContent(
    val pageIndex: Int,
    val text: String
)

data class NormalizedBox(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
)
