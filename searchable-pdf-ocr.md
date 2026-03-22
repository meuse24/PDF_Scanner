# Implementierungsplan: Durchsuchbare PDF-Scans (OCR) mit ML Kit & PdfBox-Android

## Ziel
Erstellung von "durchsuchbaren" PDFs: eine unsichtbare Textebene liegt über den gescannten Bildern.
Unterstützung aller 10 App-Sprachen (EN, AR, DE, ES, FR, HI, JA, PT, RU, ZH).

---

## 1. Abhängigkeiten

### Strategie: GMS (unbundled) für Nicht-Latin-Skripte
ML Kit bietet zwei Varianten jeder Texterkennungsbibliothek:
- **Gebündelt** (`com.google.mlkit:*`): Modell ist im APK → groß
- **GMS/unbundled** (`com.google.android.gms:play-services-mlkit-*`): Modell wird beim **ersten Einsatz automatisch via Google Play Services** heruntergeladen und gecacht → kein APK-Overhead

| Sprache(n) | Skript | Bibliothek | Strategie |
|---|---|---|---|
| EN, DE, ES, FR, PT | Latin | `com.google.mlkit:text-recognition` | gebündelt (bereits vorhanden) |
| ZH | CJK | `play-services-mlkit-text-recognition-chinese` | GMS unbundled |
| JA | CJK | `play-services-mlkit-text-recognition-japanese` | GMS unbundled |
| HI | Devanagari | `play-services-mlkit-text-recognition-devanagari` | GMS unbundled |
| RU | Cyrillic | Latin-Fallback (kein dediziertes ML Kit Cyrillic-Modell) | gebündelt |
| AR | Arabic | Latin-Fallback (kein dediziertes ML Kit Arabic-Modell) | gebündelt |

**APK-Größeneffekt:** Nur `pdfbox-android` (~5 MB) und das schon vorhandene Latin-Modell erhöhen die APK. Die drei GMS-Modelle (ZH ~18 MB, JA ~15 MB, HI ~8 MB) werden **nicht im APK** beigelegt.

**In `gradle/libs.versions.toml`:**
```toml
[versions]
pdfboxAndroid = "2.0.27.0"
mlkitTextChinese  = "16.0.0"
mlkitTextJapanese = "16.0.0"
mlkitTextDevanagari = "16.0.0"

[libraries]
pdfbox-android          = { group = "com.tom-roush", name = "pdfbox-android",                                     version.ref = "pdfboxAndroid" }
mlkit-text-chinese      = { group = "com.google.android.gms", name = "play-services-mlkit-text-recognition-chinese",    version.ref = "mlkitTextChinese" }
mlkit-text-japanese     = { group = "com.google.android.gms", name = "play-services-mlkit-text-recognition-japanese",   version.ref = "mlkitTextJapanese" }
mlkit-text-devanagari   = { group = "com.google.android.gms", name = "play-services-mlkit-text-recognition-devanagari", version.ref = "mlkitTextDevanagari" }
```

---

## 2. Vollbild-Seitenbilder für OCR

### Problem
Der aktuelle OCR-Code (`extractText`) verwendet `thumbnailPath` – ein **niedrig aufgelöstes Vorschaubild der ersten Seite**. Für eine qualitativ hochwertige Textebene über alle Seiten wird ein anderer Ansatz benötigt.

### Lösung: `android.graphics.pdf.PdfRenderer`
Android stellt `PdfRenderer` (API 21+) bereit, um beliebige PDF-Seiten als Bitmap zu rendern.

**Workflow (3 Phasen, kein Qualitätsverlust durch doppelte JPEG-Kompression):**

```
Phase 1 – Rendering (PdfRenderer, danach sofort schließen):
  → Für jede Seite: Bitmap bei ~150 DPI = PageWidthPts × (150/72) Pixel
  → PdfRenderer.Page.getWidth()/getHeight() liefert Maße in PDF-Points (1 pt = 1/72″)

Phase 2 – OCR (ML Kit):
  → Für jedes Bitmap: recognizer.process(InputImage.fromBitmap(...))
  → Ergebnis: TextBlock-Liste mit boundingBox in Pixel-Koordinaten

Phase 3 – Textebene hinzufügen (PdfBox, AppendMode):
  → PDDocument.load(originalPdfFile)  ← Original-Bilder bleiben erhalten, kein Re-Encoding
  → Für jede Seite: PDPageContentStream(doc, page, AppendMode.APPEND, compress=true)
  → Unsichtbaren Text über jede Seite legen
  → document.save(tempFile) → Original ersetzen
```

**Koordinatenumrechnung (Pixel → PDF-Points):**
```
scaleX = pageMediaBox.width  / bitmapWidth
scaleY = pageMediaBox.height / bitmapHeight

pdfX = bbox.left   × scaleX
pdfY = pageHeight  − bbox.bottom × scaleY   ← Y-Achse flippen (PDF: Ursprung unten links)
```

**Render-Auflösung:** 150 DPI ist ein guter Kompromiss zwischen OCR-Qualität und Speicherverbrauch.
Bei einem A4-Dokument: ~1240 × 1754 Pixel pro Seite (~8 MB Bitmap im Speicher).

---

## 3. TextRecognizer-Auswahl (`OcrManager`)

