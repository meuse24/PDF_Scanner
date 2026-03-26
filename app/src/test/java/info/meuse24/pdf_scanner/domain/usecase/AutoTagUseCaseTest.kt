package info.meuse24.pdf_scanner.domain.usecase

import org.junit.Assert.*
import org.junit.Test

class AutoTagUseCaseTest {

    private val useCase = AutoTagUseCase()

    @Test
    fun `empty text returns null`() {
        assertNull(useCase.extractTags(""))
        assertNull(useCase.extractTags("   "))
    }

    @Test
    fun `invoice keyword detected in German`() {
        val tags = useCase.extractTags("Rechnungsnummer: 2024-001, Zahlungsziel: 14 Tage, Nettobetrag: 100 EUR")
        assertNotNull(tags)
        assertTrue(tags!!.split(",").contains("invoice"))
    }

    @Test
    fun `contract keyword detected in English`() {
        val tags = useCase.extractTags("This Agreement is entered into between the parties.")
        assertNotNull(tags)
        assertTrue(tags!!.split(",").contains("contract"))
    }

    @Test
    fun `IBAN detected as bank indicator`() {
        val tags = useCase.extractTags("Bitte überweisen auf IBAN DE89 3704 0044 0532 0130 00")
        assertNotNull(tags)
        assertTrue(tags!!.split(",").contains("bank"))
    }

    @Test
    fun `multiple tags detected`() {
        val tags = useCase.extractTags("Rechnungsnummer 001, Versicherungsbeitrag fällig, IBAN DE89 3704 0044 0532 0130 00")
        assertNotNull(tags)
        val tagList = tags!!.split(",")
        assertTrue(tagList.contains("invoice"))
        assertTrue(tagList.contains("insurance"))
        assertTrue(tagList.contains("bank"))
    }

    @Test
    fun `irrelevant text returns null`() {
        assertNull(useCase.extractTags("Der Hund läuft durch den Park bei schönem Wetter."))
    }

    @Test
    fun `generic words no longer trigger false positives`() {
        // "Rechnung" alone (e.g. in a novel) must not tag as invoice
        assertNull(useCase.extractTags("Er bezahlte die Rechnung und verließ das Restaurant."))
        // "Berechnung" (calculation) must not tag as invoice
        assertNull(useCase.extractTags("Die Berechnung des Ergebnisses ergab 42."))
        // "Police" (English police) must not tag as insurance
        assertNull(useCase.extractTags("The police arrived at the scene quickly."))
    }

    @Test
    fun `tags are comma separated and sorted`() {
        val tags = useCase.extractTags("Mietvertrag, Versicherungsschein, Rechnungsnummer 42")
        assertNotNull(tags)
        val tagList = tags!!.split(",")
        assertEquals(tagList.sorted(), tagList)
    }
}
