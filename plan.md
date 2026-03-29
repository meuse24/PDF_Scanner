# Plan: Rechtssichere PDF-Schwärzung

## Fortschritt 2026-03-29
Bereits umgesetzt:
- `PdfEditor.applySecureRedaction(...)` als sicherer Kernpfad.
- Betroffene Seiten werden gerendert, Schwärzungsrechtecke ins Bitmap eingebrannt und als neue bildbasierte PDF-Seiten gespeichert.
- Nicht betroffene Seiten bleiben unverändert.
- Renderqualität im sicheren Redaction-Pfad auf 300 DPI angehoben.
- `PdfRenderer.Page.RENDER_MODE_FOR_PRINT` für den sicheren Redaction-Pfad aktiviert.
- Eigener Domänentyp `RedactionRect` statt semantischer Wiederverwendung von `HighlightRect`.
- Rotationsfall im sicheren Redaction-Pfad korrigiert:
  - betroffene Seiten werden in Display-Orientierung neu aufgebaut
  - Ergebnis-Seite wird mit `rotation = 0` und bereits gedrehter Seitengrösse gespeichert
- Neue Workflow-/UseCase-Schicht:
  - `RedactPdfUseCase`
  - `RedactPdfWorkflow`
- Ergebnisdatensatz wird bewusst bereinigt gespeichert:
  - `isSearchable = false`
  - `extractedText = null`
  - `tags = null`
- Tests ergänzt:
  - JVM-Workflow-Test für Erfolgs-/Fehlerpfade
  - Instrumentation-Test auf Gerät: extrahierbarer Text verschwindet, schwarzes Rechteck ist sichtbar
  - Instrumentation-Test auf Gerät: unbeeinflusste Seite eines mehrseitigen Dokuments bleibt extrahierbar
  - Instrumentation-Test auf Gerät: Byte-Scan mit bewusst roh erzeugtem ASCII-PDF als Regression-Guard
  - Instrumentation-Test auf Gerät: 90°-Seite bleibt nach sicherer Schwärzung korrekt im Querformat und unverzerrt

Verifiziert:
- `:app:compileDebugKotlin`
- `testDebugUnitTest --tests "info.meuse24.pdf_scanner.domain.workflow.RedactPdfWorkflowTest"`
- `connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=info.meuse24.pdf_scanner.util.ImportAndPdfEditorInstrumentedTest#secureRedactionRemovesExtractableTextAndBurnsBlackRect`
- `connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=info.meuse24.pdf_scanner.util.ImportAndPdfEditorInstrumentedTest#secureRedactionKeepsTextOnUntouchedPagesAndRemovesSecretBytes`
- `connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=info.meuse24.pdf_scanner.util.ImportAndPdfEditorInstrumentedTest#secureRedactionOnRotatedPageKeepsLandscapeDisplayAndBurnsExpectedArea`

Noch offen:
- UI-Integration als eigener Schwärzungsmodus/-screen
- klare Produktentscheidung und UI für "Kopie" vs "Original ersetzen"
- optionale OCR-Rekonstruktion ausserhalb der Schwärzungsbereiche
- weitergehende Sanitization für Formular-/Link-/Attachment-Fälle
- zusätzliche Regressionstests für CropBox

## 1. Zielbild
Implementierung einer Schwärzungsfunktion, bei der der Inhalt unter dem Schwärzungsbereich nicht nur verdeckt, sondern aus allen relevanten Datenpfaden entfernt wird.

Rechtssicher bedeutet in diesem Projekt:
- Der geschwärzte Inhalt ist im Ergebnis-PDF nicht mehr per Copy & Paste, Suche oder `PDFTextStripper` extrahierbar.
- Der geschwärzte Inhalt bleibt nicht in OCR-Textlayern, Formularfeldern, Annotationen, Metadaten oder lokalen App-Indizes erhalten.
- Es wird keine inkrementelle PDF-Aktualisierung verwendet, bei der alte Revisionen im Dateikörper erhalten bleiben könnten.
- Der Nutzer versteht, ob eine ungeschwärzte Originaldatei zusätzlich erhalten bleibt oder ersetzt wird.

Wichtige Abgrenzung:
- Für beliebige PDFs sind "garantierte Entfernung" und "100 % Erhalt der ursprünglichen Vektor-/Textstruktur" gleichzeitig kaum zuverlässig erreichbar.
- Für einen rechtssicheren MVP muss Sicherheit vor perfekter interner PDF-Strukturtreue stehen.

