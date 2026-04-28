package info.meuse24.pdf_scanner.domain.gateway

import java.io.File

interface DocumentFileStore {
    fun savePdf(source: Any, filename: String): File
    fun saveThumbnail(source: Any, filename: String): File?
    fun copyToTemp(source: Any, suffix: String): File
    fun exists(path: String): Boolean
}
