package info.meuse24.pdf_scanner.util

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import java.io.File
import kotlin.math.roundToInt

internal data class TestPdfPage(
    val width: Float,
    val height: Float,
    val rotation: Int = 0
)

internal data class PdfPageSignature(
    val width: Int,
    val height: Int,
    val rotation: Int
)

internal object TestPdfFactory {
    fun createPdf(file: File, pages: List<TestPdfPage>): File {
        require(pages.isNotEmpty()) { "At least one test page is required" }

        PDDocument().use { document ->
            pages.forEach { spec ->
                val page = PDPage(PDRectangle(spec.width, spec.height)).apply {
                    rotation = spec.rotation
                }
                document.addPage(page)
                PDPageContentStream(document, page).use { content ->
                    // Draw a simple vector marker so the page has real content without font resources.
                    content.addRect(
                        18f,
                        18f,
                        (spec.width / 3f).coerceAtLeast(12f),
                        (spec.height / 4f).coerceAtLeast(12f)
                    )
                    content.stroke()
                }
            }
            document.save(file)
        }

        return file
    }
}

internal fun readPdfPageSignatures(pdfFile: File, password: String? = null): List<PdfPageSignature> {
    loadPdfDocument(pdfFile, password).use { document ->
        return (0 until document.numberOfPages).map { pageIndex ->
            val page = document.getPage(pageIndex)
            PdfPageSignature(
                width = page.mediaBox.width.roundToInt(),
                height = page.mediaBox.height.roundToInt(),
                rotation = page.rotation
            )
        }
    }
}

internal fun readPdfPageRotations(pdfFile: File, password: String? = null): List<Int> {
    loadPdfDocument(pdfFile, password).use { document ->
        return (0 until document.numberOfPages).map { index ->
            document.getPage(index).rotation
        }
    }
}

internal fun normalizePdfText(text: String): String =
    text.replace("\\s+".toRegex(), " ").trim()

private fun loadPdfDocument(pdfFile: File, password: String?): PDDocument =
    if (password == null) {
        PDDocument.load(pdfFile)
    } else {
        PDDocument.load(pdfFile, password)
    }
