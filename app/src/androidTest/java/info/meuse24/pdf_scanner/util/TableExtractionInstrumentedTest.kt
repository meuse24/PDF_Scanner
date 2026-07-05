package info.meuse24.pdf_scanner.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import info.meuse24.pdf_scanner.domain.model.Document
import info.meuse24.pdf_scanner.domain.usecase.ExtractTableUseCase
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Deckt den in `docs/table-extraction-csv-implementation-plan.md` (Abschnitt "Instrumentation
 * und manuelle Prüfung") verlangten Punkt "Kleines Fixture-PDF mit echter ML-Kit-Erkennung" ab.
 *
 * Treibt die reale Produktionskette `ExtractTableUseCase` → `MlKitOcrPositionedTextExtractor` →
 * `OcrPipeline`/`MlKitTextRecognizerRunner`/`AndroidPdfPageInputImageLoader` (echter PdfRenderer,
 * echtes ML Kit) → `reconstructTable()` end-to-end. JVM-Unit-Tests können das laut Testkonzept
 * dieses Projekts bewusst nicht abdecken, da `PdfRenderer` und ML Kit im Android-Unit-Test-Stub-Jar
 * No-Ops sind.
 */
@RunWith(AndroidJUnit4::class)
class TableExtractionInstrumentedTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val ocrManager = OcrManager()
    private val ocrModelInstaller = AndroidOcrModelInstaller(context)
    private val ocrPipeline = OcrPipeline(ocrManager, ocrModelInstaller)
    private val textRecognizerRunner = MlKitTextRecognizerRunner(AndroidPdfPageInputImageLoader())
    private val positionedTextExtractor = MlKitOcrPositionedTextExtractor(
        ocrPipeline,
        textRecognizerRunner,
        DefaultDispatcherProvider()
    )
    private val extractTableUseCase = ExtractTableUseCase(positionedTextExtractor)

    private lateinit var fixtureFile: File

    @Before
    fun setUp() {
        fixtureFile = File(context.cacheDir, "androidtest_table_extraction_fixture.pdf")
    }

    @After
    fun tearDown() {
        fixtureFile.delete()
    }

    @Test
    fun extractTableUseCaseFindsGenuineTableInRealPdfViaRealOcr() = runBlocking {
        createTableFixturePdf(fixtureFile)
        val document = Document(
            filename = "androidtest_table_extraction_fixture",
            filepath = fixtureFile.absolutePath,
            timestamp = 0L,
            pageCount = 1,
            fileSize = fixtureFile.length()
        )

        val result = extractTableUseCase(document, languageCode = "auto")

        val table = result.tablesByPage[0]
        assertTrue("Es sollte eine Tabelle mit Spaltenstruktur erkannt werden", table != null)
        assertTrue("Mindestens 2 Spalten erwartet", table!!.columnBounds.size >= 2)
        assertTrue("Mindestens 3 Zeilen erwartet (Kopf- + Datenzeilen)", table.rows.size >= 3)

        val allText = table.rows.flatMap { row -> row.cells.map { it.text } }.joinToString(" ")
        assertTrue("Erwarteter Zellinhalt 'Chair' fehlt: $allText", allText.contains("Chair", ignoreCase = true))
        assertTrue("Erwarteter Zellinhalt '49' fehlt: $allText", allText.contains("49"))
    }
}

/**
 * Baut ein einseitiges PDF mit einer echten Rastergrafik (kein Vektortext, analog zu
 * `createImagePdfPages` in `SearchableAndRoundTripInstrumentedTest`): eine Kopfzeile plus drei
 * Datenzeilen zu je drei Spalten, mit großzügigem Spaltenabstand für zuverlässige OCR-Trennung.
 */
private fun createTableFixturePdf(file: File) {
    PDDocument().use { document ->
        val bitmap = Bitmap.createBitmap(1400, 1600, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                textSize = 80f
                typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            }
            val columnsX = listOf(100f, 550f, 1000f)
            val rows = listOf(
                listOf("Item", "Qty", "Price"),
                listOf("Chair", "2", "49.00"),
                listOf("Table", "1", "199.00"),
                listOf("Lamp", "3", "29.00")
            )
            rows.forEachIndexed { rowIndex, cells ->
                val y = 280f + rowIndex * 180f
                cells.forEachIndexed { columnIndex, cellText ->
                    canvas.drawText(cellText, columnsX[columnIndex], y, paint)
                }
            }

            val page = PDPage(PDRectangle.A4)
            document.addPage(page)
            val image = LosslessFactory.createFromImage(document, bitmap)
            PDPageContentStream(document, page).use { content ->
                content.drawImage(image, 0f, 0f, page.mediaBox.width, page.mediaBox.height)
            }
        } finally {
            bitmap.recycle()
        }
        document.save(file)
    }
}
