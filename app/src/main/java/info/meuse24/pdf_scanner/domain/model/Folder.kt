package info.meuse24.pdf_scanner.domain.model

data class Folder(
    val id: Long = 0,
    val name: String,
    val colorArgb: Int? = null,
    val createdAt: Long = System.currentTimeMillis()
)
