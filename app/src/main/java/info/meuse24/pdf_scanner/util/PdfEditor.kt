package info.meuse24.pdf_scanner.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import com.tom_roush.pdfbox.pdmodel.font.PDFont
import com.tom_roush.pdfbox.pdmodel.font.PDType0Font
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import com.tom_roush.pdfbox.util.Matrix
import info.meuse24.pdf_scanner.domain.usecase.PdfCompressionPreset
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Utility für PDF-Bearbeitungsoperationen: Merge, Split, Reorder.
 *
 * Alle Methoden sind blocking (kein suspend). Der Aufrufer ist verantwortlich
 * für den Dispatchers.IO-Kontext. Kein UI-Thread-Kontakt.
 *
 * PdfBox-Operationen: PDFMergerUtility für Merge (erhält Text-Layer und
 * Lesezeichen), PDDocument.addPage() für Split und Reorder. PdfBox erhält
 * bestehende Text-Layer korrekt → isSearchable bleibt nach reorderPages() gültig.
 *
 * Technical Debt: WorkManager als robustere Alternative für sehr lange
 * Operationen (> 50 MB PDFs) in einer späteren Version geplant.
 */
@Singleton
open class PdfEditor @Inject constructor() {

    class WrongPasswordException(cause: Throwable? = null) : IOException("Falsches Passwort", cause)

    /**
     * Führt mehrere PDFs zu [output] zusammen.
     * PDFMergerUtility.appendDocument() erhält Text-Layer und Lesezeichen.
     * Bei IO-Fehler (z.B. Datei durch FileProvider gesperrt): IOException
     * mit Klartextmeldung, temp-Datei wird aufgeräumt.
     */
    open fun mergePdfs(inputs: List<File>, output: File) {
        require(inputs.size >= 2) { "Mindestens zwei Dateien zum Zusammenführen erforderlich" }
        val temp = File(output.parent, "${output.nameWithoutExtension}_tmp.pdf")
        try {
            val merger = PDFMergerUtility()
            PDDocument.load(inputs[0]).use { destination ->
                inputs.drop(1).forEach { file ->
                    PDDocument.load(file).use { source ->
                        merger.appendDocument(destination, source)
                    }
                }
                destination.save(temp)
            }
            if (!temp.exists() || temp.length() == 0L) {
                throw IOException("Merge erzeugte keine Ausgabedatei")
            }
            Files.move(
                temp.toPath(), output.toPath(),
                StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING
            )
        } catch (e: Exception) {
            temp.delete()
            throw if (e is IOException) e else IOException("Fehler beim Zusammenführen: ${e.message}", e)
        }
    }

    /**
     * Teilt [input] an den angegebenen Seitenindizes auf.
     * [splitAtPages]: 0-basierte Indizes, nach denen getrennt wird.
     * Beispiel: [1] bei 4 Seiten → Teile: Seiten 0–1, Seiten 2–3.
     * Gibt die Liste der erzeugten Dateien zurück.
     * Quelle wird einmal geladen und für alle Teile genutzt.
     */
    open fun splitPdf(input: File, outputDir: File, splitAtPages: List<Int>): List<File> {
        require(splitAtPages.isNotEmpty()) { "Mindestens ein Trennpunkt erforderlich" }
        val results = mutableListOf<File>()
        PDDocument.load(input).use { source ->
            val pageCount = source.numberOfPages
            val sorted = normalizeSplitPoints(pageCount, splitAtPages)
            buildRanges(pageCount, sorted).forEachIndexed { idx, range ->
                val name = resolveUniqueFilename(
                    outputDir, "${input.nameWithoutExtension}_Teil${idx + 1}"
                )
                val partFile = File(outputDir, "$name.pdf")
                PDDocument().use { part ->
                    range.forEach { pageIdx -> part.addPage(source.getPage(pageIdx)) }
                    part.save(partFile)
                }
                results.add(partFile)
            }
        }
        return results
    }

