# Umsetzungsplan – Release-Härtung PDF Scanner

Grundlage: Code-Review-Findings vom 2026-06-27.
Prioritätsreihenfolge: Lint-Blocker → OOM-Risiko → Pfadsicherheit → Transaktionskorrektheit → Privacy/Lock → Architektur.

---

## Phase 1 – Lint-Fehler beheben (Release-Blocker)

**Status (2026-06-27): Abgeschlossen.**

- Die drei Druckdialog-Texte sind in allen neun zusätzlichen Locales vorhanden.
- `confirm_delete_multi` und `selection_count` sind in allen Locales als Plural-Ressourcen umgesetzt; alle Compose-Aufrufe von `selection_count` verwenden `pluralStringResource`.
- Verifiziert mit `./gradlew.bat --no-configuration-cache lintDebug testDebugUnitTest --console=plain`.
- Ergebnis: Build erfolgreich, 0 Lint-Errors (22 verbleibende Warnungen außerhalb des vereinbarten Phase-1-Scopes), 424 Unit-Tests ohne Fehler.

**Ziel:** `lintDebug` von 3 Errors / 18 Warnings auf 0 Errors senken.

### 1.1 Fehlende Übersetzungen nachliefern

Die drei Strings aus `strings.xml:71-73` fehlen in allen neun Locale-Dateien:

```
print_custom_page_size_title
print_custom_page_size_message
print_custom_page_size_confirm
```

**Betroffene Dateien:**
```
app/src/main/res/values-de/strings.xml
app/src/main/res/values-es/strings.xml
app/src/main/res/values-fr/strings.xml
app/src/main/res/values-pt/strings.xml
app/src/main/res/values-zh-rCN/strings.xml
app/src/main/res/values-ar/strings.xml
app/src/main/res/values-ja/strings.xml
app/src/main/res/values-ru/strings.xml
app/src/main/res/values-hi/strings.xml
```

**Vorgehen:** Übersetzungen für jede Sprache einfügen. Solange kein Native-Speaker verfügbar ist, englische Fallback-Texte verwenden und `tools:ignore="TypographyDashes"` vermeiden – stattdessen korrekte Anführungszeichen je Locale nutzen.

### 1.2 Plurals-Strings umstellen

Lint meldet Mengenangaben, die als `<plurals>` umgesetzt werden sollten (18 Warnings, teilweise als Error eingestuft). Kandidaten sind u.a. `confirm_delete_multi` und `selection_count`.

**Vorgehen:**
1. `./gradlew lint` ausführen und alle `ImpliedQuantity`-Findings auflisten.
2. Betroffene `<string>`-Einträge in `<plurals>`-Blöcke überführen.
3. Aufrufe im Kotlin-Code von `getString(R.string.x, n)` auf `resources.getQuantityString(R.plurals.x, n, n)` umstellen.
4. Änderungen in alle 10 Locale-Dateien übertragen.

**Akzeptanzkriterium:** `./gradlew lint` meldet 0 Errors.

---

## Phase 2 – OOM-Risiko in der Bild-zu-PDF-Pipeline beseitigen

**Status (2026-06-27): Abgeschlossen.**

- `BitmapPdfImageRenderer` bestimmt zuerst die Bildgrenzen, dekodiert mit `inSampleSize`, skaliert die längste Seite exakt auf höchstens `maxDimension` und komprimiert nur dieses Bitmap als JPEG.
- `PdfRenderingOps.createPdfFromImages` fordert Bilddaten über einen suspendierenden Provider indexweise an; `ImagePdfBuilder` und `PdfEditor` halten daher keine vorberechnete Liste aller Quelldaten mehr.
- Ein JVM-Test verifiziert die lazy/sequentielle Aufrufreihenfolge. Ein Instrumentationstest prüft die tatsächliche Ausgabedimension.
- Verifiziert mit `./gradlew.bat --no-configuration-cache testDebugUnitTest lintDebug --console=plain`: Build erfolgreich, 0 Lint-Errors, 425 Unit-Tests ohne Fehler.
- `compileDebugAndroidTestKotlin` wurde zusätzlich ausgeführt, bleibt aber durch bereits vor Phase 2 vorhandene Fehler in anderen Instrumentationstests blockiert (veraltete DAO-/OCR-Testdoubles). Die in Phase 2 geänderten Verträge und der neue Test erzeugen in der Compiler-Ausgabe keine zusätzlichen Fehler.
- Auf eine starre 50-Bilder-Grenze wurde bewusst verzichtet: Die sequentielle Verarbeitung beseitigt den geplanten Heap-Peak, ohne legitime Stapelimporte künstlich einzuschränken.

### 2.1 Tatsächliches Downsampling in `BitmapPdfImageRenderer`

**Datei:** `util/BitmapPdfImageRenderer.kt:16`

