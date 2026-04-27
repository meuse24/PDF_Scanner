# CLAUDE.md

## Build

```bash
./gradlew :app:compileDebugKotlin   # Kotlin-Compile-Check
./gradlew assembleDebug             # Debug-APK
./gradlew assembleRelease           # Release-APK
./gradlew installDebug              # Bauen + installieren
./gradlew test                      # Unit-Tests
./gradlew connectedDebugAndroidTest # Instrumentation-Tests
./gradlew lint && ./gradlew clean
```

Gezielte PDF-Tests:
```bash
./gradlew testDebugUnitTest --tests "info.meuse24.pdf_scanner.util.PdfEditorTest" --tests "info.meuse24.pdf_scanner.util.PdfEditorRealIntegrationTest"
./gradlew --% connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=info.meuse24.pdf_scanner.ImportAndPdfEditorInstrumentedTest
./gradlew --% connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=info.meuse24.pdf_scanner.util.SearchableAndRoundTripInstrumentedTest
```

Build-Umgebung: Android Studio Meerkat+, JDK 17+, `compileSdk 36.1`, Java/Kotlin toolchain 11.
Release-Signierung optional (`keystore.properties` + JKS); CI läuft ohne Keystore.

ADB: `C:/Users/guent/AppData/Local/Android/Sdk/platform-tools/adb.exe`
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n info.meuse24.pdf_scanner/.MainActivity
```

Hilt-Cache-Workaround (fehlende generierte Klassen): `./gradlew installDebug --no-build-cache --rerun-tasks`

## Projekt

**App:** M24 PDF-Scanner („PDF Scan") | **Paket:** `info.meuse24.pdf_scanner` | **Min SDK:** 29 | **Target SDK:** 36 | **Version:** 2.1 (Code 7)

### Schichtübersicht

**`domain/usecase/`** — alle fachlichen Operationen (Import/Export, OCR-TXT-Export, Trash/Restore/Purge, OCR/Searchable, AutoTags/RetroTag, Merge/Split/Reorder/Rotate/Delete/Duplicate, Redact/Highlight/Annotate, Grayscale, Folders, Favorites, BusinessCard/vCard, ImagesToPdf, Append).
`AutoTagUseCase` ist aktiv und über `AppSettings.autoTaggingEnabled` abschaltbar: `MakeSearchableUseCase` und der stille OCR-Backfill speichern Tags nur bei aktivierter Option; `RetroTagUseCase` ergänzt Tags für vorhandene OCR-Dokumente.

**`domain/workflow/`** — dünne Workflow-Guards um UseCases: prüfen Datei-Existenz, Verschlüsselung, leere Inputs; mappen Fehler via `WorkflowErrorMapper`.

**`domain/pdf/`** — Clean-Architecture-Ports: `PdfStructureOps`, `PdfRenderingOps`, `PdfSecurityOps`, `PdfTextOps`, `PdfAnnotationOps`, `PdfMetadataOps`, `PdfExceptions`.

**`util/PdfEditor.kt`** + `PdfEditorCore/Annotation/Overlay/Redaction/ImageOps` — zentrale PdfBox-Implementierung aller Ports.
`SearchablePdfBuilder`: Phase1 PdfRenderer+OCR, Phase2 PdfBox. ZH/JA/KO: OCR-Text ja, Searchable-PDF-Textlayer **nein** (TTC/OTC-Font-Problem).

**`data/local/`** — Room DB v9 (`pdf_scanner_db`), `ScanRecord` + `FolderEntity` + FTS4, Migrationen 1–9.
`ScanRecord`-Felder: `extracted_text`, `ocr_confidence`, `ocr_language`, `ocr_page_text_json`, `deleted_at`, `folder_id`, `is_favorite`.

**`ui/`** — Compose-Screens nach Feature gegliedert: `home/`, `viewer/`, `documentaction/`, `annotate/`, `redact/`, `pageedit/`, `split/`, `reorder/`, `append/`, `imagestopdf/`, `ocr/`, `trash/`, `folders/`, `businesscard/`, `settings/`, `signature/`, `qrscan/`, `lock/`, `tile/`, `widget/`.

**`ui/navigation/`** — `AppNavigation` (Shell + Gradient), `AppNavHost` (zerlegte NavGraphs), `AppDrawerContent`, `AppBarTitle`, `Screen`.

**`di/`** — Hilt-Module: `DatabaseModule`, `RepositoryModule`, `PdfOperationsModule`.

**`util/`** — `OcrPipeline`, `OcrManager` (ML-Kit, unbundled für HI/ZH/JA/KO), `OcrModelInstaller`, `PdfPageBitmapRenderer` (Mutex, OOM-Fallback), `PdfPageBitmapCache` (Byte-Budget), `AppLockManager` (ProcessLifecycle-Gate, BiometricPrompt), `PdfDocumentIntents`, `PdfPrintHelper`.

`PDFBoxResourceLoader.init(this)` muss in `PdfScannerApp.onCreate()` aufgerufen werden.

## Architektur-Regeln

- **Schichten:** ViewModel → UseCase/Workflow → Repository; keine Fachlogik in `MainActivity`.
- **Clean-Architecture-Status:** `domain/model/Document`, `OcrInfo`, `PdfMetadata` sind führende Modelle. `ScanArtifactPersister` für Datei-/Thumbnail-/DB-Persistenz; `DocumentWorkflowGuard` für Single-Document-Guards. Neue UseCases sollen diesen Boilerplate nicht duplizieren.
- **Externe Einstiegspunkte** (Shortcuts, QS-Tile, Widget, `ACTION_SEND`, `ACTION_SEND_MULTIPLE`, `ACTION_VIEW`) ausschließlich über `AppEntryActionViewModel`.
- **App-Lock** = UI-Gate (puffert Actions, umgeht sie nicht). Nicht als DB-/PDF-Verschlüsselung darstellen.
- **Keine Literal-Strings** in Kotlin — nur `context.getString(R.string.*)` / `stringResource()`.
- **Neue Strings** in alle 10 Locales: `values/`, `-de`, `-es`, `-fr`, `-pt`, `-zh-rCN`, `-ar`, `-ja`, `-ru`, `-hi`. Feature-Strings in `strings_<feature>.xml`.
- **Privacy-/Help-/Info-Texte** gegen `docs/privacy-policy.html` und reale Datenflüsse prüfen.
- **Fehler HomeScreen:** `viewModel.reportError(String)` → `_error: StateFlow` → AlertDialog.
- **Fehler Edit-Screens:** eigener `_error: StateFlow<String?>` im ViewModel → AlertDialog.
- **Erfolg HomeScreen:** `_success: StateFlow<String?>` → Toast + `clearSuccess()`.
- **Settings:** eigener `_success: StateFlow<String?>` → Snackbar + `clearSuccess()`; AutoTag-Switch persistiert über `AppSettingsRepository`.
- **Erfolg Edit-Screens:** meist `_success: StateFlow<Boolean>` → `LaunchedEffect` → `onNavigateBack()`.
- **Aktions-Screens** nutzen `ActionScreenContent` aus `ui/components/`; Dokument-Aktionen via `ScanAction` in `DocumentEditSheet`.
- **PDF öffnen** → `Screen.Viewer`; externer Viewer nur als explizite Aktion im Viewer.
- **Löschen** = Soft-Delete in Papierkorb; endgültige Dateilöschung nur via Purge/Retention.
- PDFs in `context.filesDir/scans/`; FileProvider-Authority: `${applicationId}.fileprovider`.
- Import via `ActivityResultContracts.OpenDocument()` (MIME `application/pdf`); sofort nach `filesDir/scans/` kopiert.
- Share-/Open-Intents (`ACTION_SEND`/`ACTION_SEND_MULTIPLE`/`ACTION_VIEW`) → `AppEntryAction.SharePdf`/`ShareImages` → normale Import-UX.
- Decoder-Reihenfolge: `EXTRA_STREAM` (Liste) → `EXTRA_STREAM` (einzeln) → `ClipData` → `intent.data`; MIME aus Intent, `ClipData.Description`, bei `content://` zusätzlich `ContentResolver.getType(uri)`.
- `ACTION_SEND_MULTIPLE` unterstützt absichtlich nur Bilder; mehrere PDFs werden nicht implizit gemerged.
- Doppelte Dateinamen: `resolveUniqueFilename()` (`_2`, `_3`, …).
- Export: `MediaStore.Downloads` IS_PENDING-Pattern; bei Fehler `resolver.delete()`.
- OCR-TXT-Export: `ExportOcrTextUseCase` schreibt gespeicherten OCR-Text via `DownloadsStorage`; Home-Bulk-Export lädt vollständige Records gezielt per `DocumentRepository.getScansByIds()`, nicht über `ScanListItem`.
- Backup: `allowBackup=false`; `backup_rules.xml` + `data_extraction_rules.xml` schließen `filesDir/scans/` und DB-Dateien aus.
- **OCR:** `OcrPipeline`, Auto-Default, manuelle Sprache möglich, unbundled Modelle via `ModuleInstallClient`.
- **AutoTags:** Scoring-basiertes lokales Keyword-Matching mit vorkompilierten Regexen; Tags als kommaseparierte Keys (`invoice`, `contract`, `insurance`, `certificate`, `bank`, `delivery`). Listenqueries laden nur `tags`, nicht `extracted_text`; automatische Vergabe respektiert `AppSettings.autoTaggingEnabled`.
- **Viewer:** `PdfPageBitmapRenderer` (Mutex, ±1 Seiten rendern); Fit-width-Cache byte-budgetiert; Zoom-Renderings nicht gecacht; `CancellationException` nicht schlucken; `onCleared()` schließt File-Descriptors.

