# Implementierungsplan: In-App-PDF-Viewer mit PdfRenderer

## Implementierungsstatus

Stand: 2026-04-23. Der optimierte Plan ist umgesetzt bis inklusive Viewer-MVP
mit Lazy-Rendering, Byte-Budget-Cache, separatem Zoom-Overlay und Viewer-
Aktionen.

Umgesetzt:

- `Screen.Viewer` und Navigation aus der Ablage.
- `PdfViewerScreen`, `PdfViewerViewModel`, `PdfViewerModels`.
- `PdfPageBitmapRenderer` mit einem `PdfRenderer` pro Dokument, `Mutex` fuer
  `openPage`, weissem Hintergrund, Bitmap-Maxkante und OOM-Fallback.
- `PdfPageBitmapCache` mit Heap-basiertem Byte-Budget.
- Sichtbare Seiten plus Nachbarseiten werden gerendert; entfernte Seiten
  verlieren ihre Bitmap-Referenz im UI-State.
- In-App-Aktionsleiste: Bearbeiten, Teilen, Exportieren, Drucken, extern
  oeffnen.
- Bearbeiten-Sheet im Viewer navigiert zu bestehenden Edit-Flows.
- `PdfDocumentIntents` zentralisiert FileProvider-/Share-/Extern-Oeffnen-
  Intents fuer Home und Viewer.
- Hilfe-/Info-Strings und Markdown-Doku sind aktualisiert.
- Unit-Tests fuer `PdfViewerViewModel` und `PdfPageBitmapCache` sind
  implementiert; `testDebugUnitTest` laeuft erfolgreich.

Nicht umgesetzt in diesem Schritt:

- Dokumentweite Suche im Viewer.
- Per-Page-Textindex und Treffer-Highlighting.
- Android-Instrumentation-Test fuer den echten Viewer-Renderpfad.

## Ziel

Die App soll PDFs direkt in der App anzeigen, statt beim Antippen eines
Archiv-Eintrags sofort einen externen PDF-Viewer per `ACTION_VIEW` zu starten.
Der Viewer soll zur bestehenden Compose-, Hilt- und Clean-Architecture-Struktur
passen und die lokale Datenschutzlinie beibehalten.

## Nicht-Ziele fuer die erste Version

- Keine externe PDF-Viewer-Bibliothek und kein kommerzielles SDK.
- Keine echte PDF-Textauswahl.
- Keine PDF-Formulare, Bookmarks, Links oder Inhaltsverzeichnis.
- Kein Tile-Renderer fuer extrem grosse Seiten.
- Keine Aenderung an PDF-Bearbeitungslogik, OCR, Redaction oder Export.

## Aktueller Kontext

- `ui/home/HomeScreen.kt` oeffnet PDFs aktuell ueber `FileProvider` und
  `Intent.ACTION_VIEW` (Zeilen 329-348). Dieselbe Logik existiert ein zweites
  Mal fuer den Bulk-Share-Pfad.
- `ui/navigation/Screen.kt` und `ui/navigation/AppNavigation.kt` enthalten die
  zentralen Compose-Routen. Alle Edit-Routen folgen dem Muster
  `screen/{scanId}` mit `createRoute(scanId: Long)`.
- `data/local/ScanRecord.kt` liefert `id`, `filename`, `filepath`, `pageCount`,
  `fileSize`, `thumbnailPath`, `isSearchable`, `isEncrypted`, `extractedText`
  und `tags`.
- `data/repository/ScanRepository.kt` beobachtet alle Scans per Flow
  (`getAllScans()`), inkl. FTS-Suche.
- `util/PdfPageJpgRenderer.kt` zeigt das bereits etablierte Muster
  `ParcelFileDescriptor` + `PdfRenderer` + `Bitmap` + weisser Hintergrund vor
  `page.render(...)`. Dort wird allerdings fuer jede Seite ein frischer
  `PdfRenderer` geoeffnet — das ist fuer Batch-Export OK, aber fuer einen
  interaktiven Viewer zu teuer.
- `util/PdfPageInputImageLoader.kt`, `util/PdfEditor.kt`,
  `util/PdfEditorRedactionOps.kt`, `util/SearchablePdfBuilder.kt` und
  `util/PdfPrintAdapter.kt` nutzen ebenfalls `PdfRenderer`.
- `ui/shared/PdfViewportMath.kt` enthaelt bereits `clampPanOffset`,
  `mapViewportOffsetToCanvasOffset`, `normalizeViewportPoint` und
  `formatZoomScale`.
