# M24 PDF Scanner

Privacy-focused Android app for scanning, viewing, creating, editing, and protecting PDFs locally on the device.

## What it does

- Scan documents to PDF with Google's ML Kit Document Scanner
- Import existing PDFs into the app archive
- Start scans from app shortcuts, a Quick Settings tile, or a home-screen widget
- Accept PDFs and images from other Android apps via Share or Open with
- View PDFs directly in the app with PdfRenderer, page scrolling, inline pinch-to-zoom, double-tap zoom toggle, cross-page scroll while zoomed, print, share, export, and external-open fallback
- Search stored OCR text inside the PDF viewer, jump between matching pages, and use locally detected IBAN, amount, and date actions
- Extract OCR text and create searchable PDFs with automatic/manual language selection; all OCR recognizers are delivered as Google Play Services modules, with Latin requested for background preload during app installation and an on-demand fallback when a model is missing
- Review OCR text per page with recognized language, quality badges, copy/share actions, and TXT export to Downloads
- Assign optional on-device automatic document tags from OCR text and filter the archive by invoice, contract, insurance, certificate, bank, or delivery tags
- Create PDFs from gallery images with A4 layouts:
  - 1 image per page
  - 2 images per page
  - 4 images per page
- Append scanned pages, gallery images, or another PDF to an existing document
- Search by filename and stored OCR text
- Organize documents with folders and favorites
- Create and restore encrypted `.m24backup` archives through Android's system file picker
- Scan PDFs for QR codes and inspect URLs, Wi-Fi credentials, and raw payloads locally
- Extract business-card contact data with OCR and export vCard 3.0 files
- Merge, split, reorder, rotate, extract, duplicate, and delete pages
- Recover deleted documents from an in-app trash for 30 days before permanent purge
- Add annotations with marks, rectangles, ovals, text notes, and zoom-aware editing
- Add page numbers, text watermarks, and signatures
- Compress PDFs, protect them with passwords, unlock them, remove text layers, convert to grayscale, and restrict usage
- Export OCR text as an editable Word document (.docx) to Downloads
- Translate PDF text on-device into 10 languages (EN, DE, ES, FR, PT, RU, AR, HI, ZH, JA) using ML Kit Translate with per-page results and copy/share actions; language models (~15–30 MB each) are downloaded on first use and stay on-device
- Securely redact content and optionally rebuild searchability with OCR
- Optionally protect the app UI with Android biometrics or device credentials

## Privacy

- No cloud upload
- No account required
- Files stay in app-internal storage unless you explicitly export them
- Encrypted backups are created only by explicit user action and are protected with the password chosen for that backup
- Backup passwords cannot be recovered; restored documents are stored again in the normal local app archive
- OCR text, OCR quality metadata, and optional automatic document tags are stored locally and can be exported only by explicit user action
- Incoming shared or opened files are copied into the archive only after user confirmation
- App Lock is a local UI gate; it does not encrypt PDFs or the database
- No own backend or document upload; Google Play Services / ML Kit SDKs may declare network permissions for model, compatibility, and diagnostics traffic
- Backup/export of internal app data is disabled

## Encrypted backups

- Backup files use the `.m24backup` extension and the MIME type `application/vnd.info.meuse24.pdf-scanner.backup`.
- The backup payload is encrypted with Tink StreamingAead. The streaming keyset is wrapped with a key derived from the user password via Argon2id.
- The cleartext header contains only format and KDF metadata plus the wrapped keyset. Document names, OCR text, tags, folder names, thumbnails, and PDFs are inside the encrypted payload.
- Per-file checksums detect copy, ZIP, and implementation errors. Cryptographic integrity comes from authenticated encryption, not from the checksums alone.
- Restore is merge-only: documents are added with unique file names; existing documents are not deleted. Re-importing the same backup may create duplicate documents.
- If OCR text was excluded during export, restored documents cannot rebuild the local full-text search index from that backup.
- The exported backup protects the file outside the app sandbox. After restore, files and metadata are stored in the app's normal local archive again.

