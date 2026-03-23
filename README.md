# PDF Scanner

A clean, privacy-focused Android app for scanning documents to PDF using Google's ML Kit Document Scanner. No cloud upload, no account required — all files stay on the device.

## Features

- **Scan documents** with automatic edge detection and perspective correction (powered by ML Kit)
- **Multi-page PDFs** — photograph multiple pages in one session
- **OCR / searchable PDFs** — extract text from scans or embed an invisible text layer for full-text search in any PDF viewer
- **PDF editing** — merge multiple scans, split a PDF at any page, or reorder pages
- **Archive** — browse, open, share, export, and delete scanned PDFs; multi-select bulk actions
- **Local-only storage** — PDFs are saved to app-internal storage, excluded from cloud backup and device transfer
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
./gradlew test                   # run unit tests (23 tests, JVM-only, no emulator needed)
```

Requires Android Studio Meerkat or newer, JDK 17+.

## Architecture

MVVM + Clean Architecture with Jetpack Compose.

```
domain/usecase/         Use Cases — one responsibility each
                          ImportScanUseCase, ExportScanUseCase, DeleteScansUseCase
                          ExtractTextUseCase, MakeSearchableUseCase
                          MergePdfsUseCase, SplitPdfUseCase, ReorderPagesUseCase

ui/
├── navigation/         AppNavigation — ModalNavigationDrawer + NavHost
├── home/
│   ├── HomeScreen.kt   Coordinator composable (scanner launcher, dialogs, routing)
│   ├── HomeViewModel.kt State coordinator — delegates all work to use cases
│   └── components/     Reusable composables: ScanItem, SelectionTitleBar,
│                         BulkActionBar, EmptyStateContent, ScannerLoadingAnimation,
│                         MergeDialog
├── split/              SplitScreen + SplitViewModel
├── reorder/            ReorderScreen + ReorderViewModel
└── help / info / privacy

data/
├── local/              Room entity (ScanRecord), DAO, AppDatabase (v3, 2 migrations)
└── repository/         ScanRepository — thin DAO wrapper

di/                     Hilt modules
util/                   FileUtil, OcrManager, SearchablePdfBuilder, PdfEditor
```

**Data flow:** UI → ViewModel (state) → Use Case (business logic) → Repository → Room

**Scanner flow:** Triggered via a `Boolean` state in `AppNavigation`, passed to `HomeScreen`, which reacts with `LaunchedEffect`. Avoids holding an Activity reference in the ViewModel.

**OCR / searchable PDFs:** Two-phase process — Phase 1: `PdfRenderer` renders each page to a bitmap (150 DPI), ML Kit OCR extracts text with bounding boxes, bitmap is immediately recycled. Phase 2: PdfBox appends an invisible text layer to the original PDF. Only one bitmap in RAM at a time, safe for large documents. CJK (ZH/JA) is supported for text extraction only; searchable PDF generation is not supported due to TTC/OTC font embedding limitations in PdfBox.

**Storage:** PDFs are saved to `filesDir/scans/<filename>.pdf`. Duplicate filenames resolved with `_2`, `_3`, etc. Sharing uses `FileProvider` (`${applicationId}.fileprovider`). Export writes to `MediaStore.Downloads` with the IS_PENDING pattern.

## Tests

Unit tests run on JVM (no emulator required):

```
util/PdfEditorTest.kt              — buildRanges (7), resolveUniqueFilename (6)
domain/usecase/DeleteScansUseCaseTest.kt    — file deletion, thumbnails, error path (5)
domain/usecase/MakeSearchableUseCaseTest.kt — idempotency, DB updates, missing files (4)
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
