package info.meuse24.pdf_scanner.domain.model

import kotlin.math.roundToInt

enum class OcrQuality {
    HIGH,
    MEDIUM,
    LOW,
    UNKNOWN
}

fun Float?.toQuality(): OcrQuality = when {
    this == null -> OcrQuality.UNKNOWN
    this >= 0.7f -> OcrQuality.HIGH
    this >= OcrThresholds.LOW_CONFIDENCE_WARNING -> OcrQuality.MEDIUM
    else -> OcrQuality.LOW
}

fun Float?.toQualityPercent(): Int? = this?.times(100f)?.roundToInt()
