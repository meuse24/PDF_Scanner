package info.meuse24.pdf_scanner.domain.usecase

import info.meuse24.pdf_scanner.domain.model.PdfPageSetup

data class ImagePdfOptions(
    val layout: ImagePageLayout,
    val pageSetup: PdfPageSetup = PdfPageSetup()
)
