package info.meuse24.pdf_scanner.util

import com.tom_roush.pdfbox.pdmodel.PDDocument
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

internal fun buildRanges(pageCount: Int, splitPoints: List<Int>): List<IntRange> {
    val boundaries = listOf(0) + splitPoints.map { it + 1 } + listOf(pageCount)
    return boundaries.zipWithNext { from, to -> from until to }.filter { !it.isEmpty() }
}

internal fun normalizeSplitPoints(pageCount: Int, splitPoints: List<Int>): List<Int> {
    if (pageCount < 2) return emptyList()
    return splitPoints
        .filter { it in 0 until (pageCount - 1) }
        .sorted()
        .distinct()
}

internal fun normalizePageIndexes(pageCount: Int, pageIndexes: List<Int>): List<Int> {
    return pageIndexes
        .filter { it in 0 until pageCount }
        .sorted()
        .distinct()
}

internal fun mapDisplayToPdfCoord(
    nx: Float,
    ny: Float,
    pageWidth: Float,
    pageHeight: Float,
    rotation: Int
): Pair<Float, Float> = when (rotation) {
    90  -> ny * pageWidth to nx * pageHeight
    180 -> (1f - nx) * pageWidth to (1f - ny) * pageHeight
    270 -> (1f - ny) * pageWidth to (1f - nx) * pageHeight
    else -> nx * pageWidth to (1f - ny) * pageHeight
}

internal inline fun PdfEditor.editPdf(
    input: File,
    saveAsCopy: Boolean,
    outputSuffix: String,
    operation: String,
    edit: (PDDocument, PDDocument) -> Unit
): File {
    val parentDir = input.parentFile
        ?: input.absoluteFile.parentFile
        ?: throw IOException("Kann übergeordnetes Verzeichnis nicht ermitteln")
    val output = if (saveAsCopy) {
        val name = resolveUniqueFilename(parentDir, "${input.nameWithoutExtension}$outputSuffix")
        File(parentDir, "$name.pdf")
    } else {
        input
    }
    return writePdf(operation, output) {
        PDDocument.load(input).use { source ->
            PDDocument().use { edited ->
                edit(source, edited)
                if (edited.numberOfPages == 0) {
                    throw IOException("$operation erzeugte keine Seiten")
                }
                edited.save(it)
            }
        }
    }
}

internal inline fun PdfEditor.writeDerivedPdf(
    input: File,
    outputDir: File,
    outputSuffix: String,
    operation: String,
    edit: (PDDocument, PDDocument) -> Unit
): File {
    val baseName = resolveUniqueFilename(outputDir, "${input.nameWithoutExtension}$outputSuffix")
    val output = File(outputDir, "$baseName.pdf")
    return writePdf(operation, output) {
        PDDocument.load(input).use { source ->
            PDDocument().use { edited ->
                edit(source, edited)
                if (edited.numberOfPages == 0) {
                    throw IOException("$operation erzeugte keine Seiten")
                }
                edited.save(it)
            }
        }
    }
}

internal inline fun PdfEditor.writePdf(
    operation: String,
    output: File,
    write: (File) -> Unit
): File {
    val parentDir = output.parentFile
        ?: output.absoluteFile.parentFile
        ?: throw IOException("Kann übergeordnetes Verzeichnis nicht ermitteln")
    val temp = File(parentDir, "${output.nameWithoutExtension}_tmp.pdf")
    try {
        write(temp)
        if (!temp.exists() || temp.length() == 0L) {
            throw IOException("$operation erzeugte keine Ausgabedatei")
        }
        Files.move(
            temp.toPath(),
            output.toPath(),
            StandardCopyOption.ATOMIC_MOVE,
            StandardCopyOption.REPLACE_EXISTING
        )
    } catch (e: Exception) {
        temp.delete()
        throw if (e is IOException) e else IOException("Fehler bei $operation: ${e.message}", e)
    }
    return output
}

internal fun normalizeRotation(rotation: Int): Int {
    val normalized = rotation % 360
    return if (normalized < 0) normalized + 360 else normalized
}

internal fun resolveUniqueFilename(dir: File, name: String): String {
    if (!File(dir, "$name.pdf").exists()) return name
    var counter = 2
    while (File(dir, "${name}_$counter.pdf").exists()) counter++
    return "${name}_$counter"
}
