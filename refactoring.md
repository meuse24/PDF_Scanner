# Refactoring-Plan: PDF Scanner App

Dieser Plan ist auf die aktuelle Codebasis zugeschnitten. Ziel ist nicht ein breitflächiger Umbau in einem Zug, sondern eine Folge kleiner, buildbarer Schritte mit klaren Architekturgewinnen.

## Bewertung des bisherigen Plans

Der bisherige Plan trifft die Richtung, war aber an mehreren Stellen zu allgemein:

- `WorkflowErrorMapper` existiert bereits. Die korrekte Maßnahme ist daher nicht "neu einführen", sondern die doppelte Fehlerabbildung aus ViewModels zu entfernen.
- Die Framework-Kopplung betrifft nicht nur `HomeViewModel`, sondern auch `FileUtil`, mehrere UseCases sowie weitere ViewModels (`Split`, `Reorder`, `PageSelection`, `DocumentEdit`).
- Ein vollständiger Umbau von `HomeScreen` und `HomeViewModel` in einem Schritt wäre unnötig riskant. Zuerst müssen Abhängigkeiten, Dispatcher und Dateisystemzugriffe abstrahiert werden.
- `HomeScreen` ist groß, aber die gefährlichere technische Schuld liegt aktuell in direkter `Context`-Nutzung, hart verdrahteten `Dispatchers.IO`-Aufrufen und duplizierter Fehlerlogik.

## Leitlinien

- Kleine, reversible Schritte.
- Keine Verhaltensänderung ohne Test oder klaren Build-Nachweis.
- Domain-/Workflow-Logik bleibt Android-frei, soweit praktikabel.
- ViewModels kennen keine Dateisystemdetails und keine Android-`Context`-APIs direkt.

## Phase 1: Infrastruktur entkoppeln

Ziel: Android-spezifische Abhängigkeiten hinter kleine Interfaces schieben.

1. **`ResourceProvider` einführen**
   - Kapselt `getString(resId)` und `getString(resId, vararg args)`.
   - Wird von ViewModels, UseCases und Fehler-Mapping genutzt.
2. **`StorageProvider` einführen**
   - Liefert mindestens `scansDir()` und bei Bedarf `tempDir()`.
   - Zentralisiert Dateisystempfade statt mehrfacher `File(context.filesDir, "scans")`.
3. **`DispatcherProvider` einführen**
   - Liefert `main`, `io`, `default`.
   - Ersetzt direkte `Dispatchers.IO`-Zugriffe in ViewModels schrittweise.
4. **Hilt-Wiring ergänzen**
   - Eigene Module für Provider-Bindings/Provides.

## Phase 2: Home-Flow stabilisieren

Ziel: `HomeViewModel` als erste große Einstiegsklasse entkoppeln und vereinfachen.

1. **`HomeViewModel` von `Context` lösen**
   - `ResourceProvider`, `StorageProvider`, `DispatcherProvider`, `WorkflowErrorMapper` injizieren.
   - Doppelte `mapWorkflowError()`-Logik entfernen.
2. **Bestehende StateFlows zunächst beibehalten**
   - Noch kein erzwungener Big-Bang auf ein einziges `HomeUiState`.
   - Erst nach Entkopplung entscheiden, welche States sinnvoll zusammengeführt werden.
3. **One-shot Events vorbereiten**
   - `error`/`success` perspektivisch in UI-Events überführen.
   - In dieser Etappe nur dann umstellen, wenn Tests und UI-Anbindung stabil mitziehen.
4. **Dateioperationen zentralisieren**
   - Rename/Merge nutzen nur noch `StorageProvider` für Zielpfade.

## Phase 3: Weitere ViewModels angleichen

Ziel: Das gleiche Muster konsistent auf andere PDF-Bearbeitungs-Flows anwenden.

1. `SplitViewModel`
2. `ReorderViewModel`
3. `PageSelectionViewModel`
4. `DocumentEditViewModel`

Je Klasse:

- `Context` entfernen
- `Dispatchers.IO` durch `DispatcherProvider.io` ersetzen
- Fehlerabbildung über `WorkflowErrorMapper`
- `scansDir` über `StorageProvider`

## Phase 4: UI-Schicht behutsam zerlegen

Ziel: `HomeScreen` kleiner und lifecycle-sicher machen, ohne die Navigation oder Scanner-Flows zu destabilisieren.

1. **Lifecycle-aware Flow-Collection**
   - `collectAsState()` auf `collectAsStateWithLifecycle()` umstellen.
2. **Große UI-Blöcke extrahieren**
   - Search/Sort-Bar
   - Add-Document-Sheet
   - Save-Dialog
   - OCR-Result-Sheet
   - Error/Loading-Dialoge
3. **State Hoisting nur dort, wo es den Screen wirklich vereinfacht**
   - Lokaler transienter Dialog-State darf lokal bleiben.
   - Geschäftszustand bleibt im ViewModel.

## Phase 5: Tests absichern

Ziel: Refactoring ohne Regressionen.

1. **Unit-Tests für Provider-basierte ViewModels**
   - Fokus zuerst auf `HomeViewModel`.
2. **Vorhandene Workflow-/Sortier-Tests grün halten**
3. **Gezielte Tests für Fehlermapping und Dateipfad-Entscheidungen**
4. **Optional später: Repository-/Room-Integrationstests**

## Umsetzungsreihenfolge

1. Provider-Infrastruktur + Hilt-Wiring
2. `HomeViewModel` auf Provider umstellen
3. Tests für `HomeViewModel` ergänzen
4. Weitere ViewModels angleichen
5. Erst danach `HomeScreen` systematisch zerlegen

## Aktuelle Etappe

Diese Umsetzung startet mit Phase 1 und dem ersten Teil von Phase 2:

- `ResourceProvider`, `StorageProvider`, `DispatcherProvider`
- Hilt-Bindings
- `HomeViewModel` ohne direkten `Context`
- vorhandenen `WorkflowErrorMapper` wiederverwenden
- gezielte Tests/Build-Verifikation