## 2. Projektkontext im bestehenden Code
Vorhandene Bausteine, die wiederverwendet werden sollten:
- `AnnotateScreen` / `HighlightRect`: bestehende Rechteck-Selektion mit normalisierten Koordinaten.
- `PdfEditor.removeTextLayer()`: bereits vorhandener, sicherer Render-und-Neuaufbau-Pfad für PDFs.
- `PdfEditor.renderPageThumbnail()` und `PdfRenderer`: bestehender Rendering-Stack.
- `SearchablePdfBuilder`: vorhandene OCR-/Textlayer-Logik für optionale Wiederherstellung der Suchbarkeit ausserhalb geschwärzter Bereiche.
- `PdfEditor`-Schreibpfade mit Temp-Datei + atomarem Move: gute Basis, weil keine inkrementellen Saves verwendet werden.
- `ScanRecord.extractedText`: muss zwingend mitbetrachtet werden, sonst bleibt der entfernte Inhalt lokal in Room/FTS suchbar.

## 3. Architekturentscheidung
Empfohlene Standardstrategie: sichere Seiten-Neuerzeugung für betroffene Seiten.

Empfohlenes Verhalten:
1. Nur Seiten mit Schwärzungen werden neu aufgebaut.
2. Diese Seiten werden mit `PdfRenderer` in hoher Auflösung gerendert.
3. Die Schwärzungsrechtecke werden direkt in das Bitmap eingebrannt.
4. Aus dem Bitmap wird eine neue PDF-Seite mit identischer Seitengrösse erzeugt.
5. Nicht betroffene Seiten werden unverändert importiert.
6. Das Ergebnis wird als vollständige neue PDF-Datei geschrieben.

Warum dieser Ansatz:
- Er entfernt sichtbaren Inhalt unter der Schwärzung zuverlässig, auch bei Bildern, Vektoren, Text, Mischlayouts und OCR-Textlayern.
- Er ist mit `pdfbox-android` realistisch umsetzbar.
- Er ist deutlich robuster als das selektive Löschen einzelner `Tj`/`TJ`-Operatoren.

Konsequenz:
- Betroffene Seiten verlieren in der sicheren Standardvariante ihre ursprüngliche interne Text-/Vektorstruktur.
- Die visuelle Formatierung bleibt weitgehend erhalten, wenn die Seite hochauflösend und verlustfrei neu aufgebaut wird.

## 4. Warum direkte Content-Stream-Manipulation als Hauptweg riskant ist
Der bisherige Plan ist an dieser Stelle zu optimistisch.

Problemfelder:
- Ein `Tj`- oder `TJ`-Operator entspricht nicht zuverlässig einem einzelnen Wort oder einer klaren Box.
- Kerning, Ligaturen, Transformationen und Textmatrizen machen Glyphenpositionen schwer eindeutig manipulierbar.
- Derselbe Inhalt kann in Form-XObjects, verschachtelten Streams oder mehrfach referenzierten Ressourcen liegen.
- Teilweise überdeckte Bilder oder Vektorpfade lassen sich nicht sauber "halb löschen", ohne neu zu rendern oder Clipping-/Rewriting-Logik zu bauen.
- Unsichtbarer Text kann separat vom sichtbaren Inhalt existieren, z. B. OCR-Textlayer.
- Selbst wenn Operatoren entfernt werden, können lokale App-Daten wie `extractedText`, Thumbnail-Dateien oder ungeschwärzte Originalkopien weiterhin Daten preisgeben.

Fazit:
- Selektive Stream-Manipulation eignet sich höchstens als spätere Optimierung für eng begrenzte PDF-Typen.
- Sie sollte nicht die Grundlage eines rechtssicheren MVP sein.

## 5. MVP nach Best Practice

### Phase 1: UX und Datenmodell
- Einführung eines eigenen `RedactionRect` oder Wiederverwendung von `HighlightRect` mit eigener Semantik.
- Wiederverwendung der bestehenden Rechteck-Selektion aus der Annotate-/Highlight-Logik.
- Speicherung pro Seite in normalisierten Anzeige-Koordinaten, wie im aktuellen Editor.
- Zusammenführen überlappender Rechtecke pro Seite vor der Verarbeitung.
- Klare Nutzerentscheidung:
  - "Als neue geschwärzte Kopie speichern"
  - optional später: "Original ersetzen"
- Deutlicher Warnhinweis: Eine neue Kopie lässt das Original ungeschwärzt im Archiv bestehen.
- Optionaler Bestätigungsdialog vor endgültiger Anwendung.

