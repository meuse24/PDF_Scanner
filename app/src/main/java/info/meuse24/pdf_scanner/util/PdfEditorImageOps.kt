package info.meuse24.pdf_scanner.util

import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import info.meuse24.pdf_scanner.domain.usecase.ImagePageLayout

internal data class CellRect(val x: Float, val y: Float, val w: Float, val h: Float)
internal data class DrawRect(val x: Float, val y: Float, val w: Float, val h: Float)

internal fun a4LayoutCells(layout: ImagePageLayout): List<CellRect> {
    val margin = 20f
    val gap = 10f
    val pageW = PDRectangle.A4.width
    val pageH = PDRectangle.A4.height
    return when (layout) {
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
