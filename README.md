# PDF Scanner

A clean, privacy-focused Android app for scanning documents to PDF using Google's ML Kit Document Scanner. No cloud upload, no account required — all files stay on the device.

## Features

### Scanning
- **Add documents** via the `+` action sheet:
  - **Scan document** with automatic edge detection and perspective correction (powered by ML Kit)
  - **Import PDF** from the system file picker; imported files are copied into the app archive
- **Multi-page PDFs** — photograph multiple pages in one session
- **OCR / searchable PDFs** — extract text from scans or embed an invisible text layer for full-text search in any PDF viewer

### Archive & Search
- Browse, open, share, export, and delete archived PDFs
- **Full-text search** across filename and extracted OCR text (Room FTS4)
- **Rename PDF** — give any document a new name directly from the archive; file and thumbnail are renamed atomically
- Multi-select bulk actions: share, export to Downloads, merge, text extraction, OCR / make searchable, delete
- Tap to open in any installed PDF viewer

### PDF Editing — Pages
- **Merge** multiple scans into one PDF
- **Split** a PDF at any page boundary
- **Reorder** pages via drag-and-drop
- **Rotate** individual or all pages (90°, 180°, 270°)
- **Delete pages** — remove selected pages, optionally save as copy
- **Extract pages** — pull selected pages into a new PDF
- **Duplicate pages** — copy selected pages within the document

### PDF Marks & Annotations
- **Annotate (mark / comment)** — full-screen annotation editor with three modes:
  - *Mark mode* — draw freehand yellow highlight strokes; for searchable PDFs, an optional snap mode aligns strokes to detected text lines and converts them to precise highlight rectangles
  - *Write mode* — tap anywhere on the page to place a short text comment; tap an existing comment to edit or delete it; drag the blue anchor dot to reposition a comment
  - *Zoom mode* — pinch-to-zoom and pan for precise placement; zoom factor shown next to the reset button
  - Page navigation with previous/next buttons and page indicator in one row
  - Undo last annotation, clear current page, or reset all marks
  - All annotations (strokes, rectangles, comments) persist across screen rotation
  - Saves as a new annotated PDF copy (suffix `_Annotiert`)
- **Page numbers** — stamp sequential page numbers onto every page
- **Text watermark** — overlay diagonal text across all pages
- **Signature** — draw a freehand signature and stamp it onto any page at adjustable size

### PDF Output & Protection
- **Compress** — reduce file size (Low / Medium / High preset)
- **Password protect** — encrypt with AES user password
- **Unlock** — remove password from a protected PDF
- **Remove text layer** — rebuild the PDF as image-only pages
- **Restrict usage** — disable printing, copying, or editing with an owner password

### Privacy & Storage
- **Local-only** — PDFs saved to app-internal storage; app backup / device transfer is disabled and scans plus Room database files are explicitly excluded in backup rules as defense in depth
- **No internet permission, no camera permission** (ML Kit runs as a separate system process)
- **Internationalized** — English (default), German, Spanish, French, Portuguese, Chinese (Simplified), Arabic, Japanese, Russian, Hindi

## Requirements

- Android 10 (API 29) or higher
- Google Play Services (ML Kit Document Scanner + Text Recognition)
- A PDF viewer app to open scanned files (e.g. Adobe Acrobat, Google Drive)

## Build

```bash
./gradlew :app:compileDebugKotlin # compile Kotlin sources
./gradlew assembleDebug          # build debug APK
./gradlew installDebug           # build + install on connected device
./gradlew assembleRelease        # build release APK
./gradlew test                   # run unit tests (JVM-only, no emulator needed)
./gradlew lint                   # run Android lint
```

Build environment:
- Android Studio Meerkat or newer
- JDK 17 or newer to run Gradle / AGP
- Android SDK Platform 36 with minor API level 1 installed (`compileSdk 36.1`)

Project language/toolchain level:
- Java source/target compatibility: 11
- Kotlin JVM toolchain: 11

In other words: the app code is compiled for Java/Kotlin 11, but the modern Android build stack itself should be run with a newer JDK.

Launch on a connected Android device:

```bash
adb devices
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n info.meuse24.pdf_scanner/.MainActivity
```

## Architecture

