# Plan: QR-Code Erkennung und Auswertung in PDFs

Dieses Dokument beschreibt den Plan zur Implementierung eines neuen Features für die PDF Scanner App: Das Erkennen und Auswerten von QR-Codes innerhalb einer PDF-Datei.

## 1. Übersicht

Nutzer sollen die Möglichkeit haben, eine PDF-Datei auf QR-Codes hin zu untersuchen. Die gefundenen Ergebnisse (Texte, URLs, WLAN-Zugangsdaten etc.) werden übersichtlich dargestellt. Von dort aus können sie in die Zwischenablage kopiert oder geteilt werden. Links in den Ergebnissen sollen direkt anklickbar sein.

---

## 2. Technische Komponenten

### 2.1 Bibliothek für QR-Codes

Wir verwenden **Google ML Kit Barcode Scanning** (bundled).

- **Vorteil**: Beste Performance unter Android, unterstützt strukturierte Daten (URL, WiFi, Kontakt usw.), ist bereits im Ökosystem der App (ML Kit Text Recognition) vorhanden.
- **Bundled vs. unbundled**: Die Bundled-Variante wird gewählt — das Modell ist kompakt genug (< 300 KB), es entfällt eine GMS-Download-Abhängigkeit zur Laufzeit. Anders als ML Kit Text Recognition für HI/ZH/JA gibt es keinen Grund für GMS-unbundled.
- **Dependency**: `com.google.mlkit:barcode-scanning:17.3.0`

### 2.2 PDF-Verarbeitung

Da ML Kit Bitmaps benötigt, müssen die PDF-Seiten gerendert werden.

- Wir nutzen `PdfRenderer` (Android API), analog zur bestehenden Thumbnail-Logik in `PdfEditor`.
- Jede Seite wird als Bitmap gerendert (96–150 DPI — QR-Codes sind auch auf niedriger Auflösung lesbar, spart Speicher), dann an den `BarcodeScanner` übergeben und sofort mit `bitmap.recycle()` freigegeben.
- Verschlüsselte PDFs (`record.isEncrypted == true`) können von `PdfRenderer` nicht geöffnet werden → Aktion im Sheet deaktivieren.

### 2.3 Datenmodell

```kotlin
// domain/usecase/QrCodeResult.kt
data class QrCodeResult(
    val rawValue: String,       // Rohinhalt des QR-Codes
    val valueType: Int,         // ML Kit Barcode.TYPE_* (URL, WIFI, TEXT, …)
    val displayUrl: String?,    // nur bei TYPE_URL: barcode.url?.url
    val wifiSsid: String?,      // nur bei TYPE_WIFI: barcode.wifi?.ssid
    val pageNumber: Int         // 1-basiert, für Anzeige
)
```

**Begründung für Aufteilung**: `rawValue` ist der universelle Fallback für alle Typen; strukturierte Felder (`displayUrl`, `wifiSsid`) erlauben spezifisches UI-Rendering ohne `when`-Kaskaden im Screen. Nicht benötigte Felder bleiben `null`. Erweiterung für weitere Typen (EMAIL, PHONE) ist ohne Breaking Change möglich.

Ablage: `domain/usecase/QrCodeResult.kt` — analog zu `AnnotationModel.kt`, `HighlightRect.kt`.

---

## 3. Architektur-Schichten

Das Projekt folgt einer **pragmatischen Schichten-Architektur** — keine strikte Clean Architecture. Konkret bedeutet das für dieses Feature:

- **Keine Interfaces**: `QrCodeScanner` und `ScanRepository` werden als konkrete Klassen direkt injiziert, analog zu `SearchablePdfBuilder`, `PdfEditor` und `ScanRepository` im Rest der App.
- **`ScanRecord` direkt verwenden**: Die Room-Entity `ScanRecord` aus `data/local/` wird in UseCase und ViewModel ohne Mapping-Schicht verwendet — so wie in allen anderen UseCases (`DeleteScansUseCase`, `ImportScanUseCase` usw.).
- **Android-Imports im UseCase sind akzeptabel**: `android.net.Uri` findet sich bereits in `ImportScanUseCase`. Für diesen UseCase: `File(record.filepath)` reicht aus, kein Uri-Typ nötig.
- **Schichtenregel**: ViewModel ruft UseCase auf, UseCase delegiert an `util`. Kein direkter Zugriff des ViewModels auf `QrCodeScanner`.