### Phase 2: Sichere Redaction-Engine
Neue Kernmethode in `PdfEditor`, z. B. `applySecureRedaction(...)`.

Empfohlener Ablauf pro Dokument:
1. Neues Zieldokument erzeugen, niemals inkrementell speichern.
2. Für jede Seite prüfen, ob Schwärzungsrechtecke vorliegen.
3. Wenn nein: Seite mit `importPage(...)` unverändert übernehmen.
4. Wenn ja:
   - Seite mit `PdfRenderer` in hoher Auflösung rendern.
   - Bitmap-Hintergrund weiss lassen oder aus Original übernehmen.
   - Schwärzungsrechtecke direkt in das Bitmap zeichnen.
   - Neue `PDPage` mit identischer `MediaBox` erzeugen.
   - Nach Möglichkeit auch `CropBox`/Rotation/Seitengrösse übernehmen.
   - Bitmap verlustfrei mit `LosslessFactory` einbetten.
5. Zieldokument speichern, Thumbnail neu generieren, Datenbank aktualisieren.

Empfohlene Qualitätsparameter:
- Standard 300 DPI für normale Dokumente.
- 450 DPI für kleine Schriften, Tabellen, Stempel, feine Linien.
- Verlustfrei statt JPEG für rechtssichere Ausgabe.
- Immer nur eine Seite gleichzeitig rendern, um RAM stabil zu halten.

### Phase 3: Suchbarkeit ausserhalb der Schwärzung
Hier ist eine bewusste Produktentscheidung nötig.

Variante A, empfohlen für MVP:
- Betroffene Seiten werden bildbasiert und auf diesen Seiten nicht mehr durchsuchbar.
- `isSearchable` für das Ergebnis auf `false` setzen oder konservativ nur dann auf `true`, wenn alle Seiten weiterhin suchbar sind.
- `extractedText` für das Ergebnis neu berechnen oder auf `null` setzen.

Variante B, spätere Erweiterung:
- Betroffene Seiten erneut per OCR verarbeiten.
- Nur Wörter ausserhalb der Schwärzungsrechtecke wieder als unsichtbarer Textlayer einfügen.
- Vorteil: Suche ausserhalb geschwärzter Bereiche bleibt erhalten.
- Nachteil: Textpositionen und Auswahlverhalten sind nur approximiert, nicht identisch zum Original.

Für den MVP ist Variante A deutlich robuster.

### Phase 4: Sanitization ausserhalb des Seitenbilds
Rechtssicherheit endet nicht beim sichtbaren Seiteninhalt.

Zusätzlich bereinigen:
- `PDDocumentInformation`: Titel, Autor, Betreff, Keywords, Creator nach Produktentscheidung leeren oder gezielt neu setzen.
- XMP-Metadaten entfernen oder neu schreiben.
- Formularfelder (`AcroForm`) und deren Werte prüfen; bei betroffenen Dokumenten im Zweifel entfernen.
- Annotationen/Links im geschwärzten Bereich entfernen; bei neu gerenderten Seiten idealerweise keine alten Annotationen übernehmen.
- Eingebettete Dateien / Attachments prüfen und im Zweifel nicht übernehmen.
- Dokumentinterne Skripte / Actions / versteckte Inhalte nicht blind mitschleppen.
- Room-Daten:
  - `extractedText` neu berechnen oder löschen
  - FTS damit automatisch bereinigen
- Thumbnail der geschwärzten Datei neu erzeugen.

### Phase 5: Dateisemantik und Archivverhalten
Für "rechtssicher" ist das Produktverhalten genauso wichtig wie die PDF-Technik.

Use Cases:
- "Neue Kopie speichern": Original bleibt absichtlich bestehen. UI muss das klar sagen.
- "Original ersetzen": nur diese Variante verhindert, dass der Nutzer versehentlich die ungeschwärzte Datei weiterverwendet.

Empfehlung:
- MVP zuerst als neue Kopie.
- Zusätzlich gut sichtbarer Hinweis: "Original bleibt unverändert im Archiv."
- Später optional ein expliziter Replace-Flow mit atomarem Austausch von PDF, Thumbnail und DB-Eintrag.

## 6. Herausforderungen und Lösungen

### Herausforderung: Garantierte Entfernung ohne Layoutbruch
Problem:
- Bei beliebigen PDFs ist punktgenaues Entfernen einzelner Inhalte bei gleichzeitiger vollständiger Erhaltung der ursprünglichen internen Struktur sehr schwer.

