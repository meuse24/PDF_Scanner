# Plan und Umsetzungsdokumentation: PDF → Word (.docx) Export

## Umsetzungsstand

**Status: umgesetzt und verifiziert.**

Der DOCX-Export ist als textbasierter Word-Export implementiert. Er nutzt gespeicherten OCR-Text
(`pageTexts` bevorzugt, sonst `extractedText`) und erzeugt ein minimales, editierbares `.docx`.
Bild-/Hybrid-Modi wurden bewusst nicht umgesetzt.

Aktueller Nutzerfluss:

- Einzelnes Dokument: Archiv/Home-Liste → 3-Punkt-Menü → **Export & Umwandeln** →
  **Als Word (.docx) exportieren**.
- Bulk: Dokumente auswählen → Mehr-Menü/Auswahlleiste → **Als Word (.docx) exportieren**.
- Wenn ein ausgewähltes Dokument noch keinen OCR-Text hat, erscheint ein Dialog
  **OCR für Word-Export erforderlich** mit OCR-Sprachauswahl.
- Nach erfolgreicher OCR wird der ursprünglich angeforderte Word-Export automatisch fortgesetzt.
  Bei DOCX-initiiertem OCR wird kein OCR-Review-Screen zwischengeschoben.
- Bei gemischter Auswahl wird OCR nur für die fehlenden Dokumente angeboten; danach wird die
  ursprüngliche Gesamtauswahl als DOCX exportiert.
- Wenn bereits OCR läuft, meldet der DOCX-OCR-Start lokalisiert, dass auf den laufenden OCR-Vorgang
  gewartet werden muss.

## Grundentscheidung (nach kritischer Prüfung)

**Es wird nur ein Text-Export gebaut.** Bild- und Hybrid-Modus sind gestrichen:

- **Bilder in DOCX sind nicht editierbar.** Ein DOCX voller Seitenbilder ist nur ein
  schwereres, schlechter portierbares PDF — kein Mehrwert, also kein Grund, das PDF zu verlassen.
- **Hybrid (Bild + Text)** ist in Word schwach: Word hat keine saubere „Text-hinter-Bild"-Ebene
  wie PDF; der Nutzer müsste Bilder erst löschen, um zu editieren. „Text wiederfindbar" deckt die
  App bereits über die **Searchable-PDF-Funktion** ab → Hybrid dupliziert das mit schlechterer UX.

→ Der Wert von „Export als Word" ist **editierbarer Text**. Genau das wird umgesetzt.

## Ausgangslage / vorhandene Bausteine

- **OCR-Text** im Domain-Modell `Document`: `extractedText` (Volltext) **und**
  `pageTexts: List<String>` (pro Seite, aus `ocr_page_text_json`). → Das ist die MVP-Quelle.
- **Text-Layer-PDFs (⚠️ noch nicht nutzbar):** `PdfTextOps.extractTextLines(file, pageIndex)`
  liefert **nur Geometrie** — `TextLine` (`domain/usecase/TextLine.kt`) hat keine `text`-Property,
  und `PdfEditor.extractTextLines` (`util/PdfEditor.kt:757`) **verwirft den String** aus
  `PDFTextStripper.writeString` und behält nur Positionen. Für die Text-Layer-Quelle ist daher
  **neue API nötig** (siehe Stufe 2): entweder `PdfTextOps.extractPageText(file, pageIndex): String`
  oder ein neuer Typ `PositionedTextLine(text, left, top, right, bottom)`.
- **Export-Senke:** `DownloadsStorage.writeDownload(displayName, mimeType, writer)`
  (MediaStore.Downloads, IS_PENDING, `DownloadEntry.delete()` für Rollback). MediaStore vergibt bei
  Namenskollision **selbst** einen Suffix (`name (1).docx`) — wir müssen Eindeutigkeit nicht erzwingen.
- **Vorbild-UseCases:** `ExportOcrTextUseCase` (Text→Downloads) und `ExportAsJpgUseCase`
  (Rollback-Muster). Der DOCX-Export ist im Kern eine **neue Senke für vorhandene Daten**.

## Das eigentliche Usability-Problem: Absatz-Rekonstruktion

OCR und PDFBox liefern beide **zeilenweisen** Text. Naiv „eine Zeile = ein Absatz" erzeugt ein
DOCX voller harter Umbrüche mitten im Satz → in Word kaum editierbar. Das ist der Punkt, der über
die Qualität des Features entscheidet. Lösung: eine **Zeilen-zu-Absatz-Heuristik**:

