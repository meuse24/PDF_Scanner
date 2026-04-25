# Plan: Landscape-Layout-Optimierung

Ziel: Im Querformat (Landscape-Phone und NavigationRail-Modus) deutlich mehr Dokumentkarten auf dem Bildschirm
zeigen. Kernproblem: Im Auswahlmodus stapeln sich TopAppBar + SelectionTitleBar + BulkActionBar auf ~172 dp
vertikale Chrome — bei ~360 dp Gerätehöhe bleiben nur ~116 dp für die Liste (~1,4 Karten). Nach der Umsetzung
soll nur die TopAppBar als Chrome verbleiben (~56 dp), was ~5 kompakte Karten ermöglicht.

## Leitplanken

- Kein UI-Verhalten im Portrait verändern — alle Änderungen sind landscape-only Guards.
- Bestehende Architektur beibehalten; kein Hoist von State, der bisher lokal ist.
- `WindowHeightSizeClass.Compact` plus aktivem NavigationRail-Modus als semantisches Signal für
  „Landscape-Phone". So werden kompakte Querformat-Phones optimiert, ohne Tablet-Landscape
  (`WindowHeightSizeClass.Medium`) oder ungewöhnliche Compact-Breiten zu verändern.
- Keine neuen Strings ohne Übersetzung in alle 10 Locales.

## Kritische Nachschärfung vor Umsetzung

Der ursprüngliche Ansatz in Phase 2/3 hebt Bulk-Callbacks von `HomeScreen` nach `AppNavigation`
hoch. Das ist für diese App unnötig riskant: Die Aktionen benötigen lokalen Home-State
(`selectedRecords`, OCR-Dialoge, Merge-Dateiname, FolderPicker, Delete-Dialog) und würden sonst
als instabile Lambda-Struktur durch `AppNavigationHost` zurückverdrahtet.

Optimierte Umsetzung:

1. `AppNavigation` blendet im Landscape-Compact-Auswahlmodus nur seine normale App-Bar aus.
2. `HomeScreen` rendert dann eine kompakte `LandscapeSelectionTopBar` direkt oberhalb der Liste.
3. Die Bulk-Aktionen bleiben dort, wo die zugehörigen Dialoge und lokalen Zustände bereits leben.
4. `SelectionTitleBar` und untere `BulkActionBar` bleiben im Portrait unverändert und werden nur
   im Landscape-Compact-Auswahlmodus unterdrückt.

Damit bleibt die Daten- und Aktionsrichtung klarer, der Scope kleiner und das Layoutziel identisch:
eine einzige obere Auswahlleiste statt TopAppBar + SelectionTitleBar + Bottom-BulkBar.

## Findings

### Finding 1 — TopAppBar bleibt im Auswahlmodus unverändert
**Datei:** `ui/navigation/AppNavigation.kt:200`

`TopAppBar` wird immer gerendert, unabhängig von `isSelectionMode`. `isSelectionMode` ist in
`AppNavigation` als `var` vorhanden (wird über `onSelectionModeChange` aus `HomeScreen` gesetzt),
wird aber nur genutzt, um den FAB zu unterdrücken (`!isSelectionMode`). Der TopAppBar-Slot bleibt
unberührt.