- `ui/annotate` und `ui/redact` enthalten bereits Compose-Gestenlogik mit
  `rememberTransformableState`, `graphicsLayer` und gerenderten PDF-Bitmaps.
- `util/DispatcherProvider.kt` + Hilt-Bereitstellung in
  `di/AppProvidersModule.kt`. Viewer muss denselben Provider verwenden.
- `util/ResourceProvider.kt` fuer `stringResource`-aehnliche Aufrufe im
  ViewModel (Fehler-Mapping ohne Context-Leak).
- Konvention der bestehenden ViewModels: Direkter Repository-Zugriff per
  `ScanRepository`, `SavedStateHandle["scanId"]`, Loading/Error/Success
  ueber `MutableStateFlow`, Fehler ueber `WorkflowErrorMapper` wenn Workflow
  involviert ist — fuer reinen Viewer ohne Workflow reicht eigener Mapper.

## Zielbild fuer Version 1

Der erste Viewer soll ein stabiler, lokaler Reader sein:

- Archiv-Eintrag oeffnet `PdfViewerScreen(scanId)`.
- PDF wird seitenweise mit Android `PdfRenderer` gerendert.
- Vertikales Scrollen durch alle Seiten.
- Fit-width-Darstellung mit weissem Seitenhintergrund.
- Pinch-Zoom und Pan fuer die aktive bzw. sichtbare Seite.
- Seitenanzeige, Dateiname und Basisaktionen.
- Aktionen im Viewer: Zurueck, Bearbeiten-Menue, Teilen, Exportieren, Drucken,
  extern oeffnen.
- Fehlerzustand fuer fehlende, unlesbare oder verschluesselte PDFs.
- Externer Viewer bleibt als Fallback und als explizite Aktion erhalten.
- Datei-Aenderungen aus anderen Screens (z. B. Rotate, Reorder, Redact)
  werden beim Zurueckkehren in den Viewer reflektiert.

## Vorgeschlagene Dateistruktur

Neue Dateien:

- `ui/viewer/PdfViewerScreen.kt`
- `ui/viewer/PdfViewerViewModel.kt`
- `ui/viewer/PdfViewerModels.kt` (UiState, PageState, Fehler-Enum)
- `ui/viewer/PdfViewerActions.kt` (Callback-Bundle zur Uebergabe an `AppNavigation`)
- `util/PdfPageBitmapRenderer.kt` (Interface + `AndroidPdfPageBitmapRenderer`)
- `util/PdfPageBitmapCache.kt` (kleiner LRU-Cache fuer gerenderte Seiten)
- `di/ViewerModule.kt` (Hilt-Bindings: `PdfPageBitmapRenderer` → `AndroidPdfPageBitmapRenderer`)
- `test/.../ui/viewer/PdfViewerViewModelTest.kt`
- `test/.../util/PdfPageBitmapCacheTest.kt`
- `androidTest/.../ui/viewer/PdfViewerInstrumentedTest.kt`

Bestehende Dateien erweitern:

- `ui/navigation/Screen.kt` → `Screen.Viewer`.
- `ui/navigation/AppNavigation.kt` → Composable-Route + TopBar-Titel +
  `gesturesEnabled`-Ausnahme fuer den Viewer (Drawer soll beim aktiven
  Pan/Zoom nicht dazwischenreissen).
- `ui/home/HomeScreen.kt` → `onOpenRecord` ruft `onNavigateToViewer(record.id)`.
- `ui/home/components/ScanItem.kt` (optional) → neue `ScanAction.OpenExternally`
  falls extern oeffnen aus der Archivliste weiterhin moeglich sein soll.
- `ui/settings/SettingsScreen.kt` + `SettingsRepository.kt` (optional) →
  Toggle `default_pdf_open_mode` (InApp/Extern) fuer Power-User.
- `ui/shared/PdfShareUtils.kt` (neu, ausgelagert) → `buildContentUri(record)`
  + `shareIntent(records)`, damit die drei aktuellen Duplikate (HomeScreen,
  BulkActionBar, Viewer) dieselbe Implementierung nutzen. Refactor schmal
  halten: nur die drei Aufrufer umstellen.
- `res/values*/strings.xml` (10 Locales) fuer neue UI-Texte.

Clean-Architecture-Hinweis: `PdfPageBitmapRenderer` ist bewusst in `util/`
(Framework-nahe Infrastruktur). Er bleibt ein Interface, damit das ViewModel
im Unit-Test von `android.graphics.pdf.PdfRenderer` entkoppelt ist. Es wird
**kein UseCase im `domain/`-Layer** angelegt: es gibt keine Business-Regel,
nur Rendering. UseCases im Projekt kapseln Persistenz/Workflow — das passt
hier nicht.

