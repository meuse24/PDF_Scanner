package info.meuse24.pdf_scanner.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfRenderer
import com.tom_roush.pdfbox.cos.COSArray
import com.tom_roush.pdfbox.cos.COSDictionary
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDDocumentInformation
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.common.PDMetadata
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import info.meuse24.pdf_scanner.domain.usecase.RedactionRect
import kotlin.math.max
import kotlin.math.min

private const val REDACTION_TOUCH_TOLERANCE = 0.001f
private const val DEFAULT_REBUILD_PAGE_WIDTH_PT = 595
private const val DEFAULT_REBUILD_PAGE_HEIGHT_PT = 842
private const val PDF_POINTS_PER_INCH = 72f
internal const val SECURE_REDACTION_RENDER_DPI = 300f

internal data class NormalizedRedactionRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
)

internal fun normalizeRedactionRect(rect: RedactionRect): NormalizedRedactionRect? {
    val left = min(rect.left, rect.right).coerceIn(0f, 1f)
    val right = max(rect.left, rect.right).coerceIn(0f, 1f)
    val top = min(rect.top, rect.bottom).coerceIn(0f, 1f)
    val bottom = max(rect.top, rect.bottom).coerceIn(0f, 1f)
    if (right - left <= REDACTION_TOUCH_TOLERANCE || bottom - top <= REDACTION_TOUCH_TOLERANCE) {
        return null
    }
    return NormalizedRedactionRect(left, top, right, bottom)
}

internal fun mergeRedactionRects(rects: List<NormalizedRedactionRect>): List<NormalizedRedactionRect> {
    if (rects.isEmpty()) return emptyList()

    val merged = mutableListOf<NormalizedRedactionRect>()
    rects.forEach { candidate ->
        var current = candidate
        var mergedAnything = true
        while (mergedAnything) {
            mergedAnything = false
            val iterator = merged.listIterator()
            while (iterator.hasNext()) {
                val existing = iterator.next()
                if (redactionRectsIntersectOrTouch(existing, current)) {
                    current = NormalizedRedactionRect(
                        left = min(existing.left, current.left),
                        top = min(existing.top, current.top),
                        right = max(existing.right, current.right),
                        bottom = max(existing.bottom, current.bottom)
                    )
                    iterator.remove()
                    mergedAnything = true
                }
            }
        }
        merged += current
    }
    return merged.sortedWith(compareBy<NormalizedRedactionRect> { it.top }.thenBy { it.left })
}

private fun redactionRectsIntersectOrTouch(
    first: NormalizedRedactionRect,
    second: NormalizedRedactionRect
): Boolean {
    return first.left <= second.right + REDACTION_TOUCH_TOLERANCE &&
        second.left <= first.right + REDACTION_TOUCH_TOLERANCE &&
        first.top <= second.bottom + REDACTION_TOUCH_TOLERANCE &&
        second.top <= first.bottom + REDACTION_TOUCH_TOLERANCE
}

internal fun sanitizeInteractiveContent(document: PDDocument) {
    val catalog = document.documentCatalog.cosObject
    catalog.removeItem(COSName.ACRO_FORM)
    catalog.removeItem(COSName.OPEN_ACTION)
    catalog.removeItem(COSName.AA)
    catalog.removeItem(COSName.getPDFName("AF"))

    val names = catalog.getDictionaryObject(COSName.NAMES) as? COSDictionary
    if (names != null) {
        names.removeItem(COSName.getPDFName("EmbeddedFiles"))
        names.removeItem(COSName.getPDFName("JavaScript"))
        if (names.keySet().isEmpty()) {
            catalog.removeItem(COSName.NAMES)
        }
    }

    repeat(document.numberOfPages) { pageIndex ->
        sanitizeInteractivePageContent(document.getPage(pageIndex).cosObject)
    }
}