    /**
     * Ordnet die Seiten von [input] gemäß [newOrder] neu an.
     * [saveAsCopy] = false: Original wird atomar überschrieben (ATOMIC_MOVE).
     * [saveAsCopy] = true: Neue Datei mit Suffix „_Sortiert" wird angelegt.
     * PdfBox erhält OCR-Text-Layer korrekt → isSearchable bleibt nach
     * reorderPages() gültig und muss NICHT zurückgesetzt werden.
     * Gibt die Zieldatei zurück.
     */
    open fun reorderPages(input: File, newOrder: List<Int>, saveAsCopy: Boolean): File {
        require(newOrder.isNotEmpty()) { "Seitenreihenfolge darf nicht leer sein" }
        return editPdf(input, saveAsCopy, "_Sortiert", "Reorder") { source, reordered ->
            newOrder.forEach { pageIdx -> reordered.importPage(source.getPage(pageIdx)) }
        }
    }

    open fun rotatePages(
        input: File,
        pageIndexes: List<Int>,
        rotationDegrees: Int,
        saveAsCopy: Boolean
    ): File {
        require(pageIndexes.isNotEmpty()) { "Mindestens eine Seite zum Drehen erforderlich" }
        require(rotationDegrees % 90 == 0) { "Rotation muss ein Vielfaches von 90 sein" }
        return editPdf(input, saveAsCopy, "_Gedreht", "Rotate") { source, rotated ->
            val selected = normalizePageIndexes(source.numberOfPages, pageIndexes).toSet()
            repeat(source.numberOfPages) { pageIdx ->
                val imported = rotated.importPage(source.getPage(pageIdx))
                if (pageIdx in selected) {
                    imported.rotation = normalizeRotation(imported.rotation + rotationDegrees)
                }
            }
        }
    }

    open fun deletePages(
        input: File,
        pageIndexes: List<Int>,
        saveAsCopy: Boolean
    ): File {
        require(pageIndexes.isNotEmpty()) { "Mindestens eine Seite zum Löschen erforderlich" }
        return editPdf(input, saveAsCopy, "_Gekürzt", "Delete") { source, trimmed ->
            val selected = normalizePageIndexes(source.numberOfPages, pageIndexes).toSet()
            repeat(source.numberOfPages) { pageIdx ->
                if (pageIdx !in selected) {
                    trimmed.importPage(source.getPage(pageIdx))
                }
            }
            if (trimmed.numberOfPages == 0) {
                throw IOException("Delete würde alle Seiten entfernen")
            }
        }
    }

    open fun extractPages(input: File, outputDir: File, pageIndexes: List<Int>): File {
        require(pageIndexes.isNotEmpty()) { "Mindestens eine Seite zum Extrahieren erforderlich" }
        return writeDerivedPdf(input, outputDir, "_Extrahiert", "Extract") { source, extracted ->
            normalizePageIndexes(source.numberOfPages, pageIndexes).forEach { pageIdx ->
                extracted.importPage(source.getPage(pageIdx))
            }
        }
    }

    open fun duplicatePages(input: File, outputDir: File, pageIndexes: List<Int>): File {
        require(pageIndexes.isNotEmpty()) { "Mindestens eine Seite zum Duplizieren erforderlich" }
        return writeDerivedPdf(input, outputDir, "_Dupliziert", "Duplicate") { source, duplicated ->
            val selected = normalizePageIndexes(source.numberOfPages, pageIndexes).toSet()
            repeat(source.numberOfPages) { pageIdx ->
                duplicated.importPage(source.getPage(pageIdx))
                if (pageIdx in selected) {
                    duplicated.importPage(source.getPage(pageIdx))
                }
            }
        }
    }

    open fun addPageNumbers(input: File, outputDir: File): File {
        return writeDerivedPdf(input, outputDir, "_Nummeriert", "PageNumbers") { source, numbered ->
            val font = loadOverlayFont(numbered)
            repeat(source.numberOfPages) { pageIdx ->
                val page = numbered.importPage(source.getPage(pageIdx))
                appendPageNumber(page, numbered, font, pageIdx + 1)
            }
        }
    }

