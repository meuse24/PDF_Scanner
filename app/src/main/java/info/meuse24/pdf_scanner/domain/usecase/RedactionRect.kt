package info.meuse24.pdf_scanner.domain.usecase

data class RedactionRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val pageIndex: Int
)