Im Landscape-Auswahlmodus führt das zu zwei Navigations-/Steuerleisten gleichzeitig:
- TopAppBar (Hamburger + App-Titel)
- SelectionTitleBar (✕ · „X ausgewählt" · Alle)

### Finding 2 — SelectionTitleBar als separate Surface unterhalb der TopAppBar
**Datei:** `ui/home/components/HomeArchiveContent.kt:81`

`SelectionTitleBar` ist eine eigene `Surface` (~48 dp), die innerhalb von `HomeArchiveContent`
als erstes Element gerendert wird. Sie liegt *unterhalb* der TopAppBar — im Portrait
vertretbar, im Landscape kostspielig.

### Finding 3 — BulkActionBar belegt ~68 dp am unteren Bildschirmrand
**Datei:** `ui/home/HomeScreen.kt:266`, `ui/home/HomeSelectionBar.kt`, `ui/home/components/BulkActionBar.kt:65`

`HomeSelectionBar` (Wrapper um `BulkActionBar`) wird via `Box.align(BottomCenter)` positioniert.
Der SnackbarHost bekommt in `HomeScreen.kt:316` ein hardcodiertes `vertical = 84.dp`-Padding,
das auf die BulkBar-Höhe ausgelegt ist. Das Padding greift auch im Landscape und reserviert
Platz, der dort unnötig ist.

### Finding 4 — ScanItem ist immer zweizeilig, kein Landscape-Modus
**Datei:** `ui/home/components/ScanItem.kt:161`

```
Column {
    Text(filename)          // Zeile 1: Dateiname (bis 2 Zeilen)
    Spacer(2dp)
    Row {                   // Zeile 2: Thumbnail · Metadaten · Aktionen
        Thumbnail(36dp)
        Column { subtitle; badges }
        Actions
    }
}
```

Interne Padding: `horizontal = 12.dp, vertical = 8.dp`. Äußeres Padding (aus
`HomeArchiveContent.kt:138`): `vertical = 4.dp`. Gesamt pro Item: ~80 dp im Portrait, im
Landscape identisch — zu hoch.

### Finding 5 — `heightSizeClass` wird nirgends übergeben (fehlende Basis)
**Datei:** `MainActivity.kt:59`, `ui/navigation/AppNavigation.kt:69`

`calculateWindowSizeClass(this)` liefert `WindowSizeClass`, aber nur
`windowSizeClass.widthSizeClass` wird an `AppNavigation` übergeben. `heightSizeClass` fehlt
komplett. Damit kann kein nachgelagerter Composable semantisch auf „Landscape-Phone"
(`WindowHeightSizeClass.Compact`) reagieren.

### Finding 6 — Karten-Padding summiert sich im Landscape
**Datei:** `ui/home/components/HomeArchiveContent.kt:138`, `ui/home/components/ScanItem.kt:161`

Äußeres Margin `vertical = 4.dp` (× 2 = 8 dp) + internes Card-Padding `vertical = 8.dp`
(× 2 = 16 dp) = **24 dp Padding pro Karte** nur für Spacing. Im Portrait akzeptabel; im
Landscape halbiert ein kompakterer Wert (z. B. 4 dp intern, 2 dp extern = 12 dp) den
Padding-Overhead ohne Verlust an Lesbarkeit.

---

## Umsetzungsplan

### Phase 1: `heightSizeClass` threading (Voraussetzung für alle weiteren Phasen)

**Betroffene Dateien:**
- `app/src/main/java/info/meuse24/pdf_scanner/MainActivity.kt`
- `app/src/main/java/info/meuse24/pdf_scanner/ui/navigation/AppNavigation.kt`
- `app/src/main/java/info/meuse24/pdf_scanner/ui/home/HomeScreen.kt`
- `app/src/main/java/info/meuse24/pdf_scanner/ui/home/components/HomeArchiveContent.kt`

**Umsetzung:**

1. In `MainActivity.kt` `heightSizeClass` extrahieren und als Parameter übergeben:
   ```kotlin
   val windowSizeClass = calculateWindowSizeClass(this)
   AppNavigation(
       widthSizeClass  = windowSizeClass.widthSizeClass,
       heightSizeClass = windowSizeClass.heightSizeClass,
       ...
   )
   ```

2. In `AppNavigation.kt` Parameter `heightSizeClass: WindowHeightSizeClass` ergänzen und
   `isLandscapeCompact` ableiten:
   ```kotlin
   val isLandscapeCompact =
       widthSizeClass != WindowWidthSizeClass.Compact &&
       heightSizeClass == WindowHeightSizeClass.Compact
   ```

3. `isLandscapeCompact` an `AppNavigationHost` und weiter an `HomeScreen` und
   `HomeArchiveContent` weitergeben. Naming: `isLandscapeCompact: Boolean` als
   Composable-Parameter.

**Akzeptanzkriterium:** `isLandscapeCompact` ist in `HomeArchiveContent` verfügbar und korrekt
`true` auf einem Landscape-Phone, `false` auf einem Portrait-Phone und einem Tablet im Landscape.

---

### Phase 2: TopAppBar übernimmt Auswahlmodus im Landscape

**Betroffene Dateien:**
- `app/src/main/java/info/meuse24/pdf_scanner/ui/navigation/AppNavigation.kt`
- `app/src/main/java/info/meuse24/pdf_scanner/ui/home/components/HomeArchiveContent.kt`
- `app/src/main/java/info/meuse24/pdf_scanner/ui/home/components/SelectionTitleBar.kt` (bleibt für Portrait)

**Ziel:** Im `isLandscapeCompact`-Modus wird die normale App-TopBar im Auswahlmodus
ausgeblendet. `HomeScreen` rendert stattdessen eine kompakte Auswahlleiste
`[✕] · „X ausgewählt" · [Alle auswählen] · [Bulk-Aktionen]`. Die `SelectionTitleBar`
wird im Landscape nicht mehr gerendert.

**Umsetzung:**

1. `AppNavigation.kt` — TopAppBar-Slot konditionalisieren:
   ```kotlin
   if (!(isLandscapeCompact && isSelectionMode && isHomeRoute)) {
       TopAppBar(...)
   }
   ```
2. `HomeScreen.kt` — im Landscape-Compact-Auswahlmodus `LandscapeSelectionTopBar` oberhalb
   von `HomeArchiveContent` rendern.
3. `HomeArchiveContent.kt` — `SelectionTitleBar` nur noch im Portrait rendern:
   ```kotlin
   if (isSelectionMode && !isLandscapeCompact) {
       SelectionTitleBar(...)
   }
   ```

**Akzeptanzkriterium:**
- Landscape-Phone im Auswahlmodus: eine kompakte obere Auswahlleiste mit ✕, Count, SelectAll — keine
  `SelectionTitleBar` darunter.
- Portrait unverändert: TopAppBar normal + SelectionTitleBar wie bisher.
- TalkBack: Close/Count/SelectAll in der Auswahlleiste korrekt beschriftet.

---

### Phase 3: Bulk-Aktionen im Landscape in die TopAppBar-Actions verlagern

**Betroffene Dateien:**
- `app/src/main/java/info/meuse24/pdf_scanner/ui/navigation/AppNavigation.kt`
- `app/src/main/java/info/meuse24/pdf_scanner/ui/home/HomeScreen.kt`
- `app/src/main/java/info/meuse24/pdf_scanner/ui/home/HomeSelectionBar.kt`

**Ziel:** Im `isLandscapeCompact`-Auswahlmodus erscheinen die Bulk-Aktionen in der
`LandscapeSelectionTopBar`; die `HomeSelectionBar` (BulkActionBar) unten wird nicht gerendert.

**Primäre Aktionen in der TopAppBar (4 Icons):**

| Icon | Aktion | `contentDescription` |
|---|---|---|
| `Share` | Teilen | `label_bulk_share` |
| `FolderOpen` | Ordner | `label_bulk_move_folder` |
| `FindInPage` | OCR/Searchable | `label_bulk_searchable` |
| `MoreVert` | Mehr (Export, Merge, Löschen) | `label_bulk_more` |

Das „Mehr"-Menü via `DropdownMenu` enthält Export, ggf. Merge und Löschen (rot).

**Umsetzung:**

1. `HomeSelectionBar.kt` — neue `LandscapeSelectionTopBar` mit Share, Ordner, OCR-Menü und
   Mehr-Menü ergänzen.
2. `HomeScreen.kt` — `HomeSelectionBar` nur noch zeigen wenn `!isLandscapeCompact`:
   ```kotlin
   if (isSelectionMode && !isLandscapeCompact) {
       HomeSelectionBar(...)
   }
   ```
3. SnackbarHost-Padding in `HomeScreen.kt:316` anpassen:
   ```kotlin
   vertical = if (isSelectionMode && !isLandscapeCompact) 84.dp else 16.dp
   ```

**Akzeptanzkriterium:**
- Landscape-Phone: Bulk-Icons in oberer Auswahlleiste; keine Bottom-BulkBar sichtbar.
- Portrait: BulkActionBar unten bleibt unverändert.
- Alle Aktionen (inkl. Löschen mit Bestätigungsdialog) weiterhin erreichbar.
- SnackbarHost überlappt nicht mit Navigation oder UI-Chrome.

---

### Phase 4: Kompakte ScanItem-Karte für Landscape

**Betroffene Dateien:**
- `app/src/main/java/info/meuse24/pdf_scanner/ui/home/components/ScanItem.kt`
- `app/src/main/java/info/meuse24/pdf_scanner/ui/home/components/HomeArchiveContent.kt`

**Ziel:** Im Landscape-Compact-Modus zeigt `ScanItem` alles in einer horizontalen Zeile:
Thumbnail | Dateiname + Metadaten | Aktionen. Ziel-Höhe: ≤ 44 dp Karte + 4 dp Margin = 48 dp
pro Item (statt ~80 dp).

**Layout-Schema Landscape-Variante:**

```
┌─ Card ─────────────────────────────────────────────────────────┐
│  [Thumb]  [Dateiname (1 Zeile, ellipsis)]  [subtitle]  [☆][⋮]│
└────────────────────────────────────────────────────────────────┘
```

- Thumbnail: 28 dp (statt 36 dp)
- Dateiname: `weight(1f)`, `maxLines = 1`
- Subtitle (Datum · Seiten · Größe): inline rechts vom Dateinamen oder in zweiter `Text`-Zeile
  innerhalb des weight(1f)-Blocks
- Badges (Searchable, OCR, Folder): optional, nur wenn Platz; bei Landscape auf max. 2
  begrenzen oder ganz weglassen (fokus auf Dateiname + Datum)
- Card-Padding: `horizontal = 8.dp, vertical = 4.dp` (statt 12/8)
- Außen-Margin (`HomeArchiveContent`): `vertical = 2.dp` (statt 4.dp)

**Umsetzung:**

1. `ScanItem` bekommt einen `compact: Boolean = false`-Parameter.

2. Im `compact`-Modus:
   ```kotlin
   Row(
       modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
       verticalAlignment = Alignment.CenterVertically
   ) {
       // Thumbnail 28dp
       Spacer(4.dp)
       Column(modifier = Modifier.weight(1f)) {
           Text(filename, maxLines = 1, overflow = Ellipsis,
                style = typography.bodyMedium, fontWeight = SemiBold)
           Text(subtitle, style = typography.labelSmall, color = outline)
       }
       Spacer(4.dp)
       // Aktionen (Favorit/Mehr/Checkbox) wie bisher
   }
   ```

3. `HomeArchiveContent` übergibt `compact = isLandscapeCompact` an `ScanItem`.
4. Äußeres Padding: `vertical = if (isLandscapeCompact) 2.dp else 4.dp`.

**Akzeptanzkriterium:**
- Landscape-Phone: ≥ 4 Karten sichtbar ohne Scrollen bei 360 dp Höhe.
- Portrait: `ScanItem` unverändert (keine visuellen Regressionen).
- Accessibility: `contentDescription` auf allen Aktionen auch in Compact-Variante.
- `200% Font Scale` im Landscape: Dateiname schneidet ab (ellipsis), kein Overflow.

---

### Phase 5: Tests und manuelle Verifikation

**Betroffene Dateien:**
- `app/src/test/java/info/meuse24/pdf_scanner/ui/HomeViewModelTest.kt` — prüfen ob Tests
  noch passen
- `app/src/main/java/info/meuse24/pdf_scanner/ui/home/components/HomeComponentPreviews.kt`
  — Landscape-Previews ergänzen

**Umsetzung:**

1. Previews für Landscape-Compact ergänzen:
   ```kotlin
   @Preview(showBackground = true, widthDp = 640, heightDp = 360, name = "ScanItem Landscape Compact")
   @Preview(showBackground = true, widthDp = 640, heightDp = 360, name = "BulkActionBar Landscape (TopAppBar)")
   ```

2. Manuelle Prüfung:
   - Portrait-Phone: Auswahlmodus unverändert (SelectionTitleBar + BulkBar unten).
   - Landscape-Phone: Auswahlmodus in TopAppBar; ≥ 4 Karten sichtbar.
   - Tablet Portrait (Medium/Compact): weiterhin SelectionTitleBar + BulkBar (Height = Medium,
     nicht Compact).
   - TalkBack: alle Aktionen in beiden Orientierungen erreichbar.

3. `./gradlew test` — keine Regressionen in bestehenden ViewModelTests.

---

## Empfohlene Reihenfolge

1. **Phase 1** — `heightSizeClass` threading
2. **Phase 4** — Kompakte Karte
3. **Phase 2** — App-TopBar im Landscape-Auswahlmodus ausblenden und Home-Auswahlleiste rendern
4. **Phase 3** — Bulk-Aktionen in `LandscapeSelectionTopBar`
5. **Phase 5** — Previews + manuelle Tests

## Risiken

- Landscape-Auswahlleiste mit mehreren Action-Icons kann bei schmalen Geräten oder großer Schrift
  überlaufen. Sicherheitsventil: letzte 2 Icons hinter `MoreVert` bündeln wenn `widthDp < 500`.
- Badges in der Compact-ScanItem-Variante weglassen ist ein bewusster Funktionsabbau im
  Landscape. Alternative: Badges nur auf erweiterter Karte bei Tap/Hover zeigen — nicht im
  MVP-Scope.

## Definition of Done

- `./gradlew test` erfolgreich.
- Landscape-Phone (Emulator 640×360 dp): im Auswahlmodus ≥ 4 Dokumentkarten sichtbar,
  Bulk-Aktionen über TopAppBar erreichbar.
- Portrait-Phone: visuell identisch mit vorherigem Stand.
- Tablet Landscape (Emulator 1280×800 dp): keine Regression, NavigationRail und normales
  Layout unverändert.
- TalkBack-Grundnavigation in beiden Orientierungen funktionsfähig.
