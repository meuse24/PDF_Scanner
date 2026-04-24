# Plan 3: Konfigurierbares Seitenformat fuer Bilder-zu-PDF

## Ausgangslage

### Aktueller Ist-Zustand im Code

- **Scanner-PDFs**: Die App konfiguriert den ML-Kit-Scanner nur mit `galleryImportAllowed`,
  `resultFormats`, `scannerMode` und `pageLimit`.
  - Referenzen:
    - `app/src/main/java/info/meuse24/pdf_scanner/ui/home/HomeScreenLaunchers.kt`
    - `app/src/main/java/info/meuse24/pdf_scanner/ui/append/AppendScreen.kt`
  - Die App setzt **keine** eigene Papiergroesse, **keine** feste Orientierung und **keine**
    Raender fuer den Scanner-Output.
  - Konsequenz: Das PDF kommt so aus ML Kit heraus und wird nur importiert.

- **Bilder-zu-PDF**: Die App erzeugt heute immer ein **A4-PDF im Hochformat**.
  - Referenzen:
    - `app/src/main/java/info/meuse24/pdf_scanner/util/PdfEditorImageOps.kt`
  - Harte Defaults in `a4LayoutCells()`:
    - Papierformat: `PDRectangle.A4`
    - Orientierung: Hochformat (implizit — kein Swap)
    - Rand: `20f` Punkte (`margin`)
    - Zellabstand: `10f` Punkte (`gap`)
    - Bilder werden proportional in die Zellen eingepasst (`fitInsideCell`)
  - Die UI erlaubt aktuell nur das Layout `SINGLE`, `TWO_PER_PAGE` oder `FOUR_PER_PAGE`.
    - Referenzen:
      - `app/src/main/java/info/meuse24/pdf_scanner/ui/imagestopdf/ImagesPdfOptionsContent.kt`
      - `app/src/main/java/info/meuse24/pdf_scanner/domain/usecase/ImagePageLayout.kt`

- **Call-Chain fuer Bilder-zu-PDF (komplett)**:
  ```
  ImagesToPdfViewModel.createPdf(layout)
    -> CreatePdfFromImagesUseCase(imageUris, filename, layout, scansDir)
    -> ImagePdfBuilder.createPdf(imageUris, layout, outputFile)
    -> PdfRenderingOps.createPdfFromImages(imageBytes, layout, outputFile)   // Port
    -> PdfEditorImageOps.a4LayoutCells(layout)                               // Impl
  ```

- **Bilder an bestehendes PDF anhaengen**:
  - `AppendSource.Images(val uris: List<Uri>, val layout: ImagePageLayout)` traegt `layout`.
  - `AppendViewModel.appendImages(layout: ImagePageLayout)` baut den Source.
  - `AppendToPdfUseCase` delegiert an `ImagePdfBuilder.createTempPdf(uris, layout)`.
  - Erbt damit ebenfalls A4 Hochformat mit 20 pt Rand.
  - Referenzen:
    - `app/src/main/java/info/meuse24/pdf_scanner/domain/usecase/AppendToPdfUseCase.kt`
    - `app/src/main/java/info/meuse24/pdf_scanner/domain/usecase/ImagePdfBuilder.kt`
    - `app/src/main/java/info/meuse24/pdf_scanner/ui/append/AppendViewModel.kt`

- **Abgeleitete PDF-Kopien** (Signatur, Graustufen, OCR usw.) behalten das bestehende
  Seitenformat des Quelldokuments; sie sind **nicht** Scope dieses Plans.

- **Persistenz-Schicht** (`AppSettings`, `AppSettingsPreferences`, `AppSettingsRepository`):
  - `AppSettings` ist ein reines `data class` in `util/AppSettings.kt`.
  - `AppSettingsPreferences` liest/schreibt alle Felder als `SharedPreferences` in `PREFS_NAME = "app_settings"`.
  - `AppSettingsRepository` (Interface in `domain/repository/`) hat fuer jedes Feld eine
    dedizierte `fun updateX(...)` Methode — dieses Muster muss fuer neue Felder fortgefuehrt werden.
  - **Kein** `SettingsRepository.kt` existiert — der korrekte Dateiname ist `AppSettingsRepository.kt`.

---

## Best-Practice-Bewertung

### Was sinnvoll ist

- **Ja** fuer **Bilder-zu-PDF** und **Bilder an PDF anhaengen**:
  - Papierformat: `A3`, `A4`, `A5`, `Letter`
  - Orientierung: Portrait / Landscape
  - Rand: Preset klein / mittel / gross

