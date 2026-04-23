# CLAUDE.md

## Build

```bash
./gradlew :app:compileDebugKotlin # Kotlin-Compile-Check
./gradlew assembleDebug          # Debug-APK
./gradlew assembleRelease        # Release-APK
./gradlew installDebug           # Bauen + installieren
./gradlew test                   # Unit-Tests
./gradlew connectedDebugAndroidTest # Alle Instrumentation-Tests auf angeschlossenem Gerät
./gradlew lint                   # Android Lint
./gradlew clean                  # Bereinigen
```

Gezielte PDF-Verifikation:

```bash
./gradlew testDebugUnitTest --tests "info.meuse24.pdf_scanner.util.PdfEditorTest" --tests "info.meuse24.pdf_scanner.util.PdfEditorRealIntegrationTest"
./gradlew --% connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=info.meuse24.pdf_scanner.ImportAndPdfEditorInstrumentedTest
./gradlew --% connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=info.meuse24.pdf_scanner.util.SearchableAndRoundTripInstrumentedTest
```

Build-Umgebung:
- Android Studio Meerkat oder neuer
- JDK 17+ zum Ausführen von Gradle / AGP
- Android SDK Platform 36 mit minor API level 1 (`compileSdk 36.1`)
- Release-Signierung ist optional konfiguriert: `keystore.properties` darf lokal
  vorhanden sein, `assembleRelease` signiert aber nur, wenn die referenzierte
  JKS-Datei existiert. CI-/Debug-Builds laufen ohne privaten Keystore.

Code-Toolchain:
- Java source/target compatibility = 11
- Kotlin JVM toolchain = 11

Wichtig: Das Projekt baut mit einem modernen Android-Build-Stack, kompiliert den App-Code aber weiterhin gezielt auf Java/Kotlin 11.

ADB (Windows): `C:/Users/guent/AppData/Local/Android/Sdk/platform-tools/adb.exe`

```bash
adb devices
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n info.meuse24.pdf_scanner/.MainActivity
```

Hilt-Build-Cache-Workaround bei fehlenden generierten Klassen nach inkrementellen Builds:

```bash
./gradlew installDebug --no-build-cache --rerun-tasks
```

## Projekt

