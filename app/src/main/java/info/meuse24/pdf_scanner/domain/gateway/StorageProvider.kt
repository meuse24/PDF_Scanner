package info.meuse24.pdf_scanner.domain.gateway

import java.io.File

interface StorageProvider {
    fun scansDir(): File
    fun tempDir(): File
}
