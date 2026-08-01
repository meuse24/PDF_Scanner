# Umsetzungsplan: Komfortable Textsuche im PDF-Viewer

## Befund

Die gesuchte Bedienoberfläche ist bereits vorhanden:

- `ViewerActionBar` zeigt `FindInPage`, aber nur bei `pageSearchAvailable`.
- `ViewerSearchToolbar` enthält Suchfeld mit Autofokus, Trefferzähler, Ladeanzeige, Auf-/Ab-Pfeile und Schließen.
- Die Navigation springt zyklisch; Back schließt zuerst die Suche.

Die Freigabe beruht ausschließlich auf `Document.hasAlignedOcrPageTexts()`: Es müssen gespeicherte OCR-Texte für alle Seiten vorliegen. Daher kann ein importiertes PDF mit nativer Textschicht nicht durchsucht werden. Außerdem zählt die aktuelle Logik Trefferseiten statt einzelner Fundstellen.

## Zielbild

Die Suchaktion ist in jedem geöffneten, nicht verschlüsselten PDF sichtbar. Sie durchsucht pro Seite bevorzugt die native PDF-Textschicht und ergänzt sie, falls nötig, aus gespeicherten OCR-Seitentexten. Das Suchfeld zeigt einzelne Fundstellen als `3 von 12`; die Pfeile navigieren zyklisch vor/zurück.

Bei Fundstellen aus der nativen Textschicht wird der aktive Treffer auf der gerenderten Seite hervorgehoben; weitere Treffer derselben Seite werden schwächer markiert. Die Hervorhebung ist ein Compose-Overlay und verändert die PDF-Datei nicht.

Hat das Dokument nach abgeschlossener Prüfung keine durchsuchbare Textquelle, erklärt ein kurzer Hinweis beim Tippen auf die Lupe, dass zuerst OCR ausgeführt werden muss. Die Prüfung startet erst beim ersten Tippen auf die Lupe; währenddessen öffnet sich die Suche mit Ladeanzeige statt voreilig „OCR nötig“ zu melden.

## Architektur und Datenfluss

### Bestehenden Port erweitern

Keinen neuen `PdfViewerTextOps` einführen. `PdfTextOps` ist bereits als PDF-Text-Port vorhanden, in `PdfOperationsModule` gebunden und wird durch `PdfEditor` implementiert. Er erhält Suchoperationen und frameworkfreie Modelle, beispielsweise:

```kotlin
interface PdfTextOps {
    fun removeTextLayer(input: File, outputDir: File): File
    fun extractTextLines(file: File, pageIndex: Int): List<TextLine>

    /** Kalter Flow; öffnet das PDF einmal und liefert die Seiten in Dokumentreihenfolge. */
    fun extractSearchText(file: File): Flow<PdfPageTextContent>

    /** Glyphenboxen einer Seite, indexgleich zum Rohtext dieser Seite. */
    fun extractPageGlyphBoxes(file: File, pageIndex: Int): List<NormalizedBox?>
}

data class PdfPageTextContent(val pageIndex: Int, val text: String)
data class NormalizedBox(val left: Float, val top: Float, val right: Float, val bottom: Float)
```

`extractSearchText` lädt das `PDDocument` genau einmal, prüft die Coroutine-Abbruchbarkeit pro Seite und emittiert fortlaufend. Es darf nicht das Muster von `extractTextLines(file, pageIndex)` übernehmen, das pro Seite ein komplettes Dokument lädt.

### Genaue Text-/Glyphenzuordnung

Die Such-Extraktion braucht einen eigenen PDFBox-Pfad, z. B. `PdfEditorSearchText.kt`:

- Den Seitentext aus den `unicode`-Werten der `TextPosition`-Folge aufbauen, nicht aus `PDFTextStripper.getText()`. Ligaturen und Dekomposition können sonst Text und Positionen unterschiedlich lang machen.
- Für jedes Zeichen eine Glyphenreferenz führen. Nicht abbildbare Zeichen, künstlich eingefügte Zeilentrenner oder degenerierte Boxen erhalten einen `null`-Platzhalter, damit kein Index verrutscht.
- `toNormalizedTextBox()` nicht direkt verwenden: Der 4-pt-Filter würde kleine Glyphen verwerfen. Das betrifft besonders die unsichtbare, skalierte Textschicht app-eigener Searchable-PDFs.
- Rotation mit `normalizeRotation(page.rotation)` behandeln; bei 90°/270° sind die angezeigten Breite und Höhe zu vertauschen.
- Die Stufe-1-Textgewinnung und die spätere Glyphengewinnung müssen dieselbe Zeichen- und Trennzeichenlogik teilen, damit ihre Indizes garantiert zusammenpassen.

### Quellen pro Seite zusammenführen

