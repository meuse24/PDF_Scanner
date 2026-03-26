package info.meuse24.pdf_scanner.domain.usecase

data class TextComment(
    val pageIndex: Int,
    val anchorX: Float,
    val anchorY: Float,
    val text: String,
    val fontSizeFraction: Float
)