- **Nein** als globale PDF-Einstellung fuer **Scanner-PDFs**:
  - `GmsDocumentScannerOptions.Builder` bietet keine Papiergroessen-, Orientierungs- oder
    Rand-API. Offizielle Optionen sind nur `setGalleryImportAllowed`, `setPageLimit`,
    `setResultFormats`, `setScannerMode`.

- **Nicht in V1** fuer **frei benutzerdefinierte Breite/Hoehe**:
  - Erhoeht UI-, Validierungs- und Preview-Komplexitaet deutlich.
  - Kein klarer Consumer-Use-Case nachgewiesen.
  - **Daher: `CUSTOM` kommt weder ins V1-Enum noch in `PdfPageSetup`.**

### Platzierung nach Android-Best-Practice

- Parameter gehoeren **primaer kontextuell in den jeweiligen Flow**, nicht in globale Settings.
- Android Design: haeufig genutzte, kontextabhaengige Optionen nah an der Funktion.
- Globale Settings erst als optionaler Default (V2), wenn Nutzer dies fordern.

---

## Produkt-Empfehlung

### V1

- Ort: **im Screen**, nicht in Settings.
  - `ImagesToPdfScreen` / `ImagesPdfOptionsContent`
  - Bilder-Append-Flow in `AppendScreen`
- UI-Block **"Seiteneinrichtung"**:
  - Papierformat: `A4` (Default) · `A5` · `A3` · `Letter`
  - Orientierung: `Portrait` / `Landscape`
  - Rand: `Klein` / `Mittel` / `Gross`
  - `LayoutPreviewCanvas` aktualisiert seinen Seitenaspekt (Hoch-/Querformat)
- Persistenz: **letzte Auswahl merken** via `AppSettings` — noch kein eigener Settings-Screen-Eintrag.

### V2 (spaeter, bei Nachfrage)

- Optionaler Default in `Settings`
- `CUSTOM` mit Breite/Hoehe in mm

---

## Architekturvorschlag (Clean Architecture)

### 1. Domain-Modell einfuehren

**Paket: `domain/model/`** (reine Werttypen, keine Android-Abhaengigkeit, Screen-uebergreifend)

```kotlin
// domain/model/PdfPageSetup.kt

enum class PdfPageSizePreset {
    ISO_A3,
    ISO_A4,   // Default
    ISO_A5,
    NA_LETTER
}

enum class PdfPageOrientation {
    PORTRAIT,   // Default
    LANDSCAPE
}

enum class PdfMarginPreset {
    SMALL,   //  10f pt
    MEDIUM,  //  20f pt  (= bisheriger Hardcode — kein Verhaltensbruch)
    LARGE    //  35f pt
}

data class PdfPageSetup(
    val sizePreset: PdfPageSizePreset = PdfPageSizePreset.ISO_A4,
    val orientation: PdfPageOrientation = PdfPageOrientation.PORTRAIT,
    val marginPreset: PdfMarginPreset = PdfMarginPreset.MEDIUM
)
```

**Paket: `domain/usecase/`** (Use-Case-Eingangstyp, kombiniert vorhandenes `ImagePageLayout`
mit neuem `PdfPageSetup`)

```kotlin
// domain/usecase/ImagePdfOptions.kt

data class ImagePdfOptions(
    val layout: ImagePageLayout,
    val pageSetup: PdfPageSetup = PdfPageSetup()
)
```

Begruendung der Paket-Trennung:
- `PdfPageSetup` ist ein reiner Wertetyp ohne Use-Case-Logik → `domain/model/`.
- `ImagePdfOptions` ist der konkrete Eingangsparameter des Bilder-PDF-Use-Cases → `domain/usecase/`.

### 2. Port-Signatur aendern

Datei: `domain/pdf/PdfRenderingOps.kt`

```kotlin
// vorher
fun createPdfFromImages(
    imageBytes: List<ByteArray?>,
    layout: ImagePageLayout,
    outputFile: File
): File

// nachher
fun createPdfFromImages(
    imageBytes: List<ByteArray?>,
    options: ImagePdfOptions,
    outputFile: File
): File
```

### 3. Call-Chain vollstaendig umstellen

Alle Stellen, die `layout: ImagePageLayout` direkt durchreichen, muessen auf `options: ImagePdfOptions`
umgestellt werden:

**`ImagePdfBuilder.kt`** (beide oeffentlichen Methoden):
```kotlin
// vorher
suspend fun createPdf(imageUris, layout: ImagePageLayout, outputFile): ImagePdfBuildResult
suspend fun createTempPdf(imageUris, layout: ImagePageLayout): ImagePdfBuildResult

// nachher
suspend fun createPdf(imageUris, options: ImagePdfOptions, outputFile): ImagePdfBuildResult
suspend fun createTempPdf(imageUris, options: ImagePdfOptions): ImagePdfBuildResult
```