## Architektur

### Rendering-Service

```kotlin
interface PdfPageBitmapRenderer {
    suspend fun openDocument(file: File): PdfDocumentHandle
    // handle haelt internen PdfRenderer + ParcelFileDescriptor offen
}

interface PdfDocumentHandle : AutoCloseable {
    val pageCount: Int
    suspend fun pageSizePt(pageIndex: Int): PageSize
    suspend fun renderPage(pageIndex: Int, targetWidthPx: Int): Bitmap
}
```

Implementierungsregeln fuer `AndroidPdfPageBitmapRenderer`:

- oeffnet `ParcelFileDescriptor.open(file, MODE_READ_ONLY)`.
- oeffnet **genau einen** `PdfRenderer` pro Dokument-Session und haelt ihn
  offen, bis das Handle geschlossen wird. `PdfRenderer`-Konstruktor und
  `openPage` sind teuer (I/O + Parsing); fuer einen Viewer darf das nicht pro
  Seite wiederholt werden (so wie in `PdfPageJpgRenderer.renderPages` fuer
  Batch-Export).
- **Thread-Safety:** `PdfRenderer` ist **nicht** thread-safe. `openPage` darf
  nicht parallel laufen, und es darf **maximal eine Page gleichzeitig offen
  sein** — `openPage` wirft sonst `IllegalStateException`. Loesung: interner
  `Mutex` serialisert `renderPage`. Alle Renderings laufen auf
  `dispatcherProvider.io`.
- rendert mit `PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY`.
- fuellt Bitmap vor dem Rendern weiss (`Canvas(bitmap).drawColor(WHITE)`),
  damit transparente Seiten korrekt wirken — Muster aus
  `PdfPageJpgRenderer`.
- `targetWidthPx` wird auf Bitmap-Maxkante (z. B. 4096 px) geclamped und
  aspektratio-erhaltend in `targetHeightPx` uebersetzt.
- Bitmap-Config: `ARGB_8888` als Default; bei `OutOfMemoryError`-Retry mit
  `RGB_565` und halbiertem `targetWidthPx`.
- `close()` gibt die aktuelle Page, den Renderer und das Pfd frei — idempotent.

Wichtig: `PdfRenderer` ist Android-Framework-Code. Der Renderer bleibt in
`util/` und wird im Unit-Test durch ein Fake-Interface ersetzt.

### Viewer ViewModel

`PdfViewerViewModel` (`@HiltViewModel`, `@Inject` konstruiert):

- Abhaengigkeiten: `ScanRepository`, `PdfPageBitmapRenderer`, `ResourceProvider`,
  `DispatcherProvider`, `SavedStateHandle`.
- liest `scanId` aus `SavedStateHandle`.
- beobachtet `ScanRepository.getAllScans()` (wie `DocumentEditViewModel`) und
  nimmt den Record mit passender `id`. Flow statt einmaliges `first()`, damit
  Updates nach Edit-Aktionen durchschlagen.
- wenn sich `filepath` **oder** `fileSize` aendern, wird das Dokument-Handle
  neu geoeffnet und der Bitmap-Cache geleert. `fileSize` mit rein, weil
  atomares Ueberschreiben (z. B. Reorder mit `saveAsCopy=false`) den
  Dateipfad beibehaelt, aber den Inhalt aendert.
- prueft Datei-Existenz, `isEncrypted` und `pageCount` vor dem ersten Render.
- verwaltet `PdfViewerUiState` als `StateFlow`.
- rendert Seiten ueber `PdfPageBitmapRenderer` auf `dispatcherProvider.io`.
- dedupliziert Render-Jobs pro `(pageIndex, targetWidthPx)` ueber eine
  `Map<Int, Job>` plus Mutex.
- cancelt laufende Jobs, wenn die Seite aus dem sichtbaren Fenster rollt,
  der Viewport breiter gerendert werden muss oder das Dokument wechselt.
- persistiert Scroll-/Zoom-Positionen in `SavedStateHandle`
  (`currentPageIndex`, `currentZoomScale`), damit Prozess-Tod/Rotation den
  Reading-Position nicht verliert.
- schliesst in `onCleared()` das Dokument-Handle und leert den Cache.

### State-Modelle

