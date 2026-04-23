package info.meuse24.pdf_scanner.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OcrQualityTest {

    @Test
    fun `null confidence maps to unknown`() {
        assertEquals(OcrQuality.UNKNOWN, (null as Float?).toQuality())
        assertNull((null as Float?).toQualityPercent())
    }

    @Test
    fun `confidence thresholds map to expected quality`() {
        assertEquals(OcrQuality.HIGH, 0.70f.toQuality())
        assertEquals(OcrQuality.MEDIUM, 0.69f.toQuality())
        assertEquals(OcrQuality.MEDIUM, 0.30f.toQuality())
        assertEquals(OcrQuality.LOW, 0.29f.toQuality())
    }

    @Test
    fun `quality percent rounds correctly`() {
        assertEquals(83, 0.825f.toQualityPercent())
    }
}