**`AppendSource.Images`** in `AppendToPdfUseCase.kt`:
```kotlin
// vorher
data class Images(val uris: List<Uri>, val layout: ImagePageLayout) : AppendSource

// nachher
data class Images(val uris: List<Uri>, val options: ImagePdfOptions) : AppendSource
```

**`CreatePdfFromImagesUseCase.kt`**:
```kotlin
// vorher
suspend operator fun invoke(imageUris, filename, layout: ImagePageLayout, scansDir)

// nachher
suspend operator fun invoke(imageUris, filename, options: ImagePdfOptions, scansDir)
```

**`AppendViewModel.appendImages()`**:
```kotlin
// vorher
fun appendImages(layout: ImagePageLayout)

// nachher
fun appendImages(options: ImagePdfOptions)
```

**`ImagesToPdfViewModel.createPdf()`**:
```kotlin
// vorher
fun createPdf(imageUris, filename, layout: ImagePageLayout)

// nachher
fun createPdf(imageUris, filename, options: ImagePdfOptions)
```

### 4. Rendering-Logik verallgemeinern

Datei: `util/PdfEditorImageOps.kt`

`a4LayoutCells(layout)` wird ersetzt durch:

```kotlin
internal fun pageRectangle(setup: PdfPageSetup): PDRectangle {
    val base = when (setup.sizePreset) {
        PdfPageSizePreset.ISO_A3     -> PDRectangle.A3
        PdfPageSizePreset.ISO_A4     -> PDRectangle.A4
        PdfPageSizePreset.ISO_A5     -> PDRectangle.A5
        PdfPageSizePreset.NA_LETTER  -> PDRectangle.LETTER
    }
    return if (setup.orientation == PdfPageOrientation.LANDSCAPE && base.width < base.height) {
        PDRectangle(base.height, base.width)   // Seiten-Swap fuer Querformat
    } else {
        base
    }
}

internal fun marginPoints(setup: PdfPageSetup): Float = when (setup.marginPreset) {
    PdfMarginPreset.SMALL  -> 10f
    PdfMarginPreset.MEDIUM -> 20f   // identisch mit bisherigem Hardcode
    PdfMarginPreset.LARGE  -> 35f
}

internal fun layoutCells(options: ImagePdfOptions): List<CellRect> {
    val page   = pageRectangle(options.pageSetup)
    val margin = marginPoints(options.pageSetup)
    val gap    = 10f
    val pageW  = page.width
    val pageH  = page.height
    return when (options.layout) {
        ImagePageLayout.SINGLE -> {
            listOf(CellRect(margin, margin, pageW - 2 * margin, pageH - 2 * margin))
        }
        ImagePageLayout.TWO_PER_PAGE -> {
            val w = pageW - 2 * margin
            val h = (pageH - 2 * margin - gap) / 2f
            listOf(
                CellRect(margin, margin + h + gap, w, h),
                CellRect(margin, margin, w, h)
            )
        }
        ImagePageLayout.FOUR_PER_PAGE -> {
            val w = (pageW - 2 * margin - gap) / 2f
            val h = (pageH - 2 * margin - gap) / 2f
            listOf(
                CellRect(margin, margin + h + gap, w, h),
                CellRect(margin + w + gap, margin + h + gap, w, h),
                CellRect(margin, margin, w, h),
                CellRect(margin + w + gap, margin, w, h)
            )
        }
    }
}
```

Die alte Funktion `a4LayoutCells` entfaellt vollstaendig; alle internen Aufrufe in
`PdfEditorImageOps` (bzw. `PdfEditor`) verwenden nun `layoutCells(options)`.

### 5. ViewModel-State und Persistenz-Laden

**`ImagesToPdfViewModel`** benoetigt:
- Injection von `AppSettingsRepository` (neu hinzufuegen)
- `private val _pageSetup = MutableStateFlow(PdfPageSetup())`
- Im `init`-Block: `_pageSetup.value = settings.value.defaultImagePdfPageSetup`
- `fun updatePageSetup(setup: PdfPageSetup)`: aktualisiert `_pageSetup` und ruft
  `settingsRepository.updateDefaultImagePdfPageSetup(setup)` auf
- `createPdf()` baut `ImagePdfOptions(layout = _selectedLayout.value, pageSetup = _pageSetup.value)`

