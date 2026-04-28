package info.meuse24.pdf_scanner.domain.gateway

import java.io.OutputStream

interface DownloadEntry {
    val displayName: String
    fun delete()
}

interface DownloadsStorage {
    fun writeDownload(
        displayName: String,
        mimeType: String,
        writer: (OutputStream) -> Unit
    ): DownloadEntry
}
