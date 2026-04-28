# Clean Architecture Refactoring Plan

Stand: 2026-04-27 (re-verifiziert)

Ziel ist nicht ein Big-Bang-Umbau, sondern eine schrittweise Entkopplung mit
laufend baubarem Projektzustand.

## Leitlinien

- `domain` bleibt pure Kotlin: keine Android-, UI-, Room-, MLKit- oder Storage-Implementierungstypen.
- `domain` enthaelt Modelle, Repository-Interfaces, UseCases, Workflows, Ports (`domain/pdf/`, `domain/gateway/`) und fachliche Fehler/Result-Typen.
- `data` implementiert Repository-Interfaces und kapselt Room, Preferences, Dateien, Android Storage und Mapper.
- `ui` enthaelt Compose Screens, ViewModels, UI State, Navigation und UI-spezifische Mapper.
- `di` verdrahtet Implementierungen gegen Domain-Interfaces.
- Der bestehende Single-Module-Aufbau kann vorerst bleiben; zuerst werden Package-Grenzen sauber gezogen.

## Verifizierte Findings (Stand 2026-04-27)

### 1. `util` ist ein Mischbereich ✓ bestaetigt

`util/` enthaelt aktuell ca. 36 Dateien mit stark gemischten Verantwortlichkeiten:

**Domain-nah (gehoert nach `domain/model`):**
- `AppSettings.kt`, `AppSortOrder.kt`, `OcrQuality.kt`

**Port-Interfaces (gehoert nach `domain/gateway`):**
- `DispatcherProvider.kt`, `StorageProvider.kt`, `DownloadsStorage.kt`, `ResourceProvider.kt`
- `OcrInputImageLoader.kt`, `TextRecognizerRunner.kt`, `QrCodeScanner.kt`

**Implementierungen (gehoert nach `data/*` oder `platform/`):**
- PDF: `PdfEditor.kt`, `PdfEditorCore/Annotation/Overlay/Redaction/ImageOps`, `SearchablePdfBuilder.kt`, `PdfPageBitmapRenderer/Cache`, `PdfPrintHelper/Adapter`
- OCR: `OcrManager.kt`, `OcrModelInstaller.kt`, `OcrPipeline.kt`, `OcrPageTextJson.kt`
- Android-Plattform: `AppLockManager.kt`, `PlayReviewPromptManager.kt`, `FileUtil.kt`, `PdfDocumentIntents.kt`

### 2. `domain` importiert aus `util` und `ui` ~ teils bestaetigt

**Bestaetigt:**
- `domain/repository/AppSettingsRepository.kt` importiert `util.AppSettings`, `util.AppSortOrder` und `ui.theme.ThemeMode`.

**Nicht bestaetigt (Pruefung 2026-04-27):**
- Aktuell keine `android.*`- oder `androidx.*`-Imports in `domain/` gefunden.
- Die im urspruenglichen Plan genannten Imports von `android.net.Uri` (ImportScanUseCase, AppendToPdfUseCase, CreatePdfFromImagesUseCase) und `android.graphics.Bitmap` (PdfRenderingOps, PdfMetadataOps) konnten nicht bestaetigt werden. Vor Phase 4 manuell gegenprufen:
  ```powershell
  rg "^import android" app/src/main/java/info/meuse24/pdf_scanner/domain
  ```

### 3. `HomeViewModel` ist zu breit ✓ bestaetigt

`ui/home/HomeViewModel.kt` hat ~792 Zeilen und buendelt:
- Listen-/Suchzustand
- Import und Export
- OCR-Extraktion und OCR-Backfill
- Make-searchable Workflow
- Datei- und Thumbnail-Rename
- Review-Prompt
- Fehler- und Snackbar-Mapping

### 4. Settings-Typen liegen in `util` ✓ bestaetigt

`AppSettings`, `AppSortOrder` und `OcrQuality` werden von Domain, Data und UI genutzt, liegen aber in `util`.
Guter Kandidat fuer `domain/model`.

**Sonderfall `ThemeMode`:** Liegt in `ui/theme/ThemeMode.kt`. Da Theme-Praeferenz fachlich ist (gespeichert, nicht nur UI-Zustand), soll sie nach `domain/model/ThemeMode.kt` verschoben und UI-seitig gemappt werden.

### 5. OCR-Domain-Typen existieren noch nicht als eigene Klassen ~ neu

Die im Plan genannten Typen (`OcrPipelineStatus`, `OcrResultStats`, `OcrUsage`, `OcrScript`, `OcrThresholds`) sind **nicht als eigenstaendige Dateien vorhanden**. Sie sind vermutlich in `OcrManager.kt` oder `OcrPipeline.kt` eingebettet. Phase 3/OCR bedeutet daher, diese Typen erst **zu extrahieren** und dann in `domain/model` zu platzieren.