    open fun applyTextWatermark(input: File, outputDir: File, text: String): File {
        require(text.isNotBlank()) { "Wasserzeichen darf nicht leer sein" }
        return writeDerivedPdf(input, outputDir, "_Wasserzeichen", "Watermark") { source, watermarked ->
            val font = loadOverlayFont(watermarked)
            val watermarkText = sanitizeOverlayText(text.trim(), font)
            if (watermarkText.isBlank()) {
                throw IOException("Wasserzeichen enthält keine unterstützten Zeichen")
            }
            repeat(source.numberOfPages) { pageIdx ->
                val page = watermarked.importPage(source.getPage(pageIdx))
                appendTextWatermark(page, watermarked, font, watermarkText)
            }
        }
    }

    open fun compressPdf(input: File, outputDir: File, preset: PdfCompressionPreset): File {
        val baseName = resolveUniqueFilename(outputDir, "${input.nameWithoutExtension}_Komprimiert")
        val output = File(outputDir, "$baseName.pdf")
        return writePdf("Compress", output) { target ->
            PDDocument().use { compressed ->
                ParcelFileDescriptor.open(input, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                    PdfRenderer(pfd).use { renderer ->
                        repeat(renderer.pageCount) { pageIndex ->
                            renderer.openPage(pageIndex).use { page ->
                                val scale = preset.renderDpi / 72f
                                val bitmapWidth = (page.width * scale).toInt().coerceAtLeast(1)
                                val bitmapHeight = (page.height * scale).toInt().coerceAtLeast(1)
                                val bitmap = Bitmap.createBitmap(
                                    bitmapWidth,
                                    bitmapHeight,
                                    Bitmap.Config.ARGB_8888
                                )
                                try {
                                    Canvas(bitmap).drawColor(Color.WHITE)
                                    page.render(
                                        bitmap,
                                        null,
                                        null,
                                        PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY
                                    )
                                    val pdfPage = PDPage(PDRectangle(page.width.toFloat(), page.height.toFloat()))
                                    compressed.addPage(pdfPage)
                                    val image = JPEGFactory.createFromImage(compressed, bitmap, preset.jpegQuality)
                                    PDPageContentStream(compressed, pdfPage).use { contentStream ->
                                        contentStream.drawImage(
                                            image,
                                            0f,
                                            0f,
                                            pdfPage.mediaBox.width,
                                            pdfPage.mediaBox.height
                                        )
                                    }
                                } finally {
                                    bitmap.recycle()
                                }
                            }
                        }
                    }
                }
                compressed.save(target)
            }
        }
    }

    open fun protectPdf(input: File, outputDir: File, password: String): File {
        require(password.isNotBlank()) { "Passwort darf nicht leer sein" }
        return writeDerivedPdf(input, outputDir, "_Geschuetzt", "Protect") { source, protectedDoc ->
            repeat(source.numberOfPages) { pageIdx ->
                val imported = protectedDoc.importPage(source.getPage(pageIdx))
                imported.rotation = source.getPage(pageIdx).rotation
            }
            val permissions = AccessPermission.getOwnerAccessPermission()
            val policy = StandardProtectionPolicy(
                "${password.trim()}_${UUID.randomUUID()}",
                password.trim(),
                permissions
            ).apply {
                setEncryptionKeyLength(128)
                setPreferAES(true)
            }
            protectedDoc.protect(policy)
        }
    }

    open fun unlockPdf(input: File, outputDir: File, password: String): File {
        require(password.isNotBlank()) { "Passwort darf nicht leer sein" }
        val baseName = resolveUniqueFilename(outputDir, "${input.nameWithoutExtension}_Entsperrt")
        val output = File(outputDir, "$baseName.pdf")
        return writePdf("Unlock", output) { target ->
            try {
                PDDocument.load(input, password.trim()).use { document ->
                    document.setAllSecurityToBeRemoved(true)
                    document.save(target)
                }
            } catch (e: InvalidPasswordException) {
                throw WrongPasswordException(e)
            }
        }
    }

    open fun isPdfEncrypted(input: File): Boolean {
        return try {
            PDDocument.load(input).use { document -> document.isEncrypted }
        } catch (_: InvalidPasswordException) {
            true
        }
    }

