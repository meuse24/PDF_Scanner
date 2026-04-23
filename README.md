# M24 PDF Scanner

Privacy-focused Android app for scanning, viewing, creating, editing, and protecting PDFs locally on the device.

## What it does

- Scan documents to PDF with Google's ML Kit Document Scanner
- Import existing PDFs into the app archive
- View PDFs directly in the app with PdfRenderer, page scrolling, zoom, print, share, export, and external-open fallback
- Create PDFs from gallery images with A4 layouts:
  - 1 image per page
  - 2 images per page
  - 4 images per page
- Search by filename and OCR text
- Scan PDFs for QR codes and inspect URLs, Wi-Fi credentials, and raw payloads locally
- Merge, split, reorder, rotate, extract, duplicate, and delete pages
- Add annotations with marks, rectangles, ovals, text notes, and zoom-aware editing
- Add page numbers, text watermarks, and signatures
- Compress PDFs, protect them with passwords, unlock them, remove text layers, convert to grayscale, and restrict usage
- Securely redact content and optionally rebuild searchability with OCR

## Privacy

- No cloud upload
- No account required
- Files stay in app-internal storage unless you explicitly export them
- No internet permission
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
- `domain/usecase/` business logic
- `domain/workflow/` orchestration and error mapping
- `data/` Room entities, DAO, database, repository
- `util/` PdfEditor, OCR, storage, file helpers

Main editor flows:

- `annotate/` full-screen PDF annotation editor
- `redact/` secure redaction editor
- `imagestopdf/` gallery images to PDF flow
- `viewer/` in-app PDF reader backed by Android PdfRenderer
- `shared/` viewport math and text-snap helpers reused by editors

Recent structure work:

- Added an in-app PdfRenderer viewer with lazy page rendering, local bitmap cache, zoom overlay, and viewer action bar
- `PdfEditor` split into focused ops files for annotations, overlays, redaction, images, and shared core helpers
- `HomeScreen` split into archive content, dialogs, sheets, and small screen models
- String resources split by feature with `strings_annotate.xml` and `strings_images_to_pdf.xml` in every locale
- Legacy `HighlightScreen` removed; active editing now lives in `annotate/` and `redact/`

## Testing

- JVM tests cover use cases, workflows, view models, and `PdfEditor` helpers
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
