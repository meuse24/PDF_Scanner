package info.meuse24.pdf_scanner.domain.usecase

const val DEFAULT_ANNOTATION_COLOR_ARGB = 0x66FFDC00
const val DEFAULT_ANNOTATION_TEXT_COLOR_ARGB = 0xFF1E1E1E.toInt()
const val DEFAULT_ANNOTATION_STROKE_WIDTH_FRACTION = 0.025f

enum class AnnotationShapeStyle { FILLED, FRAME }

data class AnnotationStroke(
    val points: List<Pair<Float, Float>>,
    val pageIndex: Int,
    val strokeWidthFraction: Float = DEFAULT_ANNOTATION_STROKE_WIDTH_FRACTION,
    val color: Int = DEFAULT_ANNOTATION_COLOR_ARGB
)

data class AnnotationRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val pageIndex: Int,
    val color: Int = DEFAULT_ANNOTATION_COLOR_ARGB,
    // Rect defaults stay filled because text-snap currently materializes area highlights as rectangles.
    val style: AnnotationShapeStyle = AnnotationShapeStyle.FILLED,
    val strokeWidthFraction: Float = DEFAULT_ANNOTATION_STROKE_WIDTH_FRACTION
)

data class AnnotationOval(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val pageIndex: Int,
    val color: Int = DEFAULT_ANNOTATION_COLOR_ARGB,
    val style: AnnotationShapeStyle = AnnotationShapeStyle.FRAME,
    val strokeWidthFraction: Float = DEFAULT_ANNOTATION_STROKE_WIDTH_FRACTION
)

data class AnnotationText(
    val pageIndex: Int,
    val anchorX: Float,
    val anchorY: Float,
    val text: String,
    val fontSizeFraction: Float,
    val color: Int = DEFAULT_ANNOTATION_TEXT_COLOR_ARGB
)