**App-Name:** M24 PDF-Scanner (Launcher-Label: „PDF Scan") | **Paket:** `info.meuse24.pdf_scanner` | **Min SDK:** 29 | **Target SDK:** 36

```
domain/
└── usecase/
    ├── ImportFileUseCase.kt       # Externe PDF per FilePicker importieren: kopieren + validieren + isEncrypted + Thumbnail + DB-Insert
    │                              # Beschädigte PDFs werden auf error_pdf_invalid normalisiert; importierte Artefakte werden bereinigt
    ├── ImportScanUseCase.kt       # PDF kopieren + optional Thumbnail + optional OCR-Textlayer; persistiert OCR-Stats/-Seitentexte
    ├── ExportScanUseCase.kt       # MediaStore.Downloads Export (IS_PENDING-Pattern)
    ├── DeleteScansUseCase.kt      # Dateilöschung + Thumbnail + DB-Delete
    ├── TrashScansUseCase.kt       # Soft-Delete in den Papierkorb; gibt betroffene IDs für Undo zurück
    ├── RestoreScansUseCase.kt     # Stellt Papierkorb-Einträge wieder her; prüft auf fehlende PDF-Dateien
    ├── PurgeTrashUseCase.kt       # Löscht ausgewählte/abgelaufene Papierkorb-Einträge endgültig via DeleteScansUseCase
    ├── ExtractTextUseCase.kt      # OCR: PdfRenderer alle Seiten + Thumbnail-Fallback; liefert OcrDocumentResult(recordId, fullText, pageTexts, stats)
    ├── MakeSearchableUseCase.kt   # OCR-Textlayer einfügen; überspringt bereits durchsuchbare Records; persistiert confidence/language/pageTextJson
    ├── MergePdfsUseCase.kt        # PDFs zusammenführen + Thumbnail + DB-Insert
    ├── SplitPdfUseCase.kt         # PDF aufteilen + Thumbnails + DB-InsertAll
    ├── ReorderPagesUseCase.kt     # Seiten umsortieren; saveAsCopy=true/_Sortiert, false=atomar überschreiben
    ├── RotatePagesUseCase.kt      # Seiten drehen; nutzt thumbnailFile() aus PageEditUtils
    ├── DeletePdfPagesUseCase.kt   # Seiten löschen; nutzt thumbnailFile() aus PageEditUtils
    ├── RemoveTextLayerUseCase.kt  # Seiten per PdfRenderer rendern → LosslessFactory → isSearchable=false
    ├── RemovePasswordUseCase.kt   # removePassword() aufrufen → isSearchable erhalten
    ├── RedactPdfUseCase.kt        # applySecureRedaction() + Thumbnail + DB-Insert; optionale OCR-Rekonstruktion via Workflow
    ├── RestrictUsageUseCase.kt    # restrictUsage() aufrufen → isSearchable=false
    ├── HighlightPdfUseCase.kt     # applyHighlight() für Strokes + Rects + Thumbnail + DB-Insert → Suffix _Markiert
    ├── AnnotatePdfUseCase.kt      # applyAnnotations() für Strokes + Rects + Ovals + TextAnnotations + Thumbnail + DB-Insert → Suffix _Annotiert
    ├── CreatePdfFromImagesUseCase.kt # Bilder via ImagePdfBuilder zu neuem PDF mit Thumbnail + DB-Insert; meldet skippedCount zurück
    ├── AppendToPdfUseCase.kt      # Hängt Scan/PDF/Bilder atomar an bestehendes PDF an; invalidiert Searchable/OCR-Metadaten
    ├── ImagePdfBuilder.kt         # Baut aus Bild-URIs ein PDF oder Temp-PDF ohne DB-Insert
    ├── ImagePageLayout.kt         # SINGLE / TWO_PER_PAGE / FOUR_PER_PAGE
    ├── AnnotationModel.kt         # AnnotationStroke, AnnotationRect, AnnotationOval, AnnotationText, AnnotationShapeStyle + Defaults
    ├── HighlightStroke.kt         # data class: Freihand-Markierung mit points + pageIndex + strokeWidthFraction
    ├── HighlightRect.kt           # data class: textausgerichtetes Highlight-Rechteck (left/top/right/bottom/pageIndex)
    ├── TextLine.kt                # data class: extrahierte Textzeile in normalisierten Anzeige-Koordinaten
    ├── TextComment.kt             # data class: Textkommentar (pageIndex, anchorX, anchorY, text, fontSizeFraction)
    ├── ConvertToGrayscaleUseCase.kt # Seiten per PdfRenderer + ColorMatrix(saturation=0) → LosslessFactory → isSearchable=false → Suffix _SW
    ├── AutoTagUseCase.kt          # On-Device-Tagger (inaktiv — nicht mehr aufgerufen; nur noch für Tests vorhanden)
    └── PageEditUtils.kt           # thumbnailFile(): gemeinsame Hilfsfunktion für Seitenbearbeitungs-UseCases

ui/
├── navigation/
│   ├── Screen.kt                  # Route-Definitionen (Ablage, Viewer, Help, Info, Privacy + alle Edit-Screens)
│   └── AppNavigation.kt           # ModalNavigationDrawer + Scaffold + NavHost + Gradient-Hintergrund
│                                  # Drawer: App-Header (Icon + Name + Version) + Ablage + Hilfe/Info/Datenschutz
│                                  # „Scanner starten" entfernt — FAB ist der primäre Scan-Einstieg
│                                  # gesturesEnabled nur auf Top-Level-Screens (Ablage/Help/Info/Privacy)
│                                  # Verwaltet addActionTrigger + isSelectionMode → FAB ausgeblendet im Auswahlmodus
│                                  # Edge-to-edge gehärtet: Scaffold mit safeDrawing(Horizontal+Bottom),
│                                  # NavHost konsumiert innerPadding; MainActivity ruft enableEdgeToEdge() auf
├── qrscan/
│   ├── QrScanScreen.kt            # Ergebnisliste für QR-Codes: URL/WiFi/Text, Copy/Share, Fortschritt, Fehlerdialog
│   └── QrScanViewModel.kt         # Lädt ScanRecord per scanId und koordiniert den QR-Scan-UseCase
├── viewer/
│   ├── PdfViewerScreen.kt         # In-App-PDF-Viewer: LazyColumn-Seiten, Page-Indicator, Zoom-Overlay, Action-Bar
│   ├── PdfViewerViewModel.kt      # Lädt ScanRecord per scanId, hält PdfRenderer-Handle, rendert sichtbare Seiten ±1, exportiert PDF
│   └── PdfViewerModels.kt         # PdfViewerUiState + PdfViewerPageState, Default-Ratio und Render-Konstanten
├── home/
│   ├── HomeScreen.kt              # Koordinator: Launcher, ViewModel-Wiring, Bulk-Aktionen und Navigation
│   ├── HomeScreenModels.kt        # PendingImport + Dateinamens-/Sortier-Helfer
│   ├── HomeViewModel.kt           # Archivkern: Liste, Auswahl, Import/Scan, Bulk-Aktionen, Suche
│   │                              # _error/_success/_ocrText/_ocrLoading/_ocrProgress/_editLoading (Merge + Dateiimport)
│   │                              # _searchQuery → filteredScans (FTS4 via flatMapLatest+debounce)
│   │                              # renameScan(record, newName): PDF + Thumbnail umbenennen + updateFilenameAndPath
│   │                              # pendingImageUris: temporäre URI-Bridge für Images-to-PDF-Navigation
│   └── components/
│       ├── HomeArchiveContent.kt  # Suchleiste/Sortierung + Archivliste + Empty/Search-Empty-State
│       ├── HomeDialogs.kt         # Delete/Rename/BulkLanguage/Error/Loading-Dialoge
│       ├── HomeSheets.kt          # Add-Document-, OCR-Result- und Save-Import-Sheets/Dialoge
│       ├── ScanItem.kt            # Card: Dateiname (maxLines=2, volle Breite) + Row(Thumbnail · Metadaten · Menü · Checkbox)
│       │                          # MoreVert öffnet ModalBottomSheet (skipPartiallyExpanded=true) statt Dropdown
│       │                          # Sheet-Sektionen: Dokument · Seiten · Bearbeiten · Analysieren & Text · Export & Umwandeln · Schutz
│                          # „Analysieren & Text" zeigt Remove text layer nur wenn isSearchable && !isEncrypted
│       │                          # SheetItem: enabled = alpha 0.38f + clickable(enabled=false); icon FindInPage für Textebene entfernen
│       │                          # onAction: (ScanAction) → Unit; Tags als farbige Badges (tertiaryContainer)
│       ├── SelectionTitleBar.kt   # ✕ · „X ausgewählt" (selection_count) · SelectAll-Icon
│       ├── BulkActionBar.kt       # Icon+Label: Teilen · Export · Merge (MergeType) · Text (TextSnippet) · OCR (FindInPage) · Löschen (rot)
│       ├── EmptyStateContent.kt   # Leerarchiv-Illustration + Hint-Texte + scrollbare Produkt-Card
│       │                          # Empty-State spricht von Dokumenten statt Scans; Card bewirbt PDF-Erstellung,
│       │                          # Beschriften/Bearbeiten/Schützen; Texte in allen 10 Locales verdichtet
│       ├── ScannerLoadingAnimation.kt  # Canvas-Animation (Dokument + Scan-Strahl)
│       └── MergeDialog.kt         # Dateiname-Eingabe + Reihenfolge-Vorschau
├── components/
│   ├── ScanPreviewCard.kt         # Dokument-Vorschaukarte (Thumbnail + Dateiname + Seitenzahl)
│   └── ActionScreenContent.kt     # Gemeinsames Layout für Aktions-Screens:
│                                  # Titel · Beschreibung · ScanPreviewCard · Formular-Slot · Bestätigen-Button
│   └── DocumentEditSheet.kt       # Gemeinsames More-/Bearbeiten-Sheet für Ablage + Viewer; enthält ScanAction
├── overlay/
│   └── OverlayActionScreens.kt    # PageNumbersScreen, TextWatermarkScreen — nutzen DocumentEditViewModel
├── documentaction/
│   ├── DocumentEditViewModel.kt   # @HiltViewModel für PageNumbers/Watermark/Compress/Protect/Unlock/Signature/Highlight/Annotate/Redaction/RemoveTextLayer/RemovePassword/RestrictUsage/Grayscale/PdfMetadata
│   │                              # Lädt ScanRecord per scanId, führt Workflows aus,
│   │                              # mappt Fehler über WorkflowErrorMapper; _editLoading/_error/_success
│   │                              # Seitenvorschau + _textLines; seitenweiser TextLine-Cache für Snap-Modus
│   │                              # applyAnnotations(strokes, rects, ovals, comments) → AnnotatePdfWorkflow
│   │                              # applyRedactions(rects, makeSearchable, languageCode) → RedactPdfWorkflow
│   │                              # convertToGrayscale() → ConvertToGrayscaleWorkflow; loadMetadata() → _metadata: StateFlow<PdfMetadata?>
│   └── DocumentActionScreens.kt   # CompressPdfScreen, ProtectPdfScreen, UnlockPdfScreen, RemovePasswordScreen, RemoveTextLayerScreen, RestrictUsageScreen
│                                  # ConvertToGrayscaleScreen, PdfMetadataScreen (read-only Metadaten-Karte)
├── annotate/
│   ├── AnnotateModels.kt          # Saveable-Modelle, Toolbar-Enums, Farb- und Breitenoptionen
│   ├── AnnotateInteractionHelpers.kt # pure Functions für Hit-Testing, Auswahl, Move, Mutationen, Tap-Defaults
│   ├── AnnotateGestureHandlers.kt # detectMark/Text/ShapeGestures — aus Screen ausgelagert
│   ├── AnnotateCanvasHelpers.kt   # Handle-/Preview-/Canvas-Helfer
│   ├── AnnotateComponents.kt      # Toolbar-, Dropdown-, Attribut- und Footer-Bausteine
│   └── AnnotateScreen.kt          # Vollbild-Beschriften mit klarer Trennung Bearbeiten/Zoom
│                                  # Werkzeuge: Markieren, Text, Rect fill/frame, Oval fill/frame; Zoom als Lupen-Aktion
│                                  # Elemente vor Save selektier-, verschieb-, umfärb-, breitenänderbar und löschbar
│                                  # Auswahl über generischen blauen Handle; Textnotizen zusätzlich editierbar
│                                  # Snap nur im Bearbeiten, Zoom mit eigener reduzierter Toolbar
│                                  # Farbe/Stift als Dropdowns; Undo/Clear icon-basiert; annotate_* in allen 10 Locales
├── append/
│   ├── AppendScreen.kt            # Quellenwahl für zusätzliche Seiten (Scan/Bilder/PDF); Fehlerdialog, Erfolg → Viewer
│   └── AppendViewModel.kt         # Pending-Image-Bridge + AppendWorkflow-Dispatch + lokalisierte Erfolgsmeldung
├── imagestopdf/
│   ├── ImagesToPdfScreen.kt       # Vorschauraster + Dateiname + Layoutwahl (1/2/4 Bilder pro A4-Seite)
│   │                              # A4-Canvas-Vorschau; Erfolg navigiert über HomeViewModel-URI-Bridge zurück
│   └── ImagesToPdfViewModel.kt    # CreatePdfFromImagesUseCase-Dispatch + skippedCount/error/success
├── ocr/
│   ├── OcrReviewScreen.kt         # Einzel-Dokument-OCR-Prüfung mit Sprache, Qualitätsbadge, Re-OCR, Copy/Share
│   ├── OcrReviewViewModel.kt      # Lädt Cache/Backfill, persistiert Re-OCR-Stats und hält Fehler-/Loading-State
│   └── OcrQualityBadge.kt         # Badge für HIGH/MEDIUM/LOW/UNKNOWN in Review und Archivliste
├── redact/
│   ├── RedactModels.kt            # Saver + Konstanten
│   ├── RedactInteractionHelpers.kt # Rechteck-Erzeugung, Min-Area, Draft-/Applied-Farben
│   ├── RedactComponents.kt        # ModeBar, Footer, Save-/Error-/Progress-Dialoge
│   └── RedactScreen.kt            # Vollbild-Sicher-Schwärzen mit Rechteckmodus + Zoom
│                                  # Save-Dialog statt permanentem Hinweis auf dem Hauptscreen
│                                  # optionaler searchable-PDF-Toggle + Sprachwahl erst beim Speichern
│                                  # allRects via rememberSaveable; nur committete Rechtecke werden gespeichert
├── shared/
│   ├── PdfViewportMath.kt         # normalize/map/clamp/zoom-Format für Annotate + Redact
│   └── TextSnapHelpers.kt         # Snap von Freihand-Markierungen auf Textzeilen
├── pageedit/
│   ├── PageSelectionViewModel.kt  # Seiten-Thumbnails, Auswahl, saveAsCopy + Rotate/Delete/Extract/Duplicate-Workflows
│   └── PageActionScreens.kt       # RotatePagesScreen, DeletePagesScreen, ExtractPagesScreen, DuplicatePagesScreen
├── split/
│   ├── SplitViewModel.kt          # Seiten-Thumbnails, Split-Punkte + SplitPdfWorkflow
│   └── SplitScreen.kt
├── reorder/
│   ├── ReorderViewModel.kt        # Seiten-Thumbnails, Reihenfolge + ReorderPagesWorkflow
│   └── ReorderScreen.kt
├── settings/
│   ├── SettingsScreen.kt          # Theme, Default-OCR, Default-Sortierung
│   └── SettingsViewModel.kt       # Persistiert AppSettings
├── signature/
│   └── SignatureScreen.kt         # Freihand-Zeichen-Pad + Seiten-/Größenauswahl — nutzt DocumentEditViewModel
├── trash/
│   ├── TrashScreen.kt             # Papierkorb-Liste mit Restore/Purge/Empty-Flow und 30-Tage-Hinweis
│   └── TrashViewModel.kt          # Beobachtet TrashRepository; Restore/Purge + Auto-Cleanup beim Öffnen
├── highlight/                     # Kein aktiver UI-Screen mehr; nur noch Backend-Highlight-Workflows bleiben erhalten
├── help/HelpScreen.kt             # IHV (secondaryContainer-Card) + Kapitel-Cards; FAB „Zurück zum IHV"
│                                  # Hilfe-Texte decken Dokument hinzufügen, Papierkorb, Seiten anhängen,
│                                  # OCR-Prüfung, In-App-Viewer, Suche/OCR/Highlight-Snap/Privacy-Verhalten ab
├── info/InfoScreen.kt             # Version dynamisch aus BuildConfig; Funktionen/Privacy inkl. Trash, Append, OCR-Review
└── privacy/PrivacyScreen.kt       # Privacy-Übersicht; Texte betonen lokale Speicherung, OCR-Text und Play-Services-Abhängigkeit

data/
├── local/
│   ├── ScanRecord.kt              # Room @Entity inkl. extracted_text, tags, ocr_confidence, ocr_language, ocr_page_text_json, deleted_at
│   ├── ScanRecordFts.kt           # @Fts4(contentEntity = ScanRecord::class) — indiziert filename + extracted_text
│   ├── ScanDao.kt                 # Filtert Ablage auf deleted_at IS NULL; OCR-/Append-Updates via markSearchableWithContent, updateExtractedTextAndOcrStats, invalidateAfterAppend
│   ├── TrashDao.kt                # getTrashedScans(), softDelete(), restore(), findExpiredTrash(), getScansByIds()
│   └── AppDatabase.kt             # Version 7, "pdf_scanner_db", MIGRATION_1_2 + _2_3 + _3_4 + _4_5 + _5_6 + _6_7
│                                  # MIGRATION_4_5: 2× ALTER TABLE, CREATE VIRTUAL TABLE fts4, 4 Trigger, INSERT INTO fts
│                                  # MIGRATION_5_6: deleted_at INTEGER; MIGRATION_6_7: ocr_confidence/ocr_language/ocr_page_text_json
├── repository/ScanRepository.kt   # Ablage-Flow + OCR-/Append-Updates (markSearchableWithContent, updateExtractedTextAndOcrStats, invalidateAfterAppend)
└── repository/TrashRepository.kt  # Papierkorb-Flow + Soft-Delete/Restore/Expired-Lookups

domain/usecase/AutoTagUseCase.kt   # INAKTIV — nicht mehr in ImportScanUseCase/MakeSearchableUseCase eingebunden
                                   # Klasse + Tests (AutoTagUseCaseTest.kt) bleiben erhalten; tags-Spalte bleibt in DB (immer null)
domain/workflow/WorkflowErrorMapper.kt  # @Singleton: ScanWorkflowError → lokalisierter String
domain/workflow/RemoveTextLayerWorkflow.kt  # Prüft: Datei existiert → RemoveTextLayerUseCase
domain/workflow/RemovePasswordWorkflow.kt   # Prüft: Datei existiert + isPdfEncrypted → RemovePasswordUseCase
                                            # fängt PasswordRequiredException → ScanWorkflowError.PasswordRequiredToRemove
domain/workflow/RedactPdfWorkflow.kt        # Prüft: Datei existiert + Rechtecke vorhanden + nicht verschlüsselt → RedactPdfUseCase
                                            # optionaler OCR-Follow-up mit Rollback bei Fehler; unerwartete Folgefehler → RedactionFailed
domain/workflow/RestrictUsageWorkflow.kt    # Prüft: Datei existiert → RestrictUsageUseCase
                                            # fängt PasswordRequiredException → ScanWorkflowError.PasswordRequiredToRemove
domain/workflow/HighlightPdfWorkflow.kt     # Prüft: Datei existiert + (strokes oder rects) + nicht verschlüsselt → HighlightPdfUseCase
domain/workflow/AnnotatePdfWorkflow.kt      # Prüft: Datei existiert + (strokes|rects|ovals|comments nicht leer) + nicht verschlüsselt → AnnotatePdfUseCase
                                            # fängt IOException → StorageWriteFailed; Throwable → AnnotateFailed
docs/privacy-policy.html            # Veröffentlichtes Privacy-Dokument (EN/DE) muss mit In-App-Privacy konsistent bleiben
di/DatabaseModule.kt               # Hilt: AppDatabase + ScanDao + TrashDao, MIGRATION_1_2 + MIGRATION_2_3 + MIGRATION_3_4 + MIGRATION_4_5 + MIGRATION_5_6 + MIGRATION_6_7
util/FileUtil.kt                   # savePdfFromUri(), saveThumbnailFromUri()
util/PdfDocumentIntents.kt         # FileProvider-URI + ACTION_VIEW/ACTION_SEND Helpers für Viewer/Home
util/PdfPageBitmapRenderer.kt      # PdfRenderer-Handle für In-App-Viewer; ein Renderer pro Dokument, Mutex für openPage, OOM-Fallback
util/PdfPageBitmapCache.kt         # ViewModel-gebundener Bitmap-Cache nach Byte-Budget
util/OcrPipeline.kt                # Gemeinsame OCR-Pipeline für Textextraktion/Searchable/Follow-up; Auto-Fallback + Qualitätsstats
util/OcrModelInstaller.kt          # ModuleInstallClient für kontrollierte GMS-Downloads unbundled OCR-Modelle
util/OcrManager.kt                 # getRecognizer(languageCode): TextRecognizer — HI/ZH/JA/KO GMS-unbundled; ZH/JA/KO nur OCR-Text
util/SearchablePdfBuilder.kt       # open class; makeSearchable(pdfFile, lang, onProgress): SearchableResult — Phase1 PdfRenderer+OCR, Phase2 PdfBox
                                   # Rückgabe: extractedText + pageTexts + stats (für lokale DB-Speicherung/OCR-Prüfung)
                                   # ZH/JA/KO NICHT als searchable PDF unterstützt (TTC/OTC-Fonts können nicht eingebettet werden)
util/PdfEditor.kt                  # Öffentliche PDF-API: Merge/Split/Reorder/Rotate/Delete/Thumbnail/Highlight/Annotate/Redaction/Image→PDF
util/PdfEditorCore.kt              # writePdf/editPdf/writeDerivedPdf + page/range helpers
util/PdfEditorAnnotationOps.kt     # Annotation-/Textline-Export-Helfer
util/PdfEditorOverlayOps.kt        # Page numbers, watermark, signature, overlay font helpers
util/PdfEditorRedactionOps.kt      # Secure-redaction rebuild + sanitization helpers
util/PdfEditorImageOps.kt          # A4-Zellenlayout + fitInside für Images-to-PDF
                                   # createPdfFromImages(): A4-Layout mit SINGLE / TWO_PER_PAGE / FOUR_PER_PAGE, Fit-Inside-Zentrierung
                                   # buildRanges() + resolveUniqueFilename() als top-level internal (JVM-testbar)
                                   # removeTextLayer(): Seiten per PdfRenderer → LosslessFactory neu rendern → kein OCR-Text
                                   # convertToGrayscale(): wie removeTextLayer, aber mit ColorMatrix(saturation=0) → Suffix _SW; isSearchable=false
                                   # readMetadata(): PDDocument.load(file, "") → PDDocumentInformation → PdfMetadata; Exception → leere PdfMetadata
                                   # removePassword(): PDDocument.load(file, "") → setAllSecurityToBeRemoved; wirft PasswordRequiredException bei echtem Benutzerpasswort
                                   # extractTextLines(file, pageIndex): PDFTextStripper/TextPosition → TextLine-Liste (Font-Filter + Zeilengruppierung)
                                   #   Koordinaten: yDirAdj = Baseline (Screen-Y, von oben); top = yDirAdj-heightDir, bottom = yDirAdj
                                   # applyHighlight(input, outputDir, strokes, rects): Strokes + gefüllte Rects; Rects zuerst, eigenes Non-Stroking-Alpha
                                   # applyAnnotations(input, outputDir, strokes, rects, ovals, comments): farbige Strokes/Rects/Ovals/Text; Suffix _Annotiert
                                   #   appendAnnotationRect/appendAnnotationOval/appendAnnotationStroke() kapseln Shape-Export
                                   #   appendTextComment(): rotationsawarere Textmatrix (0°/90°/180°/270°); eigener PDPageContentStream nach Highlight-Stream
                                   #   sanitizeCommentText(): entfernt Sonderzeichen, die PDFBox nicht encodieren kann
                                   # applySecureRedaction(): betroffene Seiten per PdfRenderer (300 DPI / PRINT) neu aufbauen,
                                   #   schwarze Boxen einbrennen, Display-CropBox/Rotation in neue Seite uebernehmen,
                                   #   Formulare/Links/Attachments/Actions sowie Dokument-/XMP-Metadaten entfernen
                                   # restrictUsage(ownerPwd, canPrint, canCopy, canEdit): AccessPermission + StandardProtectionPolicy(ownerPwd, "", ap); Suffix _Eingeschraenkt
                                   # WrongPasswordException + PasswordRequiredException als innere IOException-Subklassen
```

## Architektur-Regeln

- **Schichtenregel:** ViewModel koordiniert State + ruft Use Cases/Workflows auf → Use Cases verarbeiten → Repository persistiert
- **Keine Literal-Strings im Kotlin-Code** — ausschließlich `context.getString(R.string.*)` oder `stringResource()`
- **Neue Strings** immer in alle 10 Locale-Dateien eintragen (values/, -de, -es, -fr, -pt, -zh-rCN, -ar, -ja, -ru, -hi)
- Feature-spezifische String-Dateien folgen dem Muster `strings_<feature>.xml`; für QR-Scan liegt das in `strings_qr_scan.xml`
- **Privacy-/Help-/Info-Texte** immer auch gegen `docs/privacy-policy.html` und reale Datenflüsse prüfen
- **Fehler in HomeScreen** → `viewModel.reportError(String)` → `_error: StateFlow` → AlertDialog
- **Fehler in Edit-Screens** → eigener `_error: StateFlow<String?>` im jeweiligen ViewModel → AlertDialog im Screen
- **Erfolg in HomeScreen** → `_success: StateFlow<String?>` → Toast + `clearSuccess()`
- **Erfolg in Edit-Screens** ist screen-spezifisch: oft `_success: StateFlow<Boolean>` → `LaunchedEffect` → `onNavigateBack()`, Append nutzt lokalisierte Erfolgsmeldung + Viewer-Navigation
- **OCR** läuft über `OcrPipeline`; Auto ist Default, manuelle Sprache bleibt möglich, unbundled ML-Kit-Modelle werden bei Bedarf via `ModuleInstallClient` geladen
- **Searchable PDF** nutzt app-eigene Fonts für unterstützte Skripte; ZH/JA/KO sind für OCR-Text erlaubt, aber nicht für Searchable-PDF-Textlayer
- **OCR-Input** nutzt PdfRenderer über alle Seiten; Fallback auf `thumbnailPath` wenn PDF fehlt
- **In-App-Viewer** nutzt `PdfPageBitmapRenderer`; `PdfRenderer.openPage()` ist per Mutex serialisiert, sichtbare Seiten ±1 werden gerendert
- **Viewer-Handle-Cleanup:** `PdfViewerViewModel` hält das Dokument-Handle atomar; `CancellationException` nicht schlucken, `onCleared()` muss File-Descriptors zuverlässig schließen
- **Viewer-Cache:** Fit-width-Bitmaps bleiben im byte-budgetierten Cache; Zoom-Renderings werden quantisiert und nicht in diesen Cache geschrieben
- **Löschen aus der Ablage** ist Soft-Delete in den Papierkorb; endgültige Dateilöschung passiert nur über Purge/Retention-Cleanup
- PDFs in `context.filesDir/scans/`; FileProvider-Authority: `${applicationId}.fileprovider`
- Externe PDF-Importe laufen über `ActivityResultContracts.OpenDocument()` mit MIME `application/pdf`
- Importierte PDFs werden sofort in `filesDir/scans/` kopiert und anschließend wie normale `ScanRecord`s behandelt
- Doppelte Dateinamen: `resolveUniqueFilename()` in `util/PdfEditor.kt` (`_2`, `_3`, …)
- Export: `MediaStore.Downloads` (API 29+), IS_PENDING-Pattern, bei Fehler `resolver.delete()`
- Backup: `android:allowBackup="false"`; zusätzlich schließen `backup_rules.xml` + `data_extraction_rules.xml` `filesDir/scans/` und die DB-Dateien (`pdf_scanner_db`, `-wal`, `-shm`, `-journal`) aus
- **Aktions-Screens** (Overlay + DocumentAction) nutzen `ActionScreenContent` aus `ui/components/`
- **Dokument-Aktionen** via `ScanAction` in `DocumentEditSheet` — kein direktes Navigieren aus `ScanItem`/Sheet heraus
- **PDF öffnen** aus der Ablage navigiert auf `Screen.Viewer`; externer Viewer ist nur noch explizite Aktion im Viewer

## Mehrfachauswahl

- **Checkbox** (rechts an jedem Eintrag) → Auswahlmodus; weiteres Antippen togglet; Back/✕ beendet
- Ausgewählte Cards: `primaryContainer`; keine Einzel-Action-Buttons
- **SelectionTitleBar** (top, erscheint ab 1 Auswahl): ✕ deselektieren · `selection_count` („X ausgewählt") · SelectAll-Icon
- **BulkActionBar** (bottom): Icon+Label-Buttons — Teilen · Export · Merge (MergeType) · Text · OCR (FindInPage) · Löschen (rot)
  - Share: `ACTION_SEND` (1 Item) vs. `ACTION_SEND_MULTIPLE` (mehrere)
  - Delete: Einzel-Dialog mit Dateiname (`confirm_delete_single`) vs. Bulk-Dialog (`confirm_delete_multi`)
  - OCR: `extractTexts(records, lang)` → `ExtractTextUseCase` — Einzel-Dokument navigiert zur OCR-Prüfung; bei >1 bleibt das kombinierte Result-Sheet mit `— filename —`-Trennern
  - MakeSearchable: `makeSearchableScans(records, lang)` → `MakeSearchableUseCase` — bereits durchsuchbare werden übersprungen
  - MakeSearchable-Button immer aktiv; Klick ohne nicht-durchsuchbare PDF → `reportError(searchable_nothing_to_do)`
  - Params: `extractEnabled` (`Boolean`); `makeSearchableEnabled` immer `true`
- Löschen immer mit Bestätigungs-Dialog

## Tests

```
test/
├── domain/usecase/
│   ├── DeleteScansUseCaseTest.kt           # Dateilöschung, Thumbnail, Fehlerpfad, Mehrfach-Delete
│   ├── TrashScansUseCaseTest.kt            # Soft-Delete + Guard gegen ungültige IDs
│   ├── RestoreScansUseCaseTest.kt          # Restore + Missing-File-Fehlerpfad
│   ├── PurgeTrashUseCaseTest.kt            # purgeExpired + purgeSelected
│   ├── AppendToPdfUseCaseTest.kt           # Merge/Images/Encrypted source+target/Original-Integrität
│   ├── MakeSearchableUseCaseTest.kt        # Idempotenz, DB-Updates, fehlende Dateien, Progress
│   ├── ImportFileUseCaseTest.kt            # Dateiimport, Invalid-PDF-Cleanup, verschlüsselte PDFs ohne Thumbnail
│   ├── AutoTagUseCaseTest.kt               # 7 Tests: leer, dt. Invoice, engl. Contract, IBAN, Multi-Tag, irrelevant, sortiert
│   ├── FakeScanDao (in DeleteScansUseCaseTest)          # In-Memory ScanDao-Implementierung
│   └── FakeSearchablePdfBuilder (in MakeSearchableUseCaseTest)  # Überschreibt makeSearchable → gibt SearchableResult zurück
├── domain/workflow/
│   ├── *WorkflowTest.kt                    # Merge/Split/Reorder/Rotate/Delete/Extract/Duplicate
│   ├── *WorkflowTest.kt                    # PageNumbers/Watermark/Compress/Protect/Unlock/Signature/Searchable/Redaction
│   ├── HighlightPdfWorkflowTest.kt         # leer, Rect-only, gemischt, fehlende Datei, verschlüsselt, IO-Fehler, Erfolg
│   └── AnnotatePdfWorkflowTest.kt          # (analog zu Highlight) fehlende Datei, verschlüsselt, keine Annotations, Erfolg
├── ui/
│   ├── split/SplitViewModelTest.kt         # editLoading-Guard, Success/Failure, clearError (5 Tests)
│   ├── reorder/ReorderViewModelTest.kt     # editLoading-Guard, Success/Failure, clearError (4 Tests)
│   ├── append/AppendViewModelTest.kt       # Guard, Success/Failure, clearError, clearSuccess
│   ├── documentaction/DocumentEditViewModelTest.kt  # inkl. applyRedactions Success/Failure + OCR-Parameter
│   ├── trash/TrashViewModelTest.kt         # Flow laden, Restore, Purge, clearError/clearSuccess
│   ├── domain/usecase/CreatePdfFromImagesUseCaseTest.kt # Seitenanzahl, unreadable images, Thumbnail + DB-Insert
│   ├── home/HomeImportFilenameSuggestionTest.kt     # DISPLAY_NAME → Dateinamensvorschlag ohne .pdf
│   ├── home/HomeViewModelTest.kt           # u.a. Undo nach Trash-Restore-Fehler erhalten
│   ├── ui/imagestopdf/ImagesToPdfViewModelTest.kt   # editLoading-Guard, Erfolg, Fehler, clearError
│   ├── ui/ocr/OcrReviewViewModelTest.kt    # Cache/Backfill/Re-OCR/Fehler/clearError
│   ├── ui/qrscan/QrScanViewModelTest.kt             # Scan-Guard, Erfolg/Fehler, verschlüsselte PDFs
│   ├── ui/viewer/PdfViewerViewModelTest.kt          # ScanRecord laden, sichtbare Seiten ±1 rendern, Fehler-Mapping
│   ├── annotate/AnnotateInteractionHelpersTest.kt   # Hit-Testing, Auswahl, Move, Mutationen für Stroke/Rect/Oval/Text
│   └── ui/shared/PdfViewportMathTest.kt             # clampPanOffset + inverse Zoom/Pan-Mathematik + Snap-Hilfsfunktionen
└── util/
    ├── QrCodeScannerTest.kt                 # Resource-close-Helfer für QR-Scanner-Lifecycle
    ├── OcrQualityTest.kt                    # UNKNOWN/HIGH/MEDIUM/LOW inkl. Grenzwerte 0.0/1.0
    ├── PdfPageBitmapCacheTest.kt            # LRU/Byte-Budget und Retain-Window für Viewer-Bitmaps
    ├── PdfEditorTest.kt                    # buildRanges + resolveUniqueFilename + mapDisplayToPdfCoord + mergeTextBoxesToLines
    ├── PdfEditorRealIntegrationTest.kt     # echte PDF-Dateien im JVM-Lauf: protect/unlock, restrict/removePassword, reorder, duplicate/delete, rotate, merge/split
    ├── PdfTestFixtures.kt                  # wiederverwendbare Test-PDF-Erzeugung + Seitensignaturen für Real-PDF-Tests
    └── PdfAssertions.kt                    # Assertions für Seitensignaturen/Rotation/Encryption in Real-PDF-Tests

androidTest/
└── util/
    ├── ImportAndPdfEditorInstrumentedTest.kt
    │   # echte Android-/PdfRenderer-Pfade: pageCount, Thumbnail, renderPageThumbnail
    │   # ImportFileUseCase über content://-URI inkl. invalid PDF cleanup und encrypted import
    │   # applyHighlight/applyAnnotations/removeTextLayer/convertToGrayscale auf gerenderten PDFs
    │   # Annotate: farbige Rects/Ovals/Text + Regression für TWO_PER_PAGE Bild-PDF-Rendering
    │   # sichere Schwärzung inkl. Text-Entfernung, Sanitization, CropBox/Rotation, Metadata/XMP-Cleanup
    └── SearchableAndRoundTripInstrumentedTest.kt
        # SearchablePdfBuilder + MakeSearchableUseCase auf bildbasierten und gemischten mehrseitigen PDFs
        # ExportScanUseCase + ImportFileUseCase-Roundtrips für plain/protected/restricted PDFs via MediaStore.Downloads
        # ImportScanUseCase mit Thumbnail-URI + OCR/Textlayer
        # ExportAsJpgUseCase für mehrseitige PDFs inkl. größerem 6-Seiten-Dokument
```

ViewModel-Testmuster: `UnconfinedTestDispatcher` + reale Workflow-Instanzen mit Fake-`PdfEditor`-Subklassen;
`StateFlow.first { !it }` zum Synchronisieren auf IO-Abschluss ohne Timeouts.

PDF-Teststrategie:
- JVM-Tests decken echte PDF-Dateioperationen in `PdfEditor` ohne Android-UI ab.
- Instrumentation-Tests decken alle Android-spezifischen Pfade ab: `PdfRenderer`, `content://`-Importe, MediaStore-Export, ML-Kit-OCR und renderbasierte Pixel-Checks.
- Neue PDF-Bugs sollten immer als Regressionstest in einer dieser beiden Schichten landen; bevorzugt mit kleinem reproduzierbaren Fixture statt nur mit Fakes.

Testabhängigkeiten: `junit:4.13.2` + `kotlinx-coroutines-test:1.10.1` + `mockito-inline:5.2.0`

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
| ML Kit Text (HI/ZH/JA/KO) | 16.0.1 (GMS unbundled; ZH/JA/KO nur OCR-Text, kein searchable PDF) |
| PdfBox-Android | 2.0.27.0 |
| Compose BOM | 2026.03.00 |
| ui-text-google-fonts | via BOM (1.9.0) |
| kotlinx-coroutines-test | 1.10.1 (testImplementation) |

Versionen zentral in `gradle/libs.versions.toml`. Gradle-Besonderheiten:
- `android.disallowKotlinSourceSets=false` in `gradle.properties` (KSP + AGP 9)
- `ksp.workers.max=1` in `gradle.properties` — defensiver Workaround gegen KSP Worker-Races
- Builds ohne Configuration-Cache stabiler: `--no-configuration-cache` bei Problemen
- `buildFeatures { buildConfig = true }` für `BuildConfig.VERSION_NAME` / `VERSION_CODE`
- App-Release-Stand: `versionName "2.1"`, `versionCode 7`
- `org.gradle.caching=true`, `org.gradle.parallel=true`, `org.gradle.configuration-cache=true` aktiv
- `PDFBoxResourceLoader.init(this)` in `PdfScannerApp.onCreate()` erforderlich

## Schrift & Design

- **DM Sans** (body/title) + **Space Grotesk** (display/headline) via GMS Downloadable Fonts (`ui-text-google-fonts`); Zertifikate in `res/values/font_certs.xml`
- Gradient-Hintergrund in `AppNavigation.kt`: `primaryContainer(18%) → surface(0%) → secondaryContainer(10%)`
- Adaptives Icon: `ic_launcher_foreground.xml` nutzt `@drawable/app_icon`; legacy `mipmap-*` Launcher-PNGs sind aus `app_icon.png` abgeleitet