### 6. Bereits vorhandene Domain-Strukturen (neu erganzt)

- `domain/pdf/` existiert mit 8 Port-Interfaces: `PdfAnnotationOps`, `PdfExceptions`, `PdfImageRenderer`, `PdfMetadataOps`, `PdfRenderingOps`, `PdfSecurityOps`, `PdfStructureOps`, `PdfTextOps`. Gute Grundlage fuer Phase 6.
- `domain/service/` existiert mit `BusinessCardParser.kt`, `ScanArtifactPersister.kt`, `VCardBuilder.kt`.
- `domain/gateway/` existiert **nicht** — muss angelegt werden.
- `domain/common/` existiert **nicht** — muss bei Bedarf angelegt werden.

### 7. `data/` fehlen Ziel-Unterverzeichnisse (neu erganzt)

Aktuell hat `data/` nur: `export/`, `local/`, `mapper/`, `repository/`.
Fuer die Zielstruktur muessen angelegt werden: `data/storage/`, `data/ocr/`, `data/pdf/`, `data/settings/`, `data/platform/`.

## Zielstruktur im bestehenden Modul

Solange kein Multi-Module-Umbau gemacht wird:

```text
info.meuse24.pdf_scanner
├── data
│   ├── export          (bereits vorhanden)
│   ├── local           (bereits vorhanden)
│   ├── mapper          (bereits vorhanden)
│   ├── repository      (bereits vorhanden)
│   ├── ocr             (neu: MLKit-Implementierungen)
│   ├── pdf             (neu: PdfBox-Implementierungen)
│   ├── platform        (neu: Android-Helper wie ResourceProvider-Impl, PlayReview)
│   ├── settings        (neu: DataStore/SharedPrefs-Implementierung)
│   └── storage         (neu: StorageProvider-Impl, DownloadsStorage-Impl)
├── di
├── domain
│   ├── common          (neu: pure fachliche Helper-Funktionen)
│   ├── gateway         (neu: Port-Interfaces fuer externe Dienste)
│   ├── model           (neu: AppSettings, AppSortOrder, ThemeMode, OCR-Typen)
│   ├── pdf             (bereits vorhanden: 8 Ops-Interfaces)
│   ├── repository
│   ├── service         (bereits vorhanden: BusinessCardParser, ScanArtifactPersister, VCardBuilder)
│   ├── usecase
│   └── workflow
└── ui
    ├── ...
    └── theme
```

Spaeter optional als echte Gradle-Module:

```text
app -> ui, data, domain
ui -> domain
data -> domain
domain -> keine Android-Abhaengigkeit
```

## Phase 1: Domain-Modelle aus `util` und `ui` herausziehen

Ziel: Fachliche Settings- und OCR-Typen liegen nicht mehr in `util`/`ui`.

Aufgaben:

- `AppSettings` nach `domain/model/AppSettings.kt` verschieben.
- `AppSortOrder` nach `domain/model/AppSortOrder.kt` verschieben.
- `OcrQuality` nach `domain/model/OcrQuality.kt` verschieben.
- `ThemeMode` nach `domain/model/ThemeMode.kt` verschieben; UI mappt nur noch den Domain-Typ.
- Imports in `data`, `ui`, `domain` aktualisieren.

Akzeptanzkriterien:

- `domain/repository/AppSettingsRepository.kt` importiert nichts aus `util` oder `ui`.
- Settings-Tests laufen weiter.
- `./gradlew.bat :app:assembleDebug` ist erfolgreich.

## Phase 2: Pure Helper in `domain/common` verschieben

Ziel: Kleine fachliche Funktionen werden explizit Domain-Code.

Aufgaben:

- `normalizePageIndexes`, `normalizeSplitPoints`, `buildRanges` und verwandte pure Funktionen aus `util` in `domain/common` verschieben.
- UI- und Workflow-Imports aktualisieren.
- Falls Funktionen UI-spezifisch sind, in `ui/shared` lassen oder dorthin verschieben.

Akzeptanzkriterien:

- `domain/workflow/*` importiert keine Helper mehr aus `util`.
- Bestehende Page-/Split-/Reorder-Tests laufen weiter.

## Phase 3: Ports sauber benennen und platzieren

Ziel: Domain kennt nur Interfaces, Implementierungen liegen ausserhalb.

Aufgaben:

- `domain/gateway/` Verzeichnis anlegen.
- Domain-Ports dorthin verschieben:
  - `DispatcherProvider`
  - `ResourceProvider`, falls Workflows weiterhin Text-Mapping brauchen
  - `StorageProvider`
  - `DownloadsStorage`
  - `OcrInputImageLoader`
  - `TextRecognizerRunner`
  - `SearchablePdfBuilder` als Interface (Implementierung bleibt in `data/pdf`)
  - `QrCodeScanner`
- `data/*` Unterverzeichnisse anlegen: `storage/`, `ocr/`, `pdf/`, `settings/`, `platform/`.
- Android-Implementierungen in neue Data-Pakete verschieben:
  - `AndroidStorageProvider` -> `data/storage`
  - `AndroidDownloadsStorage` -> `data/storage`
  - `AndroidResourceProvider` -> `data/platform`
  - `PlayReviewPromptManager/Policy` -> `data/platform`
  - MLKit/OCR-Implementierungen (`OcrManager`, `OcrModelInstaller`, `OcrPipeline`) -> `data/ocr`
  - PDF-Implementierungen (`PdfEditor*`, `SearchablePdfBuilder`) -> `data/pdf`
- Hilt-Bindings in `di/` aktualisieren.

Akzeptanzkriterien:

- `domain` importiert keine Implementierungsklassen aus `util`.
- DI bindet Implementierungen gegen Domain-Ports.
- OCR-, PDF- und QR-UseCases bleiben von UI/API unveraendert nutzbar.

## Phase 4: Android-Typen aus UseCases entfernen (vor Beginn pruefen)

Ziel: UseCases verwenden fachliche Inputs statt `Uri` und `Bitmap`.

**Vor Beginn manuell verifizieren**, ob noch Android-Typen in `domain/` vorhanden:
```powershell
rg "^import android|^import androidx" app/src/main/java/info/meuse24/pdf_scanner/domain
```
Falls keine Treffer: Phase ueberspringen oder als erledigt markieren.

Aufgaben (falls Treffer vorhanden):

- `ImportedFileSource` oder `DocumentInput` einfuehren:
  - `displayName: String`
  - `mimeType: String?`
  - Domain-Port fuer Content-Zugriff statt `Uri`
- `ImportScanUseCase`, `ImportFileUseCase`, `AppendToPdfUseCase`, `CreatePdfFromImagesUseCase` auf abstrahierte Inputs umstellen.
- Android-`Uri` nur in UI/ViewModel oder Data-Storage-Adaptern aufloesen.
- Fuer Bilder ein Domain-neutrales Modell einfuehren: `ImageBytes` oder `ImageSource`.
- `Bitmap` aus `domain/pdf/*Ops`-Signaturen entfernen oder in einen Android-spezifischen Renderer verschieben.

Akzeptanzkriterien:

- `rg "^import android" app/src/main/java/info/meuse24/pdf_scanner/domain` liefert keine Treffer.
- Domain-UseCase-Tests brauchen keine Android-Framework-Typen.

## Phase 5: OCR-Domain-Typen extrahieren

Ziel: Fachliche OCR-Konzepte sind explizit im Domain-Layer sichtbar.

Hinweis: Diese Typen existieren noch nicht als eigenstaendige Klassen. Sie muessen aus `OcrManager.kt` und `OcrPipeline.kt` extrahiert werden.

Aufgaben:

- Domain-Typen in `domain/model/` anlegen:
  - `OcrPipelineStatus` (idle, running, done, error)
  - `OcrResultStats` (confidence, pageCount, language)
  - `OcrUsage` (requestCount, modelInfo)
  - `OcrScript` (Latin, CJK, Devanagari, …)
  - `OcrThresholds` (Konfidenzgrenzen)
- `OcrManager` und `OcrPipeline` verwenden die Domain-Typen statt eigener Datenstrukturen.
- OCR-Pipeline fachlich aufteilen:
  - Domain: Status, Result, Plan/UseCase-Entscheidung
  - Data: MLKit Recognizer, Model-Installation, Android InputImage

Akzeptanzkriterien:

- Domain-Modelle fuer OCR sind reine Kotlin-Datenklassen ohne Android-Imports.
- OCR-bezogene UI-States referenzieren nur noch Domain-Typen.

## Phase 6: `HomeViewModel` entlasten

Ziel: ViewModel wird zum UI-State- und Event-Orchestrator (~300-400 Zeilen).

Aufgaben:

- `RenameDocumentUseCase` erstellen:
  - prueft Zielnamen
  - verschiebt/benennt PDF und Thumbnail
  - aktualisiert Repository
  - liefert fachliche Fehler statt UI-Strings