    open fun applySignatureStamp(
        input: File,
        outputDir: File,
        signatureBitmap: Bitmap,
        pageIndex: Int,
        scaleFraction: Float
    ): File {
        require(scaleFraction > 0f) { "Signaturgroesse muss groesser als 0 sein" }
        return writeDerivedPdf(input, outputDir, "_Signiert", "Signature") { source, signed ->
            repeat(source.numberOfPages) { currentPageIndex ->
                val page = signed.importPage(source.getPage(currentPageIndex))
                if (currentPageIndex == pageIndex) {
                    appendSignatureStamp(
                        page = page,
                        document = signed,
                        signatureBitmap = signatureBitmap,
                        scaleFraction = scaleFraction
                    )
                }
            }
        }
    }

    /**
     * Gibt die Seitenanzahl von [pdfFile] zurück, 0 bei Fehler.
     * Muss auf Dispatchers.IO aufgerufen werden.
     */
    open fun getPageCount(pdfFile: File): Int {
        return try {
            ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { it.pageCount }
            }
        } catch (_: Exception) { 0 }
    }

    /**
     * Rendert Seite 0 von [pdfFile] als JPEG-Thumbnail in [outputFile].
     * Gibt true zurück bei Erfolg. Muss auf Dispatchers.IO aufgerufen werden.
     */
    open fun generateThumbnail(pdfFile: File, outputFile: File): Boolean {
        return try {
            val bitmap = renderPageThumbnail(pdfFile, 0, 200) ?: return false
            outputFile.outputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
            }
            bitmap.recycle()
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Rendert eine einzelne Seite als Bitmap, skaliert auf [maxSizePx] (längste Kante).
     * Das originale Seitenverhältnis wird beibehalten.
     * Gibt null bei Fehler zurück. Muss auf Dispatchers.IO aufgerufen werden.
     */
    open fun renderPageThumbnail(pdfFile: File, pageIndex: Int, maxSizePx: Int): Bitmap? {
        return try {
            ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    if (pageIndex >= renderer.pageCount) return null
                    renderer.openPage(pageIndex).use { page ->
                        val w = page.width.takeIf { it > 0 } ?: 210
                        val h = page.height.takeIf { it > 0 } ?: 297
                        val (bmpW, bmpH) = if (w >= h) {
                            maxSizePx to (maxSizePx * h / w).coerceAtLeast(1)
                        } else {
                            (maxSizePx * w / h).coerceAtLeast(1) to maxSizePx
                        }
                        val bitmap = Bitmap.createBitmap(bmpW, bmpH, Bitmap.Config.ARGB_8888)
                        Canvas(bitmap).drawColor(Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        bitmap
                    }
                }
            }
        } catch (_: Exception) {
            null
        }
    }
}

// ─── Interne Hilfsfunktionen ────────────────────────────────────────────────
// Top-level internal: von PdfEditor und von Unit-Tests aufrufbar,
// ohne Android-Klassen → JVM-testbar ohne returnDefaultValues.

/**
 * Berechnet Seitenbereiche für einen Split.
 * [splitPoints]: 0-basierte Indizes, nach denen getrennt wird.
 * Beispiel: pageCount=6, splitPoints=[1,3] → [0 until 2, 2 until 4, 4 until 6]
 */
internal fun buildRanges(pageCount: Int, splitPoints: List<Int>): List<IntRange> {
    val boundaries = listOf(0) + splitPoints.map { it + 1 } + listOf(pageCount)
    return boundaries.zipWithNext { from, to -> from until to }.filter { !it.isEmpty() }
}

/**
 * Normalisiert Split-Punkte auf gueltige 0-basierte Seitenindizes, nach denen
 * getrennt werden darf. Bei einem Dokument mit 4 Seiten sind also 0, 1 und 2
 * gueltig, 3 dagegen nicht.
 */
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

