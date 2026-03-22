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
│   ├── Screen.kt            # Route-Definitionen (Ablage, Help, Info, Privacy)
│   └── AppNavigation.kt     # ModalNavigationDrawer + Scaffold + NavHost + Gradient-Hintergrund
├── home/
│   ├── HomeScreen.kt        # Scan-Liste / Empty State (kein eigenes Scaffold)
│   └── HomeViewModel.kt     # saveScan, deleteScan, exportScan, extractText (OCR), _success-Flow; @ApplicationContext für Strings
├── help/HelpScreen.kt       # Anleitung
├── info/InfoScreen.kt       # Copyright, Tech Stack, Bibliotheken, Source Code, Credits
└── privacy/PrivacyScreen.kt # Datenschutz-Karten (PhoneAndroid, CloudOff, Shield, Lock)

data/
├── local/
│   ├── ScanRecord.kt        # Room @Entity (inkl. thumbnailPath)
│   ├── ScanDao.kt           # getAllScans() : Flow, insert, delete
│   └── AppDatabase.kt       # @Database, Version 2, Name "pdf_scanner_db"
└── repository/
    └── ScanRepository.kt    # @Singleton, wrapped DAO

di/
└── DatabaseModule.kt        # Hilt @Module: AppDatabase + ScanDao (inkl. MIGRATION_1_2)

util/
└── FileUtil.kt              # savePdfFromUri(), saveThumbnailFromUri()