internal fun sanitizeDocumentMetadata(document: PDDocument) {
    document.setDocumentInformation(PDDocumentInformation())
    document.document.trailer.removeItem(COSName.INFO)

    val catalog = document.documentCatalog
    catalog.setMetadata(null as PDMetadata?)
    catalog.cosObject.removeItem(COSName.METADATA)

    repeat(document.numberOfPages) { pageIndex ->
        val page = document.getPage(pageIndex)
        page.setMetadata(null as PDMetadata?)
        page.cosObject.removeItem(COSName.METADATA)
    }
}

private fun sanitizeInteractivePageContent(page: COSDictionary) {
    page.removeItem(COSName.AA)
    page.removeItem(COSName.getPDFName("AF"))

    val annotations = page.getDictionaryObject(COSName.ANNOTS) as? COSArray ?: return
    val keptAnnotations = COSArray()
    repeat(annotations.size()) { index ->
        val annotation = annotations.getObject(index) as? COSDictionary ?: return@repeat
        if (!shouldRemoveInteractiveAnnotation(annotation)) {
            keptAnnotations.add(annotation)
        }
    }

    if (keptAnnotations.size() == 0) {
        page.removeItem(COSName.ANNOTS)
    } else {
        page.setItem(COSName.ANNOTS, keptAnnotations)
    }
}

private fun shouldRemoveInteractiveAnnotation(annotation: COSDictionary): Boolean {
    val subtype = annotation.getNameAsString(COSName.SUBTYPE)
    if (subtype in setOf("Link", "Widget", "FileAttachment", "Screen")) return true
    return annotation.containsKey(COSName.A) ||
        annotation.containsKey(COSName.AA) ||
        annotation.containsKey(COSName.DEST) ||
        annotation.containsKey(COSName.getPDFName("FS")) ||
        annotation.containsKey(COSName.getPDFName("PA")) ||
        annotation.containsKey(COSName.getPDFName("AF"))
}

internal fun burnRedactionRects(bitmap: Bitmap, rects: List<NormalizedRedactionRect>) {
    val canvas = Canvas(bitmap)
    val paint = Paint().apply {
        color = Color.BLACK
        style = Paint.Style.FILL
        isAntiAlias = false
    }
    rects.forEach { rect ->
        val left = (rect.left * bitmap.width).coerceIn(0f, bitmap.width.toFloat())
        val top = (rect.top * bitmap.height).coerceIn(0f, bitmap.height.toFloat())
        val right = (rect.right * bitmap.width).coerceIn(0f, bitmap.width.toFloat())
        val bottom = (rect.bottom * bitmap.height).coerceIn(0f, bitmap.height.toFloat())
        if (right > left && bottom > top) canvas.drawRect(left, top, right, bottom, paint)
    }
}

internal fun renderPdfPageForRebuild(
    page: PdfRenderer.Page,
    renderDpi: Float,
    renderMode: Int
): Bitmap {
    val baseWidth = page.width.takeIf { it > 0 } ?: DEFAULT_REBUILD_PAGE_WIDTH_PT
    val baseHeight = page.height.takeIf { it > 0 } ?: DEFAULT_REBUILD_PAGE_HEIGHT_PT
    val scale = (renderDpi / PDF_POINTS_PER_INCH).coerceAtLeast(1f)
    val bitmapWidth = (baseWidth * scale).toInt().coerceAtLeast(1)
    val bitmapHeight = (baseHeight * scale).toInt().coerceAtLeast(1)
    val bitmap = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888)
    Canvas(bitmap).drawColor(Color.WHITE)
    page.render(bitmap, null, null, renderMode)
    return bitmap
}

internal fun displayedPageRectangle(page: PDPage): PDRectangle {
    val box = page.cropBox ?: page.mediaBox
    val rotation = normalizeRotation(page.rotation)
    return if (rotation == 90 || rotation == 270) {
        PDRectangle(box.height, box.width)
    } else {
        PDRectangle(box.width, box.height)
    }
}
