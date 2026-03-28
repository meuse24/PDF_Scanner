# Implementierungsplan: Bestehende PDFs per Filepicker zur Ablage hinzufügen

Stand: 2026-03-28 (Update mit Senior-Android-Findings)

## Ziel

Zusätzlich zum bestehenden Scan-Flow sollen Nutzer auch bereits vorhandene PDF-Dateien aus dem Dateisystem in die Ablage importieren können.

Wichtig:
- Kein zweites Archivsystem aufbauen.
- Importierte PDFs sollen am Ende genauso in `filesDir/scans/` liegen wie neu gescannte PDFs.
- Der bestehende `ScanRecord`-Flow, die Sortierung, Suche und alle Folgeaktionen sollen unverändert weiter funktionieren.

## Empfehlung

Empfohlener Ansatz: separater Import-Einstieg im bestehenden Home-Flow mit Android `OpenDocument`-Picker für MIME `application/pdf`, danach Import in denselben Persistenzpfad wie Scans.

Warum dieser Weg:
- passt zum aktuellen Compose-Setup mit `rememberLauncherForActivityResult`
- nutzt das Storage-Framework statt Dateipfade oder Legacy-Berechtigungen
- vermeidet Sonderfälle, weil die Datei direkt in die App-Ablage kopiert wird
- hält den bestehenden `ScanRecord`-basierten Rest der App unverändert

## Bestehende Bausteine, die wiederverwendet werden sollten

- `HomeScreen.kt`: verwaltet bereits Launcher, Dialoge und den Speichern-Dialog für neue Scans
- `HomeViewModel.saveScan(...)`: zentraler Einstieg für den Import eines neuen Archiv-Eintrags
- `ImportScanUseCase.kt`: kopiert eine PDF in `filesDir/scans/`, erzeugt `ScanRecord` und speichert ihn in Room
- `FileUtil.savePdfFromUri(...)`: kopiert `content://`-URIs bereits in das App-interne Scan-Verzeichnis
- `PdfEditor.getPageCount(...)`: kann die Seitenzahl nach dem Kopieren bestimmen
- `PdfEditor.generateThumbnail(...)`: kann für importierte PDFs ein Vorschaubild erzeugen
- `ScanRecord.isEncrypted`: existiert bereits und sollte beim PDF-Import befüllt werden

## Produktentscheidung

### UX-Einstieg

Das Hinzufügen-Icon (FAB) öffnet nun ein **ModalBottomSheet** (Material 3), um die Auswahl zwischen Scan und Import zu ermöglichen.

Einträge im BottomSheet:
- **Dokument scannen** (Icon: `Icons.Default.CameraAlt` / `PhotoCamera`)
- **PDF importieren** (Icon: `Icons.Default.UploadFile` / `NoteAdd`)

Begründung:
- Der FAB bleibt der zentrale Action-Point.
- Das BottomSheet bietet Platz für beschreibende Texte und Icons.
- Konsistent mit modernen Android-Apps (z.B. Google Drive).

## Architektur-Details & Best Practices

### 1. Unified State Modell (PendingImport)
Statt separater Variablen für Scan-Ergebnisse und Picker-URIs wird ein `sealed class` Modell im `HomeScreen` eingeführt:
```kotlin
sealed class PendingImport {
    data class Scan(val result: GmsDocumentScanningResult) : PendingImport()
    data class File(val uri: Uri, val originalName: String) : PendingImport()
}
```

### 2. Scoped Storage & URI Handling
- **Sofortiges Kopieren:** Sobald der Filepicker eine URI liefert, wird die Datei sofort in den internen Speicher kopiert (`filesDir/scans/`).
- **Sicherheit:** Dies vermeidet den Verlust von Berechtigungen auf `content://`-URIs nach einem Prozess-Restart oder App-Wechsel während der "Speichern"-Dialog noch offen ist.

### 3. Dateinamens-Extraktion
Der ursprüngliche Dateiname wird über den `ContentResolver` (Spalte `OpenableColumns.DISPLAY_NAME`) ermittelt, um dem Nutzer einen sinnvollen Vorschlag ohne die Endung `.pdf` zu machen.

## Fachlicher Zielzustand

Ein importiertes PDF soll nach Abschluss dieselben Eigenschaften haben wie ein gescannter Eintrag:
- eigener Dateiname in der Ablage
- Datei physisch in `filesDir/scans/`
- Eintrag in `scan_records`
- korrekt gesetzte `pageCount`
- korrekt gesetzte `fileSize`
- **automatisch generiertes Thumbnail** (zwingend über `PdfEditor`, da vom Picker keine Bilder kommen)
- korrekt gesetztes `isEncrypted`
- sofort sichtbar in Suche, Sortierung und Bulk-Aktionen

## Umsetzungsplan

### Schritt 1: UI-Einstieg für PDF-Import ergänzen
- `ModalBottomSheet` im `HomeScreen` implementieren.
- Launcher für `ActivityResultContracts.OpenDocument()` mit MIME `application/pdf`.

### Schritt 2: Gemeinsames Pending-Import-Modell
- Integration der `PendingImport` Struktur im ViewModel oder Screen-State.
- Anpassung des `SaveDialog`, sodass er sowohl `PendingImport.Scan` (mit Seitenzahl-Info) als auch `PendingImport.File` (ohne Seitenzahl-Info im Vorfeld) verarbeiten kann.

### Schritt 3: ViewModel-Erweiterung
- Neue Methode `importFile(uri, filename)` im `HomeViewModel`.
- Handhabung von Ladezuständen während des Imports (Kopieren + Thumbnailing).

### Schritt 4: Neuer Use Case `ImportFileUseCase`
Verantwortung:
1. Datei kopieren (via `FileUtil`).
2. Validierung (via `PdfEditor.getPageCount`).
3. Verschlüsselung prüfen (via `PdfEditor.isPdfEncrypted`).
4. Thumbnail generieren (via `PdfEditor.generateThumbnail`).
5. `ScanRecord` persistieren.

## Offene Produktentscheidung: OCR bei Datei-Import

**Entscheidung: OCR zunächst nicht im Import-Dialog anbieten (Variante B).**
- Reduziert Komplexität in der ersten Iteration.
- Vermeidet unnötige OCR-Läufe über bereits digitale/durchsuchbare PDFs.
- Nutzer kann OCR später über die bestehende "Durchsuchbar machen"-Aktion starten.

## Testplan Erweiterung

### Unit-Tests
- `ImportFileUseCase` erkennt defekte PDFs (0 Seiten) und bricht ab.
- Korrekte Extraktion des Display-Namens aus einer Mock-URI.
- `isEncrypted` wird bei geschützten PDFs korrekt auf `true` gesetzt.

### UI-Tests
- BottomSheet öffnet sich bei Klick auf FAB.
- Auswahl von "PDF importieren" öffnet den System-Filepicker.
- Der Speichern-Dialog zeigt den korrekten Dateinamen-Vorschlag der importierten Datei.

### Edge-Case Verifikation
- **Große Dateien:** Import einer >50MB PDF (Prüfung der UI-Reaktion/Ladeindikator).
- **Fake-PDFs:** Import einer umbenannten Textdatei (Erkennung als ungültig).
- **Abbruch:** Nutzer schließt den Picker oder den Speichern-Dialog (Aufräumen temporärer Kopien).