Beim ersten Öffnen der Suche startet ein abbrechbarer Extraktionslauf im IO-Dispatcher. So bleibt das bloße Anzeigen eines PDFs beim schlanken `PdfRenderer`-Pfad und lädt PDFBox nicht unnötig für Dokumente, die nie durchsucht werden.

1. Native Textschicht pro Seite auslesen und bevorzugen, weil nur sie Koordinaten für Highlights liefert.
2. Für jede leere native Seite den ausgerichteten `record.pageTexts[pageIndex]` als Fallback einsetzen. Damit bleiben auch hybride PDFs vollständig durchsuchbar.
3. Die Quelle jedes Seitentexts im flüchtigen Viewer-Cache festhalten (`Native` oder `OcrFallback`). Nur Treffer aus `Native` erhalten Boxen und Hervorhebung.

Sobald eine verwertbare Seite gefunden ist, wird die Suche als verfügbar markiert. Bei derselben Dokumentinstanz wird dieser Status nicht wieder zurückgesetzt; bei Dokumentwechsel wird er zurückgesetzt. Einzelne PDFBox-Seitenfehler gelten als leer und dürfen die Anzeige nicht beeinträchtigen.

Der Status für die feste Lupe lautet explizit `Checking`, `Available` oder `Unavailable`:

- `Checking`: Suchleiste öffnen, Ladeanzeige zeigen, Eingabe annehmen und nach Abschluss suchen.
- `Available`: Suchleiste sofort öffnen.
- `Unavailable`: keine Suchleiste öffnen, sondern den lokalisierten OCR-Hinweis als Snackbar zeigen.

Verschlüsselte und fehlerhaft geöffnete PDFs behalten das bestehende Fehlerverhalten.

## Suche und Navigation

`findMatchingPages()` bleibt für bestehende Aufrufer erhalten. Ergänzt wird eine Fundstellensuche:

```kotlin
data class PdfSearchMatch(val pageIndex: Int, val rawRange: IntRange)

fun findMatches(pageTexts: List<String>, query: String): List<PdfSearchMatch>
```

Die Suche ist ohne Locale-Abhängigkeit case-insensitiv, ignoriert leere Anfragen, liefert Fundstellen in Dokumentreihenfolge und behandelt überlappende Treffer bewusst als einen Treffer pro Zeichenfolge.

Vor dem Vergleich werden Seite und Anfrage einheitlich normalisiert:

```kotlin
data class NormalizedPageText(val text: String, val sourceIndex: IntArray)
fun normalizeForSearch(raw: String): NormalizedPageText
```

Die Normalisierung entfernt weiche Trennzeichen, entfernt Silbentrennung (`-` plus Zeilenumbruch), wandelt geschützte Leerzeichen um und fasst Whitespace zusammen. Die `sourceIndex`-Karte führt jeden Treffer im normalisierten Text auf den Rohtext und damit auf Glyphenboxen zurück. Diakritika- und Fuzzy-Suche sind nicht Teil dieses Umfangs.

`PdfViewerUiState` wird entsprechend angepasst:

```kotlin
val searchMatches: List<PdfSearchMatch> = emptyList()
val searchCurrentIndex: Int = -1
val searchHighlights: PdfSearchHighlights? = null
val searchExtractionRunning: Boolean = false
```

Boxen gehören nicht in `PdfSearchMatch`: Sie werden erst für die aktive Seite geladen. Der Textcache enthält alle Seitentexte; der Boxcache ist ein LRU-Cache für nur zwei bis drei zuletzt benötigte Seiten. Das verhindert einen großen Speicherbedarf zusätzlich zum Bitmap-Cache.

Bei zwei Fundstellen auf derselben Seite ändert sich nur der aktive Highlight-Zustand. `moveToMatch()` emittiert `scrollToPageRequests` nur bei einem Seitenwechsel. Damit setzt der bestehende Collector den Zoom nicht zurück und springt nicht unnötig an den Seitenanfang.

## UI und Hervorhebung

`ViewerSearchToolbar` bleibt strukturell erhalten. Der Trefferzähler zählt nun Fundstellen, der Ladeindikator zeigt auch die laufende Textprüfung, und die Navigation bleibt bis zum Abschluss der Suche deaktiviert. Eine Live-Region ergänzt die bestehenden Beschreibungen der Pfeile um „Treffer X von Y“ bzw. „Keine Treffer“ für TalkBack.

Die Lupe wird in der `ViewerActionBar` nicht mehr über `onSearch = null` entfernt. Der neue Hinweis `pdf_viewer_search_needs_ocr` wird in allen zehn unterstützten Locales ergänzt.

Das Highlight-Overlay gehört in `PdfPageCard` über das `Image`:

