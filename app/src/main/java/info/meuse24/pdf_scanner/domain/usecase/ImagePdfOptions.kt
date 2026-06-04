package info.meuse24.pdf_scanner.domain.usecase

import info.meuse24.pdf_scanner.domain.model.PdfPageSetup

enum class ImagePdfPageMode {
    FIXED_PAGE,
    PHOTO_PAGE
}

data class ImagePdfOptions(
    val layout: ImagePageLayout,
    val pageSetup: PdfPageSetup = PdfPageSetup(),
    val pageMode: ImagePdfPageMode = ImagePdfPageMode.FIXED_PAGE
)
