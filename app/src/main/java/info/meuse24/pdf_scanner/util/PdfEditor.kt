package info.meuse24.pdf_scanner.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.core.graphics.createBitmap
import com.tom_roush.pdfbox.cos.COSArray
import com.tom_roush.pdfbox.cos.COSDictionary
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDDocumentInformation
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDMetadata
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import com.tom_roush.pdfbox.pdmodel.font.PDFont
import com.tom_roush.pdfbox.pdmodel.font.PDType0Font
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import com.tom_roush.pdfbox.util.Matrix
import info.meuse24.pdf_scanner.domain.model.PdfMetadata
import info.meuse24.pdf_scanner.domain.pdf.PdfAnnotationOps
import info.meuse24.pdf_scanner.domain.pdf.PdfMetadataOps
import info.meuse24.pdf_scanner.domain.pdf.PdfPasswordRequiredException
import info.meuse24.pdf_scanner.domain.pdf.PdfRenderingOps
import info.meuse24.pdf_scanner.domain.pdf.PdfSecurityOps
import info.meuse24.pdf_scanner.domain.pdf.PdfStructureOps
import info.meuse24.pdf_scanner.domain.pdf.PdfTextOps
import info.meuse24.pdf_scanner.domain.pdf.PdfWrongPasswordException
import info.meuse24.pdf_scanner.domain.usecase.AnnotationOval
import info.meuse24.pdf_scanner.domain.usecase.AnnotationRect
import info.meuse24.pdf_scanner.domain.usecase.AnnotationShapeStyle
import info.meuse24.pdf_scanner.domain.usecase.AnnotationStroke
import info.meuse24.pdf_scanner.domain.usecase.AnnotationText
import info.meuse24.pdf_scanner.domain.usecase.HIGHLIGHT_ALPHA
import info.meuse24.pdf_scanner.domain.usecase.HIGHLIGHT_COLOR_BLUE
import info.meuse24.pdf_scanner.domain.usecase.HIGHLIGHT_COLOR_GREEN
import info.meuse24.pdf_scanner.domain.usecase.HIGHLIGHT_COLOR_RED
import info.meuse24.pdf_scanner.domain.usecase.HighlightRect
import info.meuse24.pdf_scanner.domain.usecase.HighlightStroke
import info.meuse24.pdf_scanner.domain.usecase.ImagePdfOptions
import info.meuse24.pdf_scanner.domain.usecase.PdfCompressionPreset
import info.meuse24.pdf_scanner.domain.usecase.RedactionRect
import info.meuse24.pdf_scanner.domain.usecase.TextLine
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

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
open class PdfEditor @Inject constructor() :
    PdfStructureOps,
    PdfRenderingOps,
    PdfSecurityOps,
    PdfTextOps,
    PdfAnnotationOps,
    PdfMetadataOps {

    class WrongPasswordException(cause: Throwable? = null) : PdfWrongPasswordException(cause)
    class PasswordRequiredException(cause: Throwable? = null) : PdfPasswordRequiredException(cause)

    /**
     * Führt mehrere PDFs zu [output] zusammen.
     * PDFMergerUtility.appendDocument() erhält Text-Layer und Lesezeichen.
     * Bei IO-Fehler (z.B. Datei durch FileProvider gesperrt): IOException
     * mit Klartextmeldung, temp-Datei wird aufgeräumt.
     */
    override open fun mergePdfs(inputs: List<File>, output: File) {
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
    override open fun splitPdf(input: File, outputDir: File, splitAtPages: List<Int>): List<File> {
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
    override open fun reorderPages(input: File, newOrder: List<Int>, saveAsCopy: Boolean): File {
        require(newOrder.isNotEmpty()) { "Seitenreihenfolge darf nicht leer sein" }
        return editPdf(input, saveAsCopy, "_Sortiert", "Reorder") { source, reordered ->
            newOrder.forEach { pageIdx -> reordered.importPage(source.getPage(pageIdx)) }
        }
    }

    override open fun rotatePages(
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

    override open fun deletePages(
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

    override open fun extractPages(input: File, outputDir: File, pageIndexes: List<Int>): File {
        require(pageIndexes.isNotEmpty()) { "Mindestens eine Seite zum Extrahieren erforderlich" }
        return writeDerivedPdf(input, outputDir, "_Extrahiert", "Extract") { source, extracted ->
            normalizePageIndexes(source.numberOfPages, pageIndexes).forEach { pageIdx ->
                extracted.importPage(source.getPage(pageIdx))
            }
        }
    }

    override open fun duplicatePages(input: File, outputDir: File, pageIndexes: List<Int>): File {
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

    override open fun addPageNumbers(input: File, outputDir: File): File {
        return writeDerivedPdf(input, outputDir, "_Nummeriert", "PageNumbers") { source, numbered ->
            val font = loadOverlayFont(numbered)
            repeat(source.numberOfPages) { pageIdx ->
                val page = numbered.importPage(source.getPage(pageIdx))
                appendPageNumber(page, numbered, font, pageIdx + 1)
            }
        }
    }

    override open fun applyTextWatermark(input: File, outputDir: File, text: String): File {
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

    override open fun compressPdf(input: File, outputDir: File, preset: PdfCompressionPreset): File {
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
                                val bitmap = createBitmap(
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

    override open fun protectPdf(input: File, outputDir: File, password: String): File {
        require(password.isNotBlank()) { "Passwort darf nicht leer sein" }
        return writeDerivedPdf(input, outputDir, "_Geschuetzt", "Protect") { source, protectedDoc ->
            repeat(source.numberOfPages) { pageIdx ->
                val imported = protectedDoc.importPage(source.getPage(pageIdx))
                imported.rotation = source.getPage(pageIdx).rotation
            }
            val normalizedPassword = password.trim()
            val permissions = AccessPermission.getOwnerAccessPermission()
            val policy = StandardProtectionPolicy(
                normalizedPassword,
                normalizedPassword,
                permissions
            ).apply {
                setEncryptionKeyLength(128)
                setPreferAES(true)
            }
            protectedDoc.protect(policy)
        }
    }

    override open fun unlockPdf(input: File, outputDir: File, password: String): File {
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

    /**
     * Entfernt den Textlayer, indem jede Seite als verlustfreies Bild neu gerendert wird.
     * Das Ergebnis ist eine neue PDF-Kopie ohne OCR-Textschicht.
     * Muss auf Dispatchers.IO aufgerufen werden.
     */
    override open fun removeTextLayer(input: File, outputDir: File): File {
        val baseName = resolveUniqueFilename(outputDir, "${input.nameWithoutExtension}_OhneTextlayer")
        val output = File(outputDir, "$baseName.pdf")
        return writePdf("RemoveTextLayer", output) { target ->
            PDDocument().use { cleaned ->
                ParcelFileDescriptor.open(input, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                    PdfRenderer(pfd).use { renderer ->
                        repeat(renderer.pageCount) { pageIndex ->
                            renderer.openPage(pageIndex).use { page ->
                                val w = page.width.takeIf { it > 0 } ?: 595
                                val h = page.height.takeIf { it > 0 } ?: 842
                                val bitmap = createBitmap(w, h, Bitmap.Config.ARGB_8888)
                                try {
                                    Canvas(bitmap).drawColor(Color.WHITE)
                                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                    val pdfPage = PDPage(PDRectangle(w.toFloat(), h.toFloat()))
                                    cleaned.addPage(pdfPage)
                                    val image = LosslessFactory.createFromImage(cleaned, bitmap)
                                    PDPageContentStream(cleaned, pdfPage).use { cs ->
                                        cs.drawImage(image, 0f, 0f, pdfPage.mediaBox.width, pdfPage.mediaBox.height)
                                    }
                                } finally {
                                    bitmap.recycle()
                                }
                            }
                        }
                    }
                }
                cleaned.save(target)
            }
        }
    }

    /**
     * Konvertiert alle Seiten eines PDFs in Graustufen.
     * Jede Seite wird per PdfRenderer gerendert, die Sättigung auf 0 gesetzt
     * und via LosslessFactory als neues PDF gespeichert. Der Textlayer wird dabei
     * nicht übertragen — das Ergebnis ist ein reines Bild-PDF mit Suffix „_SW".
     */
    override open fun convertToGrayscale(input: File, outputDir: File): File {
        val baseName = resolveUniqueFilename(outputDir, "${input.nameWithoutExtension}_SW")
        val output = File(outputDir, "$baseName.pdf")
        return writePdf("ConvertToGrayscale", output) { target ->
            PDDocument().use { cleaned ->
                ParcelFileDescriptor.open(input, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                    PdfRenderer(pfd).use { renderer ->
                        repeat(renderer.pageCount) { pageIndex ->
                            renderer.openPage(pageIndex).use { page ->
                                val w = page.width.takeIf { it > 0 } ?: 595
                                val h = page.height.takeIf { it > 0 } ?: 842
                                val colorBitmap = createBitmap(w, h, Bitmap.Config.ARGB_8888)
                                try {
                                    Canvas(colorBitmap).drawColor(Color.WHITE)
                                    page.render(colorBitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                    val grayBitmap = createBitmap(w, h, Bitmap.Config.ARGB_8888)
                                    try {
                                        val paint = Paint()
                                        val cm = ColorMatrix()
                                        cm.setSaturation(0f)
                                        paint.colorFilter = ColorMatrixColorFilter(cm)
                                        Canvas(grayBitmap).drawBitmap(colorBitmap, 0f, 0f, paint)
                                        val pdfPage = PDPage(PDRectangle(w.toFloat(), h.toFloat()))
                                        cleaned.addPage(pdfPage)
                                        val image = LosslessFactory.createFromImage(cleaned, grayBitmap)
                                        PDPageContentStream(cleaned, pdfPage).use { cs ->
                                            cs.drawImage(image, 0f, 0f, pdfPage.mediaBox.width, pdfPage.mediaBox.height)
                                        }
                                    } finally {
                                        grayBitmap.recycle()
                                    }
                                } finally {
                                    colorBitmap.recycle()
                                }
                            }
                        }
                    }
                }
                cleaned.save(target)
            }
        }
    }

    /**
     * Erzeugt ein neues PDF aus einer Liste von Bild-ByteArrays.
     * Null-Einträge (nicht lesbare Bilder) erzeugen eine leere Zelle.
     * Das Layout bestimmt, wie viele Bilder pro Seite angeordnet werden.
     * Bilder werden proportional (fit-inside) zentriert in ihre Zelle skaliert.
     */
    override open fun createPdfFromImages(
        imageBytes: List<ByteArray?>,
        options: ImagePdfOptions,
        outputFile: File
    ): File {
        require(imageBytes.isNotEmpty()) { "Bildliste darf nicht leer sein" }
        return writePdf("CreatePdfFromImages", outputFile) { target ->
            PDDocument().use { doc ->
                val cells = layoutCells(options)
                val pageRectangle = pageRectangle(options.pageSetup)
                imageBytes.chunked(options.layout.imagesPerPage).forEach { chunk ->
                    val page = PDPage(pageRectangle)
                    doc.addPage(page)
                    // Einen einzigen Content-Stream pro Seite öffnen, damit alle Bilder
                    // erhalten bleiben. Mehrere Streams ohne APPEND überschreiben sich.
                    PDPageContentStream(doc, page).use { cs ->
                        chunk.forEachIndexed { slotIndex, bytes ->
                            if (bytes != null) {
                                try {
                                    val image = PDImageXObject.createFromByteArray(
                                        doc, bytes, "img$slotIndex"
                                    )
                                    val cell = cells[slotIndex]
                                    val draw = fitInsideCell(
                                        image.width.toFloat(), image.height.toFloat(), cell
                                    )
                                    cs.drawImage(image, draw.x, draw.y, draw.w, draw.h)
                                } catch (_: Exception) {
                                    // Nicht lesbares Bild → leere Zelle, kein Abbruch
                                }
                            }
                        }
                    }
                }
                if (doc.numberOfPages == 0) {
                    throw IOException("CreatePdfFromImages erzeugte keine Seiten")
                }
                doc.save(target)
            }
        }
    }

    /**
     * Rechtssichere Schwärzung über Seiten-Neuaufbau:
     * Nur betroffene Seiten werden gerendert, die Schwärzungsbereiche werden
     * in das Seitenbitmap eingebrannt und als neue bildbasierte PDF-Seite
     * gespeichert. Nicht betroffene Seiten bleiben unverändert.
     *
     * Ergebnis: Unter dem geschwärzten Bereich bleibt kein extrahierbarer
     * Seiteninhalt zurück; betroffene Seiten verlieren bewusst ihren Textlayer.
     */
    override open fun applySecureRedaction(
        input: File,
        outputDir: File,
        rects: List<RedactionRect>
    ): File {
        require(rects.isNotEmpty()) { "Mindestens ein Schwärzungsbereich erforderlich" }

        val baseName = resolveUniqueFilename(outputDir, "${input.nameWithoutExtension}_Geschwaerzt")
        val output = File(outputDir, "$baseName.pdf")
        return writePdf("SecureRedaction", output) { target ->
            PDDocument.load(input).use { source ->
                ParcelFileDescriptor.open(input, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                    PdfRenderer(pfd).use { renderer ->
                        if (renderer.pageCount != source.numberOfPages) {
                            throw IOException("PdfRenderer und PdfBox liefern unterschiedliche Seitenanzahl")
                        }

                        val rectsByPage = rects
                            .groupBy { it.pageIndex }
                            .mapValues { (_, pageRects) ->
                                mergeRedactionRects(pageRects.mapNotNull(::normalizeRedactionRect))
                            }

                        PDDocument().use { redacted ->
                            repeat(source.numberOfPages) { pageIndex ->
                                val pageRects = rectsByPage[pageIndex].orEmpty()
                                if (pageRects.isEmpty()) {
                                    val imported = redacted.importPage(source.getPage(pageIndex))
                                    imported.rotation = source.getPage(pageIndex).rotation
                                } else {
                                    renderer.openPage(pageIndex).use { page ->
                                        val bitmap = renderPdfPageForRebuild(
                                            page = page,
                                            renderDpi = SECURE_REDACTION_RENDER_DPI,
                                            renderMode = PdfRenderer.Page.RENDER_MODE_FOR_PRINT
                                        )
                                        try {
                                            burnRedactionRects(bitmap, pageRects)

                                            val sourcePage = source.getPage(pageIndex)
                                            val displayRect = displayedPageRectangle(sourcePage)
                                            val pdfPage = PDPage(displayRect).apply {
                                                cropBox = displayRect
                                                rotation = 0
                                            }
                                            redacted.addPage(pdfPage)
                                            val image = LosslessFactory.createFromImage(redacted, bitmap)
                                            PDPageContentStream(redacted, pdfPage).use { contentStream ->
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

                            if (redacted.numberOfPages == 0) {
                                throw IOException("SecureRedaction erzeugte keine Seiten")
                            }
                            sanitizeInteractiveContent(redacted)
                            sanitizeDocumentMetadata(redacted)
                            redacted.save(target)
                        }
                    }
                }
            }
        }
    }

    /**
     * Liest PDF-Metadaten aus PDDocumentInformation.
     * Gibt bei verschlüsselten oder fehlerhaften PDFs ein leeres [PdfMetadata] zurück.
     */
    override open fun readMetadata(input: File): PdfMetadata {
        return try {
            PDDocument.load(input, "").use { doc ->
                val info = doc.documentInformation
                PdfMetadata(
                    title            = info.title?.takeIf { it.isNotBlank() },
                    author           = info.author?.takeIf { it.isNotBlank() },
                    creator          = info.creator?.takeIf { it.isNotBlank() },
                    subject          = info.subject?.takeIf { it.isNotBlank() },
                    keywords         = info.keywords?.takeIf { it.isNotBlank() },
                    creationDate     = runCatching { info.creationDate }.getOrNull(),
                    modificationDate = runCatching { info.modificationDate }.getOrNull()
                )
            }
        } catch (_: Exception) {
            PdfMetadata(null, null, null, null, null, null, null)
        }
    }

    /**
     * Aktualisiert eingebettete PDF-Metadaten in-place.
     * Das Original wird über eine temporäre Datei atomar ersetzt.
     * Das Änderungsdatum wird auf jetzt gesetzt, das Erstellungsdatum beibehalten.
     */
    override open fun updateMetadata(input: File, metadata: PdfMetadata): File {
        return writePdf("UpdateMetadata", input) { target ->
            PDDocument.load(input, "").use { document ->
                val info = document.documentInformation
                info.title = metadata.title
                info.author = metadata.author
                info.creator = metadata.creator
                info.subject = metadata.subject
                info.keywords = metadata.keywords
                info.creationDate = metadata.creationDate?.let { it.clone() as Calendar }
                info.modificationDate = Calendar.getInstance()
                document.save(target)
            }
        }
    }

    /**
     * Entfernt den Passwortschutz ohne Passworteingabe.
     * Funktioniert bei PDFs mit leerem Benutzerpasswort (z.B. nur Eigentümerpasswort / Nutzungseinschränkungen).
     * Wirft [PasswordRequiredException] wenn ein echtes Benutzerpasswort gesetzt ist.
     */
    override open fun removePassword(input: File, outputDir: File): File {
        val baseName = resolveUniqueFilename(outputDir, "${input.nameWithoutExtension}_OhneSchutz")
        val output = File(outputDir, "$baseName.pdf")
        return writePdf("RemovePassword", output) { target ->
            try {
                PDDocument.load(input, "").use { document ->
                    document.setAllSecurityToBeRemoved(true)
                    document.save(target)
                }
            } catch (e: InvalidPasswordException) {
                throw PasswordRequiredException(e)
            }
        }
    }

    /**
     * Setzt Nutzungseinschränkungen per Eigentümerpasswort.
     * [canPrint]/[canCopy]/[canEdit]: steuern Druck-, Kopier- und Bearbeitungsrechte.
     * Das Ergebnis ist eine neue PDF-Kopie mit Suffix „_Eingeschraenkt".
     * Wirft [PasswordRequiredException] wenn die Eingabe-PDF ein echtes Benutzerpasswort hat.
     */
    override open fun restrictUsage(
        input: File,
        outputDir: File,
        ownerPassword: String,
        canPrint: Boolean,
        canCopy: Boolean,
        canEdit: Boolean
    ): File {
        require(ownerPassword.isNotBlank()) { "Eigentümerpasswort darf nicht leer sein" }
        val baseName = resolveUniqueFilename(outputDir, "${input.nameWithoutExtension}_Eingeschraenkt")
        val output = File(outputDir, "$baseName.pdf")
        return writePdf("RestrictUsage", output) { target ->
            try {
                PDDocument.load(input, "").use { source ->
                    PDDocument().use { restricted ->
                        repeat(source.numberOfPages) { pageIdx ->
                            restricted.importPage(source.getPage(pageIdx))
                        }
                        val ap = AccessPermission()
                        ap.setCanPrint(canPrint)
                        ap.setCanPrintFaithful(canPrint)
                        ap.setCanExtractContent(canCopy)
                        ap.setCanModify(canEdit)
                        ap.setCanFillInForm(canEdit)
                        ap.setCanModifyAnnotations(canEdit)
                        ap.setCanAssembleDocument(canEdit)
                        val policy = StandardProtectionPolicy(ownerPassword.trim(), "", ap)
                        policy.setEncryptionKeyLength(128)
                        policy.setPreferAES(true)
                        restricted.protect(policy)
                        restricted.save(target)
                    }
                }
            } catch (e: InvalidPasswordException) {
                throw PasswordRequiredException(e)
            }
        }
    }

    override open fun isPdfEncrypted(input: File): Boolean {
        return try {
            PDDocument.load(input).use { document -> document.isEncrypted }
        } catch (_: InvalidPasswordException) {
            true
        }
    }

    override open fun extractTextLines(file: File, pageIndex: Int): List<TextLine> {
        if (pageIndex < 0) return emptyList()

        PDDocument.load(file).use { document ->
            if (pageIndex >= document.numberOfPages) return emptyList()

            val page = document.getPage(pageIndex)
            val rotation = normalizeRotation(page.rotation)
            val displayedWidth =
                if (rotation == 90 || rotation == 270) page.mediaBox.height else page.mediaBox.width
            val displayedHeight =
                if (rotation == 90 || rotation == 270) page.mediaBox.width else page.mediaBox.height
            val positions = mutableListOf<NormalizedTextBox>()

            val stripper = object : PDFTextStripper() {
                override fun writeString(text: String?, textPositions: MutableList<TextPosition>?) {
                    textPositions.orEmpty().forEach { position ->
                        position.toNormalizedTextBox(
                            displayedWidth = displayedWidth,
                            displayedHeight = displayedHeight
                        )?.let(positions::add)
                    }
                }
            }
            stripper.startPage = pageIndex + 1
            stripper.endPage = pageIndex + 1
            stripper.sortByPosition = true
            stripper.getText(document)

            return mergeTextBoxesToLines(positions, pageIndex)
        }
    }

    override open fun applySignatureStamp(
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
     * Zeichnet gelbe Marker-Striche auf die angegebenen Seiten von [input]
     * und speichert das Ergebnis mit Suffix "_Markiert" in [outputDir].
     * [strokes] enthält normalisierte Koordinaten (0..1) pro Seite.
     */
    override open fun applyHighlight(
        input: File,
        outputDir: File,
        strokes: List<HighlightStroke>,
        rects: List<HighlightRect>
    ): File {
        require(strokes.isNotEmpty() || rects.isNotEmpty()) {
            "Mindestens eine Markierung erforderlich"
        }
        return writeDerivedPdf(input, outputDir, "_Markiert", "Highlight") { source, result ->
            val gsStrokeHighlight = PDExtendedGraphicsState().apply {
                strokingAlphaConstant = HIGHLIGHT_ALPHA
            }
            val gsRectHighlight = PDExtendedGraphicsState().apply {
                nonStrokingAlphaConstant = HIGHLIGHT_RECT_ALPHA
            }
            repeat(source.numberOfPages) { pageIdx ->
                val page = result.importPage(source.getPage(pageIdx))
                val pageStrokes = strokes.filter { it.pageIndex == pageIdx }
                val pageRects = rects.filter { it.pageIndex == pageIdx }
                if (pageStrokes.isNotEmpty() || pageRects.isNotEmpty()) {
                    val pageWidth = page.mediaBox.width
                    val pageHeight = page.mediaBox.height
                    val rotation = normalizeRotation(page.rotation)
                    val displayedWidth = if (rotation == 90 || rotation == 270) pageHeight else pageWidth
                    PDPageContentStream(
                        result, page,
                        PDPageContentStream.AppendMode.APPEND,
                        true, true
                    ).use { cs ->
                        if (pageRects.isNotEmpty()) {
                            cs.setGraphicsStateParameters(gsRectHighlight)
                            cs.setNonStrokingColor(
                                HIGHLIGHT_COLOR_RED / 255f,
                                HIGHLIGHT_COLOR_GREEN / 255f,
                                HIGHLIGHT_COLOR_BLUE / 255f
                            )
                            pageRects.forEach { rect ->
                                val topLeft = mapDisplayToPdfCoord(
                                    rect.left,
                                    rect.top,
                                    pageWidth,
                                    pageHeight,
                                    rotation
                                )
                                val bottomRight = mapDisplayToPdfCoord(
                                    rect.right,
                                    rect.bottom,
                                    pageWidth,
                                    pageHeight,
                                    rotation
                                )
                                val rectLeft = minOf(topLeft.first, bottomRight.first)
                                val rectBottom = minOf(topLeft.second, bottomRight.second)
                                cs.addRect(
                                    rectLeft,
                                    rectBottom,
                                    abs(bottomRight.first - topLeft.first),
                                    abs(bottomRight.second - topLeft.second)
                                )
                                cs.fill()
                            }
                        }

                        cs.setGraphicsStateParameters(gsStrokeHighlight)
                        cs.setStrokingColor(
                            HIGHLIGHT_COLOR_RED / 255f,
                            HIGHLIGHT_COLOR_GREEN / 255f,
                            HIGHLIGHT_COLOR_BLUE / 255f
                        )
                        pageStrokes.forEach { stroke ->
                            val strokeWidthPt =
                                (displayedWidth * stroke.strokeWidthFraction).coerceIn(3f, 36f)
                            cs.setLineWidth(strokeWidthPt)
                            cs.setLineCapStyle(1)
                            cs.setLineJoinStyle(1)
                            if (stroke.points.size == 1) {
                                // Einzelpunkt als winziges Segment mit runden Kappen → erscheint als Kreis
                                val (px, py) = mapDisplayToPdfCoord(
                                    stroke.points[0].first, stroke.points[0].second,
                                    pageWidth, pageHeight, rotation
                                )
                                cs.moveTo(px - 0.5f, py)
                                cs.lineTo(px + 0.5f, py)
                                cs.stroke()
                            } else {
                                val first = stroke.points.first()
                                val (fx, fy) = mapDisplayToPdfCoord(
                                    first.first, first.second, pageWidth, pageHeight, rotation
                                )
                                cs.moveTo(fx, fy)
                                stroke.points.drop(1).forEach { (nx, ny) ->
                                    val (px, py) = mapDisplayToPdfCoord(
                                        nx, ny, pageWidth, pageHeight, rotation
                                    )
                                    cs.lineTo(px, py)
                                }
                                cs.stroke()
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Schreibt Markierungen (Strokes + Rects) und Textkommentare auf die Seiten von [input]
     * und speichert das Ergebnis mit Suffix "_Annotiert" in [outputDir].
     */
    override open fun applyAnnotations(
        input: File,
        outputDir: File,
        strokes: List<AnnotationStroke>,
        rects: List<AnnotationRect>,
        ovals: List<AnnotationOval>,
        comments: List<AnnotationText>
    ): File {
        require(strokes.isNotEmpty() || rects.isNotEmpty() || ovals.isNotEmpty() || comments.isNotEmpty()) {
            "Mindestens eine Annotation erforderlich"
        }
        return writeDerivedPdf(input, outputDir, "_Annotiert", "Annotate") { source, result ->
            val font = loadOverlayFont(result)
            repeat(source.numberOfPages) { pageIdx ->
                val page = result.importPage(source.getPage(pageIdx))
                val pageStrokes = strokes.filter { it.pageIndex == pageIdx }
                val pageRects = rects.filter { it.pageIndex == pageIdx }
                val pageOvals = ovals.filter { it.pageIndex == pageIdx }
                val pageComments = comments.filter { it.pageIndex == pageIdx }
                if (pageStrokes.isNotEmpty() || pageRects.isNotEmpty() || pageOvals.isNotEmpty() || pageComments.isNotEmpty()) {
                    val pageWidth = page.mediaBox.width
                    val pageHeight = page.mediaBox.height
                    val rotation = normalizeRotation(page.rotation)
                    val displayedWidth = if (rotation == 90 || rotation == 270) pageHeight else pageWidth
                    PDPageContentStream(
                        result, page,
                        PDPageContentStream.AppendMode.APPEND,
                        true, true
                    ).use { cs ->
                        pageRects.forEach { rect ->
                            appendAnnotationRect(cs, rect, displayedWidth, pageWidth, pageHeight, rotation)
                        }
                        pageOvals.forEach { oval ->
                            appendAnnotationOval(cs, oval, displayedWidth, pageWidth, pageHeight, rotation)
                        }
                        pageStrokes.forEach { stroke ->
                            appendAnnotationStroke(cs, stroke, displayedWidth, pageWidth, pageHeight, rotation)
                        }
                    }
                    // pageComments NACH dem use-Block schreiben, da appendTextComment selbst einen neuen Stream öffnet
                    if (pageComments.isNotEmpty()) {
                        pageComments.forEach { comment ->
                            appendTextComment(page, result, font, comment, pageWidth, pageHeight, rotation, displayedWidth)
                        }
                    }
                }
            }
        }
    }

    /**
     * Gibt die Seitenanzahl von [pdfFile] zurück, 0 bei Fehler.
     * Muss auf Dispatchers.IO aufgerufen werden.
     */
    override open fun getPageCount(pdfFile: File): Int {
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
    override open fun generateThumbnail(pdfFile: File, outputFile: File): Boolean {
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
    override open fun renderPageThumbnail(pdfFile: File, pageIndex: Int, maxSizePx: Int): Bitmap? {
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
                        val bitmap = createBitmap(bmpW, bmpH, Bitmap.Config.ARGB_8888)
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