- Aufeinanderfolgende Zeilen zu einem Absatz **zusammenführen** (Leerzeichen statt Umbruch).
- **Sprachneutrale** Absatzgrenzen (primär, für alle 10 Locales gültig): Leerzeile;
  großer vertikaler Zeilenabstand (nur wenn positionierter Text vorliegt); deutlich kürzere
  Zeile als der Block-Median (= Zeilenumbruch-Ende); Satzende-Interpunktion `. ! ? 。 ！ ？ ؟`.
- **Nur als Zusatzsignal** (nicht allein): Großschreibung nach Satzende — funktioniert nicht für
  AR/ZH/JA/HI (keine Groß/Kleinschreibung), darf also keine Voraussetzung sein.
- Bindestrich-Trennung am Zeilenende zusammenfügen (latein-spezifisch, ohne CJK).

Diese Logik ist **pure Kotlin** und wird isoliert unit-getestet — inkl. CJK- und RTL-Fixtures.

## DOCX-Bibliothek: eigene minimale Erzeugung

**Bibliotheks-Recherche (Juni 2026) — Ergebnis: technisch möglich, aber kein guter Fit; Eigenbau gewinnt.**
(Formulierung bewusst nicht „unmöglich": die Libs *funktionieren*, sind für diese App nur zu schwer.)

| Kandidat | Befund | Fazit |
|---|---|---|
| Apache POI XWPF | dokumentiert Word-DOCX-Support, funktioniert; auf Android aber schwergewichtig (große Dep, Method-Count/Dex-Druck) | zu schwer |
| docx4j | aktiv gepflegt, JAXB-basiert; es existiert sogar ein Android-Sample (`Docx4j4Android4`), aber JAXB-auf-Android ist heikel (Repackaging, JAXP) und schwer | schlechter Fit |
| DocxKtm | Wrapper um docx4j, ausdrücklich **JVM-only**, kein Android-Support angegeben | ungeeignet |
| DocStencil | schlank (`kotlin-reflect`), aber **rein template-basiert** — kann nicht von Grund auf erzeugen; braucht fertige Word-Vorlage, bei variabler Seitenzahl umständlich; Android nicht bestätigt | schlechter Fit |

→ Unser Bedarf ist bewusst minimal (Absätze, optionale Überschriften, RTL/CJK-Steuerung).
Dafür ist eine handgeschriebene Erzeugung **leichter** als jede Lib, ohne neue Abhängigkeit und
Android-sicher.

**Eigener `DocxBuilder`** (ZIP + OpenXML WordprocessingML, `java.util.zip.ZipOutputStream`).
Die OOXML-Minimalstruktur muss **konsistent** sein — Content-Types und Relationships müssen zu den
tatsächlich enthaltenen Parts passen, sonst öffnet Word nicht:

```
[Content_Types].xml        ← Default: rels, xml; Override für /word/document.xml,
                              /word/styles.xml, /docProps/core.xml (+ app.xml)
_rels/.rels                ← Beziehung zu word/document.xml (officeDocument);
                              zu docProps/core.xml (core-properties)
word/document.xml          ← Absätze (w:p / w:r / w:t)
word/_rels/document.xml.rels ← Beziehung zu styles.xml (nur wenn styles.xml genutzt)
word/styles.xml            ← NUR falls echte Heading-Styles versprochen werden
docProps/core.xml          ← Titel/Datum (Override + .rels-Eintrag erforderlich)
```

- **Heading-Entscheidung (MVP):** *keine* benannten Word-Heading-Styles → kein `word/styles.xml`
  nötig. Seitenüberschrift als **fett/größerer Run** im Absatz rendern. Hält die Struktur minimal
  und öffnet überall sauber. Echte `Heading1`-Styles erst, wenn `styles.xml` mitgeliefert wird.
- **Kompatibilität testen:** reine ZIP/XML-Parse-Tests beweisen *nicht*, dass Word/LibreOffice
  öffnen. Zusätzlich manueller Öffnen-Check (Word + LibreOffice) als Abnahmekriterium.

## Architektur / konkrete Schritte

Schichtregel strikt: ViewModel → UseCase → Port; `domain/` framework-frei.

### 1. Pure Heuristik (`domain/common/`)
`ParagraphReconstruction.kt` — reine Funktion `linesToParagraphs(lines: List<String>): List<String>`
(bzw. Overload mit `List<TextLine>` für vertikale Abstände). Komplett framework-frei und testbar.

### 2. Domain-Port (`domain/gateway/`)
```kotlin
// domain/gateway/DocxBuilder.kt
interface DocxBuilder {
    fun writeDocx(doc: DocxDocument, out: OutputStream)
}
data class DocxDocument(
    val title: String?,            // → docProps/core.xml
    val pages: List<DocxPage>
)
data class DocxPage(
    val heading: String?,          // optionale Seitenüberschrift → fetter Run (kein Style-Part)
    val paragraphs: List<String>
)
```
Kein `Mode`, kein `ByteArray`, kein Bild — bewusst minimal.

### 3. Impl (`util/`)
`util/DocxBuilderImpl.kt` — `ZipOutputStream`-Writer der OpenXML-Teile.
- XML-Escaping (`& < > "`) plus XML-1.0-Sanitization: illegale Steuerzeichen werden vor dem
  Escaping entfernt; Nicht-BMP-Codepoints/Surrogates werden über `codePoints()` korrekt behandelt.
- **RTL:** bei arabischem Inhalt `w:bidi` (Absatz) + `w:rtl` (Run) setzen, sonst LTR-Fehldarstellung.
- CJK unkritisch (DOCX bettet keine Fonts ein; Word nutzt System-Fonts — anders als Searchable-PDF).

### 4. Use Case (`domain/usecase/`)
```kotlin
class ExportDocxUseCase @Inject constructor(
    private val downloadsStorage: DownloadsStorage,
    private val docxBuilder: DocxBuilder
    // pdfTextOps erst in Stufe 2, sobald extractPageText existiert
) {
    /** @throws NoExportableTextException wenn kein Dokument Text enthält */
    suspend operator fun invoke(documents: List<Document>): String
}
```
Textquelle pro Dokument, priorisiert:
1. `document.pageTexts` (seitenweise) → je Seite Heading + `linesToParagraphs`,
2. sonst `document.extractedText` (Volltext) → `linesToParagraphs`,
3. *(Stufe 2)* echte Textebene ohne OCR → neue `PdfTextOps.extractPageText(...)`-API
   (heute nicht vorhanden, siehe Ausgangslage).
- **Kein `reportError` im UseCase** (keine UI-Logik in der Domain). Stattdessen wirft der UseCase
  `NoExportableTextException`; das **ViewModel** mappt auf `R.string.docx_export_nothing_to_export`
  — exakt wie der OCR-Export, wo `HomeViewModel:662` die Leer-Prüfung trägt. Konkret: VM lädt die
  vollständigen Records (`getScansByIds`), prüft selbst auf Text und ruft den UseCase nur mit
  exportierbaren Dokumenten auf.
- Dateiname `"${filename}.docx"`. **Keine** `resolveUniqueFilename()`-Zusage — das prüft nur lokale
  `File`-Verzeichnisse mit Default-Extension `pdf` und greift bei MediaStore nicht; MediaStore
  dedupliziert selbst.
- MIME `application/vnd.openxmlformats-officedocument.wordprocessingml.document`.
- Rollback bei Fehler über `DownloadEntry.delete()` (wie `ExportAsJpgUseCase`).

### 5. DI (`di/`)
`DocxBuilder`-`@Binds` in `PdfOperationsModule` (oder `AppProvidersModule`);
`ExportDocxUseCase` per Constructor-Injection.

### 6. UI-Verdrahtung (minimal, kein Modus-Dialog)
Exakt am OCR-TXT-Pfad andocken:
- **Einzel:** `ScanAction.ExportDocx` in `DocumentEditSheet` → `HomeActionDispatcher`
  (`onExportDocx`) → `HomeViewModel.exportDocx(document)`.
- **Bulk:** Eintrag „Als Word (.docx)" im „Mehr"-Menü der `BulkActionBar`, neben PDF-/OCR-TXT-Export.
- Erfolg/Fehler über vorhandene `_success`/`_error`-StateFlows;
  `NoExportableTextException` → `_error = docx_export_nothing_to_export`.
- **Aktuelle Sichtbarkeit:** Die DOCX-Aktion bleibt im Home-/Archiv-Menü sichtbar. Wenn kein
  exportierbarer Text vorhanden ist, wird nicht still ausgeblendet, sondern direkt OCR angeboten.
  Im geöffneten PDF-Viewer ist DOCX weiterhin nicht verfügbar; der Export startet aus der
  Archiv/Home-Liste.
- **JPG-Gruppierung:** „Als JPG exportieren" wurde aus dem Abschnitt „Dokument" in
  **Export & Umwandeln** verschoben.
- **OCR-Fortsetzung:** `HomeViewModel` merkt sich bei DOCX-ohne-Text die ursprünglich gewünschten
  Export-IDs (`pendingDocxExportIds`). Nach erfolgreichem OCR ruft es den DOCX-Export automatisch
  erneut auf und konsumiert den Pending-State.

### 7. Strings (alle 10 Locales)
`values/`, `-de`, `-es`, `-fr`, `-pt`, `-zh-rCN`, `-ar`, `-ja`, `-ru`, `-hi`
(`strings_export.xml` o. ä.): `docx_export_action`, `docx_export_success`,
`docx_export_error`, `docx_export_nothing_to_export`, `docx_export_ocr_prompt_title`,
`docx_export_ocr_prompt_message`, `docx_export_start_ocr`, `docx_export_ocr_busy`.

Help und Info wurden aktualisiert:

- `HelpScreen`: DOCX-Export ist in den Hilfelisten enthalten.
- `InfoScreen`: Feature-Hinweis zum textbasierten Word-Export ist ergänzt.

### 8. Tests
- **JVM** `domain/common/ParagraphReconstructionTest`: Zeilen-Merge, sprachneutrale Absatzgrenzen,
  Bindestrich-Trennung — **mit CJK- und RTL/Arabisch-Fixtures** (keine Abhängigkeit von Groß/Klein).
- **JVM** `domain/usecase/ExportDocxUseCaseTest`: Fake `DocxBuilder` + Fake `DownloadsStorage`;
  Quellen-Priorität (pageTexts→extractedText), `NoExportableTextException` bei Leer, Rollback, Dateiname.
- **JVM** `util/DocxBuilderTest`: erzeugtes ZIP öffnen, `word/document.xml` parsen,
  Absätze/Überschriften, XML-Escaping, RTL-Properties, **Content-Types/Rels-Konsistenz**.
- **Manuell/Abnahme:** erzeugtes DOCX in Word **und** LibreOffice öffnen (ZIP/XML-Tests beweisen
  das nicht). Stichprobe je ein OCR-Dokument latein / CJK / arabisch.

Implementierte Testabdeckung:

- `ParagraphReconstructionTest`: Absatzrekonstruktion, CJK/RTL-Signale, Hyphenation.
- `ExportDocxUseCaseTest`: Quellenpriorität, leere Exporte, Dateiname, Builder/Storage-Verhalten.
- `DocxBuilderImplTest`: ZIP/XML-Struktur, Escaping, RTL, illegale XML-Zeichen.
- `DocumentMappersTest`: Listenprojektion `hasStoredOcrText` und Full-Record-Ableitung.
- `HomeViewModelTest`: DOCX-ohne-OCR-Prompt, automatische Fortsetzung nach OCR, gemischte Auswahl.

Verifikation:

- `./gradlew.bat --no-daemon :app:compileDebugKotlin`
- `./gradlew.bat --no-daemon testDebugUnitTest --tests "info.meuse24.pdf_scanner.ui.home.HomeViewModelTest"`
- Zusätzlich wurden vorher die fokussierten DOCX-/Mapper-/Builder-/Paragraph-Tests erfolgreich
  ausgeführt.

Offener manueller Abnahmepunkt:

- Ein erzeugtes DOCX einmal in Microsoft Word und LibreOffice öffnen, idealerweise mit lateinischem,
  CJK- und arabischem OCR-Text. Die Unit-Tests prüfen ZIP/XML-Korrektheit, ersetzen aber keinen
  echten Office-Öffnen-Check.

## Stufenplan
1. **MVP: erledigt.** `pageTexts`/`extractedText` → Absätze (mit Heuristik), Überschrift als
   fetter Run, ohne `styles.xml`. DOCX-Aktion sichtbar; fehlender OCR-Text führt zum OCR-Angebot
   und anschließendem automatischen Word-Export.
2. **Text-Layer-PDFs:** erst **neue API** (`PdfTextOps.extractPageText` bzw. `PositionedTextLine`)
   in `PdfEditor` bauen (heute verwirft `extractTextLines` den Text!), dann als Quelle nutzen
   (exakter Text + vertikale-Abstands-Heuristik); Sichtbarkeit auf `isSearchable` erweitern.
3. **Später, optional:** `styles.xml` + echte Heading-Styles, einfache Tabellen-/Überschrift-Heuristik.
   Kein Bild-/Hybrid-Modus.
