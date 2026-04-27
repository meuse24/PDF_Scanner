# Umsetzungsvorschlag: Auto-Tags & OCR-Text-Export

## Feature 1: Automatische Dokument-Tags aus OCR reaktivieren

### Ausgangslage & Diagnose

`AutoTagUseCase` existiert und ist vollständig implementiert, wird aber nie aufgerufen.
In `MakeSearchableUseCase:42` steht `tags = null` — hartkodiert.

Das Keyword-Matching hatte früher Zuverlässigkeitsprobleme:
- Viele Begriffe wurden aus Angst vor False Positives entfernt (Kommentare belegen das: "Rechnung removed — too common", "Police removed — matches English texts")
- Nur Word-Start-Anker (`(?<!\p{L})kw`), kein Word-End-Anker → "Rechnungslegung" triggert kein "invoice", aber "Bruttobetrag123" schon
- Einzelner Keyword-Treffer reicht für Tag → sehr fehleranfällig bei kurzen Texten
- Kein Score-Schwellenwert: alles oder nichts
- Kein OCR-Text-Normalisierung vor dem Matching
- Keine Muster für strukturelle Signale (Geldbeträge, Datumsformate)

### Lösungsansatz: Scoring-basiertes Matching mit erweitertem Keyword-Set

Statt binäres Treffer/Kein-Treffer: Jeder Keyword-Treffer gibt Punkte, Tag wird nur gesetzt wenn Mindestpunktzahl erreicht. Hochwertige Signale (z. B. "Rechnungsnummer") geben mehr Punkte als allgemeine Begriffe ("Betrag").

---

### Schritt 1: `AutoTagUseCase.kt` überarbeiten

**Neue Datenstruktur:**

```kotlin
data class TagRule(
    val keyword: String,
    val score: Int   // 1–3: 3 = eindeutiges Signal, 1 = schwaches Indiz
)

// Minimale Score-Schwelle pro Tag, damit dieser gesetzt wird
const val TAG_THRESHOLD = 4
```

**Erweiterte Keyword-Liste pro Kategorie:**

```
"invoice":
  Score 3: "Rechnungsnummer", "Rechnungsdatum", "Rechnungsbetrag", "Nettobetrag",
           "Bruttobetrag", "Zahlungsziel", "Invoice No", "Invoice Date", "Faktura"
  Score 2: "Rechnung", "MwSt.-Betrag", "Mehrwertsteuer", "Umsatzsteuer", "USt.",
           "Fälligkeitsdatum", "Zahlbar bis", "Due date", "Total amount", "Subtotal",
           "Net amount", "Gross amount", "Steuernummer", "Tax number"
  Score 1: "Betrag", "Amount", "Summe", "EUR", "inkl. MwSt", "zzgl. MwSt"
  Regex-Bonus (+2): Geldbetragsformat €/EUR mit Dezimalzahl: `\d+[.,]\d{2}\s*(?:€|EUR)`

"contract":
  Score 3: "Mietvertrag", "Arbeitsvertrag", "Kaufvertrag", "Dienstleistungsvertrag",
           "Rahmenvertrag", "Werkvertrag", "Darlehensvertrag"
  Score 2: "Vertrag", "Contract", "Vereinbarung", "Agreement", "Auftragsbestätigung",
           "Leistungsvereinbarung", "Allgemeine Geschäftsbedingungen", "AGB",
           "Kündigung", "Laufzeit", "§", "Vertragspartner"
  Score 1: "Datum des Vertrags", "Vertragsschluss", "unterzeichnet"

"insurance":
  Score 3: "Versicherungsschein", "Versicherungsnummer", "Versicherungspolice",
           "Schadensfall", "Schadensnummer"
  Score 2: "Versicherungsbeitrag", "Versicherungsschutz", "Prämie", "Insurance",
           "Deckungssumme", "Selbstbehalt", "Leistungsfall", "Versicherungsnehmer"
  Score 1: "Versicherung", "versichert", "Policeninhaber"

"certificate":
  Score 3: "Zeugnis", "Certificate", "Diplom", "Diploma", "Abschlusszeugnis",
           "Hochschulzeugnis", "Zertifikat", "Urkunde"
  Score 2: "Bescheinigung", "Nachweis", "Teilnahmebescheinigung", "Ausbildungszeugnis",
           "Führungszeugnis", "Bestätigung"
  Score 1: "hiermit bestätigt", "hereby certify"

"bank":
  Score 3: "Kontoauszug", "IBAN", "Kontonummer", "Bank statement", "Girokonto"
  Score 2: "Sparkasse", "Volksbank", "Commerzbank", "Deutsche Bank", "ING-DiBa",
           "DKB", "Postbank", "Comdirect", "Buchungsdatum", "Wertstellung",
           "Haben", "Soll", "Saldo", "Kontostand", "BIC", "SWIFT"
  Score 1: "Lastschrift", "Überweisung", "Dauerauftrag"
  Regex-Bonus (+3): IBAN-Format (verbessert, siehe unten)

"delivery":
  Score 3: "Lieferschein", "Frachtbrief", "Lieferscheinnummer"
  Score 2: "Delivery note", "Sendungsnummer", "Trackingnummer", "Wareneingang",
           "Lieferadresse", "Empfänger", "Paketscheinnummer", "DHL", "UPS", "DPD",
           "GLS", "Hermes", "FedEx"
  Score 1: "Lieferung", "Versand", "Paket"
```

