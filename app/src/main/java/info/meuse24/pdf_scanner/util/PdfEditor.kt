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
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import com.tom_roush.pdfbox.util.Matrix
import info.meuse24.pdf_scanner.domain.usecase.HIGHLIGHT_ALPHA
import info.meuse24.pdf_scanner.domain.usecase.HIGHLIGHT_COLOR_BLUE
import info.meuse24.pdf_scanner.domain.usecase.HIGHLIGHT_COLOR_GREEN
import info.meuse24.pdf_scanner.domain.usecase.HIGHLIGHT_COLOR_RED
import info.meuse24.pdf_scanner.domain.usecase.HighlightRect
import info.meuse24.pdf_scanner.domain.usecase.HighlightStroke
import info.meuse24.pdf_scanner.domain.usecase.PdfCompressionPreset
import info.meuse24.pdf_scanner.domain.usecase.TextLine
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

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
    class PasswordRequiredException(cause: Throwable? = null) : IOException("PDF ist mit Benutzerpasswort geschützt", cause)

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

    /**
     * Entfernt den Textlayer, indem jede Seite als verlustfreies Bild neu gerendert wird.
     * Das Ergebnis ist eine neue PDF-Kopie ohne OCR-Textschicht.
     * Muss auf Dispatchers.IO aufgerufen werden.
     */
    open fun removeTextLayer(input: File, outputDir: File): File {
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
                                val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
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
     * Entfernt den Passwortschutz ohne Passworteingabe.
     * Funktioniert bei PDFs mit leerem Benutzerpasswort (z.B. nur Eigentümerpasswort / Nutzungseinschränkungen).
     * Wirft [PasswordRequiredException] wenn ein echtes Benutzerpasswort gesetzt ist.
     */
    open fun removePassword(input: File, outputDir: File): File {
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
    open fun restrictUsage(
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
                        ap.setCanPrintDegraded(canPrint)
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

    open fun isPdfEncrypted(input: File): Boolean {
        return try {
            PDDocument.load(input).use { document -> document.isEncrypted }
        } catch (_: InvalidPasswordException) {
            true
        }
    }

    open fun extractTextLines(file: File, pageIndex: Int): List<TextLine> {
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
     * Zeichnet gelbe Marker-Striche auf die angegebenen Seiten von [input]
     * und speichert das Ergebnis mit Suffix "_Markiert" in [outputDir].
     * [strokes] enthält normalisierte Koordinaten (0..1) pro Seite.
     */
    open fun applyHighlight(
        input: File,
        outputDir: File,
        strokes: List<HighlightStroke>,
        rects: List<HighlightRect> = emptyList()
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
                                HIGHLIGHT_COLOR_RED,
                                HIGHLIGHT_COLOR_GREEN,
                                HIGHLIGHT_COLOR_BLUE
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
                            HIGHLIGHT_COLOR_RED,
                            HIGHLIGHT_COLOR_GREEN,
                            HIGHLIGHT_COLOR_BLUE
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
    open fun applyAnnotations(
        input: File,
        outputDir: File,
        strokes: List<HighlightStroke>,
        rects: List<HighlightRect> = emptyList(),
        comments: List<info.meuse24.pdf_scanner.domain.usecase.TextComment> = emptyList()
    ): File {
        require(strokes.isNotEmpty() || rects.isNotEmpty() || comments.isNotEmpty()) {
            "Mindestens eine Annotation erforderlich"
        }
        return writeDerivedPdf(input, outputDir, "_Annotiert", "Annotate") { source, result ->
            val gsStrokeHighlight = PDExtendedGraphicsState().apply {
                strokingAlphaConstant = HIGHLIGHT_ALPHA
            }
            val gsRectHighlight = PDExtendedGraphicsState().apply {
                nonStrokingAlphaConstant = HIGHLIGHT_RECT_ALPHA
            }
            val font = loadOverlayFont(result)
            repeat(source.numberOfPages) { pageIdx ->
                val page = result.importPage(source.getPage(pageIdx))
                val pageStrokes = strokes.filter { it.pageIndex == pageIdx }
                val pageRects = rects.filter { it.pageIndex == pageIdx }
                val pageComments = comments.filter { it.pageIndex == pageIdx }
                if (pageStrokes.isNotEmpty() || pageRects.isNotEmpty() || pageComments.isNotEmpty()) {
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
                                HIGHLIGHT_COLOR_RED,
                                HIGHLIGHT_COLOR_GREEN,
                                HIGHLIGHT_COLOR_BLUE
                            )
                            pageRects.forEach { rect ->
                                val topLeft = mapDisplayToPdfCoord(
                                    rect.left, rect.top, pageWidth, pageHeight, rotation
                                )
                                val bottomRight = mapDisplayToPdfCoord(
                                    rect.right, rect.bottom, pageWidth, pageHeight, rotation
                                )
                                val rectLeft = minOf(topLeft.first, bottomRight.first)
                                val rectBottom = minOf(topLeft.second, bottomRight.second)
                                cs.addRect(
                                    rectLeft, rectBottom,
                                    abs(bottomRight.first - topLeft.first),
                                    abs(bottomRight.second - topLeft.second)
                                )
                                cs.fill()
                            }
                        }

                        if (pageStrokes.isNotEmpty()) {
                            cs.setGraphicsStateParameters(gsStrokeHighlight)
                            cs.setStrokingColor(
                                HIGHLIGHT_COLOR_RED,
                                HIGHLIGHT_COLOR_GREEN,
                                HIGHLIGHT_COLOR_BLUE
                            )
                            pageStrokes.forEach { stroke ->
                                val strokeWidthPt =
                                    (displayedWidth * stroke.strokeWidthFraction).coerceIn(3f, 36f)
                                cs.setLineWidth(strokeWidthPt)
                                cs.setLineCapStyle(1)
                                cs.setLineJoinStyle(1)
                                if (stroke.points.size == 1) {
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

/**
 * Wandelt normalisierte Anzeigekoordinaten (0..1) in kanonische PDF-Koordinaten um.
 * [pageWidth]/[pageHeight] sind die Maße der mediaBox (vor Rotation).
 * [rotation] ist der Rotationswinkel der Seite (0, 90, 180 oder 270).
 *
 * Hintergrund: PdfRenderer rendert Seiten bereits in der angezeigten Ausrichtung
 * (Rotation inklusive). Die hier errechneten Koordinaten beziehen sich auf den
 * unrotierten kanonischen Koordinatenraum, in den PDF-ContentStreams schreiben.
 */
internal fun mapDisplayToPdfCoord(
    nx: Float,
    ny: Float,
    pageWidth: Float,
    pageHeight: Float,
    rotation: Int
): Pair<Float, Float> = when (rotation) {
    90  -> ny * pageWidth  to nx * pageHeight
    180 -> (1f - nx) * pageWidth to (1f - ny) * pageHeight
    270 -> (1f - ny) * pageWidth to (1f - nx) * pageHeight
    else -> nx * pageWidth to (1f - ny) * pageHeight  // R=0
}

private const val MIN_TEXT_FONT_SIZE_PT = 4f
private const val LINE_VERTICAL_MERGE_FACTOR = 0.6f
private const val HIGHLIGHT_RECT_ALPHA = 0.3f

internal data class NormalizedTextBox(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    val centerY: Float get() = (top + bottom) / 2f
    val height: Float get() = bottom - top
}

internal fun mergeTextBoxesToLines(
    boxes: List<NormalizedTextBox>,
    pageIndex: Int
): List<TextLine> {
    if (boxes.isEmpty()) return emptyList()

    val sorted = boxes.sortedWith(compareBy<NormalizedTextBox> { it.centerY }.thenBy { it.left })
    val groups = mutableListOf<MutableList<NormalizedTextBox>>()
    var currentGroup = mutableListOf(sorted.first())
    var currentCenterY = sorted.first().centerY
    var currentHeight = sorted.first().height

    sorted.drop(1).forEach { box ->
        val tolerance = maxOf(currentHeight, box.height) * LINE_VERTICAL_MERGE_FACTOR
        if (abs(box.centerY - currentCenterY) <= tolerance) {
            currentGroup += box
            currentCenterY = currentGroup.map { it.centerY }.average().toFloat()
            currentHeight = currentGroup.maxOf { it.height }
        } else {
            groups += currentGroup
            currentGroup = mutableListOf(box)
            currentCenterY = box.centerY
            currentHeight = box.height
        }
    }
    groups += currentGroup

    return groups.map { lineBoxes ->
        TextLine(
            left = lineBoxes.minOf { it.left },
            top = lineBoxes.minOf { it.top },
            right = lineBoxes.maxOf { it.right },
            bottom = lineBoxes.maxOf { it.bottom },
            pageIndex = pageIndex
        )
    }.sortedBy { it.top }
}

private fun TextPosition.toNormalizedTextBox(
    displayedWidth: Float,
    displayedHeight: Float
): NormalizedTextBox? {
    if (displayedWidth <= 0f || displayedHeight <= 0f) return null
    if (fontSizeInPt < MIN_TEXT_FONT_SIZE_PT) return null

    val left = (xDirAdj / displayedWidth).coerceIn(0f, 1f)
    val top = ((yDirAdj - heightDir) / displayedHeight).coerceIn(0f, 1f)
    val right = ((xDirAdj + widthDirAdj) / displayedWidth).coerceIn(0f, 1f)
    val bottom = (yDirAdj / displayedHeight).coerceIn(0f, 1f)
    if (right <= left || bottom <= top) return null

    return NormalizedTextBox(
        left = left,
        top = top,
        right = right,
        bottom = bottom
    )
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
    val fontSize = calculateWatermarkFontSize(page.mediaBox.width, text.length)
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

private fun PdfEditor.appendTextComment(
    page: PDPage,
    document: PDDocument,
    font: PDFont,
    comment: info.meuse24.pdf_scanner.domain.usecase.TextComment,
    pageWidth: Float,
    pageHeight: Float,
    rotation: Int,
    displayedWidth: Float
) {
    val rawText = sanitizeCommentText(comment.text, font)
    if (rawText.isBlank()) return

    val fontSizePt = (displayedWidth * comment.fontSizeFraction).coerceIn(10f, 28f)
    val maxWidthPt = displayedWidth * 0.40f

    // Anker in PDF-Koordinaten
    val (anchorPdfX, anchorPdfY) = mapDisplayToPdfCoord(
        comment.anchorX, comment.anchorY, pageWidth, pageHeight, rotation
    )

    // Zeilenumbruch: explizite \n + Wortumbruch
    val allLines = mutableListOf<String>()
    rawText.split("\n").forEach { paragraph ->
        val words = paragraph.split(" ")
        var currentLine = StringBuilder()
        for (word in words) {
            val testLine = if (currentLine.isEmpty()) word else "${currentLine} $word"
            val lineWidthPt = try {
                font.getStringWidth(testLine) / 1000f * fontSizePt
            } catch (_: Exception) { 0f }
            if (lineWidthPt > maxWidthPt && currentLine.isNotEmpty()) {
                allLines += currentLine.toString()
                currentLine = StringBuilder(word)
            } else {
                currentLine = StringBuilder(testLine)
            }
        }
        if (currentLine.isNotEmpty()) allLines += currentLine.toString()
    }
    if (allLines.isEmpty()) return

    val lineHeight = fontSizePt * 1.2f

    // Text-Ausrichtungsvektoren je nach Rotation
    data class TextVecs(val a: Float, val b: Float, val c: Float, val d: Float,
                        val lineDX: Float, val lineDY: Float)
    val vecs = when (rotation) {
        90  -> TextVecs(0f, 1f, -1f, 0f, -lineHeight, 0f)
        180 -> TextVecs(-1f, 0f, 0f, -1f, 0f, lineHeight)
        270 -> TextVecs(0f, -1f, 1f, 0f, lineHeight, 0f)
        else -> TextVecs(1f, 0f, 0f, 1f, 0f, -lineHeight)
    }

    PDPageContentStream(
        document, page,
        PDPageContentStream.AppendMode.APPEND,
        true, true
    ).use { cs ->
        cs.setNonStrokingColor(40, 40, 40)
        cs.setFont(font, fontSizePt)
        allLines.forEachIndexed { lineIdx, line ->
            val x = anchorPdfX + vecs.lineDX * lineIdx
            val y = anchorPdfY + vecs.lineDY * lineIdx
            cs.beginText()
            cs.setTextMatrix(Matrix(vecs.a, vecs.b, vecs.c, vecs.d, x, y))
            cs.showText(line)
            cs.endText()
        }
    }
}

private fun sanitizeCommentText(text: String, font: PDFont): String {
    if (font is PDType1Font) {
        return text
            .replace("\t", "  ")
            .filter { it.code in 32..126 || it == '\n' }
            .trim()
    }
    return text
        .replace("\t", "  ")
        .replace("\r", "")
        .trim()
}

internal fun calculateWatermarkFontSize(pageWidth: Float, textLength: Int): Float {
    return ((pageWidth / textLength.coerceAtLeast(6)) * 0.55f).coerceIn(18f, 42f)
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