Lösung:
- Für den sicheren Standard die ganze betroffene Seite neu aufbauen.
- Dadurch bleibt das visuelle Layout praktisch erhalten, obwohl die interne Text-/Vektorstruktur der betroffenen Seite ersetzt wird.

### Herausforderung: Inhalte in Bildern
Problem:
- Ein schwarzes Overlay entfernt den Inhalt im Bild nicht.

Lösung:
- Betroffene Seiten rendern und das Rechteck ins Pixelbild einbrennen.
- Damit ist der Inhalt unter dem Bereich im Ergebnisbild tatsächlich weg.

### Herausforderung: Unsichtbarer OCR-Text
Problem:
- Bei durchsuchbaren PDFs kann der sichtbare Bereich geschwärzt sein, der OCR-Text darunter aber weiter extrahierbar bleiben.

Lösung:
- Bei neu gerenderten betroffenen Seiten den alten Textlayer nicht übernehmen.
- Optional später OCR nur ausserhalb der geschwärzten Bereiche neu aufbauen.

### Herausforderung: Lokale Datenlecks in der App
Problem:
- `ScanRecord.extractedText`, FTS, Thumbnails oder das ungeschwärzte Original können sensible Daten weiter enthalten.

Lösung:
- Ergebnisdatei als eigenen Datensatz mit bereinigtem `extractedText`.
- Thumbnail immer aus dem geschwärzten PDF neu erzeugen.
- Klare UI-Semantik für Copy vs Replace.

### Herausforderung: Rotation, CropBox, Seitentransformationen
Problem:
- Die UI arbeitet in Anzeige-Koordinaten, PDFs in Seitensystemen und Rotationen.

Lösung:
- Die bestehende Koordinatenlogik aus Annotation/Highlight wiederverwenden.
- Bei Rendering-basiertem Ansatz Rechtecke bevorzugt direkt auf das gerenderte Bitmap mappen, nicht erst auf rohe PDF-Operatoren.
- Seitengrösse, Rotation und sichtbaren Beschnitt beim Neuaufbau explizit übernehmen.

### Herausforderung: Performance auf mobilen Geräten
Problem:
- Hohe DPI und mehrseitige Dokumente kosten Zeit und Speicher.

Lösung:
- Nur betroffene Seiten rendern.
- Immer nur eine Seite gleichzeitig im RAM halten.
- DPI konfigurierbar, aber mit sinnvollem Standard.
- Später WorkManager für lange Jobs.

## 7. Wie stelle ich sicher, dass der Inhalt unter der Schwärzung wirklich entfernt wird?
Die belastbarste Antwort für dieses Projekt lautet:

1. Nicht mit schwarzem Overlay arbeiten.
2. Betroffene Seiten vollständig neu erzeugen.
3. Dabei das Ergebnis aus gerendertem Seitenbild plus eingebrannter Schwärzung aufbauen.
4. Alten Textlayer, Annotationen und Formularwerte auf betroffenen Seiten nicht übernehmen.
5. Das PDF vollständig neu speichern, nicht inkrementell.
6. `extractedText`, FTS und Thumbnails ebenfalls bereinigen.
7. Mit adversarial Tests verifizieren, dass die entfernten Begriffe weder im PDF noch in lokalen Indizes wieder auftauchen.

Wichtige Ehrlichkeit:
- Absolute technische Sicherheit und unveränderte interne PDF-Struktur gleichzeitig sind für beliebige Fremd-PDFs mit `pdfbox-android` nicht realistisch.
- Der beste rechtssichere Weg ist deshalb ein sicherer Neuaufbau der betroffenen Seiten.

## 8. Wie vermeide ich unnötige Formatverluste?
Best Practice für gute visuelle Qualität:
- Nur betroffene Seiten rasterisieren, nicht das ganze Dokument.
- Hohe Renderauflösung verwenden.
- Verlustfreie Bild-Einbettung verwenden.
- Originale Seitengrösse exakt beibehalten.
- Schwarze Rechtecke exakt aus der UI-Selektion ableiten.
- Für nicht betroffene Seiten die Originalstruktur unangetastet lassen.

Wenn Suchbarkeit ausserhalb der Schwärzung wichtig ist:
- Diese als nachgelagerte, optionale OCR-Rekonstruktion behandeln.
- Nicht versuchen, im MVP beliebige digitale PDFs operatorgenau zu zerschneiden.

## 9. Use Cases und empfohlene Behandlung

### Use Case A: Gescannter Vertrag mit OCR-Textlayer
Empfohlen:
- Betroffene Seiten rendern und neu aufbauen.
- Alten OCR-Text auf diesen Seiten verwerfen.
- Optional später OCR ausserhalb der Schwärzungen neu erzeugen.

