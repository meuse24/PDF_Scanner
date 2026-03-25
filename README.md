# PDF Scanner

A clean, privacy-focused Android app for scanning documents to PDF using Google's ML Kit Document Scanner. No cloud upload, no account required — all files stay on the device.

## Features

### Scanning
- **Scan documents** with automatic edge detection and perspective correction (powered by ML Kit)
- **Multi-page PDFs** — photograph multiple pages in one session
- **OCR / searchable PDFs** — extract text from scans or embed an invisible text layer for full-text search in any PDF viewer

### Archive & Sharing
- Browse, open, share, export, and delete scanned PDFs
- Full-text search across filename and extracted OCR text
- Automatic on-device tagging for common document types such as invoices, contracts, bank documents, and certificates
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
- **Highlight** — draw yellow marker strokes on any page with zoom/pan support and adjustable marker width

### PDF Output
- **Compress** — reduce file size (Low / Medium / High preset)
- **Password protect** — encrypt with AES user password
- **Unlock** — remove password from a protected PDF
- **Signature** — draw a freehand signature and stamp it onto any page at adjustable size
- **Remove text layer** — rebuild the PDF as image-only pages
- **Restrict usage** — disable printing, copying, or editing with an owner password

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
./gradlew :app:compileDebugKotlin # compile Kotlin sources
./gradlew assembleDebug          # build debug APK
./gradlew installDebug           # build + install on connected device
./gradlew assembleRelease        # build release APK
./gradlew test                   # run unit tests (JVM-only, no emulator needed)
./gradlew lint                   # run Android lint
```

Requires Android Studio Meerkat or newer, JDK 11+.

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
├── usecase/        Import/export/delete, OCR, searchable PDF, page editing,
│                   highlight, remove text layer, remove password, restrict usage,
│                   auto-tagging, shared page-thumbnail helpers
└── workflow/       Orchestrates use cases and maps failures to WorkflowResult<T>

ui/
├── navigation/         AppNavigation + route definitions
├── home/               Archive screen, bulk actions, search, tags, scan item menus
├── components/         Shared action-screen and preview composables
├── overlay/            Page numbers, text watermark
├── documentaction/     Compress, protect, unlock, highlight,
│                       remove text layer, remove password, restrict usage
├── pageedit/           Rotate, delete, extract, duplicate
├── split/              Split screen + view model
├── reorder/            Reorder screen + view model
├── signature/          Freehand signature pad + stamp workflow
├── highlight/          Marker workflow with zoom/pan-aware drawing
└── help / info / privacy

data/
├── local/              Room entity, DAO, FTS table, AppDatabase v5 + migrations
└── repository/         ScanRepository search + persistence wrapper

di/                     Hilt modules
util/                   FileUtil, OcrManager, SearchablePdfBuilder, PdfEditor
```

**Data flow:** UI → ViewModel (state) → Workflow → Use Case (business logic) → Repository → Room

**Scanner flow:** Triggered via a `Boolean` state in `AppNavigation`, passed to `HomeScreen`, which reacts with `LaunchedEffect`. Avoids holding an Activity reference in the ViewModel.

**Edit screens:** Each edit action navigates to a dedicated screen passing `scanId` as a route argument. `DocumentEditViewModel` loads the target `ScanRecord` for page numbers, text watermark, compress, protect, unlock, highlight, remove text layer, remove password, restrict usage, and signature flows and maps workflow failures through `WorkflowErrorMapper`. Page-oriented edit screens use `PageSelectionViewModel`; split and reorder use their dedicated view models.

**Search & tags:** `ScanRecord` stores extracted OCR text and comma-separated tag keys. Room FTS4 indexes filename and extracted text for debounced archive search, while `AutoTagUseCase` derives tags locally from OCR content and heuristics such as IBAN detection.

**Highlight workflow:** `HighlightScreen` renders one PDF page as a bitmap preview and draws a yellow canvas overlay on top. Zooming and panning are handled through Compose transforms, while draw gestures are inverse-mapped from the viewport back into document coordinates so highlighted strokes stay aligned even when the page is zoomed.

**Help screen:** `HelpScreen` is data-driven via `HelpSection` + `HelpAction`. The table of contents is rendered as one top-level card, the detail content is rendered as grouped `ActionCard`s below it, and scroll targets are derived from that list structure instead of hard-coded item indices. A floating action button returns the user to the contents after scrolling into the detail area.

**OCR / searchable PDFs:** Two-phase process — Phase 1: `PdfRenderer` renders each page to a bitmap (150 DPI), ML Kit OCR extracts text with bounding boxes, bitmap is immediately recycled. Phase 2: PdfBox appends an invisible text layer to the original PDF. Only one bitmap in RAM at a time, safe for large documents. CJK (ZH/JA) is supported for text extraction only; searchable PDF generation is not supported due to TTC/OTC font embedding limitations in PdfBox.

**Storage:** PDFs are saved to `filesDir/scans/<filename>.pdf`. Duplicate filenames resolved with `_2`, `_3`, etc. Sharing uses `FileProvider` (`${applicationId}.fileprovider`). Export writes to `MediaStore.Downloads` with the IS_PENDING pattern.

## Tests

Unit tests run on JVM (no emulator required):

```
util/PdfEditorTest.kt                          — PDF helper coverage
domain/usecase/DeleteScansUseCaseTest.kt       — file deletion, thumbnails, error paths
domain/usecase/MakeSearchableUseCaseTest.kt    — idempotency, DB updates, missing files
domain/usecase/AutoTagUseCaseTest.kt           — local tagging heuristics
domain/workflow/*.kt                           — merge, split, reorder, rotate, delete, extract,
                                                 duplicate, page numbers, watermark, compress,
                                                 protect, unlock, signature, highlight, searchable
ui/documentaction/DocumentEditViewModelTest.kt — document-edit action dispatch + failure mapping
ui/highlight/HighlightScreenMathTest.kt        — zoom/pan math for highlight drawing
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
- PDFs and the Room database are explicitly excluded from Android Auto Backup and device-to-device transfer (`backup_rules.xml`, `data_extraction_rules.xml`)

## License

MIT License — © 2026 Günther Meusburger

> Architecture, planning, implementation, icon design, and documentation developed in collaboration with [Claude Code](https://claude.ai/code) by Anthropic.