**Verbesserter IBAN-Regex:**
```kotlin
// Strikter: mindestens 15, maximal 34 Zeichen nach Länderkennzeichen
private val IBAN_REGEX = Regex(
    """(?<!\p{L})[A-Z]{2}\d{2}(?:\s?[A-Z0-9]{4}){3,7}(?!\p{L})"""
)
```

**Verbessertes Word-Boundary-Pattern:**
```kotlin
// Bisher nur Word-Start. Neu: auch Word-End absichern
private fun wordPattern(kw: String) =
    Regex("""(?<!\p{L})${Regex.escape(kw)}(?!\p{L})""", RegexOption.IGNORE_CASE)
```

**Text-Normalisierung:**
```kotlin
private fun normalizeText(raw: String): String =
    raw.replace(Regex("""\s+"""), " ")           // Whitespace normalisieren
       .replace(Regex("""(\w)-\n(\w)"""), "$1$2") // OCR-Zeilentrennung in Wörtern aufheben
       .trim()
```

**Neue `extractTags()`-Logik:**
```kotlin
fun extractTags(text: String): String? {
    if (text.isBlank()) return null
    val normalized = normalizeText(text)
    val found = mutableSetOf<String>()

    for ((tagKey, rules) in TAG_RULES) {
        var score = 0
        for (rule in rules) {
            if (wordPattern(rule.keyword).containsMatchIn(normalized)) {
                score += rule.score
            }
        }
        score += extraScore(tagKey, normalized)
        if (score >= TAG_THRESHOLD) found.add(tagKey)
    }

    return if (found.isEmpty()) null else found.sorted().joinToString(",")
}

private fun extraScore(tagKey: String, text: String): Int = when (tagKey) {
    "invoice" -> if (AMOUNT_REGEX.containsMatchIn(text)) 2 else 0
    "bank"    -> if (IBAN_REGEX.containsMatchIn(text)) 3 else 0
    else      -> 0
}
```

---

### Schritt 2: Integration in `MakeSearchableUseCase.kt`

```kotlin
class MakeSearchableUseCase @Inject constructor(
    private val searchablePdfBuilder: SearchablePdfBuilder,
    private val repository:           DocumentRepository,
    private val autoTagUseCase:       AutoTagUseCase          // NEU
) {
    ...
    repository.markSearchableWithContent(
        id         = record.id,
        fileSize   = pdfFile.length(),
        text       = searchableResult.extractedText.ifBlank { null },
        tags       = searchableResult.extractedText.ifBlank { null }
                         ?.let { autoTagUseCase.extractTags(it) },  // NEU
        confidence = searchableResult.stats?.confidence,
        language   = searchableResult.stats?.recognizedLanguage,
        pageTexts  = searchableResult.pageTexts
    )
```