```kotlin
data class PdfViewerUiState(
    val record: ScanRecord? = null,
    val loading: Boolean = true,
    val error: ViewerError? = null,
    val pageCount: Int = 0,
    val currentPageIndex: Int = 0,
    val zoomScale: Float = 1f,
    val pages: Map<Int, PdfPageState> = emptyMap(),
)

data class PdfPageState(
    val pageIndex: Int,
    val widthPt: Float,
    val heightPt: Float,
    val bitmap: Bitmap? = null,
    val renderedWidthPx: Int = 0,
    val loading: Boolean = false,
    val error: ViewerError? = null,
)

enum class ViewerError {
    FileMissing,
    FileEncrypted,
    FileCorrupted,
    RendererFailure,
    OutOfMemory,
}
```

- `ViewerError` als Enum, damit `ResourceProvider` lokalisierte Strings
  mapped (analog `WorkflowErrorMapper`).
- `widthPt`/`heightPt` aus `PdfRenderer.Page.getWidth()/getHeight()` in
  PDF-Punkten (1/72 inch). Daraus wird aspectRatio fuer Placeholder
  abgeleitet, noch bevor die Bitmap existiert.

### Bitmap-Cache

Version 1 nutzt einen eigenen, an das ViewModel gebundenen `LruCache`
(`androidx.collection.LruCache`, **nicht** den deprecated
`android.util.LruCache`):

- Cache-Key: `CacheKey(pageIndex, targetWidthPx)`.
- Sizing per `sizeOf` anhand `Bitmap.allocationByteCount`.
- Budget dynamisch: `min(maxMemoryBytes / 8, 96 MB)`, wobei
  `maxMemoryBytes = Runtime.getRuntime().maxMemory()`. Fixes Anzahl-Limit
  ist schlecht, weil Seitengroessen extrem variieren.
- Warmhaltung: sichtbare Seiten plus je eine Seite davor und danach.
- `entryRemoved(...)` recycelt Bitmaps **nicht sofort**. Grund: eine Bitmap
  kann gerade noch von Compose gezeichnet werden. Stattdessen:
  - Recycling erfolgt im `onCleared()` des ViewModels.
  - Bei Dokumentwechsel wird der Cache geleert und die Bitmaps werden in
    einer Queue markiert, die nach einem Compose-Frame-Delay (`withFrameNanos`
    + `awaitFrame`) recycelt wird. Einfachere Variante: gar nicht recyceln
    und dem GC vertrauen — praktikabel, solange Cache-Budget an Heap gebunden
    ist.
- Kein globaler Singleton-Cache. Der Cache bleibt an den Viewer gebunden,
  damit Speicher nach dem Verlassen frei wird.

### Zwei-Stufen-Rendering (Polish-Phase)

Um sichtbare Latenz zu reduzieren:

1. Sofort: `thumbnailPath` des Records als Vorschau einblenden (bereits
   gecached, i. d. R. < 200 KB).
2. Erste Stufe: Seite mit `targetWidthPx = layoutWidthPx` rendern.
3. Zweite Stufe: Bei Zoom `> 1.5f` High-Res-Render mit
   `targetWidthPx = layoutWidthPx * zoomScale` nachziehen. Nur fuer die
   aktive Seite.

## Navigation

1. `Screen.Viewer : Screen("viewer/{scanId}")` plus
   `createRoute(scanId: Long) = "viewer/$scanId"`.
2. In `AppNavigation`:
   - `HomeScreen` erhaelt `onNavigateToViewer: (Long) -> Unit`.
   - `NavHost` bekommt `composable(Screen.Viewer.route, arguments = listOf(navArgument("scanId") { type = NavType.LongType }))`.
   - `AppBarTitle` fuer die Viewer-Route zeigt `record.filename` (aus State
     ableitbar, Fallback `pdf_viewer_screen_title`).
   - Viewer-Route gehoert **nicht** zu den Top-Level-Routen — `gesturesEnabled`
     bleibt auf Drawer-Seite `false` (analog zu den Edit-Screens), sonst
     kollidiert der Drawer-Swipe mit dem Pan-Gestus.
3. In `HomeScreen`:
   - `onOpenRecord = { record -> onNavigateToViewer(record.id) }`.
   - Die bisherige `ACTION_VIEW`-Logik wird zu `openExternally(record)` und
     wird nur noch vom Viewer bzw. optional einem neuen `ScanAction.OpenExternally`
     aufgerufen.