**Problem:** `decodeBitmapBytes()` ruft `readBytes()` auf den rohen InputStream und ignoriert den `maxDimension`-Parameter vollständig. Der Parameter existiert im Interface `PdfImageRenderer` aber wird nie angewendet.

**Lösung:**
```
openInputStream(uri)
  → BitmapFactory.Options mit inJustDecodeBounds = true → Originalgröße messen
  → inSampleSize berechnen: größte Seite auf maxDimension begrenzen
  → Bitmap mit inSampleSize dekodieren
  → Bitmap als JPEG (quality 85) in ByteArray komprimieren
  → Bitmap.recycle()
```

Damit hält der Renderer nie mehr als ein dekodiertes Bild gleichzeitig im Heap.

**Clean-Architecture-Grenze:** Die Logik bleibt in `util/`, da sie Android-APIs (BitmapFactory) verwendet. Das Domain-Interface `PdfImageRenderer` bleibt unverändert.

### 2.2 Sequentielle statt parallele Bildverarbeitung in `ImagePdfBuilder`

**Datei:** `domain/usecase/ImagePdfBuilder.kt:32`

**Problem:** `imageUris.map { imageRenderer.decodeBitmapBytes(…) }` lädt alle Bilder gleichzeitig als `List<ByteArray?>` in den Heap, bevor `createPdfFromImages` aufgerufen wird. Bei vielen oder großen Bildern entsteht Peak-Speicherdruck.

**Lösung – seitenweise Verarbeitung:**
```kotlin
// Statt map → imageProvider-Lambda
pdfEditor.createPdfFromImages(
    imageUris = imageUris,
    imageProvider = { uri -> imageRenderer.decodeBitmapBytes(uri, IMAGE_PDF_MAX_SOURCE_DIMENSION) },
    options = options,
    outputFile = outputFile
)
```

Das erfordert, dass `PdfRenderingOps.createPdfFromImages` einen `imageProvider`-Lambda statt einer fertig befüllten Liste akzeptiert. Die `PdfEditor`-Implementierung ruft den Lambda pro Seite auf und schreibt die Seite sofort, ohne alle Bytes vorzuhalten.

**Alternativ (einfacher, ohne Interface-Änderung):** In `ImagePdfBuilder.createPdf` die Bilder per `chunked(1)` sequentiell abarbeiten und jeweils ein Einzel-PDF erstellen, das danach gemergt wird. Der Speicherfootprint ist identisch, erfordert aber zwei PdfBox-Pässe. Bevorzugte Option: Interface-Anpassung.

**Zusätzliche Absicherung:** Obergrenze für `imageUris.size` einführen (z.B. 50 Bilder). Bei Überschreitung `IllegalArgumentException` mit sprechendem Fehlermeldungs-String.

---

## Phase 3 – Dateinamen-Normalisierung durchsetzen

**Status (2026-06-27): Abgeschlossen.**

- `sanitizeFilename` wird sowohl beim Speichern als auch beim Umbenennen vor jeder Pfadbildung angewendet.
- `resolveSafeChildFile` prüft zentral über kanonische Pfade, dass das Ziel ein direktes Kind des vorgesehenen Verzeichnisses ist.
- Drei neue Unit-Tests decken Traversal-Namen aus externen Quellen, sicheres Umbenennen und vollständig ungültige Namen ab.
- Verifiziert mit `./gradlew.bat --no-configuration-cache testDebugUnitTest lintDebug --console=plain`: Build erfolgreich, 0 Lint-Errors, 428 Unit-Tests ohne Fehler.

### 3.1 Path-Traversal-Schutz in `FileUtil`

**Datei:** `util/FileUtil.kt:25`

**Problem:** `savePdf(source, filename)` baut `File(scansDir, "$filename.pdf")` direkt aus der Benutzereingabe. Enthält `filename` `../`-Segmente oder verbotene Zeichen, landet die Datei außerhalb von `scansDir`.

**Lösung:**
```kotlin
override fun savePdf(source: Any, filename: String): File {
    val safe = sanitizeFilename(filename)              // domain/common/FilenameUtils.kt
    var destFile = File(scansDir, "$safe.pdf")
    // Kanonischer Pfad-Guard
    check(destFile.canonicalPath.startsWith(scansDir.canonicalPath + File.separator)) {
        "Resolved path escapes scansDir"
    }
    // … uniqueness-Loop wie bisher …
}
```

`sanitizeFilename()` ist bereits in `domain/common/FilenameUtils.kt:16` vorhanden und entfernt `\/:*?"<>|` sowie Steuerzeichen.

### 3.2 Path-Traversal-Schutz in `RenameDocumentUseCase`

**Datei:** `domain/usecase/RenameDocumentUseCase.kt:25`

