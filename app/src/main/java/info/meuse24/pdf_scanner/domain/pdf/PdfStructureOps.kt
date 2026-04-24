package info.meuse24.pdf_scanner.domain.pdf

import java.io.File

interface PdfStructureOps {
    fun mergePdfs(inputs: List<File>, output: File)
    fun splitPdf(input: File, outputDir: File, splitAtPages: List<Int>): List<File>
    fun reorderPages(input: File, newOrder: List<Int>, saveAsCopy: Boolean): File
    fun rotatePages(
        input: File,
        pageIndexes: List<Int>,
        rotationDegrees: Int,
        saveAsCopy: Boolean
    ): File

    fun deletePages(input: File, pageIndexes: List<Int>, saveAsCopy: Boolean): File
    fun extractPages(input: File, outputDir: File, pageIndexes: List<Int>): File
    fun duplicatePages(input: File, outputDir: File, pageIndexes: List<Int>): File
}
