# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build-Befehle

```bash
./gradlew assembleDebug          # Debug-APK bauen
./gradlew assembleRelease        # Release-APK bauen
./gradlew installDebug           # Bauen + auf verbundenem Gerät installieren
./gradlew test                   # Unit-Tests ausführen
./gradlew connectedAndroidTest   # Instrumented Tests (benötigt Gerät/Emulator)
./gradlew clean                  # Build-Artefakte bereinigen
```

ADB-Pfad (Windows): `C:/Users/guent/AppData/Local/Android/Sdk/platform-tools/adb.exe`

## Architektur

**Paket:** `info.meuse24.pdf_scanner` | **Min SDK:** 29 | **Target SDK:** 36

```
ui/
├── navigation/
│   ├── Screen.kt            # Route-Definitionen (Ablage, Help, Info)
│   └── AppNavigation.kt     # ModalNavigationDrawer + Scaffold + NavHost
├── home/
│   ├── HomeScreen.kt        # Scan-Liste / Empty State (kein eigenes Scaffold)
│   └── HomeViewModel.kt     # saveScan, deleteScan, error-State
├── help/HelpScreen.kt       # Anleitung
└── info/InfoScreen.kt       # Copyright, Tech Stack, Bibliotheken, Credits

data/
├── local/
│   ├── ScanRecord.kt        # Room @Entity
│   ├── ScanDao.kt           # getAllScans() : Flow, insert, delete
│   └── AppDatabase.kt       # @Database, Version 1
└── repository/
    └── ScanRepository.kt    # @Singleton, wrapped DAO

di/
└── DatabaseModule.kt        # Hilt @Module: AppDatabase + ScanDao

util/
└── FileUtil.kt              # savePdfFromUri(), getFileProviderUri()

PdfScannerApp.kt             # @HiltAndroidApp
MainActivity.kt              # @AndroidEntryPoint → AppNavigation()
```

### Navigation-Fluss

`AppNavigation` ist das Root-Composable. Es stellt bereit:
- `ModalNavigationDrawer` mit Drawer-Items (Ablage, Scanner starten, Hilfe, Info)
- `Scaffold` mit `TopAppBar` (dynamischer Titel + Hamburger/Back-Icon) und FAB (nur auf Ablage-Screen)
- `NavHost` mit drei Routes: `ablage`, `help`, `info`

Der Scanner-Trigger (`scanTrigger: Boolean`) wird von `AppNavigation` verwaltet und als Parameter an `HomeScreen` übergeben. `HomeScreen` reagiert darauf per `LaunchedEffect` und ruft `GmsDocumentScanning` auf.

### Scanner-Pattern

```kotlin
// In HomeScreen — LaunchedEffect startet den ML Kit Scanner
LaunchedEffect(scanTrigger) {
    if (scanTrigger) {
        val options = GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(true)
            .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_PDF)
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .build()
        GmsDocumentScanning.getClient(options)
            .getStartScanIntent(context as Activity)
            .addOnSuccessListener { intentSender ->
                scanLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
            }
        onScanTriggered() // Flag sofort zurücksetzen
    }
}
```

Ergebnis auslesen: `GmsDocumentScanningResult.fromActivityResultIntent(result.data)?.pdf?.let { pdf -> pdf.uri, pdf.pageCount }`

### FileProvider

PDFs werden in `context.filesDir/scans/` gespeichert. FileProvider-Authority: `${applicationId}.fileprovider` (konfiguriert in `res/xml/file_paths.xml`).

## Tech Stack & Versionen

| Bibliothek | Version |
|---|---|
| Kotlin | 2.2.10 |
| AGP | 9.1.0 |
| KSP | 2.2.10-2.0.2 |
| Hilt | 2.59.2 |
| Room | 2.8.4 |
| Navigation Compose | 2.9.7 |
| Hilt Navigation Compose | 1.3.0 |
| ML Kit Document Scanner | 16.0.0 |
| Compose BOM | 2024.09.00 |

Alle Versionen zentral in `gradle/libs.versions.toml` (Version Catalog).

## Gradle-Besonderheiten

In `gradle.properties` ist `android.disallowKotlinSourceSets=false` gesetzt — notwendig damit KSP-generierte Sources mit AGP 9's eingebautem Kotlin-Support funktionieren.

## App-Icon

Adaptives Icon in `res/drawable/`:
- `ic_launcher_background.xml` — Verlauf Deep Indigo → Teal mit Radial-Shine
- `ic_launcher_foreground.xml` — Weißes fettes „M" + dreischichtiger Neon-Cyan-Scanstrahl + Amber-Punkt