## Mehrfachauswahl

- Checkbox (rechts) → Auswahlmodus; Back/✕ beendet.
- **SelectionTitleBar** (ab 1 Auswahl): ✕ · „X ausgewählt" · SelectAll.
- **BulkActionBar**: Teilen · Ordner · OCR-Menü · Mehr-Menü mit PDF-Export, OCR-TXT-Export, Merge und Löschen (rot).
  - Share: `ACTION_SEND` (1) vs. `ACTION_SEND_MULTIPLE` (mehrere).
  - Delete: `confirm_delete_single` vs. `confirm_delete_multi` — immer mit Dialog.
  - OCR: `ExtractTextUseCase` — Einzel → OCR-Review-Screen; >1 → kombiniertes Result-Sheet.
  - OCR-TXT-Export: nur Dokumente mit gespeichertem OCR-Text werden geschrieben; keine OCR-Nacherkennung beim Export.
  - MakeSearchable: `MakeSearchableUseCase` — überspringt bereits durchsuchbare; Button immer aktiv (Klick ohne Kandidaten → `reportError(searchable_nothing_to_do)`).

## Tests

**Muster:** `UnconfinedTestDispatcher` + reale Workflow-Instanzen mit Fake-`PdfEditor`-Subklassen; `StateFlow.first { !it }` für IO-Synchronisierung.