```
ui/qrscan/
├── QrScanScreen.kt
└── QrScanViewModel.kt

domain/usecase/
├── QrCodeResult.kt          ← Datenmodell (wie AnnotationModel.kt, HighlightRect.kt)
└── ScanQrCodesUseCase.kt    ← dünne Koordinationsschicht

util/
└── QrCodeScanner.kt         ← open class, enthält PdfRenderer + ML Kit (wie SearchablePdfBuilder)
```

---

## 4. Umsetzungsschritte

### Schritt 1: Konfiguration & Dependencies

- Neuer Versionseintrag in `gradle/libs.versions.toml`:
  ```toml
  [versions]
  mlkitBarcode = "17.3.0"

  [libraries]
  mlkit-barcode-scanning = { group = "com.google.mlkit", name = "barcode-scanning", version.ref = "mlkitBarcode" }
  ```
- Hinzufügen in `app/build.gradle.kts`:
  ```kotlin
  implementation(libs.mlkit.barcode.scanning)
  ```

### Schritt 2: ML Kit Wrapper (`util`)

`util/QrCodeScanner.kt` — `open class` für Testbarkeit, **analog zu `SearchablePdfBuilder`** (handhabt die vollständige PDF→Ergebnis-Operation, nicht nur eine einzelne Seite):

```kotlin
open class QrCodeScanner @Inject constructor() {
    open suspend fun scan(
        pdfFile: File,
        onProgress: (page: Int, total: Int) -> Unit = { _, _ -> }
    ): List<QrCodeResult>
}
```

Intern:
- Öffnet `PdfRenderer(ParcelFileDescriptor.open(pdfFile, MODE_READ_ONLY))`.
- **Format**: `BarcodeScanning.getClient(BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build())` — ausschließlich QR-Code.
- **Render-Auflösung**: **96 DPI** → A4-Seite ca. 794 × 1123 px, ~3,4 MB ARGB_8888. Geeignet für Dokument-QR-Codes ≥ 1 cm; kein adaptiver Zweifach-Scan.
- Iteriert alle Seiten: Bitmap rendern (96 DPI) → ML Kit `BarcodeScanner.process()` via `suspendCancellableCoroutine` → `bitmap.recycle()` → `onProgress` aufrufen.
- `BarcodeScanner.close()` + `PdfRenderer.close()` in `finally`-Block.
- Die ML Kit-Task läuft auf eigenen Threads; kein `withContext` nötig.
- Gibt `List<QrCodeResult>` direkt zurück — kein `Barcode`-Typ nach außen; die Konvertierung zu `QrCodeResult` findet hier statt.

Diese Struktur ist identisch zum `SearchablePdfBuilder`-Muster: die gesamte Android/Framework-Logik bleibt in `util`, der UseCase bleibt dünn und JVM-testbar.

### Schritt 3: UseCase (`domain/usecase`)

`domain/usecase/ScanQrCodesUseCase.kt` — **dünne Koordinationsschicht**, analog zu `MakeSearchableUseCase` gegenüber `SearchablePdfBuilder`:

```kotlin
class ScanQrCodesUseCase @Inject constructor(
    private val scanner: QrCodeScanner
) {
    suspend operator fun invoke(
        record: ScanRecord,
        onProgress: (page: Int, total: Int) -> Unit = { _, _ -> }
    ): List<QrCodeResult> {
        if (record.isEncrypted) return emptyList()
        val file = File(record.filepath)
        if (!file.exists()) return emptyList()
        return scanner.scan(file, onProgress)
    }
}
```

