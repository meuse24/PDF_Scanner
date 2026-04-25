package info.meuse24.pdf_scanner.domain.pdf

interface PdfImageRenderer {
    suspend fun decodeBitmapBytes(uri: Any, maxDimension: Int): ByteArray?
}
