# PDF Scanner

A clean, privacy-focused Android app for scanning documents to PDF using Google's ML Kit Document Scanner. No cloud upload, no account required — all files stay on the device.

## Features

- **Scan documents** with automatic edge detection and perspective correction (powered by ML Kit)
- **Multi-page PDFs** — photograph multiple pages in one session, the scanner combines them automatically
- **Archive** — browse, open, share, and delete scanned PDFs
- **Local-only storage** — PDFs are saved to app-internal storage and excluded from cloud backup and device transfer
- **Internationalized** — English (default), German, Spanish, French, Portuguese, Chinese (Simplified), Arabic, Japanese, Russian, Hindi

## Requirements

- Android 10 (API 29) or higher
- Google Play Services (ML Kit Document Scanner)
- A PDF viewer app to open scanned files (e.g. Adobe Acrobat, Google Drive)

## Build

```bash
./gradlew assembleDebug          # build debug APK
./gradlew installDebug           # build + install on connected device
./gradlew assembleRelease        # build release APK
./gradlew test                   # run unit tests
```

Requires Android Studio Hedgehog or newer, JDK 17+.

## Architecture

MVVM + Clean Architecture with Jetpack Compose.

```
ui/
├── navigation/     AppNavigation (ModalNavigationDrawer + NavHost)
├── home/           HomeScreen + HomeViewModel  (archive list, scan trigger)
├── help/           HelpScreen
└── info/           InfoScreen

data/
├── local/          Room entity, DAO, AppDatabase
└── repository/     ScanRepository

di/                 Hilt modules (AppDatabase, ScanDao)
util/               FileUtil (copy ML Kit temp PDF → app-internal storage)
```

**Scanner flow:** The scanner is triggered via a `Boolean` state in `AppNavigation`, passed to `HomeScreen`, which reacts with `LaunchedEffect` and calls `GmsDocumentScanning.getClient(...).getStartScanIntent(activity)`. This avoids holding an Activity reference in the ViewModel.

**Storage:** PDFs are saved to `filesDir/scans/<filename>.pdf`. Duplicate filenames are resolved automatically by appending `_2`, `_3`, etc. Sharing uses `FileProvider` (`${applicationId}.fileprovider`).

## Tech Stack

| Library | Version |
|---|---|
| Kotlin | 2.2.10 |
| AGP | 9.1.0 |
| Jetpack Compose BOM | 2024.09.00 |
| Navigation Compose | 2.9.7 |
| Hilt | 2.59.2 |
| KSP | 2.2.10-2.0.2 |
| Room | 2.8.4 |
| ML Kit Document Scanner | 16.0.0 |

All versions are managed centrally in `gradle/libs.versions.toml`.

## Privacy

- No internet permission
- No camera permission (ML Kit runs as a separate system process)
- PDFs and the Room database are explicitly excluded from Android Auto Backup and device-to-device transfer (`backup_rules.xml`, `data_extraction_rules.xml`)

## License

MIT License — © 2026 Günther Meusburger

> Architecture, planning, implementation, icon design, and documentation developed in collaboration with [Claude Code](https://claude.ai/code) by Anthropic.