- Kein `StorageProvider`, kein `DispatcherProvider` — nicht nötig; `File(record.filepath)` reicht, wie in `DeleteScansUseCase` und anderen.
- `ScanRecord` aus `data/local/` wird direkt verwendet — keine Mapping-Schicht (entspricht dem Projekt-Muster).
- Die gesamte Renderer/ML Kit-Logik steckt in `QrCodeScanner.scan()`.
- Exceptions propagieren nach oben ins ViewModel — keine interne Fehlerbehandlung im UseCase.
- `@Inject constructor`, kein `@Singleton`.

### Schritt 4: ViewModel (`ui/qrscan`)

`ui/qrscan/QrScanViewModel.kt`:

```kotlin
@HiltViewModel
class QrScanViewModel @Inject constructor(
    private val repository: ScanRepository,       // konkrete Klasse, kein Interface — Projekt-Muster
    private val scanQrCodesUseCase: ScanQrCodesUseCase,
    private val resourceProvider: ResourceProvider, // statt WorkflowErrorMapper (der mappt nur ScanWorkflowError-Enums)
    private val dispatcherProvider: DispatcherProvider,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val scanId: Long = checkNotNull(savedStateHandle["scanId"])

    private val _record   = MutableStateFlow<ScanRecord?>(null)
    private val _scanning = MutableStateFlow(false)            // analog _editLoading
    private val _results  = MutableStateFlow<List<QrCodeResult>>(emptyList())
    private val _error    = MutableStateFlow<String?>(null)
    private val _progress = MutableStateFlow(0 to 0)           // current to total

    val record:   StateFlow<ScanRecord?>        = _record.asStateFlow()
    val scanning: StateFlow<Boolean>            = _scanning.asStateFlow()
    val results:  StateFlow<List<QrCodeResult>> = _results.asStateFlow()
    val error:    StateFlow<String?>            = _error.asStateFlow()
    val progress: StateFlow<Pair<Int, Int>>     = _progress.asStateFlow()

    init { loadRecord(); startScan() }

    fun clearError() { _error.value = null }
}
```

- **`WorkflowErrorMapper` wird nicht verwendet** — der mappt ausschließlich `ScanWorkflowError`-Enums aus den Workflow-Klassen. Exceptions aus dem UseCase werden direkt gefangen; Fehlertexte kommen über `resourceProvider.getString(R.string.*)`.
- **`ScanRepository` als konkrete Klasse** — kein Interface, direkt injiziert wie überall im Projekt.
- **Kein `_success: StateFlow<Boolean>`**: Der Screen ist rein lesend — kein automatisches Zurücknavigieren, der Nutzer verlässt ihn manuell über Back.
- Fehler → `_error: StateFlow<String?>` → AlertDialog im Screen → `clearError()`.
- `startScan()` prüft `_scanning.value` als Guard gegen Doppelaufruf.
- `viewModelScope.launch(dispatcherProvider.io)` für den Scan — analog zu `PageSelectionViewModel`.

### Schritt 5: Screen (`ui/qrscan`)

`ui/qrscan/QrScanScreen.kt`:

