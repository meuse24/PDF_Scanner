package info.meuse24.pdf_scanner.domain.usecase

data class QrCodeResult(
    val rawValue: String,
    val valueType: Int,
    val displayUrl: String?,
    val wifiSsid: String?,
    val wifiPassword: String?,
    val pageNumber: Int
)