4. Edit-Screens navigieren weiterhin aus `HomeScreen` heraus. Wenn der Viewer
   ein Edit-Ziel anfordert, geschieht das ueber Callbacks, die in
   `AppNavigation` auf dieselben `navController.navigate(Screen.X.createRoute(id))`
   zeigen — **kein** zweiter Navigationsgraph.

## UI-Konzept

### Grundlayout

`PdfViewerScreen` (`Scaffold`-kompatibler Inhalt, TopBar aus `AppNavigation`):

- `Box(Modifier.fillMaxSize())`:
  - `LazyColumn` fuer Seiten mit `rememberLazyListState`.
  - `contentPadding = PaddingValues(vertical = 12.dp, horizontal = 16.dp)`.
  - `verticalArrangement = Arrangement.spacedBy(12.dp)`.
  - Floating page indicator (`Surface` mit `primaryContainer`, Text "3 / 12"),
    ausgeblendet nach 1.5 s Inaktivitaet.
  - Bottom action row **oder** Overflow-Menue im TopBar — Entscheidung unten.
  - Lade- und Fehlerzustaende als eigene Slots (vollflaechig bei globalen
    Fehlern, inline bei Seitenfehlern).
- Edge-to-edge: `Modifier.safeDrawingPadding()` fuer TopBar- und Bottom-Bar-
  Inhalte, gleich wie im Rest der App.

### Seitenanzeige

`LazyColumn { items(pageCount, key = { it })` — der Key ist wichtig, damit
Compose Items beim Dokument-Update korrekt wiederverwendet.

Pro Seite:

- `fillMaxWidth()` mit horizontalem Padding.
- `aspectRatio(page.widthPt / page.heightPt)` sobald bekannt — verhindert
  Layout-Sprunge, wenn Bitmap erst spaeter ankommt.
- Placeholder: `Surface(color = Color.White, tonalElevation = 1.dp)` mit
  `CircularProgressIndicator` und optionalem Thumbnail als Hintergrund.
- Bitmap als `Image(bitmap.asImageBitmap(), contentDescription = stringResource(R.string.pdf_viewer_page_content_description, pageIndex + 1))`.
- Weisser Seitenhintergrund und dezenter `shadow(2.dp, RoundedCornerShape(4.dp))`.
- `onGloballyPositioned` meldet sichtbare Seiten ans ViewModel (alternativ:
  `snapshotFlow { listState.layoutInfo.visibleItemsInfo.map { it.index } }`
  mit `distinctUntilChanged()` im Screen-`LaunchedEffect`).

### Zoom und Pan

Pragmatischer Ansatz fuer Version 1:

- **Mehrseitiger Scroll-Modus** ist Default mit Fit-width, `zoomScale = 1f`.
- **Einzelseiten-Zoommodus**: Tap (oder Doppel-Tap) auf Seite schaltet in
  Vollbild-Zoom fuer genau diese Seite. Implementiert als Overlay
  (`Dialog` oder zweite Composable-Phase) — vermeidet Gestenkonflikte mit
  `LazyColumn` vollstaendig.
- Im Zoommodus:
  - `rememberTransformableState` fuer Pinch + Pan.
  - `zoomScale`-Grenzen: `1f..5f`.
  - `clampPanOffset` aus `ui/shared/PdfViewportMath.kt` wiederverwenden.
  - Doppel-Tap togglet 1.0x ↔ 2.0x, Fokuspunkt in Tap-Koordinaten.
  - Bei `zoomScale > 1.5f` triggert das ViewModel das High-Res-Re-Render
    fuer diese Seite.
- Begruendung: Pinch-Zoom innerhalb eines `LazyColumn`-Items ist technisch
  machbar, aber extrem fehleranfaellig (Scroll- vs. Pan-Gestenkonflikt,
  Nested-Scroll-Consumption, Bitmap-Aufloesungs-Churn bei Scroll). Zwei
  klar getrennte Modi sind im MVP stabiler und entsprechen dem Vorgehen
  der `annotate`- und `redact`-Screens, die ebenfalls im Einzelseiten-
  Kontext arbeiten.

## Aktionen im Viewer

MVP-Aktionen im TopBar-Overflow und/oder BottomBar:

- **Teilen**: neue Utility `PdfShareUtils.shareIntent(records)`. Reduziert
  aktuelle Duplikation in `HomeScreen.kt` und `BulkActionBar`.
- **Drucken**: `PrintManager.print(...)` mit bestehendem `PdfPrintAdapter`
  (siehe `HomeScreen.kt:372-383`).
