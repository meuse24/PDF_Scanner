# Plan: Smart Document Cockpit – Phase 1 (lokal, ohne ML Kit)

Umsetzungsvorschlag für den risikoarmen ersten Teil des „Smart Document Cockpit"-Vorschlags:

1. Viewer-Volltextsuche mit Sprung zur Trefferseite
2. „IBAN kopieren" / „Betrag kopieren"
3. „Termin erstellen" (Kalender-Intent)

Kein ML Kit, keine neue Abhängigkeit, keine neue Runtime-Permission. Entity-Erkennung (Phase 2, ML Kit hinter Gateway) und Dateinamen-/Ordner-Vorschläge (Phase 3) sind bewusst **nicht** Teil dieses Plans.

**Status:** Umgesetzt und nach Code-Review korrigiert am 1. Juli 2026. Verifiziert mit vollständigen JVM-Unit-Tests, `compileDebugKotlin`, `compileDebugAndroidTestKotlin`, `lintDebug` und `assembleDebug`. Zusätzlich liefen alle 41 Instrumentation-Tests auf einem Samsung SM-A536B erfolgreich. Veraltete Instrumentation-Fake-DAOs wurden durch echte In-Memory-Room-Datenbanken ersetzt.

## Ist-Zustand (verifiziert im Code)

- `Document.pageTexts: List<String>` (`domain/model/Document.kt`) enthält OCR-Seitentexte einschließlich leerer Platzhalter. Die Seitensuche wird nur aktiviert, wenn `pageTexts.size == pageCount`. Falsch ausgerichtete Altbestände werden beim Start in begrenzten Batches sowie über „durchsuchbar machen“ metadatenbasiert repariert; ein bestehender PDF-Textlayer wird dabei nicht erneut geschrieben.
- `PdfViewerUiState` (`ui/viewer/PdfViewerModels.kt`) kennt aktuell nur `pageCount`, `currentPageIndex`, `zoomScale`, `pages` (Bitmap-Cache) — **kein** Suchzustand.
- `PdfViewerViewModel` (`ui/viewer/PdfViewerViewModel.kt`) steuert `currentPageIndex` bisher nur reaktiv aus dem sichtbaren Scroll-Bereich (`onVisiblePagesChanged`, Zeile 121). Es gibt noch keinen Weg, programmatisch zu einer Seite zu springen.
- `PdfViewerScreen.kt` rendert die Seiten in einer `LazyColumn` mit eigenem `listState` (Zeile 134, 264) — kein `TopAppBar`/Scaffold in dieser Datei; die globale Titelleiste kommt aus der Navigation-Shell. Für Suchleiste und Entity-Chips ist es am einfachsten, sie **innerhalb** von `PdfViewerScreen` als Overlay/Zusatzzeile einzubauen, ohne die Shell anzufassen.
- `AutoTagUseCase` (`domain/usecase/AutoTagUseCase.kt`, Zeilen 37–40) hat bereits funktionierende, private Regex für IBAN und Betrag. Diese sollten wiederverwendet, nicht dupliziert werden.
- `util/PdfDocumentIntents.kt` zeigt das etablierte Muster für Intent-Builder (`buildPdfViewIntent`, `openPdfExternally` mit `ActivityNotFoundException`-Fallback) — Vorlage für einen neuen Kalender-Intent.
- Icon `Icons.Default.FindInPage` ist bereits importiert, aber nur als Illustration im Fehlerzustand (`ViewerErrorState`, Zeile 431) verwendet — frei verfügbar als Such-Icon in der Viewer-UI.
- Viewer-Strings liegen in `res/values/strings.xml` (kein eigenes `strings_viewer.xml`); neue Strings folgen dieser Konvention.

## Bausteine

### 1. Gemeinsame Entity-Erkennung (Domain, pure Kotlin)

Neue Datei `domain/common/DocumentEntityDetector.kt`:

```kotlin
data class DetectedEntities(
    val ibans: List<String>,
    val amounts: List<String>,
    val dates: List<LocalDate>
)
fun detectDocumentEntities(text: String): DetectedEntities
```

- IBAN- und Betrags-Regex aus `AutoTagUseCase` hierher verschieben (nicht duplizieren); `AutoTagUseCase` ruft danach `detectDocumentEntities` auf, statt eigene private Regex zu pflegen. Die bisherigen Tag-Scores bleiben unverändert. Die bisherige IBAN-Regex wird dabei korrigiert, weil sie bei Ländern mit nicht durch vier teilbarer BBAN-Länge (unter anderem deutsche IBANs) die letzten Zeichen abschnitt.
- Neue Datums-Regex ergänzen (DE-Format `dd.MM.yyyy`/`dd.MM.yy`, ISO `yyyy-MM-dd`), Parsing über `java.time.LocalDate` mit mehreren `DateTimeFormatter`-Kandidaten, ungültige/nicht parsbare Treffer verwerfen.
- Reine Kotlin-/JDK-Typen (kein Android-Import) — passt zu den bestehenden `domain/common`-Regeln.
- Ergebnis auf sinnvolle Menge deckeln (z. B. max. 3 distinkte Treffer je Typ), um OCR-Rauschen nicht in einer langen Chip-Liste zu ertränken.

