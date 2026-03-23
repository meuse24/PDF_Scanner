package info.meuse24.pdf_scanner.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.pdmodel.PDDocument
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
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
class PdfEditor @Inject constructor() {

    /**
     * Führt mehrere PDFs zu [output] zusammen.
     * PDFMergerUtility.appendDocument() erhält Text-Layer und Lesezeichen.
     * Bei IO-Fehler (z.B. Datei durch FileProvider gesperrt): IOException
     * mit Klartextmeldung, temp-Datei wird aufgeräumt.
     */
    fun mergePdfs(inputs: List<File>, output: File) {
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
    fun splitPdf(input: File, outputDir: File, splitAtPages: List<Int>): List<File> {
        require(splitAtPages.isNotEmpty()) { "Mindestens ein Trennpunkt erforderlich" }
        val results = mutableListOf<File>()
        PDDocument.load(input).use { source ->
            val pageCount = source.numberOfPages
            val sorted = splitAtPages.filter { it in 1 until pageCount }.sorted().distinct()
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
    fun reorderPages(input: File, newOrder: List<Int>, saveAsCopy: Boolean): File {
        require(newOrder.isNotEmpty()) { "Seitenreihenfolge darf nicht leer sein" }
        val parentDir = input.parentFile
            ?: input.absoluteFile.parentFile
            ?: throw IOException("Kann übergeordnetes Verzeichnis nicht ermitteln")
        val output = if (saveAsCopy) {
            val name = resolveUniqueFilename(parentDir, "${input.nameWithoutExtension}_Sortiert")
            File(parentDir, "$name.pdf")
        } else {
            input
        }
        val temp = File(parentDir, "${output.nameWithoutExtension}_tmp.pdf")
        try {
            PDDocument.load(input).use { source ->
                PDDocument().use { reordered ->
                    newOrder.forEach { pageIdx -> reordered.addPage(source.getPage(pageIdx)) }
                    reordered.save(temp)
                }
            }
            if (!temp.exists() || temp.length() == 0L) {
                throw IOException("Reorder erzeugte keine Ausgabedatei")
            }
            Files.move(
                temp.toPath(), output.toPath(),
                StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING
            )
        } catch (e: Exception) {
            temp.delete()
            throw if (e is IOException) e else IOException("Fehler beim Neu-Anordnen: ${e.message}", e)
        }
        return output
    }

    /**
     * Gibt die Seitenanzahl von [pdfFile] zurück, 0 bei Fehler.
     * Muss auf Dispatchers.IO aufgerufen werden.
     */
    fun getPageCount(pdfFile: File): Int {
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
    fun generateThumbnail(pdfFile: File, outputFile: File): Boolean {
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
    fun renderPageThumbnail(pdfFile: File, pageIndex: Int, maxSizePx: Int): Bitmap? {
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
 * Gibt einen eindeutigen Dateinamen (ohne .pdf-Extension) zurück.
 * Bei Konflikt: "${name}_2", "${name}_3", …
 */
internal fun resolveUniqueFilename(dir: File, name: String): String {
    if (!File(dir, "$name.pdf").exists()) return name
    var counter = 2
    while (File(dir, "${name}_$counter.pdf").exists()) counter++
    return "${name}_$counter"
}
