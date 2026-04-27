# M24 PDF Scanner

Privacy-focused Android app for scanning, viewing, creating, editing, and protecting PDFs locally on the device.

## What it does

- Scan documents to PDF with Google's ML Kit Document Scanner
- Import existing PDFs into the app archive
- Start scans from app shortcuts, a Quick Settings tile, or a home-screen widget
- Accept PDFs and images from other Android apps via Share or Open with
- View PDFs directly in the app with PdfRenderer, page scrolling, zoom, print, share, export, and external-open fallback
- Extract OCR text and create searchable PDFs with automatic/manual language selection and on-demand ML Kit model downloads
- Review OCR text per page with recognized language, quality badges, copy/share actions, and TXT export to Downloads
- Assign optional on-device automatic document tags from OCR text and filter the archive by invoice, contract, insurance, certificate, bank, or delivery tags
- Create PDFs from gallery images with A4 layouts:
  - 1 image per page
  - 2 images per page
  - 4 images per page
- Append scanned pages, gallery images, or another PDF to an existing document
- Search by filename and stored OCR text
- Organize documents with folders and favorites
- Scan PDFs for QR codes and inspect URLs, Wi-Fi credentials, and raw payloads locally
- Extract business-card contact data with OCR and export vCard 3.0 files
- Merge, split, reorder, rotate, extract, duplicate, and delete pages
- Recover deleted documents from an in-app trash for 30 days before permanent purge
- Add annotations with marks, rectangles, ovals, text notes, and zoom-aware editing
- Add page numbers, text watermarks, and signatures
- Compress PDFs, protect them with passwords, unlock them, remove text layers, convert to grayscale, and restrict usage
- Securely redact content and optionally rebuild searchability with OCR
- Optionally protect the app UI with Android biometrics or device credentials

## Privacy

- No cloud upload
- No account required
- Files stay in app-internal storage unless you explicitly export them
- OCR text, OCR quality metadata, and optional automatic document tags are stored locally and can be exported only by explicit user action
- Incoming shared or opened files are copied into the archive only after user confirmation
- App Lock is a local UI gate; it does not encrypt PDFs or the database
- No own backend or document upload; Google Play Services / ML Kit SDKs may declare network permissions for model, compatibility, and diagnostics traffic
- Backup/export of internal app data is disabled

## Requirements

- Android 10+ (API 29)
- Google Play Services for ML Kit scanning and OCR
- Android SDK Platform 36.1 for building

## Build

```bash
./gradlew --no-configuration-cache compileDebugKotlin
./gradlew --no-configuration-cache testDebugUnitTest
./gradlew --no-configuration-cache lint
./gradlew --no-configuration-cache assembleDebug
./gradlew --no-configuration-cache bundleRelease
```

Useful Android verification:

```bash
./gradlew --no-configuration-cache connectedDebugAndroidTest
./gradlew --no-configuration-cache --% connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=info.meuse24.pdf_scanner.ImportAndPdfEditorInstrumentedTest
```

If Hilt-generated classes go missing after an incremental install, force a fresh install without the Gradle build cache:

```bash
./gradlew installDebug --no-build-cache --rerun-tasks
```

## Project shape

- `ui/` Jetpack Compose screens and view models
- `ui/entry/`, `ui/tile/`, and `ui/widget/` external app-entry bridges
- `domain/usecase/` business logic
- `domain/model/` and `domain/repository/` clean domain contracts
- `domain/workflow/` orchestration and error mapping
- `data/` Room entities, DAO, database, repository
- `util/` PdfEditor, OCR, storage, file helpers

Main editor flows:

- `annotate/` full-screen PDF annotation editor
- `redact/` secure redaction editor
- `imagestopdf/` gallery images to PDF flow
- `viewer/` in-app PDF reader backed by Android PdfRenderer
- `businesscard/`, `folders/`, `lock/` feature screens
- `shared/` viewport math and text-snap helpers reused by editors

Recent structure work:

- Added app shortcuts, a Quick Settings tile, a home-screen scan widget, and Android Share/Open-with import via a shared `AppEntryAction` bridge
- Added folders, favorites, app-lock settings, business-card vCard export, and Android Print integration
- Added optional OCR-based tags, tag filter chips, retroactive tagging from Settings, and OCR text TXT export
- Added an in-app PdfRenderer viewer with lazy page rendering, local bitmap cache, zoom overlay, and viewer action bar
- `PdfEditor` split into focused ops files for annotations, overlays, redaction, images, and shared core helpers
- `HomeScreen` split into archive content, dialogs, sheets, and small screen models
- String resources split by feature with `strings_annotate.xml`, `strings_images_to_pdf.xml`, `strings_shortcuts.xml`, `strings_folders.xml`, `strings_lock.xml`, and `strings_businesscard.xml` in every locale
- Legacy `HighlightScreen` removed; active editing now lives in `annotate/` and `redact/`

## Testing

- JVM tests cover AutoTag scoring, the AutoTag settings toggle, retroactive tagging, OCR text TXT export, use cases, workflows, view models, and `PdfEditor` helpers
- JVM tests also cover external app-entry decoding and navigation guards for Share/Open-with flows
- Business-card parsing/vCard generation and Room migrations are covered by unit or instrumentation tests where practical
- Viewer JVM tests cover `PdfViewerViewModel` render-window behavior and the bitmap cache
- Instrumentation tests cover Android-specific paths such as `PdfRenderer`, URI import, MediaStore export, annotation rendering, redaction, and image-to-PDF generation

## Tech stack

- Kotlin
- Jetpack Compose
- Hilt
- Room
- ML Kit Document Scanner
- ML Kit Text Recognition
- PdfBox-Android

Detailed engineering notes live in `CLAUDE.md`.