**JVM-Unit-Tests (`test/`):**
- `domain/usecase/`: Delete/Trash/Restore/Purge/Append/MakeSearchable/ImportFile/AutoTag/RetroTag/ExportOcrText/CreatePdfFromImages
- `domain/workflow/`: alle Workflow-Guards (Merge/Split/Reorder/Rotate/Delete/Extract/Duplicate/PageNumbers/Watermark/Compress/Protect/Unlock/Signature/Searchable/Redact/Highlight/Annotate)
- `ui/`: HomeViewModel, SplitVM, ReorderVM, AppendVM, DocumentEditVM, TrashVM, ImagesToPdfVM, OcrReviewVM, QrScanVM, PdfViewerVM, AnnotateInteractionHelpers, PdfViewportMath
- `ui/entry/`: `AppEntryActionCodecTest`
- `ui/navigation/`: `AppNavigationTest`
- `util/`: PdfEditorTest (buildRanges, resolveUniqueFilename, mapDisplayToPdfCoord), PdfEditorRealIntegrationTest (echte PDFs), BusinessCardParser, VCardBuilder, OcrQuality, PdfPageBitmapCache

**Instrumentation-Tests (`androidTest/`):**
- `ImportAndPdfEditorInstrumentedTest`: PdfRenderer-Pfade, content://-Import, Highlight/Annotate/RemoveTextLayer/Grayscale/Redact
- `SearchableAndRoundTripInstrumentedTest`: SearchablePdfBuilder, MakeSearchable, Export/Import-Roundtrips, ExportAsJpg
- `AppDatabaseMigrationTest`: Room-Migrationen bis v8

**PDF-Teststrategie:** Neue Bugs → Regressionstest in JVM- oder Instrumentation-Schicht; bevorzugt kleines reproduzierbares Fixture statt Fakes.
Testabhängigkeiten: `junit:4.13.2`, `kotlinx-coroutines-test:1.10.1`, `mockito-inline:5.2.0`

## Tech Stack

| Bibliothek | Version |
|---|---|
| Kotlin | 2.2.10 |
| AGP | 9.1.1 |
| KSP | 2.3.2 |
| Hilt | 2.59.2 |
| Room | 2.8.4 |
| Navigation Compose | 2.9.7 |
| ML Kit Document Scanner | 16.0.0 |
| ML Kit Barcode Scanning | 17.3.0 |
| ML Kit Text Recognition | 16.0.1 |
| ML Kit Text (HI/ZH/JA/KO) | 16.0.1 (GMS unbundled) |
| PdfBox-Android | 2.0.27.0 |
| Compose BOM | 2026.03.00 |

Versionen in `gradle/libs.versions.toml`. Gradle-Besonderheiten:
- `android.disallowKotlinSourceSets=false` with `android.suppressUnsupportedOptionWarnings` (KSP + AGP 9), `ksp.workers.max=1` (KSP Worker-Race-Workaround)
- `org.gradle.caching=true`, `org.gradle.parallel=true`, `org.gradle.configuration-cache=true`
- Bei Cache-Problemen: `--no-configuration-cache`

## Schrift & Design

- **DM Sans** (body/title) + **Space Grotesk** (display/headline) via GMS Downloadable Fonts; Zertifikate in `res/values/font_certs.xml`.
- Gradient-Hintergrund: `primaryContainer(18%) → surface(0%) → secondaryContainer(10%)`.
- Adaptives Icon: `ic_launcher_foreground.xml` → `@drawable/app_icon`; legacy `mipmap-*` PNGs aus `app_icon.png`.