**Wichtige Einschränkung:** Ohne ML Kit ist die Datumserkennung rein syntaktisch — es gibt (noch) keine Unterscheidung zwischen „Rechnungsdatum" und „Fälligkeitsdatum". Für Phase 1 pragmatisch lösen: Datum bevorzugen, das in Zeilennähe zu Schlüsselwörtern wie „Fällig", „Zahlbar bis", „Frist", „gültig bis" steht (einfache Fenster-Heuristik über den normalisierten Text); sonst erstes gefundenes Datum. Das ist eine bewusste MVP-Vereinfachung, keine korrekte Semantik — im Chip klar als „Datum gefunden", nicht als „Fälligkeitsdatum" beschriften.

### 2. Volltextsuche über Seiten (Domain, pure Kotlin)

Neue Datei `domain/common/PdfTextSearch.kt`:

```kotlin
fun findMatchingPages(pageTexts: List<String>, query: String): List<Int>
```

- Case-insensitive `contains`-Suche pro Seite, gibt sortierte Liste der Seitenindizes mit Treffer zurück.
- Bewusst als reine Funktion ausgelagert (nicht direkt im ViewModel), damit sie isoliert unit-testbar ist und später ggf. erweitert werden kann (z. B. Wortgrenzen, Normalisierung wie in `AutoTagUseCase`).

### 3. ViewModel-Erweiterung (`PdfViewerViewModel` + `PdfViewerModels.kt`)

`PdfViewerUiState` erweitern:

```kotlin
val detectedEntities: DetectedEntities? = null,
val searchActive: Boolean = false,
val searchQuery: String = "",
val searchMatches: List<Int> = emptyList(),
val searchCurrentIndex: Int = -1
```

`PdfViewerViewModel`:

- Bei `onRecordChanged`: Entity-Quelle memoizen und Normalisierung/Erkennung auf `dispatcherProvider.default` ausführen. `extractedText` wird bevorzugt; nur als Fallback werden `pageTexts` im Hintergrund verbunden.
- Neue Funktionen: `openSearch()`, `updateSearchQuery(query: String)` (mit ca. 250–300 ms Debounce auf `dispatcherProvider.default`), `goToNextMatch()`, `goToPreviousMatch()`, `closeSearch()`. Ein zusätzliches `searching`-Flag verhindert während des Debounce ein verfrühtes „Keine Treffer".
- Seitensprung: neuer `SharedFlow<Int>` `scrollToPageRequests` (gleiches Idiom wie bereits vorhandenes `printRequests`/`pendingPrintDocument`), den `PdfViewerScreen` per `LaunchedEffect` konsumiert und darüber `listState.animateScrollToItem(index)` aufruft. Kein Eingriff in die bestehende `onVisiblePagesChanged`-Logik nötig, die weiterhin die Bild-Vorschau-Fenster steuert.
- Deaktivieren/Ausblenden der Such-UI, wenn die Seitentexte fehlen oder nicht exakt zur PDF-Seitenzahl ausgerichtet sind — kein automatisches Nachtriggern von OCR in dieser Phase.

### 4. UI (`PdfViewerScreen.kt`)

