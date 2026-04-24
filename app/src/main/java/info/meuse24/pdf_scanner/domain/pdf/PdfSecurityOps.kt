package info.meuse24.pdf_scanner.domain.pdf

import java.io.File

interface PdfSecurityOps {
    fun protectPdf(input: File, outputDir: File, password: String): File
    fun unlockPdf(input: File, outputDir: File, password: String): File
    fun removePassword(input: File, outputDir: File): File
    fun restrictUsage(
        input: File,
        outputDir: File,
        ownerPassword: String,
        canPrint: Boolean,
        canCopy: Boolean,
        canEdit: Boolean
    ): File

    fun isPdfEncrypted(input: File): Boolean
}
