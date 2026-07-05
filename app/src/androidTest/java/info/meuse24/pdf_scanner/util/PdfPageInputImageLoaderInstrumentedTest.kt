package info.meuse24.pdf_scanner.util

import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Deckt den echten Loaderpfad ab (PdfRenderer, echte Bitmap-Dimensionen, Bitmap-Recycling),
 * der in reinen JVM-Unit-Tests dieses Projekts nicht erreichbar ist (kein Robolectric;
 * `PdfRenderer`/`Bitmap` sind unter dem Unit-Test-Stub-Jar No-Ops). Ergänzt
 * `PdfPageInputImageLoaderTest` (JVM, testet nur die reine `ocrBitmapSize`-Arithmetik).
 */
@RunWith(AndroidJUnit4::class)
class PdfPageInputImageLoaderInstrumentedTest {

    private val loader = AndroidPdfPageInputImageLoader()

    // Alle drei Tests nutzen einen Block-Body (statt `= runBlocking { ... }`), damit die
    // Methode garantiert Unit/void zurückgibt: `runBlocking` ist - anders als
    // kotlinx-coroutines-test `runTest` - generisch über den Block-Rückgabetyp; ein
    // Ausdrucks-Body hätte den Rückgabetyp der Funktion auf den Typ der letzten Anweisung im
    // Block inferiert (hier `Boolean` von `File.delete()`). JUnit lehnt @Test-Methoden ohne
    // void-Rückgabe zur Laufzeit mit `InvalidTestClassError` ab - ein rein durch
    // Kotlin-Kompilierung nicht erkennbarer Fehler, der erst beim echten Testlauf auffiel.

    @Test
    fun forEachPageImage_liefert_korrekte_220_DPI_Dimensionen_und_Seitenindex_Sequenz() = runBlocking {
        val pdfFile = File.createTempFile("loader-test", ".pdf").apply { deleteOnExit() }
        createA4Pdf(pdfFile, pageCount = 3)

        data class Call(val pageIndex: Int, val pageCount: Int, val width: Int, val height: Int)
        val calls = mutableListOf<Call>()

        loader.forEachPageImage(pdfFile, renderScale = PDF_TABLE_RENDER_SCALE) { pageIndex, pageCount, width, height, _ ->
            calls += Call(pageIndex, pageCount, width, height)
        }

        // Erwartung wird bewusst über dieselbe ocrBitmapSize()-Funktion berechnet statt
        // hartcodiert (Rundungsfehler: toInt() truncated, rundet nicht). Die A4-Seitenmaße in
        // Punkten werden dafür über einen eigenen, echten PdfRenderer abgefragt statt als
        // "595 x 842" angenommen: PDFBox' PDRectangle.A4 ist 595.276 x 841.89pt, und Android
        // rundet/truncatet das beim Melden von Page.width/height nicht zwangsläufig auf 595/842 -
        // auf einem echten Gerät kann Page.height z. B. 841 statt 842 liefern.
        val (pageWidthPts, pageHeightPts) = realPageSizeInPoints(pdfFile)
        val (expectedWidth, expectedHeight) = ocrBitmapSize(pageWidthPts, pageHeightPts, PDF_TABLE_RENDER_SCALE)
        assertEquals(3, calls.size)
        assertEquals(listOf(0, 1, 2), calls.map { it.pageIndex })
        calls.forEach { call ->
            assertEquals(3, call.pageCount)
            assertEquals(expectedWidth, call.width)
            assertEquals(expectedHeight, call.height)
        }

        pdfFile.delete()
        Unit
    }

    @Test
    fun forEachPageImage_bricht_bei_Exception_im_Callback_sauber_ab_ohne_Folgeseiten() = runBlocking {
        val pdfFile = File.createTempFile("loader-test-error", ".pdf").apply { deleteOnExit() }
        createA4Pdf(pdfFile, pageCount = 3)

        var processedPages = 0
        val error = runCatching {
            loader.forEachPageImage(pdfFile) { pageIndex, _, _, _, _ ->
                processedPages++
                if (pageIndex == 1) error("simulierter Fehler auf Seite 2")
            }
        }.exceptionOrNull()

        assertTrue(error != null && error.message == "simulierter Fehler auf Seite 2")
        // Nur Seiten 0 und 1 wurden verarbeitet, Seite 3 nicht mehr - kein Zombie-Callback nach
        // dem Abbruch, kein Crash durch bereits recycelte Bitmaps aus vorherigen Seiten.
        assertEquals(2, processedPages)

        pdfFile.delete()
        Unit
    }

    @Test
    fun forEachPageImage_ueberlebt_Coroutine_Cancellation_mitten_im_Mehrseiten_Lauf() = runBlocking {
        val pdfFile = File.createTempFile("loader-test-cancel", ".pdf").apply { deleteOnExit() }
        createA4Pdf(pdfFile, pageCount = 5)

        var processedPages = 0
        // Ein blosses yield() im aeusseren runBlocking synchronisiert NICHT zuverlaessig mit der
        // async-Coroutine auf Dispatchers.Default (echter, separater Thread): auf einem echten
        // Geraet gewinnt gelegentlich cancelAndJoin() das Rennen, bevor Seite 0 ueberhaupt zu
        // laufen beginnt (processedPages bleibt 0, Test war flaky). Ein CompletableDeferred
        // garantiert, dass Seite 0 nachweislich in Bearbeitung ist, bevor abgebrochen wird.
        val reachedFirstPage = CompletableDeferred<Unit>()
        val deferred = CoroutineScope(Dispatchers.Default).async {
            loader.forEachPageImage(pdfFile) { pageIndex, _, _, _, _ ->
                processedPages++
                if (pageIndex == 0) {
                    reachedFirstPage.complete(Unit)
                    yield() // Cancellation-Fenster für den Test unten.
                }
            }
        }

        reachedFirstPage.await()
        deferred.cancelAndJoin()

        // Darf nicht crashen (z. B. durch Bitmap-Zugriff nach Recycle bei Cancellation) -
        // das bloße Erreichen dieser Zeile ohne Exception ist die eigentliche Assertion. Seite 0
        // ist durch reachedFirstPage.await() garantiert bereits verarbeitet.
        assertTrue(processedPages in 1..5)

        pdfFile.delete()
        Unit
    }

    private fun createA4Pdf(file: File, pageCount: Int) {
        PDDocument().use { document ->
            repeat(pageCount) {
                document.addPage(PDPage(PDRectangle.A4))
            }
            document.save(file)
        }
    }

    /** Fragt die tatsächlichen Seitenmaße (in Punkten) über einen echten `PdfRenderer` ab, statt
     * nominale A4-Werte anzunehmen - so bleibt die Erwartung unabhängig davon, wie Android
     * PDFBox' `PDRectangle.A4` (595.276 x 841.89pt) auf ganzzahlige Punkte abbildet. */
    private fun realPageSizeInPoints(file: File): Pair<Int, Int> =
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                renderer.openPage(0).use { page -> page.width to page.height }
            }
        }
}
