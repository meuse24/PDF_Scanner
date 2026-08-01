package info.meuse24.pdf_scanner.util

import com.tom_roush.pdfbox.pdmodel.PDDocument
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlin.coroutines.cancellation.CancellationException

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

/**
 * Maps a CropBox-relative rectangle from PDF coordinates to the displayed page.
 * TextPosition coordinates are already relative to the unrotated CropBox, while
 * PdfRenderer displays the page rotation; transform all corners to preserve bounds.
 */
internal fun mapPdfBoxToDisplay(
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
    rotation: Int
): FloatArray? {
    val corners = arrayOf(
        left to top,
        right to top,
        left to bottom,
        right to bottom
    ).map { (x, y) ->
        when (normalizeRotation(rotation)) {
            90 -> 1f - y to x
            180 -> 1f - x to 1f - y
            270 -> y to 1f - x
            else -> x to y
        }
    }
    val displayLeft = corners.minOf { it.first }.coerceIn(0f, 1f)
    val displayTop = corners.minOf { it.second }.coerceIn(0f, 1f)
    val displayRight = corners.maxOf { it.first }.coerceIn(0f, 1f)
    val displayBottom = corners.maxOf { it.second }.coerceIn(0f, 1f)
    return if (displayRight > displayLeft && displayBottom > displayTop) {
        floatArrayOf(displayLeft, displayTop, displayRight, displayBottom)
    } else {
        null
    }
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

internal suspend fun PdfEditor.writePdfSuspending(
    operation: String,
    output: File,
    write: suspend (File) -> Unit
): File {
    val parentDir = output.parentFile
        ?: output.absoluteFile.parentFile
        ?: throw IOException("Kann übergeordnetes Verzeichnis nicht ermitteln")
    val temp = File.createTempFile("${output.nameWithoutExtension}_", "_tmp.pdf", parentDir)
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
    } catch (e: CancellationException) {
        temp.delete()
        throw e
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