**`AppendViewModel`** analog:
- Injection von `AppSettingsRepository`
- `private val _pageSetup = MutableStateFlow(PdfPageSetup())`
- `fun updatePageSetup(setup: PdfPageSetup)`: wie oben
- `appendImages()` baut `ImagePdfOptions(layout = <aktuelles Layout>, pageSetup = _pageSetup.value)`

### 6. Gemeinsame UI-Komponente

Neue Datei: `ui/components/PdfPageSetupSection.kt`

```kotlin
@Composable
fun PdfPageSetupSection(
    setup: PdfPageSetup,
    onSetupChange: (PdfPageSetup) -> Unit,
    modifier: Modifier = Modifier
)
```

Enthaelt:
- Segmented-Button-Row fuer `PdfPageSizePreset` (A5 · A4 · A3 · Letter)
- Segmented-Button-Row fuer `PdfPageOrientation` (Portrait · Landscape)
- Segmented-Button-Row oder Chip-Row fuer `PdfMarginPreset` (Klein · Mittel · Gross)
- Kein eigener State — vollstaendig zustandslos (State Hoisting).

Verwendung:
- `ui/imagestopdf/ImagesPdfOptionsContent.kt` — direkt in die bestehende `Column`
  unterhalb der Layout-Auswahl einbauen (kein separater `topContent`-Slot noetig, da
  die Seiteneinrichtung zur Hauptoptionsflaeche gehoert).
- `ui/append/AppendScreen.kt` — ueber den vorhandenen `topContent`-Slot von
  `ImagesPdfOptionsContent` einklinken, der bereits fuer kontextuelle Erweiterungen
  vorgesehen ist.

**`LayoutPreviewCanvas` anpassen**:
- Das Canvas hat aktuell eine fixe Portrait-Groesse (36 dp × 50 dp im Aufruf).
- Wenn `PdfPageOrientation.LANDSCAPE` aktiv ist, muss der Aufruf die Dimensionen
  tauschen (50 dp × 36 dp) oder das Canvas berechnet intern das Seitenverhaeltnis
  aus dem gewaehlten `PdfPageSetup`.

### 7. Persistenz erweitern

**`AppSettings.kt`** — drei neue Felder mit stabilen Defaults:
```kotlin
data class AppSettings(
    // ... bestehende Felder ...
    val defaultImagePdfPageSetup: PdfPageSetup = PdfPageSetup()
    // PdfPageSetup() liefert ISO_A4 / PORTRAIT / MEDIUM — identisch mit bisherigem Hardcode
)
```

**`AppSettingsPreferences.kt`** — drei neue Schluessel und Lade-/Speicher-Logik:
```kotlin
private const val KEY_IMG_PDF_SIZE_PRESET    = "img_pdf_size_preset"
private const val KEY_IMG_PDF_ORIENTATION    = "img_pdf_orientation"
private const val KEY_IMG_PDF_MARGIN_PRESET  = "img_pdf_margin_preset"
```
Serialisierung: `enum.name` als String; `valueOf()`/`enumValueOf()` beim Laden mit
`runCatching { }.getOrDefault(ISO_A4)` als Sicherheitsnetz gegen unbekannte Werte
(z. B. nach Downgrade).

**`AppSettingsRepository.kt`** (Interface) — eine neue Methode gemaess bestehendem Muster:
```kotlin
fun updateDefaultImagePdfPageSetup(setup: PdfPageSetup)
```
Intern wird `setup` in seine drei Komponenten aufgesplittet und per
`AppSettingsPreferences.save(context, current.copy(...))` persistiert.

---

## Scope

### In Scope fuer V1

- Bilder-zu-PDF (`ImagesToPdfScreen`)
- Bilder an bestehendes PDF anhaengen (`AppendScreen`, Image-Pfad)
- Presets: A3 / A4 / A5 / Letter
- Portrait / Landscape
- Margin-Presets: Klein / Mittel / Gross
- Letzte Auswahl per `AppSettings` merken
- `LayoutPreviewCanvas` zeigt korrekten Seitenaspekt

### Nicht in Scope fuer V1

- Scanner-PDFs aus ML Kit umformatieren (keine API-Unterstuetzung)
- Bestehende/importierte PDFs global auf neues Papierformat zwingen
- `CUSTOM` mit freier Breite/Hoehe (weder im Enum noch im Modell)
- `Orientation.Auto` (undefiniertes Verhalten bei gemischten Bild-Aspekten)
- Duplex-/Druckerprofile
- Globaler Default in `Settings`-Screen

---

## Defaults

| Parameter     | Default        | Begruendung                                      |
|---------------|---------------|--------------------------------------------------|
| Papierformat  | `ISO_A4`      | Bisheriger Hardcode — kein stilles Verhalten-Break |
| Orientierung  | `PORTRAIT`    | Bisheriger Hardcode                               |
| Rand          | `MEDIUM` (20f)| Identisch mit bisherigem `margin = 20f`           |

