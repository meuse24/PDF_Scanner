package info.meuse24.pdf_scanner.domain.repository

import java.io.File

interface VCardExportRepository {
    suspend fun export(filenameHint: String, vCardContent: String): File
}
