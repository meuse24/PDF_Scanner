package info.meuse24.pdf_scanner.domain.usecase

enum class ImagePageLayout(val imagesPerPage: Int) {
    SINGLE(1),
    TWO_PER_PAGE(2),
    FOUR_PER_PAGE(4)
}
