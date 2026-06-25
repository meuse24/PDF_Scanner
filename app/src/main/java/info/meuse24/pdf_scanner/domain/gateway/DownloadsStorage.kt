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

    // Default keeps existing root-only adapters and test doubles source-compatible.
    // Callers that require a subfolder fail explicitly unless the adapter opts in.
    fun writeDownloadToSubfolder(
        displayName: String,
        mimeType: String,
        subfolder: String,
        writer: (OutputStream) -> Unit
    ): DownloadEntry = error("Downloads in subfolders are not supported")
}