MVVM + Clean Architecture with Jetpack Compose.

```
domain/
├── usecase/        Import/export/delete/rename, OCR, searchable PDF, page editing,
│                   highlight (inactive, kept for tests), annotate (AnnotatePdfUseCase),
│                   remove text layer, remove password, restrict usage,
│                   shared page-thumbnail helpers
│                   Data classes: HighlightStroke, HighlightRect, TextLine, TextComment
└── workflow/       Orchestrates use cases and maps failures to WorkflowResult<T>;
                    includes AnnotatePdfWorkflow, HighlightPdfWorkflow

ui/
├── navigation/         AppNavigation + route definitions
│                       Drawer gesture disabled on AnnotateScreen
├── home/               Archive screen, add-document sheet, file import, bulk actions,
│                       search, rename dialog, scan item menus
├── components/         Shared action-screen and preview composables
├── overlay/            Page numbers, text watermark
├── documentaction/     DocumentEditViewModel handles compress, protect, unlock,
│                       highlight, annotate, remove text layer,
│                       remove password, restrict usage
├── annotate/           AnnotateScreen — 3-mode annotation editor
│                       (Mark / Write / Zoom) with zoom-aware drawing,
│                       text comment placement and drag-to-reposition
├── pageedit/           Rotate, delete, extract, duplicate
├── split/              Split screen + view model
├── reorder/            Reorder screen + view model
├── signature/          Freehand signature pad + stamp workflow
├── highlight/          Marker workflow (inactive — no nav route; functionality covered by AnnotateScreen)
└── help / info / privacy

data/
├── local/              Room entity, DAO, FTS table, AppDatabase v5 + migrations
└── repository/         ScanRepository search + persistence wrapper

di/                     Hilt modules
util/                   FileUtil, OcrManager, SearchablePdfBuilder, PdfEditor
```

**Data flow:** UI → ViewModel (state) → Workflow → Use Case (business logic) → Repository → Room

**Add-document flow:** Triggered via a `Boolean` state in `AppNavigation`, passed to `HomeScreen`, which opens an add-document bottom sheet. From there the user either starts the ML Kit scanner or opens the system `OpenDocument` picker for `application/pdf`.

**Import flow:** Existing PDFs are copied into `filesDir/scans/`, validated, checked for encryption, thumbnail-generated when possible, and persisted as normal `ScanRecord` entries. Imported documents then behave exactly like scanned documents in archive search, sorting, sharing, export, and follow-up actions.

**Edit screens:** Each edit action navigates to a dedicated screen passing `scanId` as a route argument. `DocumentEditViewModel` loads the target `ScanRecord` and dispatches to the appropriate workflow, mapping failures through `WorkflowErrorMapper`. Page-oriented edit screens use `PageSelectionViewModel`; split and reorder use their own dedicated view models.

**Annotation workflow:** `AnnotateScreen` renders one PDF page as a bitmap preview and overlays a Compose `Canvas` for drawing. Three interaction modes are managed via `pointerInput` keyed on mode:
- *Mark*: `detectDragGestures` maps touch points inverse from viewport into normalised document coordinates (0–1). For snap mode, finished strokes are matched against extracted `TextLine` objects and converted to precise `HighlightRect` entries.
- *Write*: `awaitEachGesture` with a manual `awaitPointerEvent` loop (avoids touch-slop cancellation for taps). Hit-tests anchor points; short gestures open a comment dialog, long drags reposition the comment.
- *Zoom*: `transformable` + `clampPanOffset`. Zoom factor displayed inline next to the reset button.

All annotation state (`HighlightStroke`, `HighlightRect`, `TextCommentDraft`) is persisted across screen rotation via `rememberSaveable` with custom `listSaver` implementations. `PdfEditor.applyAnnotations()` writes highlight strokes, snap rectangles, and text comments (rotation-aware text matrix for 0°/90°/180°/270° pages) into a new PDF copy.

**Search:** `ScanRecord` stores extracted OCR text. Room FTS4 indexes filename and extracted text for debounced archive search. `AutoTagUseCase` exists in the codebase (with tests) but is no longer invoked; the `tags` column is retained in the database schema and always written as `null`.

