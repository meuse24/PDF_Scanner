# Refactoring Plan: Orchestration statt Fragmentierung

Stand: 2026-03-23

## Ziel

Die bestehenden Use Cases bleiben erhalten, aber die wichtigsten Benutzerablaeufe
werden explizit modelliert. Dadurch wird die fachliche Reihenfolge wieder an
einer lesbaren Stelle sichtbar, statt verteilt im `HomeViewModel` zu liegen.

## 1. Benutzer-Workflows

| Workflow | Ausloeser | Vorbedingungen | Hauptpfad | Fehlerpfade | Owner |
| --- | --- | --- | --- | --- | --- |
| Scan importieren | Scanner-Dialog speichern | PDF-URI vorhanden | Datei speichern, Thumbnail sichern, optional OCR-Textlayer, DB-Eintrag anlegen | Quelle fehlt, Schreiben schlaegt fehl, OCR schlaegt fehl | vorerst `ImportScanUseCase`, spaeter eigener Workflow |
| Scans loeschen | Einzel- oder Mehrfachloeschen | Auswahl vorhanden | Datei loeschen, Thumbnail loeschen, DB-Eintrag entfernen | Datei nicht loeschbar | `DeleteScansUseCase` |
| Texte extrahieren | Bulk-Aktion OCR | mind. ein gueltiger Scan | Datei/Thumbnail lesen, OCR ausfuehren, Text aggregieren | keine Bildquelle, OCR schlaegt fehl | vorerst `HomeViewModel` + `ExtractTextUseCase` |
| Durchsuchbar machen | Bulk-Aktion searchable PDF | mind. ein nicht-searchable Scan | Auswahl validieren, Dateipruefung, OCR/Textlayer erzeugen, DB markieren | Auswahl leer, Datei fehlt, OCR/Schreiben schlaegt fehl | `MakeSearchableWorkflow` |
| PDFs zusammenfuehren | Bulk-Aktion Merge | mind. zwei Scans | Auswahl validieren, Reihenfolge uebernehmen, Merge, Thumbnail, DB-Eintrag | Auswahl ungueltig, Datei fehlt, Merge/Schreiben schlaegt fehl | `MergePdfsWorkflow` |
| PDF splitten | Einzelaktion Split | PDF mit >= 2 Seiten | Split-Punkte validieren, Datei pruefen, Splitten, Thumbnails erzeugen, Teile speichern | ungueltige Split-Punkte, Datei fehlt, IO-Fehler | `SplitPdfWorkflow` |
| Seiten neu anordnen | Einzelaktion Reorder | PDF mit >= 2 Seiten | Reihenfolge validieren, Datei pruefen, Reorder anwenden, Thumbnail erneuern, Datei/DB aktualisieren | ungueltige Reihenfolge, Datei fehlt, IO-Fehler | `ReorderPagesWorkflow` |
| Exportieren/Teilen | Aktion Export/Share | Quelldatei vorhanden | Datei kopieren bzw. URI teilen | Quelldatei fehlt, MediaStore-Fehler | `ExportScanUseCase` + UI |

## 2. Klassifikation der bestehenden Use Cases

### Atomar

- `DeleteScansUseCase`
- `ExportScanUseCase`
- `SplitPdfUseCase`
- `ReorderPagesUseCase`

### Komponiert

- `ImportScanUseCase`
- `MakeSearchableUseCase`
- `MergePdfsUseCase`

### Unklar / gemischt

- `ExtractTextUseCase`
  Aggregiert bereits mehrere Records und enthaelt damit nicht nur einen kleinen,
  rein atomaren Schritt. Fuer die erste Refactoring-Welle bleibt er trotzdem als
  Baustein bestehen.

## 3. Erste kritische Workflows

### `MakeSearchableWorkflow`

- kennt die Reihenfolge fuer Auswahlvalidierung, Dateipruefung und den Aufruf des
  bestehenden `MakeSearchableUseCase`
- fuehrt eine einheitliche Fehlersemantik fuer OCR- und Schreibfehler ein

### `MergePdfsWorkflow`

- kennt die fachlichen Vorbedingungen fuer Merge
- kapselt Auswahlvalidierung, Dateipruefung und die Fehlerabbildung rund um den
  bestehenden `MergePdfsUseCase`

## 4. Namenskonvention

- Bausteine bleiben `...UseCase`
- koordinierende Ablaufklassen heissen `...Workflow`
- Workflow-Fehler liegen in einem gemeinsamen Domain-Modell und werden erst im
  ViewModel auf UI-Strings gemappt

## 5. In dieser Refactoring-Welle umgesetzt

- gemeinsames Domain-Fehlermodell fuer Workflows
- `MakeSearchableWorkflow`
- `MergePdfsWorkflow`
- `SplitPdfWorkflow`
- `ReorderPagesWorkflow`
- zentrales Fehler-Mapping im `HomeViewModel`
- Workflow-Tests fuer Erfolgs- und Fehlerpfade

## 6. Naechste Kandidaten

- `ImportScanWorkflow`, aber erst wenn Import ausserhalb des bestehenden
  `ImportScanUseCase` mehr Reihenfolgelogik bekommt
- differenzierter Delete-Workflow, sobald mehr als `Boolean`/Toast benoetigt wird
- OCR-Text-Extraktion als eigener Workflow, falls Vorbedingungen und
  Fehlersemantik ueber das aktuelle `HomeViewModel` hinauswachsen
