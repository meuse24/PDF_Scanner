package info.meuse24.pdf_scanner.domain.model

enum class PageNumberHorizontalPosition {
    LEFT,
    CENTER,
    RIGHT
}

enum class PageNumberVerticalPosition {
    TOP,
    BOTTOM
}

data class PageNumberSettings(
    val horizontalPosition: PageNumberHorizontalPosition = PageNumberHorizontalPosition.CENTER,
    val verticalPosition: PageNumberVerticalPosition = PageNumberVerticalPosition.BOTTOM,
    val prefix: String = "",
    val includeTotalPageCount: Boolean = false
) {
    init {
        require(prefix.length <= MAX_PREFIX_LENGTH) {
            "Page number prefix must not exceed $MAX_PREFIX_LENGTH characters"
        }
    }

    companion object {
        const val MAX_PREFIX_LENGTH = 40
    }
}