- **Extern oeffnen**: bisherige `FileProvider` + `ACTION_VIEW`-Logik als
  expliziter Menuepunkt.
- **Bearbeiten**: Bottom Sheet mit denselben Eintraegen wie in `ScanItem.kt`.
  Aus Redundanzgruenden das vorhandene `ScanAction`-Sheet oeffnen — direkt
  aus dem Viewer `onAction(record, ScanAction.X)` an `HomeScreen`-Callbacks
  delegieren (per Lambda in `AppNavigation`).
- **Exportieren (MediaStore)**: `HomeViewModel.exportToDownloads(records)`
  bietet das schon. In Version 1 den Viewer-Call als einfachen Lambda-
  Durchreicher einbauen; keine Refactor-Runde durch `HomeViewModel`.

Nicht alles muss in Version 1 direkt im Viewer liegen. Wichtig ist, dass der
externe Viewer nicht mehr der Standardpfad ist.

## Suche (Phase 5)

Version 1 schiebt Suche nach hinten. Version 2:

- Suchfeld in TopBar.
- Wenn `record.extractedText` vorhanden ist, dokumentweite Suche mit
  Snippet-Liste. Sprung zu Seite funktioniert **nicht**, solange kein
  Seitenindex zum Text existiert.
- Wenn kein OCR-Text vorhanden ist, CTA "OCR / Durchsuchbar machen"
  navigiert zu bestehendem Flow.

Spaeter:

- Pro-Seite-Text via `PdfEditor.extractTextLines(file, pageIndex)` (gibt es
  bereits, aber `TextLine` haelt nur Koordinaten ohne Text — Schema muss
  erweitert werden, bevor Highlight-Overlays moeglich sind).
- Persistenter Per-Page-Index oder On-Demand-Extraction mit Cache.

## Fehlerfaelle

Konkret abdecken:

- **Datei fehlt** → `ViewerError.FileMissing` → Vollflaechiger Fehler,
  Button "Zurueck zur Ablage".
- **PDF ist verschluesselt** (`record.isEncrypted` **oder** `PdfRenderer`
  wirft `SecurityException` beim Oeffnen) → `FileEncrypted` → Hinweis +
  Aktionen "Oeffnungspasswort entfernen" (falls noch nicht) und "Extern
  oeffnen".
- **`PdfRenderer` wirft `IOException`** → `FileCorrupted` → allgemeiner
  Lesefehler, Retry-Button.
- **`pageCount <= 0`** → `FileCorrupted`.
- **`OutOfMemoryError`** beim Bitmap-Alloc → Retry mit `RGB_565` und
  halbiertem `targetWidthPx`; wenn auch das scheitert, `ViewerError.OutOfMemory`
  mit Hinweis. Wichtig: OOM nicht propagieren, `catch (oom: OutOfMemoryError)`
  im Renderer-Interna.
- **Seitenfehler (Render einer Einzelseite schlaegt fehl, Dokument OK)** →
  `PdfPageState.error` inline, andere Seiten rendern weiter.

## Performance- und Speicherregeln

- Rendering immer auf `dispatcherProvider.io`.
- Ein `PdfRenderer` pro geoeffnetem Dokument, **nicht** pro Seite.
- Nie alle Seiten eines langen PDFs gleichzeitig rendern.
- Maximal sichtbare Seiten plus ±1 rendern.
- Bitmap-Maxkante fuer MVP: 2048 px in Fit-width; bis 4096 px im High-Res-
  Zoom-Re-Render. Clamp serverseitig im Renderer.
- Bitmap-Cache in Bytes (Heap-%), nicht in Anzahl.
- Render-Jobs pro `(pageIndex, targetWidthPx)` deduplizieren; Wechsel auf
  groesseres `targetWidthPx` cancelt den kleineren Job.
- `Bitmap.Config.RGB_565` als OOM-Fallback.
- Keine Dateikopien fuer normales Anzeigen erstellen.
- Mutex-Serialisierung im Renderer (PdfRenderer ist single-threaded).

## Datenschutz

- Keine neue Internet-Berechtigung.
- Keine Cloud- oder SDK-Abhaengigkeit.
- Nur lokale Dateien aus `record.filepath` lesen.
- Externes Teilen/Oeffnen bleibt explizite Nutzeraktion.
- Datenschutztext muss nicht angepasst werden, weil keine neuen Datenfluesse
  entstehen. `docs/privacy-policy.html` unveraendert.

## Tests

### Unit-Tests

`PdfViewerViewModelTest` (UnconfinedTestDispatcher, Fake-Renderer):