Spaeter (V2) optional region-basierter Default: `NA_LETTER` fuer US-Locale, `ISO_A4` sonst,
erkennbar ueber `Locale.getDefault()`.

---

## Tests

### Unit-Tests (JVM, `test/`)

**`util/PdfEditorImageOpsTest.kt`** (neue Datei):
- `pageRectangle`: alle vier Presets, Portrait und Landscape (inkl. Swap-Pruefung)
- `marginPoints`: alle drei Presets auf Punktwert-Gleichheit
- `layoutCells`: Zellanzahl und Groesse fuer jede Kombination aus Layout × Papierformat × Orientierung
- Regression: `layoutCells(ImagePdfOptions(SINGLE, PdfPageSetup()))` erzeugt exakt
  dieselbe `CellRect` wie das bisherige `a4LayoutCells(SINGLE)`.

**`ui/imagestopdf/ImagesToPdfViewModelTest.kt`** (erweitern):
- `updatePageSetup` aktualisiert den State und delegiert an `AppSettingsRepository`
- `createPdf` reicht `ImagePdfOptions` mit aktuellem Layout und `PdfPageSetup` weiter

**`ui/append/AppendViewModelTest.kt`** (erweitern):
- `appendImages` baut `AppendSource.Images` mit korrektem `ImagePdfOptions`

### Regression

- Alle bestehenden A4-basierten Tests bleiben gruen (Defaults identisch mit Hardcode).
- Bestehende Import-/Append-/Merge-Flows fuer PDFs als Quelle unveraendert.

---

## Konkrete Umsetzungsreihenfolge

1. `PdfPageSetup` (Enum + data class) in `domain/model/` einfuehren.
2. `ImagePdfOptions` in `domain/usecase/` einfuehren.
3. `PdfRenderingOps`-Port-Signatur auf `ImagePdfOptions` umstellen.
4. `PdfEditorImageOps`: `a4LayoutCells` durch `pageRectangle`, `marginPoints`, `layoutCells`
   ersetzen; Impl an neuen Port anpassen.
5. `ImagePdfBuilder.createPdf/createTempPdf` auf `ImagePdfOptions` umstellen.
6. `AppendSource.Images` auf `ImagePdfOptions` umstellen.
7. `CreatePdfFromImagesUseCase` auf `ImagePdfOptions` umstellen.
8. `AppSettings` + `AppSettingsPreferences` + `AppSettingsRepository`-Interface erweitern.
9. `ImagesToPdfViewModel` und `AppendViewModel`: `AppSettingsRepository` injizieren,
   `_pageSetup`-State ergaenzen, `createPdf`/`appendImages` anpassen.
10. Gemeinsame `PdfPageSetupSection`-Komponente bauen.
11. `ImagesPdfOptionsContent` und `AppendScreen` anbinden; `LayoutPreviewCanvas` anpassen.
12. Tests ergaenzen (JVM) und Regressionstests sicherstellen.
13. Alle 10 Locale-Dateien: neue Strings fuer Format-Labels in `strings_imagestopdf.xml`.
14. Optional erst danach globalen Settings-Eintrag pruefen (V2).

---

## Fazit

- **Bisheriger Default ist A4 Hochformat 20 pt Rand — alle neuen Defaults sind identisch.**
- **Scanner-PDFs sind strukturell nicht konfigurierbar — kein Scope.**
- **V1-Modell enthaelt kein `CUSTOM` und kein `Auto`-Orientierungs-Preset.**
- **Die vollstaendige Call-Chain muss konsistent umgestellt werden:**
  `PdfRenderingOps` → `ImagePdfBuilder` → `AppendSource.Images` → Use Cases → ViewModels.
- **Persistenz folgt dem bestehenden `AppSettings`-Muster** (SharedPreferences, enum.name,
  Interface-Methode pro Gruppe).
- **Best Practice: im Flow selbst platzieren, letzte Auswahl merken, nicht zuerst global.**

---

## Quellen

- ML Kit `GmsDocumentScannerOptions.Builder`:
  https://developers.google.com/android/reference/com/google/mlkit/vision/documentscanner/GmsDocumentScannerOptions.Builder
- Android Design: Settings:
  https://developer.android.com/design/ui/mobile/guides/patterns/settings
- Android `PrintAttributes.MediaSize`:
  https://developer.android.com/reference/android/print/PrintAttributes.MediaSize
- PdfBox-Android `PDRectangle`-Konstanten:
  https://github.com/TomRoush/PdfBox-Android