PdfScannerApp.kt             # @HiltAndroidApp
MainActivity.kt              # @AndroidEntryPoint → AppNavigation()
```

### Navigation-Fluss

`AppNavigation` ist das Root-Composable. Es stellt bereit:
- `ModalNavigationDrawer` mit Drawer-Items (Ablage, Scanner starten, Hilfe, Info, Datenschutz)
- `Scaffold` mit `TopAppBar` (dynamischer Titel + Hamburger/Back-Icon) und FAB (nur auf Ablage-Screen)
- `NavHost` mit vier Routes: `ablage`, `help`, `info`, `privacy`
- Dezenter Gradient-Hintergrund via `Brush.verticalGradient` (primaryContainer → surface → secondaryContainer), gilt für alle Screens

Help, Info und Privacy navigieren mit `popUpTo(Screen.Ablage.route) + launchSingleTop = true`, sodass sie immer direkt über Ablage liegen und sich nicht mehrfach stapeln können.

Der Scanner-Trigger (`scanTrigger: Boolean`) wird von `AppNavigation` verwaltet und als Parameter an `HomeScreen` übergeben. `HomeScreen` reagiert darauf per `LaunchedEffect` und ruft `GmsDocumentScanning` auf.

### Scanner-Pattern

```kotlin
// In HomeScreen — LaunchedEffect startet den ML Kit Scanner
LaunchedEffect(scanTrigger) {
    if (scanTrigger) {
        val options = GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(true)
            .setResultFormats(
                GmsDocumentScannerOptions.RESULT_FORMAT_PDF,
                GmsDocumentScannerOptions.RESULT_FORMAT_JPEG
            )
            .setPageLimit(50)
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .build()
        GmsDocumentScanning.getClient(options)
            .getStartScanIntent(context as Activity)
            .addOnSuccessListener { intentSender ->
                scanLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
            }
            .addOnFailureListener { e ->
                val msg = if (e is MlKitException && e.errorCode == MlKitException.UNSUPPORTED)
                    context.getString(R.string.error_device_unsupported)
                else
                    context.getString(R.string.error_scanner_unavailable)
                viewModel.reportError(msg)
            }
        onScanTriggered() // Flag sofort zurücksetzen
    }
}
```

Ergebnis auslesen: `GmsDocumentScanningResult.fromActivityResultIntent(result.data)` liefert `.pdf` (URI + pageCount) und `.pages` (JPEG-URIs, erstes Element = Thumbnail).

Thumbnail-Anzeige in `ScanItem`: `produceState(Dispatchers.IO)` + zweistufiger `BitmapFactory.decodeFile` mit `inSampleSize` (Ziel 160px), kein externes Image-Loading-Framework.

### FileProvider & Dateispeicherung

PDFs werden in `context.filesDir/scans/` gespeichert. FileProvider-Authority: `${applicationId}.fileprovider` (konfiguriert in `res/xml/file_paths.xml`).

Doppelte Dateinamen werden in `FileUtil.savePdfFromUri()` automatisch aufgelöst (`_2`, `_3`, …). `HomeViewModel` speichert `savedFile.nameWithoutExtension` als `ScanRecord.filename`, damit DB und Dateisystem immer übereinstimmen.

### Fehlerbehandlung & Erfolgs-Feedback

Alle Fehlermeldungen sind lokalisiert (kein Literal-String im Kotlin-Code):
- `FileUtil` verwendet `context.getString(R.string.error_*)` — Context ist per `@ApplicationContext` injiziert
- `HomeViewModel` erhält ebenfalls `@ApplicationContext context: Context` und nutzt `context.getString()`
- UI-Fehler (kein PDF-Viewer, Scanner nicht verfügbar) werden über `viewModel.reportError(String)` geleitet
- Erfolgsmeldungen (z. B. Export) über `_success: MutableStateFlow<String?>` → `LaunchedEffect` in `HomeScreen` zeigt Toast und ruft `clearSuccess()` auf

### Export in Downloads

`HomeViewModel.exportScan()` nutzt `MediaStore.Downloads` (API 29+, keine `WRITE_EXTERNAL_STORAGE`-Permission nötig). IS_PENDING-Pattern: Eintrag anlegen → kopieren → IS_PENDING auf 0 setzen. Bei Fehler wird der unvollständige Eintrag per `resolver.delete()` bereinigt.

### Backup / Privacy

`res/xml/backup_rules.xml` (API < 31) und `res/xml/data_extraction_rules.xml` (API 31+) schließen `filesDir/scans/` und die Room-DB `pdf_scanner_db` explizit von Cloud-Backup und Device-Transfer aus.

## Tech Stack & Versionen

| Bibliothek | Version |
|---|---|
| Kotlin | 2.2.10 |
| AGP | 9.1.0 |
| KSP | 2.2.10-2.0.2 |
| core-ktx | 1.18.0 |
| activity-compose | 1.13.0 |
| Hilt | 2.59.2 |
| Room | 2.8.4 |
| Navigation Compose | 2.9.7 |
| Hilt Navigation Compose | 1.3.0 |
| ML Kit Document Scanner | 16.0.0 |
| ML Kit Text Recognition | 16.0.1 |
| Compose BOM | 2024.09.00 |
| ui-text-google-fonts | (via BOM → 1.9.0) |

Alle Versionen zentral in `gradle/libs.versions.toml` (Version Catalog).

## Gradle-Besonderheiten

In `gradle.properties` ist `android.disallowKotlinSourceSets=false` gesetzt — notwendig damit KSP-generierte Sources mit AGP 9's eingebautem Kotlin-Support funktionieren.

## App-Icon

Adaptives Icon in `res/drawable/`:
- `ic_launcher_background.xml` — Verlauf Deep Indigo → Teal mit Radial-Shine
- `ic_launcher_foreground.xml` — Rotes Dokument mit weißem Falteck, grauen Inhaltslinien, Neon-Cyan-Scanstrahl + Amber-Ursprungspunkt; alles innerhalb der adaptiven Safe Zone (x/y 18–90 im 108dp-Canvas)

## Schrift & Hintergrund

- **DM Sans** via `androidx.compose.ui:ui-text-google-fonts` (Google Fonts API / GMS Downloadable Fonts Provider)
- Zertifikatsdatei: `res/values/font_certs.xml` — enthält `com_google_android_gms_fonts_certs` (dev + prod)
- Alle Typography-Stile in `Type.kt` verwenden `DmSans`-FontFamily (Normal, Medium, SemiBold, Bold)
- Dezenter Gradient-Hintergrund in `AppNavigation.kt`: `primaryContainer(18%) → surface(0%) → secondaryContainer(10%)` — automatisch für alle Screens aktiv

## OCR (Texterkennung)

- `HomeViewModel.extractText(record: ScanRecord)` — on-device via ML Kit Text Recognition (Latein-Modell gebündelt)
- Voraussetzung: `thumbnailPath` im ScanRecord (Fallback: Fehler-String falls null)
- `suspendCancellableCoroutine` für Task-Callback; `_ocrLoading: StateFlow<Boolean>` verhindert Doppelaufruf
- Ergebnis in `_ocrText: StateFlow<String?>`, Reset per `clearOcrText()`
- UI: `ModalBottomSheet` mit `SelectionContainer` + Share-Button + Copy-Button + Toast `ocr_copied`

## Internationalisierung

10 Sprachen: `values/` (EN, Default), `values-de/`, `values-es/`, `values-fr/`, `values-pt/`, `values-zh-rCN/`, `values-ar/`, `values-ja/`, `values-ru/`, `values-hi/`. Alle Fehlermeldungen sind ebenfalls in allen Sprachen vorhanden. Neue Strings immer in alle 10 Dateien eintragen.

## Privacy-Screen

Eigenständiger Screen (`ui/privacy/PrivacyScreen.kt`) mit 4 Icon-Karten:
- `PhoneAndroid` — keine Werbung, lokale Speicherung
- `CloudOff` — keine Serverübertragung
- `Shield` — Google Play Services für Scannen (Diagnose-Daten möglich)
- `Lock` — App sammelt keine Daten, keine Netzwerkberechtigung

Footer mit `privacy_footer`-String (Stand März 2026).

## Info-Screen Abschnitte

Reihenfolge: App-Name/Version → Copyright & License → Tech Stack → Libraries → Source Code → Credits.

- **Credits**: Claude Code (Architektur, Implementierung), OpenAI Codex und Google Gemini CLI (Code-Reviews) — Strings: `info_credits_reviews_intro`, `info_credits_reviews_tools`.
