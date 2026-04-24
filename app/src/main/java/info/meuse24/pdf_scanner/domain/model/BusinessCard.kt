package info.meuse24.pdf_scanner.domain.model

data class BusinessCard(
    val fullName: String? = null,
    val organization: String? = null,
    val jobTitle: String? = null,
    val emails: List<String> = emptyList(),
    val phones: List<String> = emptyList(),
    val urls: List<String> = emptyList(),
    val address: String? = null
)