### Use Case B: Digital erzeugte Rechnung mit selektierbarem Text
Empfohlen:
- Für rechtssicheren MVP ebenfalls betroffene Seiten rendern und neu aufbauen.
- So wird der Inhalt sicher entfernt, auch wenn die Seite danach intern bildbasiert ist.

### Use Case C: Gemischte PDFs mit Bild, Text, Stempel, Unterschrift
Empfohlen:
- Gleiches sicheres Rendering-Modell für betroffene Seiten.
- Kein Versuch, gemischte Streams selektiv auseinanderzunehmen.

### Use Case D: Nutzer will Original behalten
Empfohlen:
- Ergebnis als neue Datei mit klarer Kennzeichnung speichern.
- UI-Hinweis, dass das Original weiterhin sensible Daten enthält.

### Use Case E: Nutzer will ein wirklich ersetztes Original
Empfohlen:
- Später eigener Replace-Flow.
- Alter Dateipfad, Thumbnail und DB-Eintrag werden atomar ersetzt.

## 10. Konkrete Umsetzung im Projekt

### Neue/anzupassende Bausteine
- `PdfEditor.kt`
  - neue Methode `applySecureRedaction(...)`
  - optional Hilfsmethoden für Seiten-Rendering und Bitmap-Redaction
- neue Use Case / Workflow-Schicht
  - `ApplyRedactionUseCase`
  - `RedactPdfWorkflow`
- `DocumentEditViewModel`
  - neuer Action-Pfad für Redaction
- UI
  - bestehende Rechteck-Selektion wiederverwenden
  - klarer Modus "Schwärzen"
  - Warntext zu Copy vs Replace
- Datenhaltung
  - `ScanRecord` des Ergebnisses mit bereinigtem `extractedText`
  - `isSearchable` bewusst setzen

### Was nicht in den MVP gehört
- Allgemeine Operator-Level-Redaction für beliebige PDFs.
- Halbautomatische Manipulation einzelner `Tj`/`TJ`-Tokens als Sicherheitskern.
- Erhalt der exakten Textselektierbarkeit auf betroffenen Seiten um jeden Preis.

## 11. Teststrategie

### Pflicht-Tests
- Unit-Test: PDF mit bekanntem String wie `GEHEIM-123`; nach Schwärzung darf `PDFTextStripper` den String nicht mehr finden.
- Byte-Test: bekannte ASCII-Testgeheimnisse zusätzlich im Dateibytestrom suchen; sie dürfen nicht mehr vorkommen.
- Instrumentation-Test: gerenderte Seite enthält an der richtigen Position ein opakes Rechteck.
- Regressionstest: nicht betroffene Seiten bleiben extrahierbar und visuell unverändert.
- Rotationstest: 0°, 90°, 180°, 270°.
- OCR-Test: ursprünglicher OCR-Text ist nach Schwärzung nicht mehr vorhanden.
- Room-/FTS-Test: `extractedText` des Ergebnisdatensatzes enthält keine geschwärzten Begriffe.
- Thumbnail-Test: Vorschaubild zeigt die Schwärzung und nicht das Original.

### Manuelle Verifikation
- Ergebnis in externem PDF-Viewer öffnen.
- Copy & Paste auf geschwärztem Bereich testen.
- Dokumentensuche im Viewer testen.
- Dateieigenschaften/Metadaten prüfen.
- Archivsuche in der App auf den geschwärzten Begriff testen.

## 12. Spätere Ausbaustufe
Falls später bessere Strukturtreue nötig wird, ist eine zweite Engine denkbar:
- nur für klar klassifizierte PDFs
- nur für einfache Textlayer ohne komplexe XObjects
- mit hartem Fallback auf die sichere Render-Strategie

Diese Ausbaustufe darf nie die sichere Standardvariante ersetzen.

## 13. Empfehlung
Für einen rechtssicheren MVP in diesem Projekt sollte die Schwärzung standardmässig so implementiert werden:

- Sichere Redaction durch Neuaufbau nur der betroffenen Seiten
- keine direkte Operator-Manipulation als Primärstrategie
- vollständige Bereinigung von OCR-Text, lokalen Suchindizes, Thumbnails und optional Metadaten
- klare Produktentscheidung zwischen "Kopie" und "Original ersetzen"

Damit ist die Schwärzung technisch belastbar, mit dem vorhandenen Stack realistisch umsetzbar und gegenüber den typischen Leckpfaden eines mobilen PDF-Workflows sauber abgesichert.