---

### Schritt 3: Rückwirkende Tagging-Aktion für vorhandene Dokumente

Neues `RetroTagUseCase` (einfach):
```kotlin
class RetroTagUseCase @Inject constructor(
    private val repository:     DocumentRepository,
    private val autoTagUseCase: AutoTagUseCase
) {
    suspend operator fun invoke(): Int {
        val candidates = repository.getAllSearchableWithoutTags()
        candidates.forEach { doc ->
            val tags = doc.extractedText?.let { autoTagUseCase.extractTags(it) }
            if (tags != null) repository.updateTags(doc.id, tags)
        }
        return candidates.size
    }
}
```

Neues `ScanDao`-Query:
```sql
SELECT * FROM scan_records
WHERE extracted_text IS NOT NULL AND (tags IS NULL OR tags = '')
AND deleted_at IS NULL
```

---

### Schritt 4: Tag-Filter in der Home-Liste

**`ArchiveFilter` erweitern:**
```kotlin
sealed class ArchiveFilter {
    object AllDocuments : ArchiveFilter()
    object Favorites    : ArchiveFilter()
    data class Folder(val id: Long, val name: String) : ArchiveFilter()
    data class Tag(val key: String)                   : ArchiveFilter()  // NEU
}
```

**Filter-Chips in HomeScreen** (unterhalb der Suchleiste, horizontal scrollbar):
- „Alle" · „Rechnungen" · „Verträge" · „Versicherungen" · „Zertifikate" · „Bank" · „Lieferscheine"
- Nur Chips anzeigen, für die mindestens 1 Dokument existiert

**DAO-Query für Tag-Filter:**
```sql
SELECT * FROM scan_records
WHERE (tags LIKE '%' || :tagKey || '%')
  AND deleted_at IS NULL
ORDER BY timestamp DESC
```

---

### Schritt 5: Rückwirkende Tagging-Aktion in den Einstellungen

In den Einstellungen: Schaltfläche „Dokumente automatisch taggen" → ruft `RetroTagUseCase` auf → Toast mit Anzahl getaggter Dokumente.

---

### Tests

- `AutoTagUseCaseTest`: JVM-Unit-Test mit Texten aus echten Dokumenten (Rechnung, Kontoauszug, Zertifikat, Lieferschein) — mindestens 10 Testfälle pro Kategorie; auch Falsch-Positiv-Tests (z. B. generischer Brief ohne Rechnungsmerkmale → kein "invoice"-Tag)
- `MakeSearchableUseCaseTest`: Mock für `AutoTagUseCase`, prüft dass Tag nicht null ist wenn Text vorhanden
- Bestehenden `AutoTagUseCaseTest` erweitern

---

### Strings

Neue Strings in alle 10 Locales:
- `filter_tag_all`, `filter_tag_invoice`, `filter_tag_contract`, `filter_tag_insurance`, `filter_tag_certificate`, `filter_tag_bank`, `filter_tag_delivery`
- `settings_retro_tag_action`, `settings_retro_tag_done` (mit `%d`-Platzhalter)

---

### Aufwand-Schätzung Feature 1

| Schritt | Aufwand |
|---------|---------|
| AutoTagUseCase überarbeiten (Scoring + Keywords) | 2–3 h |
| MakeSearchableUseCase integrieren | 0,5 h |
| RetroTagUseCase + DAO-Query | 1 h |
| Tag-Filter UI + ArchiveFilter | 2–3 h |
| Settings-Schaltfläche | 0,5 h |
| Strings (10 Locales) | 1 h |
| Tests | 2 h |
| **Gesamt** | **~9–11 h** |

---

---

## Feature 2: OCR-Text als Datei exportieren (TXT)

### Ausgangslage

OCR-Text liegt in `ScanRecord.extractedText` (vollständig) und `ocrPageTextJson` (seitenweise) vor.
Die App kann Text kopieren und teilen (`OcrReviewScreen:228ff`), hat aber keinen Datei-Export.
`ExportScanUseCase` zeigt das MediaStore-Muster (`DownloadsStorage`), das direkt wiederverwendet werden kann.

