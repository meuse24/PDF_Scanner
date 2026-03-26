package info.meuse24.pdf_scanner.domain.usecase

import javax.inject.Inject

/**
 * Extracts document tags from OCR text using keyword matching and IBAN regex.
 * Returns a comma-separated sorted string of tag keys, or null if no tags found.
 * All processing is on-device — no data is transmitted.
 */
class AutoTagUseCase @Inject constructor() {

    fun extractTags(text: String): String? {
        if (text.isBlank()) return null
        val found = mutableSetOf<String>()

        for ((tagKey, keywords) in TAG_KEYWORDS) {
            if (keywords.any { kw -> wordStartPattern(kw).containsMatchIn(text) }) {
                found.add(tagKey)
            }
        }
        if (IBAN_REGEX.containsMatchIn(text)) found.add("bank")

        return if (found.isEmpty()) null else found.sorted().joinToString(",")
    }

    companion object {
        // IBAN: 2-letter country code + 2 digits + up to 30 alphanumeric chars (spaces allowed)
        private val IBAN_REGEX = Regex("""[A-Z]{2}\d{2}[\s]?(?:[A-Z0-9]{4}[\s]?){3,7}""")

        // Matches keyword only at the start of a word (not inside a longer word like "Berechnung").
        // \p{L} covers all Unicode letters including German umlauts.
        private fun wordStartPattern(kw: String) =
            Regex("""(?<!\p{L})${Regex.escape(kw)}""", RegexOption.IGNORE_CASE)

        val TAG_KEYWORDS: Map<String, List<String>> = mapOf(
            // Generic "Rechnung"/"MwSt"/"VAT" removed — too common in any financial text.
            "invoice" to listOf(
                "Invoice", "Faktura",
                "Rechnungsnummer", "Rechnungsdatum", "Rechnungsbetrag",
                "Nettobetrag", "Bruttobetrag", "Zahlungsziel"
            ),
            // Generic "Vertrag"/"Contract" removed — "vertragen" etc. cause false positives.
            "contract" to listOf(
                "Mietvertrag", "Arbeitsvertrag", "Kaufvertrag", "Dienstleistungsvertrag",
                "Vereinbarung", "Agreement"
            ),
            // "Police" removed — matches English texts about the police.
            "insurance" to listOf(
                "Versicherungsschein", "Versicherungsbeitrag", "Schadensfall",
                "Insurance", "Versicherung"
            ),
            "certificate" to listOf(
                "Zeugnis", "Certificate", "Diplom", "Diploma",
                "Bescheinigung", "Zertifikat", "Urkunde", "Abschlusszeugnis"
            ),
            // "BIC" (too short) and "Tracking" removed.
            "bank" to listOf(
                "Kontoauszug", "Bank statement", "Kontonummer",
                "Sparkasse", "Volksbank", "Commerzbank", "Deutsche Bank", "Girokonto"
            ),
            // "Tracking" and generic "Lieferung" removed.
            "delivery" to listOf(
                "Lieferschein", "Delivery note", "Frachtbrief",
                "Sendungsnummer", "Wareneingang"
            )
        )
    }
}
