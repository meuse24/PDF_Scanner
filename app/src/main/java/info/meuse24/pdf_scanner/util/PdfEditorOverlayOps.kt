package info.meuse24.pdf_scanner.util

import android.graphics.Bitmap
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDFont
import com.tom_roush.pdfbox.pdmodel.font.PDType0Font
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import com.tom_roush.pdfbox.util.Matrix
import java.io.File

internal fun PdfEditor.appendPageNumber(
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

internal fun PdfEditor.appendTextWatermark(
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
        contentStream.setTextMatrix(Matrix.getRotateInstance(Math.toRadians(45.0), x, y))
        contentStream.showText(text)
        contentStream.endText()
    }
}

internal fun calculateWatermarkFontSize(pageWidth: Float, textLength: Int): Float {
    return ((pageWidth / textLength.coerceAtLeast(6)) * 0.55f).coerceIn(18f, 42f)
}

internal fun PdfEditor.appendSignatureStamp(
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

internal fun PdfEditor.loadOverlayFont(document: PDDocument): PDFont {
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
        }
    }
    @Suppress("DEPRECATION")
    return PDType1Font.HELVETICA
}

internal fun sanitizeOverlayText(text: String, font: PDFont): String {
    if (font is PDType1Font) {
        return text.filter { it.code in 32..126 }
    }
    return text.replace("\n", " ").replace("\r", "").trim()
}
