package info.meuse24.pdf_scanner.util

import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import info.meuse24.pdf_scanner.domain.model.PdfMarginPreset
import info.meuse24.pdf_scanner.domain.model.PdfPageOrientation
import info.meuse24.pdf_scanner.domain.model.PdfPageSetup
import info.meuse24.pdf_scanner.domain.model.PdfPageSizePreset
import info.meuse24.pdf_scanner.domain.usecase.ImagePdfOptions
import info.meuse24.pdf_scanner.domain.usecase.ImagePageLayout

internal data class CellRect(val x: Float, val y: Float, val w: Float, val h: Float)
internal data class DrawRect(val x: Float, val y: Float, val w: Float, val h: Float)

internal fun pageRectangle(setup: PdfPageSetup): PDRectangle {
    val base = when (setup.sizePreset) {
        PdfPageSizePreset.ISO_A3 -> PDRectangle.A3
        PdfPageSizePreset.ISO_A4 -> PDRectangle.A4
        PdfPageSizePreset.ISO_A5 -> PDRectangle.A5
        PdfPageSizePreset.NA_LETTER -> PDRectangle.LETTER
    }
    return if (setup.orientation == PdfPageOrientation.LANDSCAPE && base.width < base.height) {
        PDRectangle(base.height, base.width)
    } else {
        base
    }
}

internal fun marginPoints(setup: PdfPageSetup): Float = when (setup.marginPreset) {
    PdfMarginPreset.SMALL -> 10f
    PdfMarginPreset.MEDIUM -> 20f
    PdfMarginPreset.LARGE -> 35f
}

internal fun layoutCells(options: ImagePdfOptions): List<CellRect> {
    val page = pageRectangle(options.pageSetup)
    val margin = marginPoints(options.pageSetup)
    val gap = 10f
    val pageW = page.width
    val pageH = page.height
    return when (options.layout) {
        ImagePageLayout.SINGLE -> {
            val w = pageW - 2 * margin
            val h = pageH - 2 * margin
            listOf(CellRect(margin, margin, w, h))
        }
        ImagePageLayout.TWO_PER_PAGE -> {
            val w = pageW - 2 * margin
            val h = (pageH - 2 * margin - gap) / 2f
            listOf(
                CellRect(margin, margin + h + gap, w, h),
                CellRect(margin, margin, w, h)
            )
        }
        ImagePageLayout.FOUR_PER_PAGE -> {
            val w = (pageW - 2 * margin - gap) / 2f
            val h = (pageH - 2 * margin - gap) / 2f
            listOf(
                CellRect(margin, margin + h + gap, w, h),
                CellRect(margin + w + gap, margin + h + gap, w, h),
                CellRect(margin, margin, w, h),
                CellRect(margin + w + gap, margin, w, h)
            )
        }
    }
}

internal fun fitInsideCell(imgW: Float, imgH: Float, cell: CellRect): DrawRect {
    val scale = minOf(cell.w / imgW, cell.h / imgH)
    val drawW = imgW * scale
    val drawH = imgH * scale
    return DrawRect(
        x = cell.x + (cell.w - drawW) / 2f,
        y = cell.y + (cell.h - drawH) / 2f,
        w = drawW,
        h = drawH
    )
}
