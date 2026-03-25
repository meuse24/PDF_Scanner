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
            if (keywords.any { kw -> text.contains(kw, ignoreCase = true) }) {
                found.add(tagKey)
            }
        }
        if (IBAN_REGEX.containsMatchIn(text)) found.add("bank")

        return if (found.isEmpty()) null else found.sorted().joinToString(",")
    }

    companion object {
        // IBAN: 2-letter country code + 2 digits + up to 30 alphanumeric chars (spaces allowed)
        private val IBAN_REGEX = Regex("""[A-Z]{2}\d{2}[\s]?(?:[A-Z0-9]{4}[\s]?){3,7}""")

        val TAG_KEYWORDS: Map<String, List<String>> = mapOf(
            "invoice" to listOf(
                "Rechnung", "Invoice", "Faktura", "MwSt", "VAT",
                "Rechnungsbetrag", "Zahlungsziel", "Nettobetrag", "Bruttobetrag",
                "Rechnungsnummer", "Rechnungsdatum"
            ),
            "contract" to listOf(
                "Vertrag", "Vereinbarung", "Contract", "Agreement",
                "Mietvertrag", "Arbeitsvertrag", "Kaufvertrag", "Dienstleistungsvertrag"
            ),
            "insurance" to listOf(
                "Versicherung", "Insurance", "Versicherungsschein",
                "Schadensfall", "Versicherungsbeitrag", "Police"
            ),
            "certificate" to listOf(
                "Zeugnis", "Certificate", "Diploma", "Diplom",
                "Bescheinigung", "Zertifikat", "Urkunde", "Abschlusszeugnis"
            ),
            "bank" to listOf(
                "Kontoauszug", "Bank statement", "Kontonummer", "BIC",
                "Sparkasse", "Volksbank", "Commerzbank", "Deutsche Bank", "Girokonto"
            ),
            "delivery" to listOf(
                "Lieferschein", "Delivery note", "Frachtbrief",
                "Sendungsnummer", "Tracking", "Wareneingang", "Lieferung"
            )
        )
    }
}
