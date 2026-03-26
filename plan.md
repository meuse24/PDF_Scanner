# Plan: Text-Snap-Markierungen fuer durchsuchbare PDFs

Stand: 2026-03-26
Status: umgesetzt und verifiziert

## Ziel

Im Highlight-Screen soll es fuer durchsuchbare PDFs einen optionalen Text-Snap-Modus geben.
Ein gezeichneter Marker-Strich rastet dabei an erkannte Textzeilen ein und wird als sauberes
Highlight-Rechteck gespeichert. Ohne Text-Snap oder bei nicht durchsuchbaren PDFs bleibt das
bestehende Freihand-Verhalten unveraendert.

## Konsolidierte Architekturentscheidungen

1. Rechtecke und Freihandstriche bleiben getrennte Typen.
   `HighlightRect` und `HighlightStroke` haben unterschiedliche Daten- und Renderpfade.
   Das vermeidet Bool-Flags und haelt UI, Workflow und PDF-Ausgabe klar.

2. Textzeilen werden aus `PDFTextStripper` / `TextPosition` im Anzeige-Koordinatensystem
   extrahiert.
   Dadurch muss der Snap-Pfad nicht gegen einen zweiten, abweichenden Koordinatenraum arbeiten.

3. OCR-Overlay-Mini-Text wird herausgefiltert.
   Zeichen mit `fontSizeInPt < 4f` werden ignoriert, damit unsichtbare OCR-Textlayer nicht als
   falsche Snap-Zeilen auftauchen.

4. Textzeilen werden pro Seite im ViewModel gecacht.
   Seitenwechsel bleiben responsiv und dieselbe Seite wird nicht wiederholt analysiert.

5. Rechtecke werden vor Freihandstrichen gezeichnet und mit niedrigerem Alpha gerendert.
   Das gilt sowohl fuer die Compose-Vorschau als auch fuer die PDF-Ausgabe.
   Ergebnis: bessere Lesbarkeit und saubere Layer-Reihenfolge.

6. Snap bleibt optional und faellt bei keinem Treffer sauber auf Freihand zurueck.
   Es gibt keinen Modal-Fehlerfall und keinen erzwungenen Sonderpfad beim Zeichnen.

## Umsetzungsplan

[x] Domaenenmodell erweitern
- `TextLine` eingefuehrt
- `HighlightRect` eingefuehrt
- bestehende Highlight-Farbkonstanten zentral weiterverwendet

[x] PDF-Pipeline erweitern
- `PdfEditor.extractTextLines(file, pageIndex)` implementiert
- Zeilengruppierung als testbare Hilfsfunktion ausgelagert
- `PdfEditor.applyHighlight(...)` um Rechtecke erweitert
- Rechtecke werden mit Non-Stroking-Alpha und korrekter Ebenenreihenfolge in PDFs geschrieben

[x] Workflow- und UseCase-Schicht erweitern
- `DocumentEditViewModel` verwaltet `textLines` als StateFlow
- seitenweiser Cache fuer extrahierte Textzeilen eingebaut
- `HighlightPdfWorkflow` akzeptiert jetzt Strokes, Rects oder gemischte Eingaben
- `HighlightPdfUseCase` speichert neue Highlight-Kopien weiterhin inkl. bestehender Tags

[x] Highlight-UI erweitern
- Snap-Chip nur fuer durchsuchbare PDFs sichtbar
- Rechteck-Markierungen sind saveable und seitenbezogen
- `snapStrokeToTextLines()` implementiert und testbar gemacht
- Undo, Page-Clear und Reset-All beruecksichtigen jetzt Rechtecke und Freihandstriche
- Speichern uebergibt beide Markierungsarten an die Pipeline

[x] Lokalisierung erweitern
- `highlight_mode_snap` in Default-Strings und allen vorhandenen Locale-Dateien angelegt

[x] Regressionen absichern
- `HighlightScreenMathTest` um Snap- und Rechtecklogik erweitert
- `HighlightPdfWorkflowTest` um Rect-only- und Mixed-Cases erweitert
- `PdfEditorTest` um Zeilengruppierung erweitert

## Verifikation

Erfolgreich ausgefuehrt am 2026-03-26:
- `./gradlew.bat :app:compileDebugKotlin`
- `./gradlew.bat testDebugUnitTest --tests "info.meuse24.pdf_scanner.ui.highlight.HighlightScreenMathTest" --tests "info.meuse24.pdf_scanner.domain.workflow.HighlightPdfWorkflowTest"`
- `./gradlew.bat testDebugUnitTest --tests "info.meuse24.pdf_scanner.ui.highlight.HighlightScreenMathTest" --tests "info.meuse24.pdf_scanner.util.PdfEditorTest"`
- `./gradlew.bat testDebugUnitTest`

## Restliche Beobachtungspunkte

1. Multi-Column- oder Tabellen-PDFs koennen spaeter von einer optionalen X-Ueberlappungsheuristik
   profitieren, falls linke Randstriche sonst zu viele Zeilen markieren.

2. Sinnvoll waere noch ein manueller Geraetetest mit echten durchsuchbaren PDFs,
   insbesondere fuer OCR-lastige Dokumente mit dichtem Textlayout.