- laedt Record anhand `scanId`.
- meldet `FileMissing` bei fehlender Datei.
- meldet `FileEncrypted` bei `isEncrypted = true`.
- rendert sichtbare Page-Indizes (Aufruf-Zaehler am Fake).
- rendert nicht doppelt, wenn Page bereits mit derselben `targetWidthPx`
  geladen ist.
- cancelt laufenden Render, wenn Page aus Sichtfenster rollt.
- leert Cache und oeffnet Handle neu, wenn sich `filepath` aendert.
- leert Cache und oeffnet Handle neu, wenn sich `fileSize` aendert (atomares
  Ueberschreiben aus Reorder).
- behandelt `RendererFailure`.
- persistiert `currentPageIndex` und `zoomScale` in `SavedStateHandle`.

`PdfPageBitmapCacheTest`:

- respektiert Byte-Budget.
- `entryRemoved` wird korrekt aufgerufen.
- Cache-Key beruecksichtigt `targetWidthPx`.

Fuer Unit-Tests `PdfPageBitmapRenderer` und `PdfDocumentHandle` als Fakes
(Dummy-Bitmaps mit bekannter Size).

### Instrumentation-Tests

`PdfViewerInstrumentedTest` (auf echter PDF-Fixture aus `PdfTestFixtures`):

- kleines 1-Seiten-PDF rendert und zeigt Bitmap (Pixel-Nicht-Weiss-Check).
- mehrseitiges PDF (6 Seiten) rendert, Scrollen zu Seite 4 laedt neue
  Seiten, entfernt Seite 0.
- verschluesselte PDF zeigt `FileEncrypted`-UI statt Crash.
- beschaedigte PDF zeigt `FileCorrupted`-UI.
- Rotation des Device: `currentPageIndex` bleibt erhalten.

Bestehende Fixtures aus `PdfTestFixtures.kt` und
`ImportAndPdfEditorInstrumentedTest.kt` wiederverwenden — u. a. fuer ein
verschluesseltes Fixture-PDF.

## Umsetzung in Phasen

### Phase 1: Route und Basis-Viewer

- `Screen.Viewer` anlegen, `AppNavigation`-Composable-Route ergaenzen,
  TopBar-Titel dynamisch.
- `HomeScreen.onOpenRecord` auf `onNavigateToViewer` umstellen; alte
  `ACTION_VIEW`-Logik in `openExternally(record)` extrahieren.
- `PdfPageBitmapRenderer` + Hilt-Binding.
- `PdfViewerViewModel` mit Record-Laden, Fehlerzustaenden, Minimal-State.
- `PdfViewerScreen` mit fit-width `LazyColumn`, Placeholder, Image-Render.
- "Extern oeffnen" als TopBar-Overflow-Menuepunkt.
- **Akzeptanz:** einseitiges und mehrseitiges PDF sichtbar, Back navigiert
  zurueck, verschluesselte Datei zeigt Fehler.

### Phase 2: Sichtbare Seiten und Cache

- Sichtbare Seiten aus `LazyListState.layoutInfo` via `snapshotFlow`.
- Nur sichtbare Seiten ± 1 rendern.
- `PdfPageBitmapCache` mit Byte-Budget.
- Render-Jobs deduplizieren und abbrechen.
- **Akzeptanz:** 20-Seiten-PDF scrollt ohne Speicher-Explosion.

### Phase 3: Zoom und Reader-Polish

- Einzelseiten-Zoommodus (Tap oeffnet, Back schliesst).
- Pinch-Zoom/Pan mit `rememberTransformableState` + `clampPanOffset`.
- Floating page indicator.
- Doppel-Tap-Zoom 1.0x ↔ 2.0x.
- High-Res-Re-Render bei `zoomScale > 1.5f`.
- Thumbnail-Vorschau als Placeholder.
- Fehler- und Empty-States polieren.

### Phase 4: Aktionen integrieren

- `PdfShareUtils` extrahieren und in HomeScreen/BulkActionBar + Viewer nutzen.
- Drucken-Menuepunkt.
- Bearbeiten-BottomSheet mit `ScanAction`-Liste; Callbacks werden in
  `AppNavigation` auf dieselben Navigation-Routen wie im HomeScreen gemappt.
- Optional Exportieren direkt aus Viewer.
- Optional `ScanAction.OpenExternally` in `ScanItem.kt`.

### Phase 5: Suche

