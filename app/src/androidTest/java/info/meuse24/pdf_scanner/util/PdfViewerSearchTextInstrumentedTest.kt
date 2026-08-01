package info.meuse24.pdf_scanner.util

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class PdfViewerSearchTextInstrumentedTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun searchExtractionMapsCropBoxAndRotationAndKeepsSmallGlyphAlignment() = runBlocking {
        val file = File(context.cacheDir, "viewer_search_crop_rotation.pdf")
        try {
            createFixture(file)

            val page = extractSearchTextFromPdf(file).toList().single()
            val boxes = extractPageGlyphBoxesFromPdf(file, pageIndex = 0)

            assertTrue("Extracted text: ${page.text}", page.text.contains("tiny"))
            assertTrue("Extracted text: ${page.text}", page.text.contains("X"))
            assertEquals(page.text.length, boxes.size)
            // The only null entry is the separator inserted between PDF text segments. In
            // particular, the 1-pt glyphs retain boxes: there must be no 4-pt filter.
            assertEquals(1, boxes.count { it == null })
            val normalSizeBox = requireNotNull(boxes.last())
            // The source position is (100, 150) in a CropBox (50, 80, 300, 400).
            // Rotation 90 maps the unrotated display location (0.167, about 0.80)
            // to the displayed page's upper-left area: x = 1 - y, y = x.
            assertTrue("Box: $normalSizeBox", normalSizeBox.left in 0.08f..0.14f)
            assertTrue("Box: $normalSizeBox", normalSizeBox.top in 0.15f..0.19f)
            assertTrue(normalSizeBox.right > normalSizeBox.left)
            assertTrue(normalSizeBox.bottom > normalSizeBox.top)
        } finally {
            file.delete()
        }
    }

    @Test
    fun searchExtractionReturnsEmptyContentForImageOnlyPage() = runBlocking {
        val file = File(context.cacheDir, "viewer_search_image_only.pdf")
        try {
            PDDocument().use { document ->
                document.addPage(PDPage(PDRectangle.A4))
                document.save(file)
            }

            val page = extractSearchTextFromPdf(file).toList().single()

            assertTrue(page.text.isEmpty())
            assertTrue(extractPageGlyphBoxesFromPdf(file, pageIndex = 0).isEmpty())
        } finally {
            file.delete()
        }
    }

    private fun createFixture(file: File) {
        PDDocument().use { document ->
            val page = PDPage(PDRectangle(500f, 700f)).apply {
                cropBox = PDRectangle(50f, 80f, 300f, 400f)
                rotation = 90
            }
            document.addPage(page)
            PDPageContentStream(document, page).use { content ->
                content.beginText()
                content.setFont(PDType1Font.HELVETICA, 1f)
                content.newLineAtOffset(100f, 150f)
                content.showText("tiny")
                content.endText()
                content.beginText()
                content.setFont(PDType1Font.HELVETICA, 12f)
                content.newLineAtOffset(100f, 120f)
                content.showText("X")
                content.endText()
            }
            document.save(file)
        }
    }
}
