package info.meuse24.pdf_scanner.domain.usecase

import info.meuse24.pdf_scanner.domain.common.DetectedEntities
import info.meuse24.pdf_scanner.domain.common.detectNormalizedDocumentEntities
import info.meuse24.pdf_scanner.domain.common.normalizeOcrText
import javax.inject.Inject

/**
 * Extracts document tags from OCR text using keyword matching and IBAN regex.
 * Returns a comma-separated sorted string of tag keys, or null if no tags found.
 * All processing is on-device — no data is transmitted.
 */
class AutoTagUseCase @Inject constructor() {

    fun extractTags(text: String): String? {
        if (text.isBlank()) return null
        val normalized = normalizeOcrText(text)
        val detectedEntities = detectNormalizedDocumentEntities(normalized)
        val found = mutableSetOf<String>()

        for ((tagKey, rules) in TAG_RULES) {
            val score = rules.sumOf { rule ->
                if (WORD_PATTERNS.getValue(rule.keyword).containsMatchIn(normalized)) rule.score else 0
            } + extraScore(tagKey, detectedEntities)
            if (score >= TAG_THRESHOLD) {
                found.add(tagKey)
            }
        }

        return if (found.isEmpty()) null else found.sorted().joinToString(",")
    }

    companion object {
        private const val TAG_THRESHOLD = 4

        private data class TagRule(
            val keyword: String,
            val score: Int
        )

        // Match full keyword boundaries so "Berechnung" and similar OCR fragments do not trigger tags.
        // \p{L} covers all Unicode letters including German umlauts.
        private fun extraScore(tagKey: String, entities: DetectedEntities): Int = when (tagKey) {
            "invoice" -> if (entities.amounts.isNotEmpty()) 2 else 0
            "bank" -> if (entities.ibans.isNotEmpty()) 3 else 0
            else -> 0
        }

        private fun r(score: Int, vararg keywords: String): List<TagRule> =
            keywords.map { TagRule(it, score) }

        private val TAG_RULES: Map<String, List<TagRule>> = mapOf(
            "invoice" to listOf(
                *r(3, "Rechnungsnummer", "Rechnungsdatum", "Rechnungsbetrag", "Nettobetrag",
                    "Bruttobetrag", "Zahlungsziel", "Invoice No", "Invoice Date", "Faktura").toTypedArray(),
                *r(2, "Rechnung", "MwSt.-Betrag", "Mehrwertsteuer", "Umsatzsteuer", "USt.",
                    "Fälligkeitsdatum", "Zahlbar bis", "Due date", "Total amount", "Subtotal",
                    "Net amount", "Gross amount", "Steuernummer", "Tax number").toTypedArray(),
                *r(1, "Betrag", "Amount", "Summe", "EUR", "inkl. MwSt", "zzgl. MwSt").toTypedArray()
            ),
            "contract" to listOf(
                *r(3, "Mietvertrag", "Arbeitsvertrag", "Kaufvertrag", "Dienstleistungsvertrag",
                    "Rahmenvertrag", "Werkvertrag", "Darlehensvertrag").toTypedArray(),
                *r(2, "Vertrag", "Contract", "Vereinbarung", "Agreement", "Auftragsbestätigung",
                    "Leistungsvereinbarung", "Allgemeine Geschäftsbedingungen", "AGB", "Kündigung",
                    "Laufzeit", "Vertragspartner").toTypedArray(),
                *r(1, "§", "Datum des Vertrags", "Vertragsschluss", "unterzeichnet").toTypedArray()
            ),
            "insurance" to listOf(
                *r(3, "Versicherungsschein", "Versicherungsnummer", "Versicherungspolice",
                    "Schadensfall", "Schadensnummer").toTypedArray(),
                *r(2, "Versicherungsbeitrag", "Versicherungsschutz", "Prämie", "Insurance",
                    "Deckungssumme", "Selbstbehalt", "Leistungsfall", "Versicherungsnehmer").toTypedArray(),
                *r(1, "Versicherung", "versichert", "Policeninhaber").toTypedArray()
            ),
            "certificate" to listOf(
                *r(3, "Zeugnis", "Certificate", "Diplom", "Diploma", "Abschlusszeugnis",
                    "Hochschulzeugnis", "Zertifikat", "Urkunde").toTypedArray(),
                *r(2, "Bescheinigung", "Nachweis", "Teilnahmebescheinigung", "Ausbildungszeugnis",
                    "Führungszeugnis", "Bestätigung").toTypedArray(),
                *r(1, "hiermit bestätigt", "hereby certify").toTypedArray()
            ),
            "bank" to listOf(
                *r(3, "Kontoauszug", "IBAN", "Kontonummer", "Bank statement", "Girokonto").toTypedArray(),
                *r(2, "Sparkasse", "Volksbank", "Commerzbank", "Deutsche Bank", "ING-DiBa",
                    "DKB", "Postbank", "Comdirect", "Buchungsdatum", "Wertstellung",
                    "Haben", "Soll", "Saldo", "Kontostand", "BIC", "SWIFT").toTypedArray(),
                *r(1, "Lastschrift", "Überweisung", "Dauerauftrag").toTypedArray()
            ),
            "delivery" to listOf(
                *r(3, "Lieferschein", "Frachtbrief", "Lieferscheinnummer").toTypedArray(),
                *r(2, "Delivery note", "Sendungsnummer", "Trackingnummer", "Wareneingang",
                    "Lieferadresse", "Empfänger", "Paketscheinnummer", "DHL", "UPS", "DPD",
                    "GLS", "Hermes", "FedEx").toTypedArray(),
                *r(1, "Lieferung", "Versand", "Paket").toTypedArray()
            )
        )

        private val WORD_PATTERNS: Map<String, Regex> = TAG_RULES.values
            .flatten()
            .map { it.keyword }
            .distinct()
            .associateWith { kw ->
                Regex("""(?<!\p{L})${Regex.escape(kw)}(?!\p{L})""", RegexOption.IGNORE_CASE)
            }
    }
}