- Einfache dokumentweite Suche ueber `record.extractedText`.
- Snippets anzeigen.
- Seiten-Sprung, wenn Seitenzuordnung im Text vorhanden.
- Spaeter: per-page OCR/Text-Index fuer echte Treffer-Navigation.

## Akzeptanzkriterien fuer Version 1 (Phase 1–3)

- Antippen eines Dokuments in der Ablage oeffnet den In-App-Viewer.
- Ein normales einseitiges PDF wird sichtbar und nicht leer gerendert.
- Ein mehrseitiges PDF (min. 20 Seiten) laesst sich fluessig vertikal
  durchblaettern, ohne dass alle Seiten gleichzeitig im Speicher liegen.
- Tap auf Seite oeffnet Einzelseiten-Zoommodus mit Pinch + Pan.
- Zoomstufe > 1.5x triggert ein schaerferes Re-Render (nach Polish-Phase).
- Zurueck (System + TopBar) fuehrt zur Ablage.
- Extern oeffnen ist im TopBar-Overflow erreichbar.
- Bei fehlender, ungueltiger oder verschluesselter Datei stuerzt der Viewer
  nicht ab, zeigt lokalisierte Fehlermeldung.
- Rotation / Prozess-Tod erhaelt aktuelle Seite und Zoom.
- Edit-Aktionen aus dem Viewer (Rotate, Reorder, ...) zeigen nach Rueckkehr
  das aktualisierte PDF, ohne Reopen der App.
- Keine neue Netzwerk- oder Kamera-Berechtigung.
- `assembleDebug`, `./gradlew test` und die neuen Instrumentation-Tests
  laufen gruen.

## Wichtige Fallstricke

1. **`PdfRenderer`-Thread-Safety:** nur ein offener `openPage` gleichzeitig.
   Mutex im Renderer-Handle ist Pflicht, sonst `IllegalStateException`.
2. **Bitmap-Recycling vs. Compose:** Eine Bitmap, die Compose noch
   zeichnet, darf nicht recycelt werden. Im Zweifel Recycling an
   `onCleared()` binden und GC vertrauen.
3. **Atomares Dateiueberschreiben:** Reorder/Delete-Pages-Use-Cases mit
   `saveAsCopy=false` aendern `filepath` nicht, aber `fileSize`. Cache muss
   auf `fileSize` reagieren.
4. **Gestenkonflikt `LazyColumn` + `transformable`:** Im MVP vermeiden,
   indem Zoom im separaten Einzelseiten-Overlay laeuft.
5. **`onCleared` + laufende Coroutines:** `viewModelScope` wird automatisch
   gecancelt, aber `PdfDocumentHandle.close()` muss in `onCleared()`
   **ausserhalb** von `viewModelScope` erfolgen (Scope ist dann schon
   canceled). Option: `Dispatchers.IO` + `GlobalScope.launch`-Fallback
   nicht verwenden, stattdessen `close()` synchron auf dem Hauptthread —
   `PdfRenderer.close()` ist schnell.
6. **Strings in 10 Locales:** jeder neue Viewer-String muss in `values/`,
   `-de`, `-es`, `-fr`, `-pt`, `-zh-rCN`, `-ar`, `-ja`, `-ru`, `-hi`.
7. **Drawer-Gesten:** Viewer-Route darf den Drawer-Swipe nicht aktiv haben,
   sonst faengt der Drawer horizontale Pan-Gesten ab.
8. **Backup-Rules:** `filesDir/scans/` ist bereits ausgeschlossen — nichts
   zu tun, aber pruefen, falls neue Caches eingefuehrt werden (`cacheDir`
   ist unproblematisch).

## Offene Entscheidungen vor Implementierung

- **Zoom-Modus:** Mehrseitig integriert oder Einzelseiten-Overlay? Plan
  empfiehlt Overlay fuer Phase 3. Bestaetigt der User?
- **Viewer-Aktionen-Umfang:** Nur Teilen/Drucken/Extern in Phase 4, oder
  direkt das volle `ScanAction`-Sheet?
- **Settings-Toggle "Default-Open-Mode":** Phase 4 oder erst spaeter?
- **Bitmap-Cache-Budget:** `1/8` des Heaps plus `96 MB` Cap OK, oder
  niedriger fuer schwache Geraete (z. B. `1/16` + `48 MB` Cap)?
- **Suche in Phase 1?** Plan empfiehlt explizit Phase 5 — bestaetigen.
- **`PdfShareUtils`-Refactor in Phase 4 oder direkt Phase 1?** Duplikate in
  HomeScreen existieren bereits; sauberer waere Phase 1.