private inline fun PdfEditor.editPdf(
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

private inline fun PdfEditor.writeDerivedPdf(
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

private inline fun PdfEditor.writePdf(
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

private fun normalizeRotation(rotation: Int): Int {
    val normalized = rotation % 360
    return if (normalized < 0) normalized + 360 else normalized
}

private fun PdfEditor.appendPageNumber(
    page: PDPage,
    document: PDDocument,
    font: PDFont,
    pageNumber: Int
) {
    val label = pageNumber.toString()
    val fontSize = (page.mediaBox.height * 0.018f).coerceIn(10f, 14f)
    val textWidth = font.getStringWidth(label) / 1000f * fontSize
    val x = ((page.mediaBox.width - textWidth) / 2f).coerceAtLeast(12f)
    val y = 16f
    val graphicsState = PDExtendedGraphicsState().apply { nonStrokingAlphaConstant = 0.7f }

    PDPageContentStream(
        document,
        page,
        PDPageContentStream.AppendMode.APPEND,
        true,
        true
    ).use { contentStream ->
        contentStream.setGraphicsStateParameters(graphicsState)
        contentStream.beginText()
        contentStream.setNonStrokingColor(82, 82, 91)
        contentStream.setFont(font, fontSize)
        contentStream.newLineAtOffset(x, y)
        contentStream.showText(label)
        contentStream.endText()
    }
}

private fun PdfEditor.appendTextWatermark(
    page: PDPage,
    document: PDDocument,
    font: PDFont,
    text: String
) {
    val fontSize = (page.mediaBox.width / text.length.coerceAtLeast(6)) * 0.55f
        .coerceIn(18f, 42f)
    val textWidth = font.getStringWidth(text) / 1000f * fontSize
    val centerX = page.mediaBox.width / 2f
    val centerY = page.mediaBox.height / 2f
    val x = (centerX - textWidth / 2f).coerceAtLeast(18f)
    val y = centerY - fontSize / 3f
    val graphicsState = PDExtendedGraphicsState().apply { nonStrokingAlphaConstant = 0.16f }

    PDPageContentStream(
        document,
        page,
        PDPageContentStream.AppendMode.APPEND,
        true,
        true
    ).use { contentStream ->
        contentStream.setGraphicsStateParameters(graphicsState)
        contentStream.beginText()
        contentStream.setNonStrokingColor(75, 85, 99)
        contentStream.setFont(font, fontSize)
        contentStream.setTextMatrix(
            Matrix.getRotateInstance(Math.toRadians(45.0), x, y)
        )
        contentStream.showText(text)
        contentStream.endText()
    }
}

private fun PdfEditor.appendSignatureStamp(
    page: PDPage,
    document: PDDocument,
    signatureBitmap: Bitmap,
    scaleFraction: Float
) {
    val image = LosslessFactory.createFromImage(document, signatureBitmap)
    val maxWidth = page.mediaBox.width * scaleFraction.coerceIn(0.12f, 0.45f)
    val aspectRatio = signatureBitmap.height.toFloat() / signatureBitmap.width.toFloat()
    val width = maxWidth.coerceAtLeast(48f)
    val height = (width * aspectRatio).coerceAtLeast(24f)
    val margin = 20f
    val x = (page.mediaBox.width - width - margin).coerceAtLeast(margin)
    val y = margin

    PDPageContentStream(
        document,
        page,
        PDPageContentStream.AppendMode.APPEND,
        true,
        true
    ).use { contentStream ->
        contentStream.drawImage(image, x, y, width, height)
    }
}

private fun PdfEditor.loadOverlayFont(document: PDDocument): PDFont {
    val candidates = listOf(
        "/system/fonts/Roboto-Regular.ttf",
        "/system/fonts/NotoSans-Regular.ttf",
        "/system/fonts/DroidSans.ttf"
    )
    for (path in candidates) {
        try {
            val file = File(path)
            if (file.exists()) {
                return PDType0Font.load(document, file.inputStream(), true)
            }
        } catch (_: Exception) {
            // Fall through to the next available system font.
        }
    }
    @Suppress("DEPRECATION")
    return PDType1Font.HELVETICA
}

private fun sanitizeOverlayText(text: String, font: PDFont): String {
    if (font is PDType1Font) {
        return text.filter { it.code in 32..126 }
    }
    return text.replace("\n", " ").replace("\r", "").trim()
}

/**
 * Gibt einen eindeutigen Dateinamen (ohne .pdf-Extension) zurück.
 * Bei Konflikt: "${name}_2", "${name}_3", …
 */
internal fun resolveUniqueFilename(dir: File, name: String): String {
    if (!File(dir, "$name.pdf").exists()) return name
    var counter = 2
    while (File(dir, "${name}_$counter.pdf").exists()) counter++
    return "${name}_$counter"
}
