package info.meuse24.pdf_scanner.domain.usecase

enum class PdfCompressionPreset(
    val renderDpi: Int,
    val jpegQuality: Float
) {
    LOW(renderDpi = 150, jpegQuality = 0.80f),
    MEDIUM(renderDpi = 120, jpegQuality = 0.65f),
    HIGH(renderDpi = 96, jpegQuality = 0.50f)
}
