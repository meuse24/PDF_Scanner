package info.meuse24.pdf_scanner.domain.model

enum class PdfPageSizePreset {
    ISO_A3,
    ISO_A4,
    ISO_A5,
    NA_LETTER
}

enum class PdfPageOrientation {
    PORTRAIT,
    LANDSCAPE
}

enum class PdfMarginPreset {
    SMALL,
    MEDIUM,
    LARGE
}

data class PdfPageSetup(
    val sizePreset: PdfPageSizePreset = PdfPageSizePreset.ISO_A4,
    val orientation: PdfPageOrientation = PdfPageOrientation.PORTRAIT,
    val marginPreset: PdfMarginPreset = PdfMarginPreset.MEDIUM
)