- Ein `Canvas` zeichnet die normalisierten Boxen in der tatsächlichen Bildfläche.
- Es wird erst gezeichnet, wenn echte Seitenmaße vorhanden sind; so entstehen bei Platzhalteraspekt und `ContentScale.Fit` keine falschen Markierungen im Letterboxing.
- Der Overlay-Canvas hat keine Pointer-Modifier und übernimmt daher keine Zoom-, Scroll- oder Doppeltipp-Gesten.
- Das `graphicsLayer` der gesamten `LazyColumn` transformiert das Overlay bereits gemeinsam mit der Seite; es ist keine eigene Zoom-/Pan-Rechnung erforderlich.

Da die Papierfläche ausdrücklich weiß ist, nutzen aktive und passive Marker feste, getestete halbtransparente Gelb-/Orangetöne als dokumentbezogene Annotationsfarben. Sie werden zentral als benannte Konstanten gehalten und auf ausreichenden Kontrast auf Weiß geprüft; sie sind keine App-Flächenfarben und beeinflussen Dynamic Color nicht.

## Lebenszyklus und Fehlerbehandlung

- Extraktions-, Such- und Box-Jobs tragen den bestehenden `documentKey` und werden bei Dokumentwechsel, `closeSearch()` und `onCleared()` abgebrochen.
- PDFBox-Zugriffe laufen im IO-Dispatcher, Normalisierung und Trefferzuordnung im Default-Dispatcher.
- `CancellationException` wird immer weitergereicht.
- Text- und Boxcache sind rein flüchtig und werden bei Invalidation geleert. Keine Room-Migration oder Volltextpersistenz: Die Textschicht gehört zur Datei und könnte bei Dateiänderungen veralten.

## Betroffene Dateien

- `domain/pdf/PdfTextOps.kt` sowie neue, frameworkfreie Suchmodelle unter `domain/pdf/`
- `domain/common/PdfTextSearch.kt`
- neue PDFBox-Implementierung `util/PdfEditorSearchText.kt`
- `ui/viewer/PdfViewerModels.kt`, `PdfViewerViewModel.kt`, `PdfViewerScreen.kt`
- alle lokalisierten `strings*.xml` für den OCR-Hinweis
- Unit- und Instrumentation-Tests.

`PdfOperationsModule.kt` ändert sich nur, falls die Erweiterung entgegen dem Plan nicht von `PdfEditor` implementiert würde.

## Tests und Abnahme

1. **JVM, Suche:** Groß-/Kleinschreibung, mehrere und überlappende Vorkommen, Seitenreihenfolge, Leeranfrage und zyklische Navigation.
2. **JVM, Normalisierung:** Zeilenumbruch im Wort, Silbentrennung, NBSP, weiche Trennzeichen und Mehrfach-Whitespace; die Indexkarte muss jeden Treffer korrekt auf den Rohtext zurückführen.
3. **PDFBox-Integration:** echtes Text-PDF, Bild-PDF, 90°-Seite und app-erzeugtes Searchable-PDF. Text, Glyphenanzahl, Boxgrenzen und Rotation werden geprüft; speziell darf der 4-pt-Filter keine Zeichen verlieren.
4. **Performance:** Test-Fake weist nach, dass die Stufe-1-Extraktion das Dokument einmal statt einmal je Seite öffnet.
5. **ViewModel:** native Textschicht hat Vorrang, OCR füllt nur leere native Seiten, der Status `Checking/Available/Unavailable` stimmt, Abbruch und Teilfehler sind robust, und gleiche Seite löst keine Scroll-/Zoom-Rücksetzung aus.
6. **Manuell/Compose:** Lupe, Autofokus, Tastatur, Zähler, beide Pfeile, Back, gleiche-Seite-Navigation, Zoom/Pan, Letterboxing und TalkBack prüfen.
7. **Regression:** verschlüsselte/beschädigte PDFs, Bitmap-Cache, Druck, Teilen und Smart-Actions bleiben unverändert.

## Nicht enthalten

- Keine Volltext-Indizierung oder Room-Migration für importierte PDFs.
- Kein OCR-Start direkt aus dem Viewer.
- Keine Suche in gesperrten PDFs ohne vorheriges Entsperren.
- Keine Fuzzy- oder diakritikaunabhängige Suche.
- Keine Änderung der PDF-Datei für die Hervorhebung.

## Umsetzungsetappen

1. `PdfTextSearch` mit Normalisierung, Indexkarte und Unit-Tests implementieren.
2. `PdfTextOps` und die einmalige, rotationssichere PDFBox-Extraktion ergänzen; Integrationstests hinzufügen.
3. ViewModel auf Quellen pro Seite, asynchronen Status, Fundstellen und gleichseitige Navigation umstellen.
4. Suchleiste, dauerhaft sichtbare Lupe, OCR-Hinweis und Übersetzungen ergänzen.
5. LRU-Boxcache und Canvas-Overlay in `PdfPageCard` implementieren.
6. Compile-, Unit-, Instrumentation-, Accessibility- und manuelle Regressionstests durchführen.