- Such-Icon (`Icons.Default.FindInPage`) neben der bestehenden Aktionsreihe (Export/Share/Print, `FilledTonalIconButton`-Block ~Zeile 603) ergänzen; Klick ruft `viewModel.openSearch()`.
- Bei aktiver Suche: schmale Suchleiste (TextField + Treffer-Zähler „3/12" + Auf/Ab-Pfeile + X zum Schließen) oberhalb der `LazyColumn` einblenden.
- System-Zurück schließt zuerst die Suche; ein Suchsprung setzt einen aktiven Viewer-Zoom zurück, damit die Zielseite zuverlässig sichtbar wird.
- Erkannte-Daten-Zeile: horizontal scrollbare Chip-Reihe (IBAN, Betrag, Datum), sichtbar wenn `detectedEntities` nicht leer:
  - IBAN-Chip → `ClipboardManager.setPrimaryClip(...)`, danach `transientMessage` (bestehendes Feld, aktuell für Export-Erfolg genutzt) mit „IBAN kopiert" setzen.
  - Betrag-Chip → gleiches Muster, „Betrag kopiert".
  - Datum-Chip → `buildCalendarInsertIntent(...)` starten, `ActivityNotFoundException` abfangen (Muster wie `openPdfExternally`) und bei Fehlschlag `transientMessage`/Snackbar „Keine Kalender-App gefunden" zeigen.

### 5. Kalender-Intent (`util/`)

Neue Funktion in `util/PdfDocumentIntents.kt` (oder neue Datei `util/DocumentEntityIntents.kt`, falls thematisch sauberer getrennt):

```kotlin
fun buildCalendarInsertIntent(title: String, dateMillis: Long): Intent =
    Intent(Intent.ACTION_INSERT).apply {
        data = CalendarContract.Events.CONTENT_URI
        putExtra(CalendarContract.Events.TITLE, title)
        putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, dateMillis)
        putExtra(CalendarContract.EXTRA_EVENT_END_TIME, dateMillis + DAY_MILLIS)
        putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, true)
    }
```

- `ACTION_INSERT` an eine Kalender-App delegiert die eigentliche Erstellung — **keine neue Runtime-Permission**, kein Manifest-Eintrag nötig (gleiches Prinzip wie der bestehende `ACTION_VIEW`-PDF-Intent).
- Beginn und Ende werden für den ausgewählten lokalen Kalendertag gesetzt; das Ende liegt exklusiv am Folgetag.
- Titel-Vorschlag: Dateiname des Dokuments (`record.filename`), optional mit erkanntem Schlüsselwort-Kontext („Zahlungsziel", „Frist") falls in Fensternähe des Datums gefunden.

### 6. Strings (alle 10 Locales)

Neue Keys in `strings.xml` + `-de`, `-es`, `-fr`, `-pt`, `-zh-rCN`, `-ar`, `-ja`, `-ru`, `-hi`:

`pdf_viewer_search`, `pdf_viewer_search_hint`, `pdf_viewer_search_no_matches`, `pdf_viewer_search_match_count`, `pdf_viewer_search_previous`, `pdf_viewer_search_next`, `pdf_viewer_search_close`, `pdf_viewer_copy_iban`, `pdf_viewer_copy_iban_success`, `pdf_viewer_copy_amount`, `pdf_viewer_copy_amount_success`, `pdf_viewer_create_event`, `pdf_viewer_no_calendar_app`.

### 7. Tests

- JVM: `DocumentEntityDetectorTest` — IBAN/Betrag/Datum, mehrzeilige OCR-Trennungen (Bindestrich-Umbrüche wie in `AutoTagUseCase` schon behandelt), keine Treffer, mehrere Treffer, Datums-Heuristik mit/ohne Schlüsselwort-Nähe.
- JVM: `PdfTextSearchTest` — Groß-/Kleinschreibung, leere Liste, kein Treffer, mehrere Treffer.
- JVM: `PdfViewerViewModelTest` erweitern — Suchzustand-Übergänge (`openSearch`/`updateSearchQuery`/`goToNextMatch`/`closeSearch`), `detectedEntities`-Befüllung beim Laden eines Records, Verhalten bei leeren `pageTexts`.
- `AutoTagUseCase`-Tests weiterhin grün halten, nachdem IBAN/Betrag-Regex nach `DocumentEntityDetector` verschoben wurden (Verhalten darf sich nicht ändern, nur die Quelle).
- OCR-Seitenindex-Regression: Serialisierung und PDF-OCR behalten leere Seiteneinträge bei; die Viewer-Suche bleibt bei nicht ausgerichteten Alt-Daten deaktiviert.

## Bekannte Grenzen dieser Phase

- Kein Wort-Highlighting auf der Seite selbst (keine Bounding-Boxes gespeichert) — nur „springe zur Seite mit Treffer". Für ein MVP ausreichend.
- Suche nur für Dokumente mit gespeichertem OCR-Text (`hasStoredOcrText`/`pageTexts` nicht leer) verfügbar.
- Bei Alt-Dokumenten, deren leere OCR-Seiten bereits verworfen wurden, bleibt die Seitensuche nur bis zum automatischen oder manuell ausgelösten Metadaten-Backfill deaktiviert; damit werden in der Zwischenzeit falsche Seitensprünge vermieden.
- Datums-/„Frist"-Erkennung ist eine syntaktische Heuristik, keine echte Semantik — bewusst als Übergangslösung vor Phase 2 (ML Kit Entity Extraction) markiert.
- Bei sehr langen Dokumenten (viele hundert Seiten) Debounce der Sucheingabe beachten, damit die Such-Berechnung nicht bei jedem Tastendruck über alle Seiten läuft.

## Nicht Teil dieses Plans (spätere Phasen)

- ML Kit Entity Extraction hinter `EntityExtractionGateway` (Phase 2).
- Dateinamen-/Ordner-Vorschläge auf Basis erkannter Entities/Tags (Phase 3).
- „Sendung verfolgen" (Tracking-Nummern) — unklare Trefferqualität für AT/DE-Carrier, separat zu evaluieren.