- **Während des Scans**: `LinearProgressIndicator` mit `progress.current / progress.total` + Text „Seite X von Y".
- **Nach Abschluss**: `LazyColumn` mit Material 3 `Card` pro Ergebnis.
- **Leerlauf-State**: Illustration + Text „Keine QR-Codes gefunden" (analog `EmptyStateContent`).
- **Pro Ergebnis-Card**:
  - Seitenzahl (z.B. „Seite 3")
  - `rawValue` als Text; bei `TYPE_URL` zusätzlich als `AnnotatedString` mit `LinkAnnotation` (klickbar)
  - Bei `TYPE_WIFI`: SSID im Klartext, Passwort standardmäßig als `••••••••` mit Augensymbol-Toggle zum Einblenden (`var passwordVisible by remember { mutableStateOf(false) }`)
  - Icon-Button: In Zwischenablage kopieren (`ClipboardManager`)
  - Icon-Button: Teilen (`Intent.ACTION_SEND`)
- **Fehler**: `AlertDialog` aus `_error`, mit `clearError()` beim Schließen.
- Keine `ActionScreenContent`-Wrapper verwenden — der Screen hat kein Formular und keinen Confirm-Button.

### Schritt 6: Navigation

**`Screen.kt`** — neuer Eintrag:
```kotlin
data object QrScan : Screen("qr-scan/{scanId}") {
    fun createRoute(scanId: Long) = "qr-scan/$scanId"
}
```

**`AppNavigation.kt`** — neue `composable`-Route:
```kotlin
composable(
    route = Screen.QrScan.route,
    arguments = listOf(navArgument("scanId") { type = NavType.LongType })
) {
    QrScanScreen(onNavigateBack = { navController.popBackStack() })
}
```

Wiring in `AppNavigation` analog zu allen anderen `scanId`-Screens: der `NavHost` übergibt den `hiltViewModel()` automatisch.

### Schritt 7: Einstiegspunkt in `HomeScreen` + `ScanItem`

**`ScanAction` in `ScanItem.kt`** — neuer Eintrag:
```kotlin
data object ScanQrCodes : ScanAction
```

**Sheet-Sektion**: Eintrag in **Ausgabe** (analog zu Print, ExportAsJpg), da es sich um eine lesende Auswertung handelt:
```kotlin
// ── AUSGABE ──────────────────────────────────────────────────────────
SheetItem(Icons.Default.QrCodeScanner, R.string.action_scan_qr_codes, notEncrypted) {
    onAction(ScanAction.ScanQrCodes)
}
```

Icon: `Icons.Default.QrCodeScanner` (in Material Icons Extended vorhanden).
`enabled = notEncrypted` — verschlüsselte PDFs können nicht gerendert werden.

**`HomeScreen.kt`** — neuer Parameter + When-Branch:
```kotlin
onNavigateToQrScan: (Long) -> Unit = {},
// …
ScanAction.ScanQrCodes -> onNavigateToQrScan(record.id)
```

**`AppNavigation.kt`** — Weitergabe des Callbacks an `HomeScreen`:
```kotlin
onNavigateToQrScan = { scanId -> navController.navigate(Screen.QrScan.createRoute(scanId)) },
```

### Schritt 8: Lokalisierung

**Pflicht: alle 10 Locale-Dateien** (CLAUDE.md-Regel: `values/`, `-de`, `-es`, `-fr`, `-pt`, `-zh-rCN`, `-ar`, `-ja`, `-ru`, `-hi`).

Separate Strings-Datei pro Feature — Muster aus `strings_annotate.xml` / `strings_images_to_pdf.xml`:

- `res/values/strings_qr_scan.xml` (Englisch / Fallback)
- `res/values-de/strings_qr_scan.xml`
- … (alle weiteren 8 Locales)

Benötigte Strings:

| Key | Englisch | Deutsch |
|-----|----------|---------|
| `action_scan_qr_codes` | Scan QR codes | QR-Codes scannen |
| `qr_scan_title` | QR Codes | QR-Codes |
| `qr_scan_progress` | Scanning page %1$d of %2$d | Seite %1$d von %2$d wird gescannt |
| `qr_scan_empty` | No QR codes found | Keine QR-Codes gefunden |
| `qr_scan_page` | Page %1$d | Seite %1$d |
| `qr_scan_copy` | Copy to clipboard | In Zwischenablage kopieren |
| `qr_scan_copied` | Copied | Kopiert |
| `qr_scan_share` | Share | Teilen |
| `qr_scan_wifi_ssid` | Network: %1$s | Netzwerk: %1$s |
| `qr_scan_wifi_password` | Password | Passwort |
| `qr_scan_wifi_show_password` | Show password | Passwort anzeigen |
| `qr_scan_wifi_hide_password` | Hide password | Passwort verbergen |
| `qr_scan_error_encrypted` | Cannot scan encrypted PDF | Verschlüsselte PDF kann nicht gescannt werden |

---

## 5. Sicherheit & Best Practices

- **Datenschutz**: Der Scan erfolgt vollständig lokal (ML Kit bundled, kein Netzwerkzugriff). Konsistent mit der Privacy Policy in `docs/privacy-policy.html` — keine Anpassung nötig.
- **Ressourcen**: Bitmaps werden nach `scanner.scanPage()` sofort mit `bitmap.recycle()` freigegeben. `BarcodeScanner.close()` in `finally`-Block im UseCase.
- **Asynchronität**: `ScanQrCodesUseCase` läuft auf `Dispatchers.IO`. ML Kit-Tasks laufen intern auf eigenen Threads. Der UI-Thread wird nicht blockiert.
- **Verschlüsselte PDFs**: Die Aktion ist im Sheet via `enabled = notEncrypted` deaktiviert; zusätzlich prüft der UseCase `record.isEncrypted` als Guard.
- **Große PDFs**: Durch seitenweises Rendern + sofortiges `recycle()` bleibt der Heap-Druck kontrolliert, auch bei 100+ Seiten.

---

## 6. Tests

### Unit-Tests (JVM)

`test/domain/usecase/ScanQrCodesUseCaseTest.kt`:
- Leeres Ergebnis bei PDF ohne QR-Codes
- Korrekte Seitenzahl (1-basiert) im Ergebnis
- `scanner.close()` wird in jedem Fall aufgerufen (auch bei Exception)
- Fortschritts-Callbacks werden für jede Seite aufgerufen
- Verschlüsseltes PDF: UseCase gibt leere Liste zurück, kein Crash

Fake: `FakeQrCodeScanner extends QrCodeScanner` — überschreibt `scan(file, onProgress)` und gibt konfigurierbare Ergebnisse zurück, analog zu `FakeSearchablePdfBuilder` in `MakeSearchableUseCaseTest`. Da der UseCase nur `scanner.scan()` aufruft und `File`-Guards prüft, ist er vollständig JVM-testbar ohne Android-Laufzeit.

`test/ui/qrscan/QrScanViewModelTest.kt`:
- `_scanning`-Guard: zweiter Aufruf während laufendem Scan wird ignoriert
- Erfolg: `_results` enthält Ergebnisse, `_scanning` → false
- Fehler: `_error` wird gesetzt, `_scanning` → false
- `clearError()` setzt `_error` auf null

Testmuster: `UnconfinedTestDispatcher` + Fake-`QrCodeScanner`-Subklasse, analog zu bestehenden ViewModel-Tests.

### Manuelle Gerätetests

- QR-Code auf Seite 1, 3, letzte Seite → korrekte Seitenzahlen
- Mehrere QR-Codes auf einer Seite → alle werden angezeigt
- URL-QR-Code: Link ist klickbar, öffnet Browser
- WLAN-QR-Code: SSID und Passwort werden strukturiert angezeigt
- Großes PDF (50+ Seiten): kein OOM, Fortschrittsanzeige korrekt
- Verschlüsseltes PDF: Aktion im Sheet ist ausgegraut

---

## 7. Getroffene Entscheidungen

| Frage | Entscheidung | Begründung |
|-------|-------------|------------|
| Barcode-Formate | Nur `FORMAT_QR_CODE` | Klares Feature-Versprechen, schnellster Scan. Erweiterung auf weitere Formate ist ohne Architektur-Änderung möglich. |
| Render-Auflösung | 96 DPI (~794 × 1123 px / A4, ~3,4 MB ARGB_8888) | Ausreichend für Dokument-QR-Codes ≥ 1 cm; kein adaptiver Zweifach-Scan. |
| WiFi-Passwort | Versteckt (`••••••••`) + Augensymbol-Toggle | Verhindert unbeabsichtigtes Exponieren beim Zeigen des Bildschirms. |
