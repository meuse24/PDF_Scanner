# PDF Scanner

A clean, privacy-focused Android app for scanning documents to PDF using Google's ML Kit Document Scanner. No cloud upload, no account required — all files stay on the device.

## Features

### Scanning
- **Scan documents** with automatic edge detection and perspective correction (powered by ML Kit)
- **Multi-page PDFs** — photograph multiple pages in one session
- **OCR / searchable PDFs** — extract text from scans or embed an invisible text layer for full-text search in any PDF viewer

### Archive & Sharing
- Browse, open, share, export, and delete scanned PDFs
- Multi-select bulk actions: share, export to Downloads, merge, OCR, make searchable, delete
- Tap to open in any installed PDF viewer

### PDF Editing — Pages
- **Merge** multiple scans into one PDF
- **Split** a PDF at any page boundary
- **Reorder** pages via drag-and-drop
- **Rotate** individual or all pages (90°, 180°, 270°)
- **Delete pages** — remove selected pages, optionally save as copy
- **Extract pages** — pull selected pages into a new PDF
- **Duplicate pages** — copy selected pages within the document

### PDF Marks
- **Page numbers** — stamp sequential page numbers onto every page
- **Text watermark** — overlay diagonal text across all pages

### PDF Output
- **Compress** — reduce file size (Low / Medium / High preset)
- **Password protect** — encrypt with AES user password
- **Unlock** — remove password from a protected PDF
- **Signature** — draw a freehand signature and stamp it onto any page at adjustable size

### Privacy & Storage
- **Local-only** — PDFs saved to app-internal storage, excluded from cloud backup and device transfer
- **No internet permission, no camera permission** (ML Kit runs as a separate system process)
- **Internationalized** — English (default), German, Spanish, French, Portuguese, Chinese (Simplified), Arabic, Japanese, Russian, Hindi

## Requirements

- Android 10 (API 29) or higher
- Google Play Services (ML Kit Document Scanner + Text Recognition)
- A PDF viewer app to open scanned files (e.g. Adobe Acrobat, Google Drive)

## Build

```bash
./gradlew assembleDebug          # build debug APK
./gradlew installDebug           # build + install on connected device
./gradlew assembleRelease        # build release APK
./gradlew test                   # run unit tests (JVM-only, no emulator needed)
```

Requires Android Studio Meerkat or newer, JDK 21+.

## Architecture

MVVM + Clean Architecture with Jetpack Compose.

```
domain/
├── usecase/        Use Cases — one responsibility each
│                     ImportScan, ExportScan, DeleteScans
│                     ExtractText, MakeSearchable
│                     MergePdfs, SplitPdf, ReorderPages
│                     RotatePages, DeletePdfPages
│                     PageEditUtils (shared thumbnail helper)
└── workflow/       Workflow layer — orchestrates use cases, maps errors
                      to WorkflowResult<T> (Success / Failure)

ui/
├── navigation/         AppNavigation — ModalNavigationDrawer + NavHost
├── components/         Shared composables:
│                         ScanPreviewCard, ActionScreenContent
├── home/
│   ├── HomeScreen.kt   Coordinator (scanner launcher, dialogs, routing)
│   │                   ScanItem.onAction(ScanAction) → navigates to edit screens
│   ├── HomeViewModel.kt State coordinator — delegates to use cases + workflows
│   └── components/     ScanItem (+ ScanAction sealed interface), SelectionTitleBar,
│                         BulkActionBar, EmptyStateContent, ScannerLoadingAnimation,
│                         MergeDialog
├── overlay/            PageNumbersScreen, TextWatermarkScreen
│                         + ScanDetailViewModel (loads ScanRecord by ID)
├── documentaction/     CompressPdfScreen, ProtectPdfScreen, UnlockPdfScreen
├── pageedit/           RotatePagesScreen, DeletePagesScreen,
│                         ExtractPagesScreen, DuplicatePagesScreen
│                         + PageSelectionViewModel
├── split/              SplitScreen + SplitViewModel
├── reorder/            ReorderScreen + ReorderViewModel
├── signature/          SignatureScreen (freehand pad + page/size selector)
└── help / info / privacy

data/
├── local/              Room entity (ScanRecord), DAO, AppDatabase (v3, 2 migrations)
└── repository/         ScanRepository — thin DAO wrapper

di/                     Hilt modules
util/                   FileUtil, OcrManager, SearchablePdfBuilder, PdfEditor
```

**Data flow:** UI → ViewModel (state) → Workflow → Use Case (business logic) → Repository → Room

**Scanner flow:** Triggered via a `Boolean` state in `AppNavigation`, passed to `HomeScreen`, which reacts with `LaunchedEffect`. Avoids holding an Activity reference in the ViewModel.

**Edit screens:** Each edit action navigates to a dedicated screen passing `scanId` as a route argument. `ScanDetailViewModel` or `PageSelectionViewModel` loads the record from the repository. `HomeViewModel` executes the operation via the corresponding workflow and reports success/error back through `StateFlow`s observed in `HomeScreen`.

**Help screen:** `HelpScreen` is data-driven via `HelpSection` + `HelpAction`. The table of contents is rendered as one top-level card, the detail content is rendered as grouped `ActionCard`s below it, and scroll targets are derived from that list structure instead of hard-coded item indices. A floating action button returns the user to the contents after scrolling into the detail area.

**OCR / searchable PDFs:** Two-phase process — Phase 1: `PdfRenderer` renders each page to a bitmap (150 DPI), ML Kit OCR extracts text with bounding boxes, bitmap is immediately recycled. Phase 2: PdfBox appends an invisible text layer to the original PDF. Only one bitmap in RAM at a time, safe for large documents. CJK (ZH/JA) is supported for text extraction only; searchable PDF generation is not supported due to TTC/OTC font embedding limitations in PdfBox.

**Storage:** PDFs are saved to `filesDir/scans/<filename>.pdf`. Duplicate filenames resolved with `_2`, `_3`, etc. Sharing uses `FileProvider` (`${applicationId}.fileprovider`). Export writes to `MediaStore.Downloads` with the IS_PENDING pattern.

## Tests

Unit tests run on JVM (no emulator required):

```
util/PdfEditorTest.kt                          — buildRanges (7), resolveUniqueFilename (6)
domain/usecase/DeleteScansUseCaseTest.kt       — file deletion, thumbnails, error path (5)
domain/usecase/MakeSearchableUseCaseTest.kt    — idempotency, DB updates, missing files (4)
domain/workflow/PageNumbersWorkflowTest.kt     — success path, missing file, DB update
domain/workflow/TextWatermarkWorkflowTest.kt   — success path, blank text validation
```

Test infrastructure uses in-memory fakes (`FakeScanDao`, `FakeSearchablePdfBuilder`) without Mockito or Hilt.

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
- PDFs and the Room database are explicitly excluded from Android Auto Backup and device-to-device transfer (`backup_rules.xml`, `data_extraction_rules.xml`)

## License

MIT License — © 2026 Günther Meusburger

> Architecture, planning, implementation, icon design, and documentation developed in collaboration with [Claude Code](https://claude.ai/code) by Anthropic.