**OCR / searchable PDFs:** Two-phase process — Phase 1: `PdfRenderer` renders each page to a bitmap (150 DPI), ML Kit OCR extracts text with bounding boxes, bitmap is immediately recycled. Phase 2: PdfBox appends an invisible text layer to the original PDF. Only one bitmap in RAM at a time, safe for large documents. CJK (ZH/JA) is supported for text extraction only; searchable PDF generation is not supported due to TTC/OTC font embedding limitations in PdfBox.

**Storage:** PDFs are saved to `filesDir/scans/<filename>.pdf`. Duplicate filenames resolved with `_2`, `_3`, etc. Sharing uses `FileProvider` (`${applicationId}.fileprovider`). Export writes to `MediaStore.Downloads` with the IS_PENDING pattern.

## Tests

Unit tests run on JVM (no emulator required):

```
util/PdfEditorTest.kt                          — PDF helper coverage
domain/usecase/DeleteScansUseCaseTest.kt       — file deletion, thumbnails, error paths
domain/usecase/MakeSearchableUseCaseTest.kt    — idempotency, DB updates, missing files
domain/usecase/ImportFileUseCaseTest.kt        — file import, invalid PDF cleanup, encrypted imports
domain/usecase/AutoTagUseCaseTest.kt           — local tagging heuristics incl. false-positive cases
domain/workflow/*.kt                           — merge, split, reorder, rotate, delete, extract,
                                                 duplicate, page numbers, watermark, compress,
                                                 protect, unlock, signature, highlight,
                                                 annotate, searchable
ui/documentaction/DocumentEditViewModelTest.kt — document-edit action dispatch + failure mapping
ui/home/HomeImportFilenameSuggestionTest.kt    — file picker display-name to archive filename mapping
ui/highlight/HighlightScreenMathTest.kt        — zoom/pan math, snap and rect helpers
ui/reorder/ReorderViewModelTest.kt             — reorder state + workflow dispatch
ui/split/SplitViewModelTest.kt                 — split state + workflow dispatch
```

Test infrastructure uses in-memory fakes plus Mockito for Android framework types such as `Bitmap`.

## Tech Stack

| Library | Version |
|---|---|
| Kotlin | 2.2.10 |
| AGP | 9.1.0 |
| Jetpack Compose BOM | 2026.03.00 |
| Navigation Compose | 2.9.7 |
| Hilt | 2.59.2 |
| KSP | 2.2.10-2.0.2 |
| Room | 2.8.4 |
| ML Kit Document Scanner | 16.0.0 |
| ML Kit Text Recognition | 16.0.1 |
| PdfBox-Android | 2.0.27.0 |

All versions managed centrally in `gradle/libs.versions.toml`.

## Privacy

- No internet permission
- No camera permission (ML Kit runs as a separate system process)
- `android:allowBackup="false"` disables Android backup / device-transfer app data export at the manifest level
- `backup_rules.xml` and `data_extraction_rules.xml` still explicitly exclude `filesDir/scans/` plus Room database files (`pdf_scanner_db`, `-wal`, `-shm`, `-journal`) as defense in depth

### Verifying Privacy Claims

The core privacy claims are intentionally easy to audit in the repository:

1. Manifest-level backup disable:
   `app/src/main/AndroidManifest.xml` → `android:allowBackup="false"`
2. Defense-in-depth backup exclusions:
   `app/src/main/res/xml/backup_rules.xml` and `app/src/main/res/xml/data_extraction_rules.xml`
3. Local storage location for PDFs:
   `app/src/main/java/info/meuse24/pdf_scanner/util/StorageProvider.kt` → `filesDir/scans`
4. Local Room database name:
   `app/src/main/java/info/meuse24/pdf_scanner/di/DatabaseModule.kt` → `pdf_scanner_db`
5. Network absence:
   inspect `app/src/main/AndroidManifest.xml` for missing internet permission declarations
6. Export behavior:
   `app/src/main/java/info/meuse24/pdf_scanner/util/DownloadsStorage.kt` writes only when the user explicitly exports to `MediaStore.Downloads`

## License

MIT License — © 2026 Günther Meusburger

> Architecture, planning, implementation, icon design, and documentation developed in collaboration with [Claude Code](https://claude.ai/code) by Anthropic.
