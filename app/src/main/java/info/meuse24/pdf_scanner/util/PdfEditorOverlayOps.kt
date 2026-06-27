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
import info.meuse24.pdf_scanner.domain.model.PageNumberHorizontalPosition
import info.meuse24.pdf_scanner.domain.model.PageNumberSettings
import info.meuse24.pdf_scanner.domain.model.PageNumberVerticalPosition
import java.io.File

internal fun PdfEditor.appendPageNumber(
    page: PDPage,
    document: PDDocument,
    font: PDFont,
    pageNumber: Int,
    totalPageCount: Int,
    settings: PageNumberSettings
) {
    val label = sanitizeOverlayText(
        buildPageNumberLabel(pageNumber, totalPageCount, settings),
        font
    )
    val mediaBox = page.mediaBox
    val rotation = normalizeRotation(page.rotation)
    val displayedWidth =
        if (rotation == 90 || rotation == 270) mediaBox.height else mediaBox.width
    val displayedHeight =
        if (rotation == 90 || rotation == 270) mediaBox.width else mediaBox.height
    val horizontalMargin = minOf(PAGE_NUMBER_MARGIN, displayedWidth * 0.1f)
    val verticalMargin = minOf(PAGE_NUMBER_MARGIN, displayedHeight * 0.1f)
    val availableWidth = (displayedWidth - horizontalMargin * 2f).coerceAtLeast(0f)
    val availableHeight = (displayedHeight - verticalMargin * 2f).coerceAtLeast(0f)
    val baseFontSize = minOf(
        (displayedHeight * 0.018f).coerceIn(10f, 14f),
        availableHeight
    )
    val textWidthAtUnitSize = font.getStringWidth(label) / 1000f
    val (fontSize, textWidth) = fitPageNumberText(
        baseFontSize = baseFontSize,
        textWidthAtUnitSize = textWidthAtUnitSize,
        availableWidth = availableWidth
    )
    val displayX = when (settings.horizontalPosition) {
        PageNumberHorizontalPosition.LEFT -> horizontalMargin
        PageNumberHorizontalPosition.CENTER ->
            (displayedWidth - textWidth) / 2f
        PageNumberHorizontalPosition.RIGHT ->
            displayedWidth - textWidth - horizontalMargin
    }.coerceAtLeast(horizontalMargin)
    val displayBaselineYFromTop = when (settings.verticalPosition) {
        PageNumberVerticalPosition.TOP -> verticalMargin + fontSize
        PageNumberVerticalPosition.BOTTOM -> displayedHeight - verticalMargin
    }
    val (relativeX, relativeY) = mapDisplayToPdfCoord(
        nx = displayX / displayedWidth,
        ny = displayBaselineYFromTop / displayedHeight,
        pageWidth = mediaBox.width,
        pageHeight = mediaBox.height,
        rotation = rotation
    )
    val x = mediaBox.lowerLeftX + relativeX
    val y = mediaBox.lowerLeftY + relativeY
    val textMatrix = when (rotation) {
        90 -> Matrix(0f, 1f, -1f, 0f, x, y)
        180 -> Matrix(-1f, 0f, 0f, -1f, x, y)
        270 -> Matrix(0f, -1f, 1f, 0f, x, y)
        else -> Matrix(1f, 0f, 0f, 1f, x, y)
    }
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
        contentStream.setNonStrokingColor(82 / 255f, 82 / 255f, 91 / 255f)
        contentStream.setFont(font, fontSize)
        contentStream.setTextMatrix(textMatrix)
        contentStream.showText(label)
        contentStream.endText()
    }
}

internal fun fitPageNumberText(
    baseFontSize: Float,
    textWidthAtUnitSize: Float,
    availableWidth: Float
): Pair<Float, Float> {
    require(baseFontSize > 0f)
    require(textWidthAtUnitSize >= 0f)
    require(availableWidth >= 0f)

    val fontSize = if (textWidthAtUnitSize == 0f) {
        baseFontSize
    } else {
        minOf(baseFontSize, availableWidth / textWidthAtUnitSize)
    }
    return fontSize to textWidthAtUnitSize * fontSize
}

internal fun buildPageNumberLabel(
    pageNumber: Int,
    totalPageCount: Int,
    settings: PageNumberSettings
): String {
    val number = if (settings.includeTotalPageCount) {
        "$pageNumber / $totalPageCount"
    } else {
        pageNumber.toString()
    }
    val prefix = settings.prefix.trim()
    return if (prefix.isEmpty()) number else "$prefix $number"
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
        contentStream.setNonStrokingColor(75 / 255f, 85 / 255f, 99 / 255f)
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

private const val PAGE_NUMBER_MARGIN = 16f