- `BuildScanSearchQueryUseCase` oder `HomeSearchController` erstellen:
  - kapselt `buildFtsQuery`
  - macht Suchlogik isoliert testbar
- `OcrBackfillUseCase` erstellen:
  - findet searchable Dokumente ohne extrahierten Text
  - extrahiert OCR-Text
  - aktualisiert Repository
- Review-Prompt hinter kleinen Port/UseCase setzen (Domain-Port: `ReviewPromptPolicy`), damit `HomeViewModel` nicht direkt `PlayReviewPromptManager` kennt.
- Snackbar-/Fehlertexte in UI-Mapping-Funktionen buendeln.

Akzeptanzkriterien:

- `HomeViewModel` verliert direkte `java.io.File`-Operationen.
- `HomeViewModel` hat keine direkten Abhaengigkeiten zu Android-Implementierungsklassen ausserhalb DI.
- Bestehende `HomeViewModelTest`-Faelle bleiben erhalten oder werden auf neue UseCase-Tests verteilt.

## Phase 7: PDF/OCR-Abstraktionen abrunden

Ziel: PDF- und OCR-Domain-Vertraege sind stabil und Implementierungen austauschbar.

Hinweis: `domain/pdf/` hat bereits 8 gute Port-Interfaces. Diese Phase prueft und verfeinert sie.

Aufgaben:

- `PdfRenderingOps`, `PdfMetadataOps`, `PdfTextOps`, `PdfSecurityOps`, `PdfStructureOps` auf Domain-neutrale Signaturen pruefen (kein `Bitmap`, kein `Uri`).
- `SearchablePdfBuilder` als formalen Domain-Port in `domain/gateway/` definieren; PDFBox-Impl bleibt in `data/pdf`.
- Fehler-Typen fuer OCR/PDF fachlich modellieren (statt generischer Exceptions); UI-Mapping aus Domain herausloesen.

Akzeptanzkriterien:

- PDF/OCR-UseCases koennen in Unit-Tests mit Fake-Ports laufen.
- Android-/MLKit-Klassen kommen nur noch in `data/`, `platform/` oder `ui/` vor.

## Phase 8: Optionaler Multi-Module-Umbau

Diese Phase erst angehen, wenn Package-Grenzen sauber sind und die Architekturchecks gruen sind.

Aufgaben:

- Gradle-Module `:domain`, `:data`, `:ui` oder Feature-Module einfuehren.
- Domain als Android-freies Kotlin/JVM-Modul bauen.
- Data als Android-Library mit Room, Storage, MLKit, PDFBox bauen.
- App-Modul enthaelt Application, MainActivity und Hilt-Wiring.

Akzeptanzkriterien:

- Gradle verhindert Architekturverletzungen technisch.
- `:domain:test` laeuft ohne Android Gradle Plugin.

## Empfohlene Reihenfolge fuer Umsetzung

1. Phase 1: `AppSettings`, `AppSortOrder`, `OcrQuality`, `ThemeMode` nach `domain/model`.
2. Phase 2: Pure Helper aus `util` nach `domain/common`.
3. Phase 3: Ports nach `domain/gateway`; Implementierungen in neue `data/*`-Unterverzeichnisse.
4. Phase 4: Android-Typen in `domain` pruefen — bei keinen Treffern ueberspringen.
5. Phase 5: OCR-Domain-Typen aus `OcrManager`/`OcrPipeline` extrahieren.
6. Phase 6: `HomeViewModel` durch neue UseCases entlasten.
7. Phase 7: PDF/OCR-Ports verfeinern.
8. Phase 8 (optional): Echte Gradle-Module einfuehren.

## Kontrollkommandos

Nach jedem Schritt:

```powershell
./gradlew.bat :app:assembleDebug
```

Architekturchecks:

```powershell
# Android-Imports in domain (Ziel: keine Treffer)
rg "^import android|^import androidx" app/src/main/java/info/meuse24/pdf_scanner/domain

# ui/util/data-Imports in domain (Ziel: keine Treffer)
rg "^import info\.meuse24\.pdf_scanner\.(ui|data|util)" app/src/main/java/info/meuse24/pdf_scanner/domain

# Implementierungen in domain (Ziel: keine Treffer)
rg "^import info\.meuse24\.pdf_scanner\.(util\.PdfEditor|util\.OcrManager|util\.OcrPipeline)" app/src/main/java/info/meuse24/pdf_scanner/domain
```

Zielzustand:

- Alle drei Checks liefern keine Treffer in `domain`.
- `./gradlew.bat test` ist gruен.