**Problem:** `File(targetScansDir, "$trimmed.pdf")` wird ohne Sanitisierung gebaut.

**Lösung:** Denselben `sanitizeFilename(trimmed)` + kanonischen Pfad-Check wie in 3.1 anwenden. Da `RenameDocumentUseCase` in `domain/` liegt, darf es `domain/common/FilenameUtils.sanitizeFilename()` direkt aufrufen.

```kotlin
val safe = sanitizeFilename(trimmed)
if (safe.isBlank()) return RenameDocumentResult.BlankName
val newFile = File(targetScansDir, "$safe.pdf")
check(newFile.canonicalPath.startsWith(targetScansDir.canonicalPath + File.separator))
```

---

## Phase 4 – Transaktionale Korrektheit im Import

**Status (2026-06-27): Abgeschlossen.**

- Der komplette Ablauf nach erfolgreichem PDF-Copy ist durch kompensierenden Cleanup geschützt.
- Bei Fehlern in Thumbnail-Erzeugung, OCR oder Repository-Persistierung werden die kopierte PDF und ein bereits erzeugtes Thumbnail gelöscht.
- Zwei neue Unit-Tests verifizieren OCR- und Datenbankfehler.
- Verifiziert mit `./gradlew.bat --no-configuration-cache testDebugUnitTest lintDebug --console=plain`: Build erfolgreich, 0 Lint-Errors, 430 Unit-Tests ohne Fehler.

### 4.1 Cleanup bei Fehler in `ImportScanUseCase`

**Datei:** `domain/usecase/ImportScanUseCase.kt:30`

**Problem:** Schlägt `searchablePdfBuilder.makeSearchable()` oder `repository.saveScan()` fehl, bleiben `savedFile` und ggf. `thumbnailFile` als verwaiste Dateien in `filesDir/scans/`. `ImportFileUseCase` (Zeile 56–59) macht dies korrekt mit einem `try/catch`-Block.

**Lösung:** `ImportScanUseCase` auf das gleiche Muster umstellen:

```kotlin
val savedFile = fileStore.savePdf(pdfUri, filename)
val baseName  = savedFile.nameWithoutExtension
val thumbnailPath = thumbnailUri?.let {
    fileStore.saveThumbnail(it, baseName)?.absolutePath
}
try {
    // OCR + repository.saveScan(…)
} catch (e: Exception) {
    savedFile.delete()
    thumbnailPath?.let { File(it).delete() }
    throw e
}
```

---

## Phase 5 – App-Lock und Privacy-Verbesserungen

### 5.1 `FLAG_SECURE` bei aktiviertem App-Lock setzen

**Datei:** `MainActivity.kt:41`

**Problem:** Ist App-Lock aktiv und `isLocked == true`, sind Dokumentinhalte in der Android-Aufgabenübersicht und für Screenshot-APIs sichtbar, da `FLAG_SECURE` fehlt.

**Lösung:** In `MainActivity.onCreate()` einen Observer auf `isLocked` und `settings.appLockEnabled` reagieren lassen:

```kotlin
lifecycleScope.launch {
    combine(appLockManager.isLocked, settingsFlow) { locked, s -> s.appLockEnabled && locked }
        .collect { secure ->
            if (secure) window.setFlags(FLAG_SECURE, FLAG_SECURE)
            else window.clearFlags(FLAG_SECURE)
        }
}
```

**Hinweis:** `FLAG_SECURE` verhindert keine Accessibility-Audits auf gerooteten Geräten; das ist dokumentiertes Android-Verhalten und muss nicht als Bug kommuniziert werden.

### 5.2 Stale Staging-Verzeichnisse beim App-Start bereinigen

**Datei:** `domain/usecase/RestoreBackupUseCase.kt:97` / `PdfScannerApp.onCreate()`

**Problem:** `backup_restore/`-Unterverzeichnisse in `tempDir` werden bei normalem Ablauf durch `cancel()` oder den Fehlerfall in `prepare()` gelöscht. Ein harter Prozessabbruch (OOM-Kill, Force-Stop) zwischen `prepare()` und `confirm()`/`cancel()` hinterlässt Klartext-PDFs im Cache.

**Lösung:** In `PdfScannerApp.onCreate()` (oder einem dafür zuständigen `AppInitializer`) nach dem Startup synchron oder via Coroutine:

```kotlin
File(storageProvider.tempDir(), "backup_restore")
    .takeIf { it.exists() }
    ?.deleteRecursively()
```

**Clean-Architecture-Grenze:** Die Bereinigung kann als eigener `CleanStagingDirsUseCase` (Domain, nutzt `StorageProvider`) modelliert werden, den `AppModule` beim Start aufruft. Alternativ direkt in `PdfScannerApp` via `StorageProvider`-Implementierung, solange keine Fachlogik eingebaut wird.

