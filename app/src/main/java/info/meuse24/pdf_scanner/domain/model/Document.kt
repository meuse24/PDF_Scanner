package info.meuse24.pdf_scanner.domain.model

data class Document(
    val id: Long = 0,
    val filename: String,
    val filepath: String,
    val timestamp: Long,
    val pageCount: Int,
    val fileSize: Long,
    val thumbnailPath: String? = null,
    val isSearchable: Boolean = false,
    val isEncrypted: Boolean = false,
    val extractedText: String? = null,
    val tags: String? = null,
    val ocrConfidence: Float? = null,
    val ocrLanguage: String? = null,
    val pageTexts: List<String> = emptyList(),
    val deletedAt: Long? = null,
    val folderId: Long? = null,
    val isFavorite: Boolean = false
) {
    val ocrInfo: OcrInfo?
        get() {
            if (
                extractedText == null &&
                ocrConfidence == null &&
                ocrLanguage == null &&
                pageTexts.isEmpty()
            ) {
                return null
            }
            return OcrInfo(
                extractedText = extractedText,
                confidence = ocrConfidence,
                language = ocrLanguage,
                pageTexts = pageTexts
            )
        }
}
