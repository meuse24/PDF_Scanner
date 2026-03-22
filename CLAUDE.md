# CLAUDE.md

## Build

```bash
./gradlew assembleDebug          # Debug-APK
./gradlew assembleRelease        # Release-APK
./gradlew installDebug           # Bauen + installieren
./gradlew test                   # Unit-Tests
./gradlew clean                  # Bereinigen
```

ADB (Windows): `C:/Users/guent/AppData/Local/Android/Sdk/platform-tools/adb.exe`

## Projekt

**App-Name:** M24 PDF-Scanner (Launcher-Label: „PDF Scan") | **Paket:** `info.meuse24.pdf_scanner` | **Min SDK:** 29 | **Target SDK:** 36

```
ui/
├── navigation/
│   ├── Screen.kt            # Route-Definitionen (Ablage, Help, Info, Privacy)
│   └── AppNavigation.kt     # ModalNavigationDrawer + Scaffold + NavHost + Gradient-Hintergrund
│                            # Verwaltet scanTrigger + isSelectionMode → FAB ausgeblendet im Auswahlmodus
├── home/
│   ├── HomeScreen.kt        # Scan-Liste, Empty State, Mehrfachauswahl, Bestätigungs-Dialoge
│   └── HomeViewModel.kt     # saveScan, deleteScan, deleteScans, exportScan, extractText, extractTexts (OCR)
│                            # @ApplicationContext; _error/_success/_ocrText/_ocrLoading: StateFlow
├── help/HelpScreen.kt
├── info/InfoScreen.kt       # Version dynamisch aus BuildConfig
└── privacy/PrivacyScreen.kt # 4 Icon-Karten (PhoneAndroid, CloudOff, Shield, Lock)

data/
├── local/
│   ├── ScanRecord.kt        # Room @Entity (id, filename, filepath, thumbnailPath, pageCount, date)
│   ├── ScanDao.kt           # getAllScans(): Flow, insert, delete
│   └── AppDatabase.kt       # Version 2, "pdf_scanner_db"
└── repository/ScanRepository.kt

di/DatabaseModule.kt         # Hilt: AppDatabase + ScanDao, MIGRATION_1_2
util/FileUtil.kt             # savePdfFromUri(), saveThumbnailFromUri()
```

## Architektur-Regeln

- **Keine Literal-Strings im Kotlin-Code** — ausschließlich `context.getString(R.string.*)` oder `stringResource()`
- **Neue Strings** immer in alle 10 Locale-Dateien eintragen (values/, -de, -es, -fr, -pt, -zh-rCN, -ar, -ja, -ru, -hi)
- **Fehler** → `viewModel.reportError(String)` → `_error: StateFlow` → Snackbar/Toast in HomeScreen
- **Erfolg** → `_success: StateFlow<String?>` → Toast + `clearSuccess()`
- **OCR-Fehler** fallback wenn `thumbnailPath == null`
- PDFs in `context.filesDir/scans/`; FileProvider-Authority: `${applicationId}.fileprovider`
- Doppelte Dateinamen: `FileUtil` löst automatisch auf (`_2`, `_3`, …)
- Export: `MediaStore.Downloads` (API 29+), IS_PENDING-Pattern, bei Fehler `resolver.delete()`
- Backup: `backup_rules.xml` + `data_extraction_rules.xml` schließen `filesDir/scans/` und DB aus

## Mehrfachauswahl

- **Checkbox** (rechts an jedem Eintrag) → Auswahlmodus; weiteres Antippen togglet; Back/✕ beendet
- Ausgewählte Cards: `primaryContainer`; keine Einzel-Action-Buttons
- **SelectionTitleBar** (top, erscheint ab 1 Auswahl): ✕ deselektieren · `count/total` · SelectAll-Icon
- **BulkActionBar** (bottom): Share · Export · OCR (TextSnippet) · Delete (rot)
  - Share: `ACTION_SEND` (1 Item) vs. `ACTION_SEND_MULTIPLE` (mehrere)
  - Delete: Einzel-Dialog mit Dateiname (`confirm_delete_single`) vs. Bulk-Dialog (`confirm_delete_multi`)
  - OCR: `extractTexts(records)` — mehrere Records sequenziell, `— filename —` Trenner nur bei >1
- Löschen immer mit Bestätigungs-Dialog

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
| Compose BOM | 2024.09.00 |
| ui-text-google-fonts | via BOM (1.9.0) |

Versionen zentral in `gradle/libs.versions.toml`. Gradle-Besonderheiten:
- `android.disallowKotlinSourceSets=false` in `gradle.properties` (KSP + AGP 9)
- `buildFeatures { buildConfig = true }` für `BuildConfig.VERSION_NAME` / `VERSION_CODE`

## Schrift & Design

- **DM Sans** (body/title) + **Space Grotesk** (display/headline) via GMS Downloadable Fonts (`ui-text-google-fonts`); Zertifikate in `res/values/font_certs.xml`
- Gradient-Hintergrund in `AppNavigation.kt`: `primaryContainer(18%) → surface(0%) → secondaryContainer(10%)`
- Adaptives Icon: `ic_launcher_background.xml` (Indigo→Teal) + `ic_launcher_foreground.xml` (Safe Zone 18–90 im 108dp-Canvas)