---

## Phase 6 – Architektur: HomeViewModel aufteilen

**Datei:** `ui/home/HomeViewModel.kt` (~900 Zeilen, 28 Konstruktor-Abhängigkeiten)

**Problem:** Der ViewModel ist ein God-Object. 28 Use-Cases werden direkt injiziert; Import-, Export-, OCR-, Archiv- und Review-Verantwortlichkeiten sind vermischt. Das macht Tests aufwendig und erschwert Weiterentwicklung.

**Empfohlener Schnitt** (keine Feature-Module nötig, aber vorbereitet):

| Koordinator | Verantwortung | Use-Cases |
|---|---|---|
| `HomeImportCoordinator` | Scan + PDF-Import + Bild-zu-PDF | `ImportScanUseCase`, `ImportFileUseCase` |
| `HomeOcrCoordinator` | Text-Extraktion, Backfill, Searchable | `ExtractTextUseCase`, `OcrBackfillUseCase`, `MakeSearchableWorkflow` |
| `HomeExportCoordinator` | PDF, JPG, DOCX, OCR-TXT Export | `ExportScanUseCase`, `ExportAsJpgUseCase`, `ExportDocxUseCase`, `ExportOcrTextUseCase` |
| `HomeArchiveCoordinator` | Trash, Restore, Move, Rename, Favorite | `TrashScansUseCase`, `RestoreScansUseCase`, `MoveDocumentsUseCase`, `RenameDocumentUseCase`, `ToggleFavoriteUseCase` |

`HomeViewModel` hält die vier Koordinatoren als Abhängigkeiten und delegiert. States werden je Koordinator als eigene `StateFlow` gehalten und im ViewModel zu `HomeUiState` gemergt.

**Vorgehen (schrittweise, ohne Regression):**
1. `HomeImportCoordinator` extrahieren – alle Import-Methoden verschieben, Tests laufen lassen.
2. `HomeExportCoordinator` extrahieren.
3. `HomeOcrCoordinator` extrahieren.
4. `HomeArchiveCoordinator` extrahieren.
5. `HomeViewModel` auf Koordinatoren umstellen; bestehende Tests gegen `HomeViewModel` bleiben und testen das zusammengesetzte Verhalten.

---

## Phase 7 – Barrierefreiheit der Zeichenflächen (Backlog)

**Betroffene Screens:** `ui/signature/SignatureScreen.kt:325`, `ui/annotate/AnnotateScreen`, `ui/redact/RedactScreen`

**Problem:** Eigene Pointer-Flächen ohne semantische Aktionen sind für TalkBack nicht bedienbar.

**Minimalziel für nächste Release:**
- Content-Description auf Zeichenflächen (`Modifier.semantics { contentDescription = … }`).
- Alternative Aktions-Buttons (z.B. „Unterschrift löschen", „Punkt hinzufügen") außerhalb der Zeichenfläche sichtbar machen.
- Tastaturbedienung (Tab-Fokus) für alle Buttons prüfen.

**Vollständige Accessibility-Härtung** (späteres Milestone):
- `SemanticsProperties.CustomActions` für Zeichengesten.
- TalkBack-Explorationstest für alle Edit-Screens.
- Mindest-Touch-Target 48 dp für alle interaktiven Elemente prüfen.

---

## Reihenfolge und Aufwandsschätzung

| Phase | Aufwand | Release-Relevanz |
|---|---|---|
| 1 – Lint / Plurals | ~1 Tag | **Blocker** |
| 2 – OOM Bilder | ~0,5–1 Tag | Hoch (Absturzrisiko) |
| 3 – Dateinamen | ~0,5 Tag | Hoch (Sicherheit) |
| 4 – Import Cleanup | ~1–2 h | Mittel |
| 5 – App-Lock / Staging | ~2–3 h | Mittel |
| 6 – HomeViewModel | ~2–3 Tage | Niedrig (Technische Schuld) |
| 7 – Barrierefreiheit | ~2 Tage | Niedrig (Backlog) |

**Empfehlung:** Phasen 1–5 vor dem nächsten Release abschließen. Phase 6 als eigenen Refactoring-Sprint einplanen. Phase 7 ins Backlog aufnehmen mit TalkBack-Test als Akzeptanzkriterium.

---

## Nicht aufgenommen

Die folgenden produktseitigen Vorschläge aus dem Review wurden bewusst ausgeklammert, da sie neue Features darstellen und nicht Teil der Release-Härtung sind:

- Automatische Dateinamenvorschläge aus OCR
- WorkManager-Backups
- Fortschrittsanzeige bei großen Importen
- Aktions-Sichtbarkeit reduzieren
- Macrobenchmark-Tests / CI-Pipeline

Diese können als separates Backlog-Epic erfasst werden.
