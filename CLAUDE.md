# CLAUDE.md

## Build

```bash
./gradlew assembleDebug          # Debug-APK
./gradlew assembleRelease        # Release-APK
./gradlew installDebug           # Bauen + installieren
./gradlew test                   # Unit-Tests
./gradlew lintDebug              # Android Lint (Debug)
./gradlew clean                  # Bereinigen
```

ADB (Windows): `C:/Users/guent/AppData/Local/Android/Sdk/platform-tools/adb.exe`

## Projekt

**App-Name:** M24 PDF-Scanner (Launcher-Label: „PDF Scan") | **Paket:** `info.meuse24.pdf_scanner` | **Min SDK:** 29 | **Target SDK:** 36

```
domain/
└── usecase/
    ├── ImportScanUseCase.kt       # PDF kopieren + optional Thumbnail + optional OCR-Textlayer
    ├── ExportScanUseCase.kt       # MediaStore.Downloads Export (IS_PENDING-Pattern)
    ├── DeleteScansUseCase.kt      # Dateilöschung + Thumbnail + DB-Delete
    ├── ExtractTextUseCase.kt      # OCR: PdfRenderer alle Seiten + Thumbnail-Fallback (dedupliziert)
    ├── MakeSearchableUseCase.kt   # OCR-Textlayer einfügen; überspringt bereits durchsuchbare Records
    ├── MergePdfsUseCase.kt        # PDFs zusammenführen + Thumbnail + DB-Insert
    ├── SplitPdfUseCase.kt         # PDF aufteilen + Thumbnails + DB-InsertAll
    ├── ReorderPagesUseCase.kt     # Seiten umsortieren; saveAsCopy=true/_Sortiert, false=atomar überschreiben
    ├── RotatePagesUseCase.kt      # Seiten drehen; nutzt thumbnailFile() aus PageEditUtils
    ├── DeletePdfPagesUseCase.kt   # Seiten löschen; nutzt thumbnailFile() aus PageEditUtils
    ├── RemoveTextLayerUseCase.kt  # Seiten per PdfRenderer rendern → LosslessFactory → isSearchable=false
    ├── RemovePasswordUseCase.kt   # removePassword() aufrufen → isSearchable erhalten
    ├── RestrictUsageUseCase.kt    # restrictUsage() aufrufen → isSearchable=false
    ├── HighlightPdfUseCase.kt     # applyHighlight() aufrufen + Thumbnail + DB-Insert → Suffix _Markiert
    ├── HighlightStroke.kt         # data class: points List<Pair<Float,Float>> + pageIndex + strokeWidthFraction
    └── PageEditUtils.kt           # thumbnailFile(): gemeinsame Hilfsfunktion für Seitenbearbeitungs-UseCases

ui/
├── navigation/
│   ├── Screen.kt                  # Route-Definitionen (Ablage, Help, Info, Privacy + alle Edit-Screens)
│   └── AppNavigation.kt           # ModalNavigationDrawer + Scaffold + NavHost + Gradient-Hintergrund
│                                  # Verwaltet scanTrigger + isSelectionMode → FAB ausgeblendet im Auswahlmodus
├── home/
│   ├── HomeScreen.kt              # Koordinator: Scanner-Launcher, Dialoge, Listen-Routing
│   │                              # ScanItem.onAction(ScanAction) → navigiert zu Edit-Screens
│   ├── HomeViewModel.kt           # Archivkern: Liste, Auswahl, Scanner-Trigger, Bulk-Aktionen
│   │                              # _error/_success/_ocrText/_ocrLoading/_ocrProgress/_editLoading (nur Merge)
│   └── components/
│       ├── ScanItem.kt            # Card: Dateiname (maxLines=2, volle Breite) + Row(Thumbnail · Metadaten · Menü · Checkbox)
│       │                          # MoreVert: Hauptmenü (7 Items) + Submenu Seitenstruktur (5) + Submenu Schutz&Passwort (5)
│       │                          # encryption-aware enabled-State: notEncrypted = !record.isEncrypted
│       │                          # ScanAction sealed interface (Split/Reorder/Rotate/…/RemoveTextLayer/RemovePassword/RestrictUsage/Highlight)
│       │                          # onAction: (ScanAction) → Unit
│       ├── SelectionTitleBar.kt   # ✕ · count/total · SelectAll-Icon
│       ├── BulkActionBar.kt       # Share · Export · Merge · OCR · MakeSearchable · Delete (rot)
│       ├── EmptyStateContent.kt   # Leerarchiv-Illustration + Hint-Texte
│       ├── ScannerLoadingAnimation.kt  # Canvas-Animation (Dokument + Scan-Strahl)
│       └── MergeDialog.kt         # Dateiname-Eingabe + Reihenfolge-Vorschau
├── components/
│   ├── ScanPreviewCard.kt         # Dokument-Vorschaukarte (Thumbnail + Dateiname + Seitenzahl)
│   └── ActionScreenContent.kt     # Gemeinsames Layout für Aktions-Screens:
│                                  # Titel · Beschreibung · ScanPreviewCard · Formular-Slot · Bestätigen-Button
├── overlay/
│   └── OverlayActionScreens.kt    # PageNumbersScreen, TextWatermarkScreen — nutzen DocumentEditViewModel
├── documentaction/
│   ├── DocumentEditViewModel.kt   # @HiltViewModel für PageNumbers/Watermark/Compress/Protect/Unlock/Signature/Highlight/RemoveTextLayer/RemovePassword/RestrictUsage
│   │                              # Lädt ScanRecord per scanId, führt Workflows aus,
│   │                              # mappt Fehler über WorkflowErrorMapper; _editLoading/_error/_success
│   │                              # loadHighlightPage(pageIndex): lädt Bitmap → _highlightPageBitmap für HighlightScreen
│   └── DocumentActionScreens.kt   # CompressPdfScreen, ProtectPdfScreen, UnlockPdfScreen, RemovePasswordScreen, RemoveTextLayerScreen, RestrictUsageScreen
├── pageedit/
│   ├── PageSelectionViewModel.kt  # Seiten-Thumbnails, Auswahl, saveAsCopy + Rotate/Delete/Extract/Duplicate-Workflows
│   └── PageActionScreens.kt       # RotatePagesScreen, DeletePagesScreen, ExtractPagesScreen, DuplicatePagesScreen
├── split/
│   ├── SplitViewModel.kt          # Seiten-Thumbnails, Split-Punkte + SplitPdfWorkflow
│   └── SplitScreen.kt
├── reorder/
│   ├── ReorderViewModel.kt        # Seiten-Thumbnails, Reihenfolge + ReorderPagesWorkflow
│   └── ReorderScreen.kt
├── signature/
│   └── SignatureScreen.kt         # Freihand-Zeichen-Pad + Seiten-/Größenauswahl — nutzt DocumentEditViewModel
├── highlight/
│   └── HighlightScreen.kt         # Gelber-Marker-Pad: PDF-Seite als Hintergrund + Canvas-Overlay; Seiten-/Breiten-Auswahl — nutzt DocumentEditViewModel
├── help/HelpScreen.kt             # IHV (secondaryContainer-Card) + Kapitel-Cards; FAB „Zurück zum IHV"
├── info/InfoScreen.kt             # Version dynamisch aus BuildConfig
└── privacy/PrivacyScreen.kt       # 4 Icon-Karten (PhoneAndroid, CloudOff, Shield, Lock)

data/
├── local/
│   ├── ScanRecord.kt              # Room @Entity (id, filename, filepath, thumbnailPath, pageCount, fileSize, isSearchable, isEncrypted)
│   ├── ScanDao.kt                 # getAllScans(): Flow, insert, delete, markSearchable(id, fileSize)
│   └── AppDatabase.kt             # Version 4, "pdf_scanner_db", MIGRATION_1_2 + _2_3 + _3_4
└── repository/ScanRepository.kt

domain/workflow/WorkflowErrorMapper.kt  # @Singleton: ScanWorkflowError → lokalisierter String
domain/workflow/RemoveTextLayerWorkflow.kt  # Prüft: Datei existiert → RemoveTextLayerUseCase
domain/workflow/RemovePasswordWorkflow.kt   # Prüft: Datei existiert + isPdfEncrypted → RemovePasswordUseCase
                                            # fängt PasswordRequiredException → ScanWorkflowError.PasswordRequiredToRemove
domain/workflow/RestrictUsageWorkflow.kt    # Prüft: Datei existiert → RestrictUsageUseCase
                                            # fängt PasswordRequiredException → ScanWorkflowError.PasswordRequiredToRemove
domain/workflow/HighlightPdfWorkflow.kt     # Prüft: Datei existiert + strokes nicht leer + nicht verschlüsselt → HighlightPdfUseCase
di/DatabaseModule.kt               # Hilt: AppDatabase + ScanDao, MIGRATION_1_2 + MIGRATION_2_3 + MIGRATION_3_4
util/FileUtil.kt                   # savePdfFromUri(), saveThumbnailFromUri()
util/OcrManager.kt                 # getRecognizer(languageCode): TextRecognizer — HI GMS-unbundled; ZH/JA nur OCR-Text
util/SearchablePdfBuilder.kt       # open class; makeSearchable(pdfFile, lang, onProgress) — Phase1 PdfRenderer+OCR, Phase2 PdfBox
                                   # ZH/JA NICHT als searchable PDF unterstützt (TTC/OTC-Fonts können nicht eingebettet werden)
util/PdfEditor.kt                  # mergePdfs, splitPdf, reorderPages, rotatePages, deletePages, getPageCount, generateThumbnail
                                   # appendTextWatermark nutzt calculateWatermarkFontSize() (internal, testbar)
                                   # buildRanges() + resolveUniqueFilename() als top-level internal (JVM-testbar)
                                   # removeTextLayer(): Seiten per PdfRenderer → LosslessFactory neu rendern → kein OCR-Text
                                   # removePassword(): PDDocument.load(file, "") → setAllSecurityToBeRemoved; wirft PasswordRequiredException bei echtem Benutzerpasswort
                                   # applyHighlight(input, outputDir, strokes): normalisierte Strokes → PDPageContentStream (APPEND) mit strokingAlpha=0.4; Suffix _Markiert
                                   # restrictUsage(ownerPwd, canPrint, canCopy, canEdit): AccessPermission + StandardProtectionPolicy(ownerPwd, "", ap); Suffix _Eingeschraenkt
                                   # WrongPasswordException + PasswordRequiredException als innere IOException-Subklassen
```

## Architektur-Regeln

- **Schichtenregel:** ViewModel koordiniert State + ruft Use Cases/Workflows auf → Use Cases verarbeiten → Repository persistiert
- **Keine Literal-Strings im Kotlin-Code** — ausschließlich `context.getString(R.string.*)` oder `stringResource()`
- **Neue Strings** immer in alle 10 Locale-Dateien eintragen (values/, -de, -es, -fr, -pt, -zh-rCN, -ar, -ja, -ru, -hi)
- **Fehler in HomeScreen** → `viewModel.reportError(String)` → `_error: StateFlow` → AlertDialog
- **Fehler in Edit-Screens** → eigener `_error: StateFlow<String?>` im jeweiligen ViewModel → AlertDialog im Screen
- **Erfolg in HomeScreen** → `_success: StateFlow<String?>` → Toast + `clearSuccess()`
- **Erfolg in Edit-Screens** → `_success: StateFlow<Boolean>` → `LaunchedEffect` → `onNavigateBack()`
- **OCR** nutzt PdfRenderer über alle Seiten; Fallback auf `thumbnailPath` wenn PDF fehlt
- PDFs in `context.filesDir/scans/`; FileProvider-Authority: `${applicationId}.fileprovider`
- Doppelte Dateinamen: `resolveUniqueFilename()` in `util/PdfEditor.kt` (`_2`, `_3`, …)
- Export: `MediaStore.Downloads` (API 29+), IS_PENDING-Pattern, bei Fehler `resolver.delete()`
- Backup: `backup_rules.xml` + `data_extraction_rules.xml` schließen `filesDir/scans/` und DB aus
- **Aktions-Screens** (Overlay + DocumentAction) nutzen `ActionScreenContent` aus `ui/components/`
- **ScanItem-Aktionen** via `ScanAction` sealed interface — kein direktes Navigieren aus dem Item heraus

## Mehrfachauswahl

- **Checkbox** (rechts an jedem Eintrag) → Auswahlmodus; weiteres Antippen togglet; Back/✕ beendet
- Ausgewählte Cards: `primaryContainer`; keine Einzel-Action-Buttons
- **SelectionTitleBar** (top, erscheint ab 1 Auswahl): ✕ deselektieren · `count/total` · SelectAll-Icon
- **BulkActionBar** (bottom): Share · Export · Merge · OCR (TextSnippet) · MakeSearchable (ManageSearch) · Delete (rot)
  - Share: `ACTION_SEND` (1 Item) vs. `ACTION_SEND_MULTIPLE` (mehrere)
  - Delete: Einzel-Dialog mit Dateiname (`confirm_delete_single`) vs. Bulk-Dialog (`confirm_delete_multi`)
  - OCR: `extractTexts(records, lang)` → `ExtractTextUseCase` — Sprachauswahl-Dialog; `— filename —` Trenner nur bei >1
  - MakeSearchable: `makeSearchableScans(records, lang)` → `MakeSearchableUseCase` — bereits durchsuchbare werden übersprungen
  - MakeSearchable-Button immer aktiv; Klick ohne nicht-durchsuchbare PDF → `reportError(searchable_nothing_to_do)`
  - Params: `extractEnabled` (`Boolean`); `makeSearchableEnabled` immer `true`
- Löschen immer mit Bestätigungs-Dialog

## Tests

```
test/
├── domain/usecase/
│   ├── DeleteScansUseCaseTest.kt           # Dateilöschung, Thumbnail, Fehlerpfad, Mehrfach-Delete
│   ├── MakeSearchableUseCaseTest.kt        # Idempotenz, DB-Updates, fehlende Dateien, Progress
│   ├── FakeScanDao (in DeleteScansUseCaseTest)          # In-Memory ScanDao-Implementierung
│   └── FakeSearchablePdfBuilder (in MakeSearchableUseCaseTest)  # Überschreibt makeSearchable
├── domain/workflow/
│   ├── *WorkflowTest.kt                    # Merge/Split/Reorder/Rotate/Delete/Extract/Duplicate
│   ├── *WorkflowTest.kt                    # PageNumbers/Watermark/Compress/Protect/Unlock/Signature/Searchable
│   └── HighlightPdfWorkflowTest.kt         # leere Strokes, fehlende Datei, verschlüsselt, IO-Fehler, Erfolg (6 Tests)
├── ui/
│   ├── split/SplitViewModelTest.kt         # editLoading-Guard, Success/Failure, clearError (5 Tests)
│   ├── reorder/ReorderViewModelTest.kt     # editLoading-Guard, Success/Failure, clearError (4 Tests)
│   └── documentaction/DocumentEditViewModelTest.kt  # addPageNumbers Success/Failure/Guard/clearError (4 Tests)
└── util/
    └── PdfEditorTest.kt                    # buildRanges (7 Tests) + resolveUniqueFilename (6 Tests)
```

ViewModel-Testmuster: `UnconfinedTestDispatcher` + reale Workflow-Instanzen mit Fake-`PdfEditor`-Subklassen;
`StateFlow.first { !it }` zum Synchronisieren auf IO-Abschluss ohne Timeouts.

Testabhängigkeiten: `junit:4.13.2` + `kotlinx-coroutines-test:1.10.1` + `mockito-inline:5.2.0`

## Tech Stack

| Bibliothek | Version |
|---|---|
| Kotlin | 2.2.10 |
| AGP | 9.1.0 |
| KSP | 2.2.10-2.0.2 |
| Hilt | 2.59.2 |
| Room | 2.8.4 |
| Navigation Compose | 2.9.7 |
| ML Kit Document Scanner | 16.0.0 |
| ML Kit Text Recognition | 16.0.1 |
| ML Kit Text (HI/ZH/JA) | 16.0.1 (GMS unbundled; ZH/JA nur OCR-Text, kein searchable PDF) |
| PdfBox-Android | 2.0.27.0 |
| Compose BOM | 2026.03.00 |
| ui-text-google-fonts | via BOM (1.9.0) |
| kotlinx-coroutines-test | 1.10.1 (testImplementation) |

Versionen zentral in `gradle/libs.versions.toml`. Gradle-Besonderheiten:
- `android.disallowKotlinSourceSets=false` in `gradle.properties` (KSP + AGP 9)
- `buildFeatures { buildConfig = true }` für `BuildConfig.VERSION_NAME` / `VERSION_CODE`
- `org.gradle.caching=true`, `org.gradle.parallel=true`, `org.gradle.configuration-cache=true` aktiv
- `PDFBoxResourceLoader.init(this)` in `PdfScannerApp.onCreate()` erforderlich

## Schrift & Design

- **DM Sans** (body/title) + **Space Grotesk** (display/headline) via GMS Downloadable Fonts (`ui-text-google-fonts`); Zertifikate in `res/values/font_certs.xml`
- Gradient-Hintergrund in `AppNavigation.kt`: `primaryContainer(18%) → surface(0%) → secondaryContainer(10%)`
- Adaptives Icon: `ic_launcher_background.xml` (Indigo→Teal) + `ic_launcher_foreground.xml` (Safe Zone 18–90 im 108dp-Canvas)