---

### Schritt 1: `ExportOcrTextUseCase.kt`

```kotlin
class ExportOcrTextUseCase @Inject constructor(
    private val downloadsStorage: DownloadsStorage
) {
    suspend operator fun invoke(documents: List<Document>): String {
        require(documents.isNotEmpty())
        val text = buildExportText(documents)
        val filename = if (documents.size == 1)
            "${documents.first().filename}_ocr.txt"
        else
            "ocr_export_${System.currentTimeMillis()}.txt"

        val entry = downloadsStorage.writeDownload(
            displayName = filename,
            mimeType    = "text/plain"
        ) { output ->
            output.writer(Charsets.UTF_8).use { it.write(text) }
        }
        return entry.displayName
    }

    private fun buildExportText(documents: List<Document>): String = buildString {
        documents.forEachIndexed { idx, doc ->
            if (documents.size > 1) {
                appendLine("=== ${doc.filename} ===")
                appendLine()
            }
            val text = doc.extractedText
            if (text.isNullOrBlank()) {
                appendLine("[Kein OCR-Text vorhanden]")
            } else {
                append(text)
            }
            if (idx < documents.lastIndex) {
                appendLine()
                appendLine()
            }
        }
    }
}
```

---

### Schritt 2: Einstiegspunkt 1 — `OcrReviewScreen`

Neben den bestehenden Icons (Kopieren, Teilen) ein drittes Icon „Als Datei speichern" (Symbol: `Download` oder `SaveAlt`).

Im `OcrReviewViewModel`:
```kotlin
fun exportAsText() {
    viewModelScope.launch {
        _isExporting.value = true
        try {
            val filename = exportOcrTextUseCase(listOf(currentDocument))
            _success.value = context.getString(R.string.ocr_export_success, filename)
        } catch (e: Exception) {
            _error.value = context.getString(R.string.ocr_export_error)
        } finally {
            _isExporting.value = false
        }
    }
}
```

---

### Schritt 3: Einstiegspunkt 2 — `DocumentEditSheet`

Neue `ScanAction.ExportOcrText` in der Aktionsliste, sichtbar nur wenn `document.extractedText != null`.

---

### Schritt 4: Mehrfachauswahl — `BulkActionBar`

Neues Icon „Text exportieren" in der BulkActionBar (nach dem OCR-Button).
- Nur Dokumente mit `extractedText != null` werden exportiert
- Alle leer → `reportError(R.string.ocr_export_nothing_to_export)`

---

### Schritt 5: Strings

Neue Strings in alle 10 Locales:
- `ocr_export_as_file` (Button-Label)
- `ocr_export_success` (mit `%s` für Dateinamen)
- `ocr_export_error`
- `ocr_export_nothing_to_export`

---

### Tests

- `ExportOcrTextUseCaseTest`: Mock `DownloadsStorage`; prüft Dateinamen-Logik, Fallback-Text, Multi-Dokument-Trenner
- `OcrReviewViewModelTest`: prüft `_success` / `_error` Flow nach Export

---

### Aufwand-Schätzung Feature 2

| Schritt | Aufwand |
|---------|---------|
| ExportOcrTextUseCase | 1 h |
| OcrReviewScreen + ViewModel | 1,5 h |
| DocumentEditSheet (ScanAction) | 0,5 h |
| BulkActionBar | 1 h |
| Strings (10 Locales) | 0,5 h |
| Tests | 1 h |
| **Gesamt** | **~5,5 h** |

---

---

## Empfohlene Reihenfolge

1. **Feature 1, Schritt 1–2** (AutoTagUseCase + Integration): Kern der Funktionalität, sofort testbar
2. **Feature 2** (OCR-Text-Export): unabhängig, schnell umsetzbar, gutes sichtbares Ergebnis
3. **Feature 1, Schritt 3–5** (RetroTag + Filter-UI): Polishing, erhöht Nutzwert der Tags erheblich

Gesamtaufwand: **~15–17 Stunden** für beide Features vollständig.