## Play Console encryption note

The app uses standard cryptography for user-initiated file and backup protection, including password-protected PDFs and encrypted `.m24backup` files. It does not provide a general-purpose cryptographic service; document this as standard file/backup protection in the Play Console encryption declaration.

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
./gradlew --no-configuration-cache assembleRelease
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
- `domain/model/` app/domain models including settings, theme, OCR status/result metadata, and PDF metadata
- `domain/repository/`, `domain/gateway/`, and `domain/pdf/` clean domain contracts
- `domain/common/` pure Kotlin helpers shared by workflows and use cases
- `domain/workflow/` orchestration and error mapping
- `data/` Room entities, DAO, database, repository
- `util/` Android/PdfBox/ML Kit implementations behind domain ports

Main editor flows:

- `annotate/` full-screen PDF annotation editor
- `redact/` secure redaction editor
- `imagestopdf/` gallery images to PDF flow
- `viewer/` in-app PDF reader backed by Android PdfRenderer
- `businesscard/`, `folders/`, `lock/` feature screens
- `shared/` viewport math and text-snap helpers reused by editors

Recent structure work:

- Clean Architecture package boundaries: `domain` no longer imports Android, UI, data, or util implementation types
- Extracted domain gateways for storage, downloads, resource mapping, file access, OCR text extraction, searchable PDF generation, QR scanning, and review-prompt policy
- Moved persistent settings, theme mode, OCR quality/status/result models, and pure page-range/filename helpers into `domain`
- Added app shortcuts, a Quick Settings tile, a home-screen scan widget, and Android Share/Open-with import via a shared `AppEntryAction` bridge
- Added folders, favorites, app-lock settings, business-card vCard export, and Android Print integration
- Added optional OCR-based tags, tag filter chips, retroactive tagging from Settings, and OCR text TXT export
- Added an in-app PdfRenderer viewer with lazy page rendering, local bitmap cache, inline pinch-to-zoom with cross-page scroll, double-tap zoom toggle, debounced zoom re-render, zoom overlay, and viewer action bar
- `PdfEditor` split into focused ops files for annotations, overlays, redaction, images, and shared core helpers
- `HomeScreen` split into archive content, dialogs, sheets, and small screen models
- String resources split by feature with `strings_annotate.xml`, `strings_images_to_pdf.xml`, `strings_shortcuts.xml`, `strings_folders.xml`, `strings_lock.xml`, and `strings_businesscard.xml` in every locale
- Legacy `HighlightScreen` removed; active editing now lives in `annotate/` and `redact/`
- Added on-device PDF text translation via ML Kit Translate with `TextTranslator` domain gateway, `TranslateTextUseCase`, and `TranslationReviewScreen`; models downloaded on demand, no language data bundled in the APK

## Testing

- JVM tests cover AutoTag scoring, the AutoTag settings toggle, retroactive tagging, OCR text TXT export, use cases, workflows, view models, and `PdfEditor` helpers
- JVM tests also cover external app-entry decoding and navigation guards for Share/Open-with flows
- JVM tests cover the encrypted backup codec, ZIP payload/staging validation, merge restore, SAF orchestration use cases, and Backup UI state machine
- Business-card parsing/vCard generation and Room migrations are covered by unit or instrumentation tests where practical
- Viewer JVM tests cover `PdfViewerViewModel` render-window behavior and the bitmap cache
- JVM tests cover `TranslateTextUseCase` (page-text selection, blank-page filtering, fallback to `extractedText`, progress forwarding) and `TranslationReviewViewModel` (load record, translate success/error, progress cleared, duplicate-call guard)
- Instrumentation tests cover Android-specific paths such as `PdfRenderer`, URI import, MediaStore export, annotation rendering, redaction, and image-to-PDF generation

## Tech stack

- Kotlin
- Jetpack Compose
- Hilt
- Room
- ML Kit Document Scanner
- ML Kit Text Recognition
- ML Kit Translate
- PdfBox-Android

Detailed engineering notes live in `CLAUDE.md`.