```kotlin
@Singleton
class OcrManager @Inject constructor() {
    fun getRecognizer(languageCode: String): TextRecognizer = when (languageCode) {
        "zh" -> TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
        "ja" -> TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build())
        "hi" -> TextRecognition.getClient(DevanagariTextRecognizerOptions.Builder().build())
        else -> TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) // Latin (EN/DE/ES/FR/PT) + RU/AR Fallback
    }
}
```

**Erste Nutzung ohne Internet:** Die GMS-Modelle liefern einen Fehler, der als `_error` StateFlow
weitergeleitet wird. Die PDF-Datei bleibt unverändert.

---

## 4. RTL-Textplatzierung (Arabisch)

### Problem
- PDF-Koordinatensystem hat seinen Ursprung **unten links**, Y wächst aufwärts.
- Arabischer Text läuft von **rechts nach links**; die boundingBox von ML Kit umschließt den gesamten Block.
- `PDPageContentStream.showText()` rendert Text standardmäßig **links nach rechts**.

### Lösung für den unsichtbaren Textlayer
Für **unsichtbare** Text-Overlays (Suchanfragen in PDF-Viewern) ist die exakte Zeichenpositionierung
weniger kritisch als die **Anwesenheit der Unicode-Bytes** im Content-Stream.

**Implementierung:**
```
Für RTL-Skripte (AR): X-Position = rechter Rand der BoundingBox − Textbreite
  pdfX = bbox.right × scaleX − estimatedTextWidth   // Annäherung: rechter Rand
Alternativ vereinfacht: pdfX = bbox.left × scaleX   // reicht für die meisten Viewer
```

**Bekannte Einschränkung:** Arabisches Text-Overlay ist für **Volltextsuche** ausreichend,
aber die Zeichenreihenfolge im Content-Stream kann in einigen Viewern bei Text-Auswahl
von rechts nach links gespiegelt erscheinen. Für v1 wird diese Einschränkung dokumentiert.

---

## 5. Schriftart für den Textlayer

| Skript | Strategie |
|---|---|
| Latin / Cyrillic (EN,DE,ES,FR,PT,RU) | Systemfont `/system/fonts/Roboto-Regular.ttf` via `PDType0Font.load()` |
| CJK, Devanagari, Arabic | Systemfont-Pfad versuchen (NotoSansCJK, NotoSansDevanagari, NotoSansArabic), Fallback: Roboto |
| Absoluter Fallback | `PDType1Font.HELVETICA` (Latin, kein Embedding nötig) |

Das Font-Embedding via `PDType0Font` erzeugt eine **ToUnicode-CMap** im PDF, die es
PDF-Viewern (Chrome, Acrobat, etc.) erlaubt, Unicode-Text zu indexieren und zu suchen.

---

## 6. Workflow-Integration

### Neuer Scan (SaveDialog)
```
[OutlinedTextField: Dateiname]
[Switch: Durchsuchbares PDF ●──]
[Caption: Sprachmodell wird bei Bedarf heruntergeladen]
```
→ `viewModel.saveScan(pdfUri, pageCount, filename, thumbnailUri, makeSearchable=true)`
→ Nach Speichern: `searchablePdfBuilder.makeSearchable(savedFile, languageCode, onProgress)`

### Bestehender Scan (BulkActionBar)
Neuer 5. Button: `Icons.Default.ManageSearch` → `viewModel.makeSearchable(selectedRecords)`

### Fortschrittsanzeige
`_ocrProgress: MutableStateFlow<Pair<Int,Int>?>` — der bestehende Loading-Dialog
zeigt bei `ocrProgress != null` den Text "Seite X von Y".

---

## 7. Datenbank

`ScanRecord`: neues Feld `@ColumnInfo(name="is_searchable", defaultValue="0") val isSearchable: Boolean = false`

```sql
-- MIGRATION_2_3
ALTER TABLE scan_records ADD COLUMN is_searchable INTEGER NOT NULL DEFAULT 0
```

UI: ScanItem zeigt ein kleines Badge/Label "Durchsuchbar", wenn `isSearchable == true`.

---

## 8. Neue Strings (10 Locales)

| Key | EN |
|---|---|
| `dialog_searchable_pdf` | Searchable PDF |
| `dialog_searchable_hint` | Text layer added via OCR (model downloaded as needed) |
| `cd_make_searchable` | Make searchable |
| `searchable_progress` | Page %1$d of %2$d |
| `searchable_success` | PDF is now searchable: %1$s |
| `searchable_failed` | Failed to create searchable PDF |
| `searchable_badge` | Searchable |

---

## 9. Bekannte Einschränkungen (v1)

1. **RU / AR:** Kein dediziertes ML Kit-Modell → Latin-Fallback, OCR-Qualität eingeschränkt.
2. **Arabisch RTL:** Text-Overlay für Suche ausreichend; Textauswahl in Viewern ggf. gespiegelt.
3. **Seitenrotation:** Pages mit Rotation ≠ 0° werden nicht rotationskorrigiert (seltener Sonderfall).
4. **CJK-Font:** Wird nur korrekt gemappt wenn NotoSansCJK auf dem Gerät vorhanden ist (Android-Standard).
5. **GMS offline:** Erstes Erstellen eines durchsuchbaren ZH/JA/HI-PDFs benötigt Internet.
