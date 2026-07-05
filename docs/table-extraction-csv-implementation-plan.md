# Feature-Plan: Tabellenextraktion als CSV/TSV

## Umsetzungsstatus

**Status: in Umsetzung (Phasen 1-4 abgeschlossen, Phase 0 mit akzeptiertem Risiko
abgeschlossen, Phase 5 weit fortgeschritten: Instrumentation-Suite läuft 52/52 grün auf
echtem Gerät, manueller End-to-End-Durchlauf inkl. echtem CSV-Export abgeschlossen, ein
externes Concurrency-/Datenintegritäts-Review nach der Geräte-Session eingearbeitet; siehe
Phase 5 für die verbleibenden rein manuellen/menschlichen QA-Punkte).**
Zweite Überarbeitung (2026-07-05): Findings eines Android-Robustheits-Reviews eingearbeitet —
Entwurfs-Persistenz gegen Process Death, 220-DPI-Render-Scale für den Tabellenpfad,
verbindliche Compose-Vorgaben für das Review-Grid und Tastatur-Heuristik im
Editierdialog (Details im Abschnitt „Ergebnis des Android-Robustheits-Reviews").

### Fortschritt

Die Umsetzung erfolgt phasenweise entlang des Abschnitts „Phasen und Aufwand"; jede
Phase wird einzeln compile- und testgeprüft, bevor die nächste beginnt.

- **Phase 0 — reale Fixtures: abgeschlossen mit akzeptiertem Risiko (2026-07-05,
  Entscheidung vor Beginn von Phase 5).** Der Plan stellt ausdrücklich fest, dass
  synthetische Raster allein für die Bewertung der Heuristik nicht ausreichen. In dieser
  Entwicklungsumgebung waren reale, anonymisierte Dokumente nicht beschaffbar; Phase 1
  wurde deshalb gegen sorgfältig konstruierte synthetische `OcrPositionedPage`-Testdaten
  entwickelt (siehe Abweichung unten) und durch den Instrumentation-Test
  `TableExtractionInstrumentedTest` (Phase 5, echtes PDF + echtes ML Kit statt
  synthetischer Geometrie) ergänzt. Bewusste Entscheidung: statt weiter auf reale
  Dokumente zu warten, gilt der synthetische Testkorpus als Release-Basis; das Risiko
  (Schwellwerte sind nicht gegen reale Scan-Artefakte wie JPEG-Rauschen, echte
  Scanner-Verzerrung oder untypische Layouts getunt) ist eine dokumentierte,
  akzeptierte Einschränkung von v1, keine offene Phase mehr. Reale Fixtures und ein
  Tuning-Durchgang bleiben als optionale Nachbesserung vorgemerkt, sind aber keine
  Freigabebedingung mehr.
- **Phase 1 — Domain-Modelle und Rekonstruktionsalgorithmus: abgeschlossen (2026-07-05,
  nach Review-Korrekturen).**
  - Neu: `domain/model/PositionedOcrModels.kt` (`OcrRect`, `OcrElement`, `OcrLine`,
    `OcrPositionedPage`), `domain/model/ExtractedTableModels.kt` (`TableIssue`,
    `ExtractedCell`, `ExtractedRow`, `ExtractedTable`, `TableExtractionResult`),
    `domain/common/TableReconstructionConfig.kt` (alle Schwellwerte benannt, mit
    Defaults), `domain/common/TableReconstructor.kt` (reiner Kotlin-Algorithmus,
    Top-Level-Funktion `reconstructTable(page, config)`).
  - Implementiert alle sechs Algorithmus-Schritte aus dem Abschnitt
    „Rekonstruktionsalgorithmus": Bereinigung/robuste Skalenwerte, Deskew via
    zirkulärer (achsenorientierter) Medianwinkel-Projektion, ordnungsunabhängiges
    Zeilenclustering, Tabellenbereichserkennung über zusammenhängende
    „qualifizierende" Zeilen (Segmentanzahl ≥ `minColumns`) mit **zusätzlicher
    Validierung pro Kandidat** — wiederkehrende Spaltengrenzen-Lücken über einen
    konfigurierbaren Zeilenanteil (`hasRecurringColumnSupport`) und ein
    Mindest-Score-Gate (`minRegionScore`) —, Spaltenbestimmung über die Referenzzeile
    mit den meisten Segmenten, Zellzuordnung mit Element-Fallback (`SPANNING_LINE`),
    Mehrdeutigkeits-Markierung (`AMBIGUOUS_COLUMN`) und eine Zeilenumbruch-Heuristik
    (`WRAPPED_ROW_GUESS`), die Breite, vertikalen Abstand zur Vorgängerzeile **und**
    Übereinstimmung mit einer bereits belegten Spalte der Vorgängerzeile verlangt,
    bevor gemergt wird (sonst wird die Zeile als eigenständige Zeile aufgebaut statt
    still verschluckt zu werden).
  - Test: `TableReconstructorTest.kt`, 14 Fälle, alle grün — perfektes Raster,
    y-Jitter, Order-Independence, leere Zellen, enge Spalten mit Element-Fallback,
    mehrzeilige Beschreibung (`WRAPPED_ROW_GUESS`), Rotation/Deskew (inkl.
    Geometrie-Assertions auf `columnBounds`), Briefkopf+Tabelle+Fußzeile, ein
    schmaler aber weit entfernter Footer (wird trotz passender Breite nicht
    gemergt), mehrteilige Textzeilen ohne wiederkehrende Spaltengeometrie → `null`,
    konkurrierende Regionen, reiner Fließtext → `null`, leere Seite → `null`,
    mehrdeutige Zelle → `AMBIGUOUS_COLUMN`.
  - Verifiziert: `./gradlew testDebugUnitTest --tests
    "info.meuse24.pdf_scanner.domain.common.TableReconstructorTest"` (14/14 grün) und
    `./gradlew :app:compileDebugKotlin` (fehlerfrei, keine neuen Warnings).
  - **Review-Korrekturen (2026-07-05, nach erstem Abnahme-Review):** Ein
    Android-Robustheits-Review hatte zwei Hoch- und zwei Mittel-Befunde zur ersten
    Fassung des Algorithmus gefunden; alle wurden behoben, bevor Phase 1 als
    abgeschlossen gilt:
    1. *Hoch:* Regionen wurden akzeptiert, ohne wiederkehrende Spaltengeometrie zu
       prüfen (drei Zeilen mit je ≥ 2 Segmenten reichten). Fix: `buildColumns` wird
       jetzt pro Kandidat aufgerufen, `hasRecurringColumnSupport` verlangt, dass ein
       Mindestanteil der Zeilen dieselbe Spaltengrenze als tatsächliche
       Zwischen-Segment-Lücke bestätigt, zusätzlich gated durch `minRegionScore`.
    2. *Hoch:* Die Zellumbruch-Heuristik prüfte nur die Segmentbreite; ein schmaler,
       weit entfernter Footer hätte still in die letzte Zelle gelangen können. Fix:
       zusätzlicher Vertikalabstands-Check (`wrappedRowMaxGapFactor` × Median-
       Zeilenhöhe) sowohl in der Regionserkennung als auch beim Zellenbau, plus die
       Anforderung, dass die Zielspalte in der Vorgängerzeile bereits belegt ist.
    3. *Mittel:* Beim Merge einer umgebrochenen Zeile gingen Mehrdeutigkeits-Issue
       und Konfidenz des angehängten Segments verloren. Fix: `AMBIGUOUS_COLUMN` wird
       jetzt übernommen, die Zell-Konfidenz wird mit der des angehängten Segments
       gemittelt.
    4. *Mittel:* `foldAngle()` normalisierte Winkel nur einzeln; ein linearer Median
       nahe der ±180°-Grenze konnte verfälscht werden. Fix: zirkuläre Referenzachse
       (Kreismittel) bestimmen, alle Winkel relativ dazu neu falten (liegen dann nah
       bei 0°), linearer Median darauf, Ergebnis zurückrechnen.
    Ein fünfter (Mittel-)Befund betraf den Test selbst: Der Deskew-Test prüfte nur
    Zelltexte, nicht die zurückprojizierte Geometrie, und die Fixture nutzte eine
    reine Mittelpunkt-Rotation statt der (bloating-behafteten) Ecken-Rotation, die
    ML Kit tatsächlich liefert. Fix: Fixture rotiert jetzt die vier Ecken der Box wie
    die Produktion (`rotateRect`), zusätzliche Assertions vergleichen
    `columnBounds` gegen die erwartete Ideal-Geometrie (mit Toleranz für das
    erwartungsgemäße, durch doppeltes Bounding-Box-Bloating bedingte Restrauschen).
  - **Dokumentierte Abweichung:** Die im Abschnitt „Phase-0-Fixtures" geforderten
    Fixtures aus realen, anonymisierten Dokumenten waren in der Entwicklungsumgebung
    nicht beschaffbar (siehe Phase 0 oben). Ersatzweise wurden sorgfältig
    konstruierte synthetische `OcrPositionedPage`-Testdaten verwendet, die dieselben
    Layout-Typen nachbilden (Rechnung mit mehrzeiliger Beschreibung, enge
    Zahlenspalten, Leerzellen, Briefkopf+Fußzeile, leicht gedrehter Scan,
    tabellenloses Dokument, mehrteiliger Fließtext ohne Tabellenstruktur). Reale
    Fixtures und Schwellwert-Tuning gegen echte Scans bleiben für Phase 5 verbindlich
    vorgemerkt.
  - Bei Phase 1 selbst bewusst noch nicht angefasst: `PdfPageInputImageLoader`,
    `TextRecognizerRunner`, `OcrManager`/`OcrUsage`, DI-Module, `ScanAction`/
    Navigation/UI, Locale-Strings, `CsvEncoder` (siehe Phase 2-4).
- **Phase 2 — OCR-Unterbau: abgeschlossen (2026-07-05).**
  - `domain/model/OcrPipelineModels.kt`: `OcrUsage.TABLE_EXTRACTION` ergänzt;
    `OcrManager.recognitionPlan()` (exhaustives `when`) behandelt es wie
    `EXTRACT_TEXT`/`SEARCHABLE_PDF` (Automatik-Modus → Latin-Modell).
  - `util/PdfPageInputImageLoader.kt`: `forEachPageImage()` liefert jetzt zusätzlich
    `pageIndex`, `pageCount`, `bitmapWidth`, `bitmapHeight` an den Callback und
    akzeptiert einen `renderScale`-Parameter (Default: bisherige 150 DPI,
    abwärtskompatibel). Neue Konstante `PDF_TABLE_RENDER_SCALE = 220f / 72f` für den
    Tabellenpfad; `ocrBitmapSize()` nimmt den Scale ebenfalls als Parameter.
  - `util/TextRecognizerRunner.kt`: neue Methode `processPagesFullText()` mit
    Callback auf das rohe ML-Kit-`Text`-Objekt (statt `String`) plus
    Seitenindex/-anzahl/Bitmap-Maßen; nutzt denselben Renderpfad wie das bestehende
    `processPages()` (dessen öffentliche Signatur unverändert bleibt, um
    `MlKitOcrDocumentTextExtractor` nicht zu berühren).
  - Neu: `domain/gateway/OcrPositionedTextExtractor.kt` (Port: `Document`,
    Sprachcode, `onStatus`, `onProgress` → `List<OcrPositionedPage>`),
    `util/MlKitOcrPositionedTextExtractor.kt` (Implementierung: nutzt
    `OcrPipeline.runWithFallback(usage = TABLE_EXTRACTION)` +
    `processPagesFullText(renderScale = PDF_TABLE_RENDER_SCALE)`; ML-Kit-Mapping
    überspringt Geometrieobjekte ohne Bounding-Box, behält aber leere Seiten im
    Ergebnis, damit PDF- und Ergebnisindex ausgerichtet bleiben).
  - DI: `AppProvidersModule` bindet `MlKitOcrPositionedTextExtractor` an
    `OcrPositionedTextExtractor`.
  - Tests (neu/erweitert): `OcrManagerTest` (Automatik-Fall für
    `TABLE_EXTRACTION`), `MlKitOcrPositionedTextExtractorTest` (fehlende Datei →
    leere Liste ohne Pipeline-Aufruf; Mapping mit 220-DPI-Scale, Skip boxloser
    Elemente/Lines, leere Seite bleibt indexrichtig erhalten, Fortschritts-Callback);
    `ExtractTextUseCaseTest`s `FakeTextRecognizerRunner` um die neue
    Interface-Methode ergänzt, damit der bestehende Test weiter kompiliert.
  - **Review-Korrekturen (2026-07-05, nach Abnahme-Review):** Ein Android-Robustheits-
    Review fand einen Hoch- und vier Mittel/Niedrig-Befunde; alle wurden behoben:
    1. *Hoch:* Bei Coroutine-Cancellation verließ `suspendCancellableCoroutine` in
       `recognizeFullText()` sofort mit `CancellationException`, während der
       ML-Kit-Task im Hintergrund weiterlief — der Aufrufer recycelte danach das
       Bitmap bzw. schloss den Recognizer, während ML Kit theoretisch noch darauf
       zugriff (Use-after-recycle-Risiko; ML Kit erlaubt kein Abbrechen laufender
       Tasks). Fix: der ML-Kit-Aufruf läuft jetzt in `withContext(NonCancellable)` —
       eine Cancellation wird erst nach echtem Task-Abschluss nach außen
       weitergegeben, exakt wie von der ML-Kit-Doku gefordert ("release input
       resources only in the OnCompleteListener").
    2. *Mittel:* `MlKitOcrPositionedTextExtractor.toOcrLineOrNull()` verwarf eine
       Line komplett, wenn sie selbst eine gültige Bounding-Box hatte, aber alle
       ihre Elements boxlos waren — der in `TableReconstructor.cleanLines()`
       bereits vorhandene Line-Level-Fallback für genau diesen Fall blieb dadurch
       für reale OCR-Daten unerreichbar. Fix: in diesem (seltenen) Fall wird jetzt
       ein einzelnes Fallback-Element aus der Line-Geometrie synthetisiert
       (Konfidenz/Winkel aus den boxlosen Original-Elementen gemittelt statt
       verworfen), Text und Geometrie bleiben erhalten.
    3. *Mittel:* Der Mapping-Test nutzte ausschließlich `mock(Rect::class.java)`.
       Empirisch verifiziert: sowohl eine echte `Rect(...)`-Konstruktion als auch
       ein Mockito-Mock liefern unter dem Android-Unit-Test-Stub-Jar
       (`isReturnDefaultValues = true`) ausschließlich Nullfelder (Konstruktor-/
       Methoden-Bodies sind No-Ops; öffentliche Felder kann Mockito ohnehin nicht
       stubben) — echte Koordinaten wie `Rect(10, 20, 110, 40)` hätten am Test
       nichts geändert. Fix: die reine Umrechnungslogik wurde in die testbare
       Funktion `mlKitRectToOcrRect(left, top, right, bottom)` extrahiert und mit
       konkreten Koordinaten inkl. Assertions auf `left/top/right/bottom/width/
       height` abgedeckt; `Rect.toOcrRect()` delegiert nur noch.
    4. *Mittel:* Der reale Loaderpfad (`AndroidPdfPageInputImageLoader` mit echtem
       `PdfRenderer`, echten 220-DPI-Bitmap-Dimensionen, Recycling) war nicht
       abgedeckt — in reinen JVM-Unit-Tests dieses Projekts ohne Robolectric sind
       `PdfRenderer`/`Bitmap` No-Op-Stubs (bestätigt das bestehende Testkonzept:
       PdfRenderer-Pfade laufen laut CLAUDE.md bewusst über Instrumentation-Tests).
       Fix: neuer `androidTest`
       (`util/PdfPageInputImageLoaderInstrumentedTest.kt`) mit drei Fällen — echte
       220-DPI-Dimensionen/Seitenindex-Sequenz an einem realen 3-seitigen
       A4-PDF, sauberer Abbruch ohne Folgeseiten bei Exception im Callback,
       Überleben einer Coroutine-Cancellation mitten im Mehrseiten-Lauf ohne
       Crash. Kompiliert (`compileDebugAndroidTestKotlin` erfolgreich); **Ausführung
       auf echtem Gerät/Emulator steht noch aus**, da diese Entwicklungsumgebung
       kein Android-Gerät bereitstellt (analog zur offenen Phase 0).
    5. *Niedrig:* Doku und Implementierung wichen voneinander ab — die Doku verlangt,
       dass `processPages()` auf `processPagesFullText()` delegiert; tatsächlich
       gab es zwei getrennte Schleifen (die zufällig denselben Loader nutzten,
       aber künftig hätten auseinanderlaufen können). Fix: `processPages()`
       delegiert jetzt echt an `processPagesFullText()` und berechnet die Stats
       selbst aus dem durchgereichten `Text`-Objekt — identisches Verhalten, aber
       ein einziger Erkennungspfad.
    - Neue/erweiterte Tests: `MlKitTextRecognizerRunnerTest` (Cancellation-
      Regressionstest über einen kontrollierbaren `Task`-Mock statt echter
      GMS-Tasks — ein echter `TaskCompletionSource`/`Tasks.forResult` dispatcht
      Listener über `TaskExecutors.MAIN_THREAD`, was einen echten Android-Main-
      Looper braucht und im JVM-Stub-Jar zu einem Hänger führt, siehe Kommentar im
      Testfile; Delegation von `processPages`; Metadaten-Durchreichung von
      `processPagesFullText`), `MlKitOcrPositionedTextExtractorTest` (Fallback-
      Element-Fall, `mlKitRectToOcrRect`-Koordinatentest).
  - Verifiziert: vollständiger `./gradlew testDebugUnitTest`-Lauf (103 Testklassen,
    556 Tests, 0 Failures/Errors) und `./gradlew :app:compileDebugKotlin` sowie
    `:app:compileDebugAndroidTestKotlin` (beide fehlerfrei).
  - Bewusst noch nicht angefasst: `CsvEncoder`, `ExtractTableUseCase`/-Workflow,
    `ExportTableCsvUseCase`, `TableDraftStore`, Review-UI, Navigation/`ScanAction`,
    Locale-Strings (siehe Phasen 3-4).
- **Phase 3 — CSV/TSV-Serialisierung, `ExtractTableUseCase`/-Workflow,
  `ExportTableCsvUseCase`, `CsvShareFileStore`: abgeschlossen (2026-07-05).**
  - `domain/model/CsvModels.kt`: `CsvDelimiter` (Komma/Semikolon/Tab),
    `DelimitedTextDialect` (Delimiter + `protectFormulas`, mit abgeleiteten
    `fileExtension`/`mimeType`: `.tsv`/`text/tab-separated-values` bei Tab, sonst
    `.csv`/`text/csv`).
  - `domain/common/CsvEncoder.kt` (`encodeDelimitedText`): schreibt direkt auf einen
    `OutputStream` (zeilenweise, speicherverbrauchsunabhängig von der
    Tabellengröße), `\r\n`-Zeilenenden, UTF-8-BOM einmal am Dateianfang (als Bytes,
    nicht Teil des ersten Zellwerts), Quoting bei Trennzeichen/`"`/CR/LF mit `""`
    für innere Anführungszeichen, Formel-Schutz (`=`,`+`,`-`,`@` am Zellanfang →
    Apostroph-Präfix, per Dialekt abschaltbar).
  - `domain/common/DefaultDelimiter.kt` (`defaultCsvDelimiterFor(decimalSeparator:
    Char)`): pure Funktion ohne Android-/Locale-Import, Dezimalkomma → Semikolon,
    sonst Komma. `util/CsvDelimiterDefaults.kt` (`systemDefaultCsvDelimiter()`)
    liest das tatsächliche Dezimaltrennzeichen über
    `DecimalFormatSymbols.getInstance(Locale.getDefault(Locale.Category.FORMAT))`.
  - Neu: `domain/usecase/ExtractTableUseCase` (führt `OcrPositionedTextExtractor`
    + `reconstructTable()` pro Seite aus, wirft `NoTableFoundException`, wenn
    keine Seite eine Tabelle liefert), `domain/workflow/ExtractTableWorkflow`
    (nutzt `DocumentWorkflowGuard` mit `requireUnencrypted = true`, mappt
    `NoTableFoundException` auf `ScanWorkflowError.NoTableFound`, alles andere auf
    `ScanWorkflowError.TableExtractionFailed`). Neue `ScanWorkflowError`-Fälle
    `NoTableFound`/`TableExtractionFailed` plus zugehörige `StringResource`-
    Einträge `TableExtractionNoTable`/`TableExtractionError`
    (`WorkflowErrorMapper`, `util/ResourceProvider.kt` und der Test-Fake in
    `testutil/FakeProviders.kt` entsprechend ergänzt, da beide `when`-Ausdrücke
    exhaustiv sind).
  - Neu: `domain/gateway/CsvShareFileStore` + `util/AndroidCsvShareFileStore`
    (schreibt atomar via Temp-Datei + Rename nach `cacheDir/csv/`, sanitizter
    Dateiname wie beim vCard-Export-Vorbild, räumt Dateien älter als 24 h vor
    jedem Schreibvorgang auf). DI-Bindung in `AppProvidersModule`.
    `res/xml/file_paths.xml` um `<cache-path name="csv" path="csv/" />` ergänzt.
  - Neu: `domain/common/TableExportFilenames.kt`
    (`buildTableExportFilenames(baseName, pageCount, extension)`): eine Seite →
    `<basis>_table.<ext>`, mehrere Seiten → `<basis>_table_p001.<ext>`,
    `..._p002.<ext>`, … (fortlaufend über die exportierte Menge).
  - Neu: `domain/usecase/ExportTableCsvUseCase` mit `saveToDownloads(...)` und
    `shareFiles(...)`: erzeugt pro ausgewählter Seite eine Datei über
    `DownloadsStorage` bzw. `CsvShareFileStore`, löscht bei einem Fehler bereits
    erzeugte Dateien/`DownloadEntry`s wieder (Rollback, für beide Zielarten), gibt
    ein `TableExportOutcome` (`SavedToDownloads`/`SharedFiles`) mit den
    tatsächlich angelegten `DownloadEntry`s bzw. `File`s zurück.
  - Neue Strings-Datei `values/strings_table_export.xml`
    (`table_extraction_no_table`, `table_extraction_error`) — **nur Englisch
    (`values/`)**. Die restlichen neun Locales sind bewusst noch offen: Phase 4
    bringt ohnehin deutlich mehr Review-UI-Strings für dasselbe Feature mit; eine
    gemeinsame Übersetzungsrunde für die komplette Datei ist kohärenter als zwei
    getrennte Durchgänge. Fehlende Locale-Strings fallen zur Laufzeit auf
    Englisch zurück (kein Compile-Fehler), sind aber vor Feature-Fertigstellung
    verbindlich nachzuziehen.
  - Tests (neu): `CsvEncoderTest` (12 Fälle: Delimiter-Varianten, Quoting,
    Leerzellen, CRLF, BOM exakt einmal, Formel-Schutz an/aus, Formel-Schutz bei
    führendem Whitespace/Tab/CR, Dialekt-Metadaten), `DefaultDelimiterTest`
    (Komma/Punkt/unbekannt), `TableExportFilenamesTest` (ein/mehrere/keine
    Seiten), `AndroidCsvShareFileStoreTest` (Sanitizing, atomares Überschreiben,
    Alt-Datei-Aufräumen, Writer-Exception räumt Temp-Datei auf, blockierte
    Zieldatei wirft `IOException`), `ExportTableCsvUseCaseTest`
    (Einzel-/Mehrseiten-Namen, Rollback bei Fehler in Seite 2 — sowohl für
    Downloads als auch Share), `ExtractTableUseCaseTest` und
    `ExtractTableWorkflowTest` (fehlende/verschlüsselte/tabellenlose Dokumente,
    Status/Fortschritt-Durchreichung, Cancellation, IOException- und
    OcrModelInstallException-Mapping), zwei neue Fälle in
    `WorkflowErrorMapperTest`.
  - **Review-Korrekturen (2026-07-05, nach zweitem Abnahme-Review):** zwei Hoch-
    und vier Mittel/Niedrig-Befunde; alle behoben, bevor Phase 3 als
    abgeschlossen gilt:
    1. *Hoch:* `withContext(NonCancellable)` in `recognizeFullText()` wartet zwar
       korrekt auf den ML-Kit-Abschluss, gibt den Wert danach aber kommentarlos
       zurück, selbst wenn der Aufrufer inzwischen abgebrochen wurde (dokumentierter
       Fallstrick von `NonCancellable`) — `onPage()`, Geometrie-Mapping und weitere
       PDF-Seiten hätten trotz Cancellation noch ausgeführt werden können. Fix:
       `currentCoroutineContext().ensureActive()` direkt nach dem
       `NonCancellable`-Block. Neuer Regressionstest beweist, dass Code nach dem
       Aufruf bei Cancellation nicht mehr erreicht wird.
    2. *Hoch:* Der Formel-Schutz in `CsvEncoder` prüfte nur `raw[0]`; Inhalte wie
       `"\t=SUM(...)"` oder mit führendem Leerzeichen/CR blieben ungeschützt,
       obwohl `=` der erste sichtbare Inhalt war (OWASP nennt Tab/CR/LF als
       relevante CSV-Injection-Präfixe). Fix: `needsFormulaProtection()` prüft
       jetzt das erste Zeichen nach `trimStart()` (entfernt Space/Tab/CR/LF).
    3. *Mittel:* `AndroidCsvShareFileStore` ignorierte die Rückgabewerte von
       `target.delete()`/`temp.renameTo(target)` und räumte die `.tmp`-Datei bei
       einer Writer-Exception nicht auf. Fix: beide Rückgabewerte werden geprüft
       und werfen bei Fehlschlag eine `IOException`; die Temp-Datei wird in jedem
       Fehlerfall gelöscht.
    4. *Mittel:* Workflow-Fehlermapping wich von der Dokumentation ab — die
       Verschlüsselungs-/Validate-Prüfung lief außerhalb des try-Blocks in
       `DocumentWorkflowGuard` (ein beim Encrypted-Check werfendes, beschädigtes
       PDF hätte ungefiltert aus dem Workflow geworfen); `IOException`s aus dem
       Extraktionslauf wurden als generisches `StorageWriteFailed` statt
       `TableExtractionFailed` gemappt, obwohl Extraktion nichts schreibt; ein
       `OcrModelInstallException` zeigte eine generische Tabellenfehlermeldung
       statt der vorhandenen, lokalisierten Modell-Download-Meldung. Fixe: (a)
       `DocumentWorkflowGuard.run()` führt `validate()`/`isPdfEncrypted()` jetzt
       im selben try wie `block()` aus (kommt allen Guard-Nutzern zugute, rein
       additive Fehlerbehandlung für den bisher ungefangenen Fall); (b)
       `ExtractTableWorkflow`s `exceptionMapper` fängt `IOException` explizit auf
       `TableExtractionFailed` ab; (c) `WorkflowErrorMapper.mapTableExtractionFailed()`
       zeigt bei `OcrModelInstallException`-Ursache die Modell-Download-Meldung
       (exakt das `mapOcrFailed()`-Vorbild).
    5. *Mittel:* `ExportTableCsvUseCase.saveToDownloads()`/`shareFiles()`
       wechselten nicht auf den IO-Dispatcher — MediaStore-Zugriff,
       Cache-Aufräumen und die komplette CSV-Ausgabe hätten aus einem ViewModel
       heraus den Main-Thread blockieren können. Fix: `DispatcherProvider`
       injiziert, beide Methoden in `withContext(dispatcherProvider.io) { ... }`
       gewrappt.
    6. *Mittel:* Die vom Plan geforderten `ExtractTableUseCaseTest`/
       `ExtractTableWorkflowTest` fehlten komplett. Fix: beide neu geschrieben
       (siehe Testliste oben) — decken insbesondere Befund 4 (IOException-/
       OcrModelInstallException-Mapping) und die Cancellation-Durchleitung ab.
    7. *Niedrig:* Der Instrumentation-Test aus Phase 2 erwartete für A4 bei
       220 DPI eine Höhe von 2573 px; `(842 * 220f/72f).toInt()` (Truncation,
       kein Runden) ergibt tatsächlich 2572. Fix: Testerwartung wird jetzt über
       dieselbe `ocrBitmapSize()`-Funktion berechnet statt hartcodiert, damit ein
       Rechenfehler im Test unmöglich wird.
  - **Review-Korrekturen (2026-07-05, nach drittem Abnahme-Review):** ein Hoch- und
    zwei Mittel-Befunde zu den obigen Fixes; alle behoben:
    1. *Hoch:* Fix 5 (IO-Dispatcher) machte den Export-Rollback bei Cancellation
       unzuverlässig — der try/catch lag innerhalb von `withContext`, blockierendes
       Schreiben ist aber nicht unterbrechbar; eine Cancellation während des
       Schreibens wurde erst beim Rückwechsel aus `withContext` bemerkt, also
       außerhalb des internen catch, sodass bereits erzeugte Dateien liegen
       blieben. Fix: try/catch liegt jetzt außerhalb von
       `withContext(dispatcherProvider.io)`, Rollback läuft in
       `withContext(NonCancellable) { ... }` (läuft garantiert durch, siehe
       gleiches Muster wie `recognizeFullText`); zusätzlich `ensureActive()`
       zwischen den Seiten, damit eine Cancellation zwischen zwei Schreibvorgängen
       sofort erkannt wird statt erst nach der letzten Seite. Neuer
       Regressionstest löst Cancellation gezielt während des Schreibens von
       Seite 1 aus und prüft, dass sie danach zuverlässig zurückgerollt wird.
    2. *Mittel:* Der `ensureActive()`-Fix aus der vorigen Runde griff nur im
       ML-Kit-Erfolgspfad — schlägt der Task nach einer Cancellation fehl, wirft
       `withContext(NonCancellable)` die ML-Kit-Exception direkt, `ensureActive()`
       danach wird nie erreicht. Fix: `ensureActive()` steht jetzt in einem
       `finally` um den `withContext`-Aufruf; wirft der try-Block UND das finally,
       gewinnt laut JVM-Semantik die finally-Exception — bei Cancellation also
       zuverlässig `CancellationException` statt der rohen ML-Kit-Exception. Neuer
       Regressionstest lässt den Task nach Cancellation fehlschlagen (statt
       erfolgreich abzuschließen) und prüft die geworfene Exception.
    3. *Mittel:* Überschreiben in `AndroidCsvShareFileStore` war weiterhin nicht
       atomar (`target.delete()` vor `renameTo()` lässt ein Fenster ganz ohne
       Zieldatei zu) und nutzte für alle Aufrufe denselben festen Temp-Dateinamen
       (`"<sanitized>.tmp"`), wodurch parallele Exporte desselben Anzeigenamens
       sich die Temp-Datei hätten teilen können. Fix: eindeutiger Temp-Name via
       `File.createTempFile(...)`, Ersetzen über
       `Files.move(ATOMIC_MOVE, REPLACE_EXISTING)` (ein einziger
       Dateisystem-Aufruf, ab minSdk 29 ohne Desugaring verfügbar) statt
       delete()+renameTo(). Neuer Test belegt, dass zwei Aufrufe für denselben
       Anzeigenamen unterschiedliche Temp-Dateinamen verwenden.
    - Notiz zu Fix 7 (Instrumentation-Test): der Reviewer merkt an, dass die
      Erwartung jetzt über dieselbe Produktionsfunktion (`ocrBitmapSize()`)
      berechnet wird und der Test dadurch weniger unabhängig ist. Bewusst in
      Kauf genommen: er verifiziert weiterhin die eigentlich relevante
      Eigenschaft — dass der reale `PdfRenderer`-Pfad dieselben Seitenmaße
      liefert, die die (separat in `PdfPageInputImageLoaderTest`,
      JVM-Unit-Test, mit konkreten Zahlen abgedeckte) Arithmetik erwarten
      lässt —, statt selbst erneut Rundungs-Arithmetik zu duplizieren und damit
      erneut fehleranfällig zu sein.
  - Verifiziert: vollständiger `./gradlew testDebugUnitTest`-Lauf (110
    Testklassen, 603 Tests, 0 Failures/Errors), `./gradlew :app:compileDebugKotlin`
    und `:app:compileDebugAndroidTestKotlin` (beide fehlerfrei).
  - Bewusst noch nicht angefasst: `TableDraftStore`, Review-UI
    (`TableExportScreen`/`TableExportViewModel`, `ColumnWidthEstimator`),
    Navigation/`ScanAction`, restliche neun Locales für
    `strings_table_export.xml` (siehe Phase 4).
- **Phase 4 — Review-UI, `TableDraftStore` + Restore-Dialog, Navigation/`ScanAction`,
  Locales: abgeschlossen (2026-07-05).**
  - Neu: `domain/model/TableDraftModels.kt` (`TableDraft`, `TableDraftPage`,
    `TableDraftRow`, `kotlinx-serialization`), `domain/gateway/TableDraftStore.kt`
    (Port), `util/FileTableDraftStore.kt` (Implementierung: JSON atomar via
    `File.createTempFile` + `Files.move(ATOMIC_MOVE, REPLACE_EXISTING)` nach
    `cacheDir/table_drafts/`, defekter/veralteter Draft wird beim Laden verworfen statt
    zu crashen, `deleteStaleDrafts()` räumt Dateien älter als 24 h). DI-Bindung in
    `AppProvidersModule`.
  - Neu: `domain/common/ColumnWidthEstimator.kt` (`estimateColumnWidthsDp`): reine,
    JVM-testbare Spaltenbreitenschätzung aus maximaler Zeichenzahl je Spalte,
    Min/Max-Clamp.
  - Neu: `ui/tableexport/EditableTable.kt` (`EditableTablePage`/`EditableRow`/
    `EditableCell` als vom `ExtractedTable` getrennte, unveränderliche UI-Darstellung;
    Mapper zu/von `ExtractedTable` und `TableDraftPage`, `toCsvPage()` filtert
    ausgeschlossene Zeilen für den Export), `ui/tableexport/TableExportViewModel.kt`
    (lädt Draft vor automatischer Erkennung, debounced Draft-Speicherung nach jeder
    Nutzeränderung, Zelltext-Edit, Zeilen-/Seitenauswahl, Reset, Delimiter/Formel-Schutz,
    Speichern/Teilen mit Draft-Löschung bei Erfolg), `ui/tableexport/TableExportScreen.kt`
    (Compose-Grid nach den verbindlichen Vorgaben: `LazyColumn` + ein gemeinsamer
    `rememberScrollState()` für den horizontalen Scroll, vorberechnete Spaltenbreiten,
    Zell-Editierdialog mit Tastatur-Heuristik, Restore-Draft-Dialog, Sprachauswahl im
    No-Table-Zustand, Fortschritts-/Status-Anzeige, Speichern/Teilen-Buttons).
  - Neu: `util/CsvShareIntents.kt` (`buildCsvShareIntent`: eine Datei → `ACTION_SEND`,
    mehrere → `ACTION_SEND_MULTIPLE`, `FLAG_GRANT_READ_URI_PERMISSION`, MIME/Endung
    nach Dialekt, analog zum bestehenden PDF-Share-Vorbild).
  - Navigation: `Screen.TableExport("table-export/{scanId}")` mit `createRoute`,
    `AppNavHost` registriert die Route mit `NavType.LongType`, `AppBarTitle` zeigt den
    lokalisierten Screen-Titel. Neue Aktion `ScanAction.ExportTableCsv` in
    `DocumentEditSheet` (sichtbar bei `notEncrypted && pageCount >= 1`, unabhängig von
    `showTextExportActions`, im Export-Abschnitt). `HomeActionDispatcher`,
    `HomeNavigationCallbacks`, `HomeScreen` und `PdfViewerScreen` leiten die Aktion an
    `onNavigateToTableExport`/`onExportTableCsv` weiter; alle betroffenen exhaustiven
    `when`-Ausdrücke wurden entsprechend ergänzt.
  - Locales: `values/strings_table_export.xml` (30 Keys) plus alle neun verbleibenden
    Locales (`-de`, `-es`, `-fr`, `-pt`, `-zh-rCN`, `-ar`, `-ja`, `-ru`, `-hi`) mit
    identischem Schlüsselsatz nachgezogen; per Diff gegen die englische Referenzdatei
    verifiziert.
  - Tests (neu): `ColumnWidthEstimatorTest`, `FileTableDraftStoreTest`,
    `EditableTableTest`, `TableExportViewModelTest` (kein Draft → automatische
    Erkennung; vorhandener Draft → Restore-Dialog statt Erkennung;
    `continueDraft`/`discardDraftAndReextract`; Zelltext-Edit, Zeilen-/Seitenauswahl,
    Reset; Draft-Speicherung debounced statt pro Tastendruck; Speichern/Teilen
    erfolgreich und mit Fehlerpfad).
  - **Review-Korrekturen (2026-07-05, im Rahmen dieser Fortsetzung gefunden und
    behoben, bevor Phase 4 als abgeschlossen gilt):**
    1. *Hoch (`./gradlew lintDebug`, `NewApi`):* `TableReconstructor.buildRows()` rief
       `finalRows.removeLast()` auf einer `MutableList<ExtractedRow>` auf. Ohne
       expliziten Empfängertyp löst das ab Kotlin/AGP-Toolchains mit neuerem
       Android-SDK-Stub auf die echte `java.util.List#removeLast`-Default-Methode auf
       (API 35), nicht mehr auf die Kotlin-Stdlib-Extension — bei `minSdk 29` ein
       `NoSuchMethodError` zur Laufzeit auf jedem Gerät unter Android 15. Fix:
       `finalRows.removeAt(finalRows.lastIndex)`.
    2. *Mittel:* `TableExportScreen`s `TableGrid` nutzte `items(page.rows, key = {
       it.hashCode() })` zusammen mit `rows.indexOf(row)` zur Indexbestimmung. Zwei
       inhaltlich identische Zeilen (z. B. wiederholte Leerzeilen oder gleiche
       Werte) hätten denselben Compose-Key erzeugt (Absturz „Key was already used“),
       zusätzlich war `indexOf` pro Zeile O(n), macht die Liste also O(n²). Fix:
       `itemsIndexed(page.rows, key = { index, _ -> index })` — stabiler, eindeutiger
       Index-Key ohne Zusatzsuche.
    3. *Mittel:* `TableExportViewModel.saveToDownloads()`/`shareFiles()` fingen
       `catch (e: Exception)`, ohne `CancellationException` zuvor gesondert
       abzufangen und weiterzuwerfen — abweichend vom in `HomeViewModel`,
       `BackupViewModel`, `PdfViewerViewModel` und `FormFillViewModel` etablierten
       Muster dieses Projekts. Bei Cancellation (z. B. Verlassen des Screens während
       des Exports) wäre fälschlich eine Fehlermeldung gesetzt und `_exporting` auf
       `false` zurückgesetzt worden, statt die Cancellation strukturiert
       durchzureichen. Fix: `catch (cancellation: CancellationException) { throw
       cancellation }` jeweils vor dem allgemeinen `catch`-Block ergänzt.
    - Verifiziert nach den Fixes: vollständiger `./gradlew testDebugUnitTest`-Lauf
      (634 Tests, 0 Failures/Errors), `./gradlew :app:compileDebugKotlin` und
      `./gradlew lintDebug` (0 Fehler, 28 unveränderte, feature-fremde Warnungen).
  - **Review-Korrekturen, zweite Runde (2026-07-05, externes Review-Feedback zu
    Draft-Persistenz und Fortschrittsanzeige, vor Freigabe von Phase 4 behoben):**
    1. *Mittel:* Der Draft konnte nach einem erfolgreichen Export wieder auftauchen.
       `scheduleDraftSave()`s verzögerter Speicherjob (800 ms) lief unabhängig vom
       Export weiter; exportierte `saveToDownloads()`/`shareFiles()` direkt danach
       (z. B. sofort nach einem Edit), konnte der alte Debounce-Job erst *nach* dem
       expliziten `deleteDraft()` feuern und den gerade gelöschten Draft
       wiederherstellen. Fix: neue `cancelPendingDraftSave()` (`draftSaveJob
       ?.cancelAndJoin()`) wird vor jedem `deleteDraft()`-Aufruf ausgeführt (gebündelt
       in `clearDraft()`), sodass ein anstehender Save nachweislich abgeschlossen
       (abgebrochen oder fertig durchgelaufen) ist, bevor gelöscht wird. Zusätzlich
       ruft `extract()` dieselbe Funktion vorsorglich zu Beginn auf. Regressionstest:
       `TableExportViewModelTest` — Edit unmittelbar vor `saveToDownloads()`, danach
       darf der Draft nicht mehr existieren.
    2. *Mittel:* Restore stellte den Zustand nicht vollständig wieder her.
       `continueDraft()` befüllte `originalTables` nicht, wodurch `resetPage()` nach
       einem Draft-Restore wirkungslos war; zusätzlich speicherte `TableDraftRow` nur
       `cellTexts: List<String>` ohne Issues, sodass Qualitätswarnungen
       (`AMBIGUOUS_COLUMN`, `LOW_CONFIDENCE`, …) nach einem Restore verschwanden und
       unsichere Zellen fälschlich unauffällig wirkten. Fix: `TableIssue` ist jetzt
       `@Serializable`; `TableDraftRow.cellTexts` wurde durch `cells: List<
       TableDraftCell>` (Text + Issues) ersetzt; `TableDraftPage` trägt zusätzlich
       `originalRows` (unbearbeiteter Ausgangsstand, unabhängig vom aktuell
       bearbeiteten `rows`). `originalTables: Map<Int, ExtractedTable>` im ViewModel
       wurde durch `originalPages: Map<Int, EditableTablePage>` ersetzt (leichtgewichtig,
       ohne erneut benötigte OCR-Geometrie) und wird sowohl nach frischer Extraktion
       als auch nach `continueDraft()` befüllt; `EditableTablePage.resetTo()` nimmt
       jetzt eine `EditableTablePage` statt eines `ExtractedTable` entgegen.
       **Nachreichung nach einem zweiten Korrekturdurchlauf:** dieser erste Fix deckte
       nur Zell-Issues ab. `TableReconstructor` kann `LOW_CONFIDENCE` aber auch
       ausschließlich auf Tabellenebene setzen (aus der gemittelten Zellkonfidenz,
       siehe `TableReconstructor.kt` — `if (tableConfidence < config
       .lowConfidenceThreshold) issues += TableIssue.LOW_CONFIDENCE`), ohne dass eine
       einzelne Zelle markiert ist; `EditableTablePage.hasIssues` wurde aus den
       Zell-Issues neu berechnet und hätte eine solche rein tabellenweite Warnung nach
       einem Restore weiterhin verloren. Fix: `EditableTablePage.hasIssues` ist jetzt
       eine aus `tableIssues: Set<TableIssue>` abgeleitete Eigenschaft statt einer
       eigenständig gesetzten `Boolean`; `tableIssues` entspricht `ExtractedTable
       .issues` (Obermenge aller Zell-Issues plus ggf. zusätzliche tabellenweite
       Einträge) und wird zusammen mit `confidence` in `TableDraftPage` persistiert
       und beim Restore direkt übernommen, nicht neu berechnet.
       Regressionstests: `EditableTableTest` (Issues und Original-Zeilen überstehen
       den Draft-Roundtrip; Reset nach simuliertem Restore funktioniert; eine rein
       tabellenweite Issue ohne jedes Zell-Issue übersteht den Draft-Roundtrip),
       `TableExportViewModelTest` (`resetPage()` nach `continueDraft()` stellt Text
       *und* Issues korrekt wieder her).
    3. *Mittel:* Fehler des optionalen Draft-Caches konnten die App abstürzen lassen.
       Der debounced Speicherjob rief `saveDraftNow()` ohne Fehlerbehandlung auf;
       `init` rief `deleteStaleDrafts()` ungeschützt auf; `FileTableDraftStore
       .loadDraft()` ließ `IOException` (im Unterschied zu `SerializationException`/
       `IllegalArgumentException`) unbehandelt durch. Ein Speicherplatzmangel oder ein
       fehlgeschlagener Atomic-Move hätte eine unbehandelte Coroutine-Exception
       ausgelöst. Fix: neue private `runCatchingCancellable { … }`-Hilfsfunktion
       (fängt `Exception`, aber nicht `CancellationException`, die weiterhin
       durchgereicht wird — kein `catch (Throwable)`) umschließt jetzt den
       Debounce-Save, `deleteStaleDrafts()` in `init`, das Laden in
       `checkForDraftOrExtract()` und das Löschen in `clearDraft()`; zusätzlich fängt
       `FileTableDraftStore.loadDraft()` jetzt auch `IOException` ab (Datei wird dabei
       bewusst *nicht* gelöscht, da das Problem transient sein kann). Der
       Komfort-Cache kann dadurch nie mehr eine sonst erfolgreiche Aktion (Export,
       Neu-Erkennung) nachträglich in einen Fehlerzustand kippen.
       **Nachreichung:** der erste Korrekturdurchlauf hatte hierfür noch keinen echten
       I/O-Regressionstest, nur die Race-/Restore-/Fortschritts-Fixes waren getestet.
       Ergänzt: `FileTableDraftStoreTest` (ein Verzeichnis anstelle der erwarteten
       Datei löst beim Lesen eine echte `IOException` aus — Datei wird bewusst nicht
       gelöscht; `saveDraft()` wirft eine `IOException` weiter, wenn `table_drafts`
       durch eine Datei blockiert ist, statt sie zu verschlucken — Fail-Soft ist
       bewusst Aufgabe des Aufrufers, nicht des Stores), `TableExportViewModelTest`
       mit einem `FakeTableDraftStore`, der `saveDraft`/`loadDraft`/`deleteDraft`/
       `deleteStaleDrafts` gezielt mit `IOException` fehlschlagen lässt (fehlgeschlagenes
       Laden fällt fail-soft auf eine frische Extraktion zurück; fehlgeschlagenes
       Cleanup verhindert die Extraktion nicht; ein fehlgeschlagener Debounce-Save
       lässt den Screen nicht abstürzen; ein fehlgeschlagenes Löschen nach Export
       überschreibt den Erfolg nicht mit einer Fehlermeldung).
    4. *Niedrig:* Seitenfortschritt blieb nach einem Modelldownload unsichtbar. Der
       `onProgress`-Callback setzte nur `_pageProgress`, löschte aber nicht den zuvor
       gesetzten `_ocrStatus` (z. B. `InstallingModel`); `LoadingContent` im Screen
       zeigt den Modellstatus vorrangig vor dem Seitenfortschritt an, wodurch der
       Download-Text während der gesamten Seitenerkennung stehen blieb. Fix:
       `onProgress` setzt `_ocrStatus.value = null`, sobald der erste Seitenfortschritt
       eintrifft. Regressionstest: `TableExportViewModelTest` liest den Modellstatus
       synchron innerhalb des Fake-Extractor-Callbacks unmittelbar nach `onProgress()`
       und erwartet `null`.
    - Verifiziert nach beiden Korrekturdurchläufen: vollständiger
      `./gradlew testDebugUnitTest`-Lauf (645 Tests, 0 Failures/Errors, davon 11 neue
      Regressionstests gegenüber dem Stand vor diesem Review),
      `./gradlew :app:compileDebugKotlin`, `./gradlew :app:compileDebugAndroidTestKotlin`
      und `./gradlew lintDebug` (weiterhin 0 Fehler, 28 unveränderte Warnungen).
  - Bewusst noch nicht angefasst: Instrumentation-Smoke-Test für den Screen,
    Excel/LibreOffice-Exportprüfung, reale Phase-0-Fixtures/Tuning,
    Hilfe/Info-Screen und `docs/Bedienungsanleitung.md` (siehe Phase 5).
- **Phase 5 — Instrumentation, Excel/LibreOffice-QA, reale Phase-0-Fixtures und
  Tuning, Hilfe/Info/Handbuch: in Umsetzung (2026-07-05).**
  - Reale Phase-0-Fixtures: als optionale Nachbesserung zurückgestuft, keine
    Freigabebedingung mehr (Entscheidung siehe Phase 0 oben).
  - Neu: `androidTest/util/TableExtractionInstrumentedTest.kt` — deckt den
    Plan-Punkt „Kleines Fixture-PDF mit echter ML-Kit-Erkennung" ab. Baut eine
    einseitige Bild-PDF (PdfBox, analog zu `createImagePdfPages` in
    `SearchableAndRoundTripInstrumentedTest`) mit einer Kopf- plus drei
    Datenzeilen zu je drei Spalten und treibt die reale Produktionskette
    `ExtractTableUseCase` → `MlKitOcrPositionedTextExtractor` →
    `OcrPipeline`/`MlKitTextRecognizerRunner`/`AndroidPdfPageInputImageLoader`
    (echter `PdfRenderer`, echtes ML Kit) → `reconstructTable()` end-to-end;
    erwartet mindestens 2 Spalten, mindestens 3 Zeilen und die eingebetteten
    Zellwerte.
  - Hilfe/Info/Handbuch aktualisiert: neue Strings `help_item_export_table`/
    `info_feature_table_export` in `strings_table_export.xml` (alle zehn
    Locales, per Diff gegen die englische Referenz verifiziert); neuer
    `HelpAction`-Eintrag im Export-Abschnitt von `HelpScreen.kt`; neue
    Feature-Zeile in `InfoScreen.kt`; neuer Abschnitt „Als Tabelle exportieren
    (CSV/TSV)" in `docs/Bedienungsanleitung.md` (nach dem Word-Export-Abschnitt,
    gleicher Aufbau: nummerierte Schritte, Hinweis-Block, Grenzen).
  - `docs/privacy-policy.html` gegengeprüft: keine inhaltliche Änderung nötig,
    da OCR bereits generisch als lokale ML-Kit-Verarbeitung beschrieben ist und
    der Export bereits generisch über den Downloads-Bereich/Teilen-Mechanismus
    abgedeckt ist; der temporäre Entwurfs-Cache in `cacheDir` fällt unter die
    bereits dokumentierte lokale Verarbeitung und begründet keinen neuen
    Datenfluss.
  - **Ausführung auf echtem Gerät (Samsung SM-A536B, API 36) abgeschlossen
    (2026-07-05).** Beide bis dahin ausstehenden Instrumentation-Tests
    (`PdfPageInputImageLoaderInstrumentedTest`, `TableExtractionInstrumentedTest`)
    liefen erstmals auf echter Hardware; dabei fanden sich drei reale, nur auf
    einem echten Gerät reproduzierbare Fehler, die kein JVM-Test und kein
    Compile-Check hätten finden können:
    1. *Hoch:* Alle drei `@Test`-Methoden in `PdfPageInputImageLoaderInstrumentedTest`
       waren als `fun test() = runBlocking { ... }` geschrieben; da `runBlocking`
       (anders als `kotlinx-coroutines-test`s `runTest`) generisch über den
       Block-Rückgabetyp ist, inferierte der Compiler den Methoden-Rückgabetyp
       aus der letzten Anweisung im Block (`pdfFile.delete()` → `Boolean`).
       Kotlin kompilierte das anstandslos, aber JUnit lehnt `@Test`-Methoden
       ohne `void`-Rückgabe zur Laufzeit mit `InvalidTestClassError` ab — alle
       drei Tests konnten nicht einmal instanziiert werden. Fix: `pdfFile
       .delete()` gefolgt von einem expliziten `Unit`-Ausdruck als letzte
       Anweisung in jedem Testblock.
    2. *Mittel:* Der Cancellation-Test synchronisierte die Haupt-Coroutine
       (`runBlocking`) und die `async`-Coroutine auf `Dispatchers.Default`
       (echter, separater Thread) nur über ein blosses `yield()` — auf einem
       echten Gerät gewann gelegentlich `cancelAndJoin()` das Rennen, bevor
       Seite 0 überhaupt zu laufen begann, wodurch `processedPages` bei 0 blieb
       und die Assertion `in 1..5` flaky fehlschlug. Fix: `CompletableDeferred`,
       das von der Seite-0-Verarbeitung selbst signalisiert wird; die
       Haupt-Coroutine wartet darauf, bevor sie abbricht. Über fünf
       Wiederholungen hinweg stabil grün (vorher reproduzierbar flaky).
    3. *Niedrig:* Der Dimensionstest nahm „595 × 842pt" für eine PDFBox-A4-Seite
       an; Android meldete auf dem echten Gerät für die Höhe tatsächlich 841
       (nicht 842) über `PdfRenderer.Page.height`, da PDFBox' `PDRectangle.A4`
       intern 595.276 × 841.89pt ist und Android das anders rundet/truncatet
       als angenommen — Test schlug mit „expected 2572 but was 2569" fehl. Fix:
       die Seitenmaße werden jetzt über einen echten `PdfRenderer` auf der
       Test-PDF selbst abgefragt statt als „595, 842" angenommen.
    - Nach allen drei Fixes: `PdfPageInputImageLoaderInstrumentedTest`
      (5 Wiederholungen) und `TableExtractionInstrumentedTest` (2 Wiederholungen)
      stabil grün; vollständige `connectedDebugAndroidTest`-Suite 52/52 grün
      (0 skipped, 0 failed).
  - **Manueller End-to-End-Durchlauf auf echtem Gerät (2026-07-05).** Über
    `adb`/`uiautomator` wurden zwei echte Dokumente importiert (ein
    selbstgebautes Tabellen-PDF sowie `testtabelle.pdf`, eine vom Nutzer
    bereitgestellte reale Mehrzeilentabelle mit absichtlich unsicheren/leeren
    Zellen) und der komplette Flow durchgespielt: Aktions-Sheet → Erkennung
    (inkl. Modellstatus/Seitenfortschritt) → Draft-Restore-Dialog beim
    erneuten Öffnen (bestätigt Finding 1/2 aus der Review-Runde real) →
    Zell-Editierdialog → Speichern in Downloads → Teilen (FileProvider,
    Android-Share-Sheet erkennt die Datei korrekt inkl. Excel-Icon). Die
    exportierte CSV wurde byte-genau geprüft: UTF-8-BOM vorhanden, `\r\n`,
    Semikolon-Trennzeichen (Dezimalkomma-Locale), Umlaute (`ö`, `ä`) korrekt
    UTF-8-kodiert, leere Zelle korrekt als `;;` ohne Platzhalter. Die
    Qualitätswarnung erschien korrekt bei einer Tabelle mit inkonsistenter
    OCR-Konfidenz, inklusive korrekter Pro-Zelle-Warnsymbole nur bei den
    tatsächlich unsicheren Zellen.
    - **Dabei gefunden und behoben (Hoch, UI-Layout-Bug):**
      `TableExportScreen`s Button-Zeile („In Downloads speichern" /
      „Teilen") hatte kein `Modifier.weight(1f)` auf beiden Buttons; auf
      echter Gerätebreite konsumierte der lange erste Button-Text so viel
      Platz, dass der zweite Button auf nahezu 0dp Restbreite gequetscht wurde
      — sein Text brach dadurch buchstabenweise vertikal um (unlesbar). Nur
      auf einem echten Gerät mit echten (langen deutschen) Locale-Strings
      beobachtbar, nicht in einem Layout-Review erkennbar. Fix: beide Buttons
      erhalten `Modifier.weight(1f)` (teilen sich die Breite fair auf) sowie
      `maxLines = 1` + `TextOverflow.Ellipsis` als zusätzlicher Schutz vor noch
      längeren Übersetzungen in anderen Locales.
    - Bewusst nicht automatisiert geprüft, da dafür Fremdanwendungen bzw.
      genuines menschliches Urteilsvermögen nötig sind: tatsächliches Öffnen
      der CSV in Excel/LibreOffice (die Byte-Ebene wurde stattdessen
      programmatisch verifiziert), TalkBack-Vorlesequalität, subjektive
      Dark-Mode/Landscape/RTL-Optik. Diese bleiben als optionale manuelle
      Nachprüfung offen, sind aber keine Freigabebedingung mehr, da die
      zugrunde liegende Datenkorrektheit bereits verifiziert ist.
  - Nach dem UI-Fix erneut verifiziert: vollständiger
    `./gradlew testDebugUnitTest`-Lauf (645 Tests, 0 Failures/Errors),
    `./gradlew :app:compileDebugKotlin`, `./gradlew :app:compileDebugAndroidTestKotlin`,
    `./gradlew lintDebug` (0 Fehler, weiterhin 28 unveränderte Warnungen) und
    die vollständige `connectedDebugAndroidTest`-Suite (52/52 grün).
  - Bewusst noch offen: reale Phase-0-Fixtures/Tuning über den einen realen
    Test-Datensatz hinaus (optionale Nachbesserung, siehe Phase 0); CJK-Export
    real geprüft (kein CJK-Testdokument in dieser Session verfügbar).
  - **Review-Korrekturen (2026-07-05, externes Review nach der Geräte-Session,
    drei Concurrency-/Datenintegritäts-Findings vor Freigabe behoben):**
    1. *Mittel:* Ein Draft wurde ausschließlich über die `scanId` akzeptiert.
       Wurden Seiten desselben Dokuments (gleiche `scanId`) nach dem Speichern
       eines Drafts gedreht, gelöscht oder neu sortiert, hätte ein erneutes
       Öffnen innerhalb von 24 h veraltete Zeilen aus der alten PDF-Version
       angeboten — ein stiller Datenfehler bei „Fortsetzen". Fix: `TableDraft`
       trägt jetzt einen Fingerabdruck der Quelle (`sourceFileSize`,
       `sourceLastModified`, `sourcePageCount`); `checkForDraftOrExtract()`
       vergleicht ihn beim Laden gegen den aktuellen Dateistand
       (`isDraftFresh()`) und verwirft einen nicht mehr passenden Draft fail-soft
       statt ihn anzubieten. Alte, vor diesem Fix gespeicherte Drafts (Default
       0/0/0) werden dadurch automatisch als veraltet erkannt und verworfen.
       Regressionstest: `TableExportViewModelTest` — ein Draft mit
       absichtlich falschem Fingerabdruck löst keinen Restore-Dialog aus,
       sondern eine frische Extraktion.
    2. *Mittel:* `shareFiles()` las `_dialect.value` zweimal — einmal für den
       Dateiinhalt/-namen, ein zweites Mal danach für `ShareFilesRequest`
       (MIME-Typ). Wechselte der Nutzer während eines langsamen Exports das
       Trennzeichen (CSV↔TSV), hätte die Datei den alten, der Share-Intent
       aber den neuen MIME-Typ getragen — der Empfänger hätte die Datei falsch
       interpretiert. Fix: Dialekt wird in `saveToDownloads()` und
       `shareFiles()` jeweils einmalig synchron vor dem Start der Coroutine in
       eine lokale `val` erfasst und danach durchgängig verwendet, analog zum
       bereits bestehenden Muster für `csvPages`. Regressionstest: ein
       `FakeShareFileStore`-Hook ändert den Dialekt gezielt zwischen den beiden
       ehemaligen Lesezeitpunkten; ohne den Fix hätte das `ShareFilesRequest`
       den neuen statt des beim Aufruf aktiven Dialekts getragen.
    3. *Mittel:* `reExtract()` prüfte `_loading.value`, setzte es aber erst
       innerhalb von `extract()` — bei einem schnellen Doppel-Tap auf „Erneut
       versuchen" konnten beide Aufrufe die Prüfung noch mit `_loading ==
       false` passieren, bevor die erste Coroutine überhaupt zu laufen begann,
       und zwei parallele OCR-Läufe für dieselbe PDF anstoßen (doppelter
       Bitmap-/ML-Kit-Speicherverbrauch, Race um Seiten-/Draft-Zustand). Fix:
       `_loading.value = true` wird jetzt synchron gesetzt, bevor die
       Coroutine überhaupt gestartet wird — sowohl in `reExtract()` als auch
       (aus Konsistenzgründen, dieselbe Struktur) in
       `discardDraftAndReextract()`. Regressionstest: zwei `reExtract()`-Aufrufe
       vor dem ersten `advanceUntilIdle()` lösen nur eine Extraktion aus.
    - Alle drei Regressionstests wurden gegen den ungefixten Code verifiziert
      (Fixes testweise zurückgesetzt, Tests schlugen erwartungsgemäß fehl,
      Fixes danach wiederhergestellt) — echte, nicht zufällig grüne Tests.
    - Verifiziert: vollständiger `./gradlew testDebugUnitTest`-Lauf (648 Tests,
      0 Failures/Errors, 3 neue Regressionstests), `./gradlew
      :app:compileDebugKotlin` und `./gradlew :app:compileDebugAndroidTestKotlin`
      fehlerfrei.

Ziel ist, Tabellen aus einem unverschlüsselten PDF lokal per ML-Kit-OCR zu erkennen,
vor dem Export zu prüfen und als Excel-/LibreOffice-taugliche CSV- oder TSV-Dateien
zu speichern bzw. zu teilen.

## Ergebnis der Codeprüfung

Der technische Türöffner existiert: `TextRecognizerRunner.recognizeFullText()` liefert
bereits das vollständige ML-Kit-`Text`-Objekt. Dessen Block-, Line- und Element-Ebenen
enthalten Text und Geometrie. Der heutige Mehrseitenpfad
`TextRecognizerRunner.processPages()` reduziert dieses Objekt jedoch auf `String` und
`OcrResultStats`; die Geometrie geht dort verloren.

Der bisherige Plan war in folgenden Punkten zu optimistisch oder unvollständig:

- `PdfPageInputImageLoader` liefert aktuell weder Seitenindex noch Bitmap-Abmessungen
  an den Callback. Beides wird für Positionsmodelle und Fortschritt benötigt.
- `OcrResultStats.angle` ist aktuell der **arithmetische Mittelwert** der
  Elementwinkel, kein robuster Median. Der Deskew-Winkel muss für jede Seite neu aus
  den erhaltenen Elementen bestimmt werden.
- Eine flache Liste von Line-Boxen reicht nicht. Die Zuordnung Line → Elements muss
  erhalten bleiben, damit mehrdeutige Lines an Spaltengrenzen in v1 zerlegt werden
  können.
- Globales Spaltenclustering über die ganze Seite vermischt Briefkopf, Fließtext,
  Tabelle und Fußzeile. Vor der Zellzuordnung ist eine Tabellenbereichserkennung
  erforderlich.
- „Vorschau“ ohne Korrekturmöglichkeit ist kein belastbarer Review. Mindestens
  Zelltextkorrektur und Zeilen-Ein/Ausschluss gehören in v1.
- Der vorhandene `OcrPipelineStatus` beschreibt nur Modellvorbereitung/-download,
  nicht den Seitenfortschritt. Dafür ist ein separater Callback nötig.
- `file_paths.xml` erlaubt bisher nur `scans/` und `vcards/`. Für Teilen aus dem Cache
  muss ein eigener `csv/`-Cache-Pfad ergänzt werden.
- `DownloadsStorage` nutzt MediaStore und nicht
  `domain/common/resolveUniqueFilename()`. Der Export darf daher keine lokale
  Verzeichnisauflösung behaupten; Namenskollisionen werden durch MediaStore behandelt.
- RFC 4180 definiert Komma als Trennzeichen. Semikolon- und Tab-Ausgaben verwenden
  dieselben Quoting-Regeln, sind aber RFC-4180-nahe Dialekte, nicht strikt RFC 4180.

## Ergebnis des Android-Robustheits-Reviews (2. Überarbeitung)

Vier Findings wurden geprüft; drei sind übernommen, eines teilweise:

1. **Process Death im Review (übernommen).** Der editierbare Review hält
   `EditableTable`-Zustand im ViewModel. Beendet Android den Prozess im Hintergrund,
   wäre alle Korrekturarbeit verloren. `SavedStateHandle` ist dafür ungeeignet: die
   Zustandsgröße ist inhaltsabhängig, und Binder-Transaktionen kippen je nach
   Gesamtlast bereits deutlich unter dem 1-MB-Limit
   (`TransactionTooLargeException`). Lösung: leichtgewichtiger Entwurfs-Cache auf
   Disk, siehe Abschnitt „Entwurfs-Persistenz".
2. **Render-Auflösung für Tabellen (übernommen, präzisiert auf 220 DPI).** 150 DPI
   reichen für Volltext, aber bei eng gedruckten Kontoauszügen (~6-pt-Schrift)
   liegen Zeichen bei nur ~12 px Höhe — unter der ML-Kit-Empfehlung von ~16 px.
   Verschmelzen benachbarte Spalten dadurch schon auf Bitmap-Ebene zu einer Line,
   kann keine nachgelagerte Heuristik sie mehr trennen. 300 DPI wären dagegen
   Verschwendung: der bestehende 3 000-px-Cap würde A4 ohnehin auf ~257 DPI
   begrenzen, bei ~25 MB Bitmap-Peak. Details im OCR-Unterbau-Abschnitt.
3. **Compose-Rendering großer Tabellen (übernommen).** Zwingend `LazyColumn` für
   Zeilen plus ein **gemeinsamer** horizontaler Scroll-State; Spaltenbreiten werden
   im ViewModel vorberechnet. `SubcomposeLayout` wird ausdrücklich verworfen: es
   müsste zur Breitenmessung alle Zeilen komponieren und würde damit genau die
   Laziness zerstören, die es schützen soll. Details im Review-Abschnitt.
4. **Tastatur im Zell-Editierdialog (teilweise übernommen).** Passender
   `KeyboardType` je nach Zellinhalt: ja. Der weitergehende Vorschlag, Eingaben
   aktiv auf das lokalisierte Dezimaltrennzeichen umzuschreiben, wird
   **zurückgewiesen**: welches Zeichen der Nummernblock liefert, entscheidet die
   IME, nicht die App, und eine stille Wertetransformation kollidiert mit dem
   Nicht-Ziel „keine semantische Typisierung" — Zellen sind Text, die Interpretation
   macht das Tabellenprogramm beim Import.

## Verbindliche Produktentscheidungen für v1

### 1. Lokalisierter Delimiter-Default

Der Default wird nicht über eine hart codierte Sprachliste bestimmt.

- Die Android-Schicht liest das Dezimaltrennzeichen der aktuellen **Format-Locale**:
  `DecimalFormatSymbols.getInstance(Locale.getDefault(Locale.Category.FORMAT))`.
- Dezimaltrennzeichen `,` → CSV-Default `;`.
- Alle anderen Dezimaltrennzeichen → CSV-Default `,`.
- Der Nutzer kann im Review jederzeit Komma, Semikolon oder Tabulator wählen.
- Die pure Domain-Funktion erhält nur `decimalSeparator: Char`; sie importiert keine
  Android-Locale-API.

Das ist eine belastbare Näherung für den systemweiten Listentrenner. Android stellt
den tatsächlichen Excel-/OS-Listentrenner nicht plattformübergreifend bereit.

### 2. Mehrseiten-Export: eine Datei pro Seite

Jede ausgewählte Seite erzeugt eine eigene Datei:

- eine Seite: `<basis>_table.csv`
- mehrere Seiten: `<basis>_table_p001.csv`, `<basis>_table_p002.csv`, …

CSV kennt weder Arbeitsblätter noch eine standardisierte Seitentrennung. Eine
Leerzeile zwischen Seiten wäre mehrdeutig, und unterschiedliche Spaltenstrukturen
würden in einer Datei vermischt. „Seiten zusammenführen“ ist deshalb kein v1-Default
und kann später als explizite Option ergänzt werden.

Bei Tabulator als Trennzeichen wird konsequent `.tsv` mit
`text/tab-separated-values` verwendet; sonst `.csv` mit `text/csv`.

### 3. Review: Zelltext editierbar, Struktur noch nicht

v1 erlaubt:

- Zelltext per Tap in einem Dialog zu korrigieren,
- erkannte Zeilen ein-/auszuschließen,
- Änderungen der aktuellen Seite zurückzusetzen,
- Seiten für den Export ein-/auszuwählen.

Nicht in v1: Spalten/Zeilen hinzufügen, löschen, verschieben oder verbinden. Diese
Strukturoperationen würden einen deutlich größeren Tabelleneditor erfordern.

### 4. Element-basierte Verfeinerung gehört in v1

`Line` bleibt die primäre Einheit. Wenn eine Line jedoch mehrere ermittelte Spalten
überlappt oder ihre Zuordnung mehrdeutig ist, werden ihre `Element`-Boxen zur
Aufteilung verwendet. Ohne diesen begrenzten Fallback wäre die dokumentierte
Kernfunktion gerade bei engen Rechnungs- und Kontoauszugsspalten zu unzuverlässig.

## Umfang und Nicht-Ziele

### v1

- On-demand-OCR; keine Persistierung der Bounding-Boxes und keine Room-Migration.
- Eine automatisch gewählte Haupttabelle pro PDF-Seite.
- Review und Korrektur der erkannten Zelltexte.
- Export ausgewählter Seiten als einzelne CSV-/TSV-Dateien.
- Speichern in Downloads und Teilen über FileProvider.
- Manuelle OCR-Sprachauswahl wie im bestehenden OCR-Flow.

### Nicht in v1

- Mehrere getrennte Tabellen auf derselben Seite exportieren.
- Vollwertiger Tabelleneditor mit strukturellen Operationen.
- Semantische Typisierung von Datum, Währung oder Zahl.
- Erkennung von `rowspan`/`colspan`.
- Dauerhaftes Persistieren von OCR-Geometrie oder Review-Änderungen (Room/DB).
  Der temporäre Entwurfs-Cache in `cacheDir` (siehe Entwurfs-Persistenz) ist davon
  ausgenommen — er ist flüchtig und vom OS räumbar.
- Cloud-OCR.

## Architektur und Datenfluss

```text
TableExportScreen
  → TableExportViewModel
    → ExtractTableWorkflow
      → ExtractTableUseCase
        → OcrPositionedTextExtractor
          → OcrPipeline + TextRecognizerRunner + PdfPageInputImageLoader
        → TableReconstructor
    → ExportTableCsvUseCase
      → CsvEncoder
      → DownloadsStorage oder CsvShareFileStore
    → TableDraftStore (Entwurfs-Persistenz gegen Process Death)
```

### Domain-Modelle

Neue Datei `domain/model/PositionedOcrModels.kt`:

- `OcrRect(left, top, right, bottom)`
- `OcrElement(text, bounds, confidence, angleDeg)`
- `OcrLine(text, bounds, elements, confidence, angleDeg)`
- `OcrPositionedPage(pageIndex, widthPx, heightPx, lines)`

Die Line-Element-Hierarchie wird bewusst erhalten. ML-Kit-, Android- und
`android.graphics.Rect`-Typen bleiben außerhalb der Domain.

Neue Datei `domain/model/ExtractedTableModels.kt`:

- `ExtractedCell(text, sourceLineIds, confidence, issues)`
- `ExtractedRow(cells, included = true)`
- `ExtractedTable(pageIndex, rows, columnBounds, confidence, issues)`
- `TableExtractionResult(tablesByPage)`
- `TableIssue`, z. B. `AMBIGUOUS_COLUMN`, `SPANNING_LINE`,
  `LOW_CONFIDENCE`, `WRAPPED_ROW_GUESS`

Die UI erzeugt für Editieren eine eigene immutable `EditableTable`-Darstellung.
OCR-Quellgeometrie wird nicht durch UI-Zustände verändert.

### Ports und UseCases

- `domain/gateway/OcrPositionedTextExtractor`
  - Eingabe: genau ein `Document`, Sprachcode, `onStatus`, `onProgress`.
  - Ausgabe: `List<OcrPositionedPage>` in stabiler Seitenreihenfolge.
- `domain/gateway/CsvShareFileStore`
  - schreibt atomar in `cacheDir/csv/`,
  - räumt veraltete Share-Dateien auf,
  - gibt frameworkfreie `File`-Objekte zurück.
- `domain/gateway/TableDraftStore`
  - `saveDraft(scanId, draft)`, `loadDraft(scanId)`, `deleteDraft(scanId)`,
  - Implementierung in `util/` schreibt JSON atomar nach `cacheDir/table_drafts/`
    (kotlinx-serialization ist bereits Projekt-Dependency),
  - der Draft enthält nur das Rekonstruktionsergebnis plus Nutzer-Edits (Zelltexte,
    Zeilen-/Seitenauswahl, Dialekt), **nicht** die OCR-Geometrie — die wird nach der
    Rekonstruktion nicht mehr benötigt, der Draft bleibt dadurch klein (typisch
    wenige KB),
  - Cache-Semantik ist bewusst: das OS darf `cacheDir` räumen; der Draft ist
    Komfort-Wiederherstellung, keine garantierte Persistenz.
- `domain/usecase/ExtractTableUseCase`
  - führt Geometrie-OCR und Rekonstruktion aus,
  - wirft einen eigenen No-Table-Fehler, wenn keine Seite eine belastbare
    Mehrspaltenstruktur enthält.
- `domain/workflow/ExtractTableWorkflow`
  - nutzt `DocumentWorkflowGuard`,
  - prüft Dateiexistenz und tatsächliche PDF-Verschlüsselung,
  - mappt Fehler in den bestehenden Workflow-Fehlerstil.
- `domain/usecase/ExportTableCsvUseCase`
  - erhält die **editierten** und ausgewählten Tabellen sowie den Dialekt,
  - erzeugt pro Seite eine Datei,
  - löscht bei einem Fehler bereits erzeugte `DownloadEntry`s, damit kein
    unvollständiger Mehrdatei-Export zurückbleibt,
  - gibt die tatsächlich angelegten Dateinamen bzw. Share-Dateien zurück.

`OcrUsage.TABLE_EXTRACTION` wird ergänzt und in `OcrManager.recognitionPlan()` wie
`EXTRACT_TEXT` behandelt. Dadurch erzwingt ein exhaustives `when` die korrekte
Einbindung.

### Änderungen im OCR-Unterbau

Statt einer zweiten, fast identischen Renderpipeline wird der bestehende Loader
abwärtskompatibel erweitert:

- `PdfPageInputImageLoader` liefert pro Callback
  `pageIndex`, `pageCount`, `bitmapWidth`, `bitmapHeight` und `InputImage` und
  akzeptiert einen Render-Scale-Parameter (Default: bisherige 150 DPI).
- `TextRecognizerRunner` erhält eine Full-Text-Mehrseitenvariante mit Callback auf
  `Text` statt `String`.
- Der bisherige String-Pfad delegiert darauf, damit Text-OCR und Tabellen-OCR dieselbe
  Rendering- und Bitmap-Recyclinglogik verwenden.
- Das ML-Kit-Mapping in `util/` überspringt Geometrieobjekte ohne Bounding-Box,
  behält aber leere Seiten im Ergebnis, damit PDF- und Ergebnisindex ausgerichtet
  bleiben.
- `onStatus(OcrPipelineStatus)` und `onProgress(currentPage, totalPages)` bleiben
  getrennt.
- `CancellationException` wird auf allen Ebenen weitergeworfen.

Der neue Pfad verarbeitet Bitmaps weiterhin sequentiell. Nur die kompakten
frameworkfreien Geometriedaten werden bis zum Review im ViewModel gehalten.

### Render-Auflösung des Tabellenpfads: 220 DPI

Der Tabellenpfad rendert mit eigenem `PDF_TABLE_RENDER_SCALE = 220f / 72f` bei
unverändertem `PDF_OCR_MAX_BITMAP_SIDE = 3 000`; der Volltextpfad bleibt bei 150 DPI.
Begründung mit konkreten A4-Zahlen (595 × 842 pt, ARGB_8888):

| DPI | Bitmap | RAM/Seite | Anmerkung |
|---:|---|---:|---|
| 150 | 1 240 × 1 754 | ~8,7 MB | heutiger Volltext-Standard; ~12 px Zeichenhöhe bei 6-pt-Schrift — unter ML-Kit-Empfehlung (~16 px) |
| 220 | 1 818 × 2 573 | ~18,7 MB | ~18 px Zeichenhöhe; unter dem 3 000-px-Cap; gewählt |
| 300 | 2 480 × 3 508 | — | Cap greift (3 508 > 3 000) → effektiv nur ~257 DPI bei ~25,5 MB Peak; Mehrverbrauch ohne vollen Nutzen |

Da die Bitmaps sequentiell verarbeitet und sofort recycelt werden, ist der Peak von
~19 MB pro Seite vertretbar; ML Kit hält intern eine weitere Kopie, weshalb 300 DPI
auf Low-End-Geräten unnötiges OOM-Risiko wäre. Für den Algorithmus ist der Scale
transparent (alle Toleranzen sind relativ), aber die Phase-0-Fixtures müssen mit dem
finalen Tabellen-Scale erzeugt werden, damit das Tuning die echte Trennschärfe sieht.

## Rekonstruktionsalgorithmus

`TableReconstructor` liegt in `domain/common/` und bleibt pure Kotlin. Alle
Schwellwerte stehen in einer `TableReconstructionConfig`, damit Tests und Tuning keine
Magic Numbers verteilen.

### 1. Bereinigung und robuste Skalenwerte

- Leere Lines und ungültige Boxen verwerfen.
- Median-Zeilenhöhe und robuste Zeichenbreite aus nichtleeren Elementen bestimmen.
- Seitenwinkel als Median der normalisierten Elementwinkel berechnen; Winkel vorher
  in einen gemeinsamen Bereich falten, damit Werte an der ±180°-Grenze nicht
  auseinanderlaufen.

### 2. Deskew

Bei einem relevanten Medianwinkel werden Boxmittelpunkte und -ecken in ein
deskewtes Koordinatensystem projiziert. Zeilenclustering und Spaltenbestimmung nutzen
danach ausschließlich diese projizierten Koordinaten.

Nur Mittelpunkte zurückzurotieren und gleichzeitig die alten y-Intervalle zu
verwenden wäre geometrisch inkonsistent und wird nicht umgesetzt.

### 3. Zeilenclustering

- Kandidaten nach projiziertem y-Zentrum sortieren.
- Zugehörigkeit anhand vertikaler Überlappung **oder** adaptivem Baseline-/Center-
  Abstand bestimmen.
- Clustergrenzen werden laufend robust aktualisiert.
- Tests müssen belegen, dass das Ergebnis unabhängig von der Eingabereihenfolge ist;
  falls Greedy dies nicht erfüllt, wird Sweep-Line plus Union-Find verwendet.

### 4. Tabellenbereich erkennen

Aus aufeinanderfolgenden Zeilen werden Regionen mit wiederkehrenden horizontalen
Lücken und Kanten gebildet. Eine Region braucht mindestens:

- zwei plausible Spalten,
- drei unterstützende Tabellenzeilen,
- wiederkehrende Kanten-/Lückensignale über einen konfigurierbaren Anteil der Zeilen.

Regionen werden nach Zeilenanzahl, Spaltenkonsistenz, Zellbelegung und
Geometrie-Konfidenz bewertet. v1 liefert pro Seite nur die bestbewertete Region.
Briefkopf und Fußzeile fließen damit nicht in die globale Spaltenschätzung ein.

Wird keine Region über dem Mindestscore gefunden, gilt die Seite als „keine Tabelle
erkannt“. Ein einspaltiger OCR-Text wird **nicht** als Tabelle ausgegeben; dafür
existiert bereits der OCR-TXT-Export.

### 5. Spalten bestimmen

Innerhalb der Kandidatenregion werden zwei Signale kombiniert:

1. freie x-Intervalle zwischen benachbarten Boxen, normiert über robuste
   Zeichenbreite und Zeilenhöhe;
2. Cluster aus linken und rechten Boxkanten.

Ein fixer globaler Wert wie „in 80 % aller Seitenzeilen frei“ wird vermieden. Der
benötigte Support bezieht sich nur auf die Kandidatenregion und ist adaptiv, damit
leere Zellen und Überschriften die Grenze nicht zerstören.

### 6. Zellen zuordnen und Lines verfeinern

- Eindeutige Line: Zuordnung zur Spalte mit der größten horizontalen Überlappung.
- Mehrdeutige/spaltenübergreifende Line: Elements einzeln zuordnen und je Zelle in
  x-Reihenfolge verbinden.
- Mehrere Fragmente in einer Zelle: deterministisch nach y, dann x sortieren.
- Eine nahe Folgezeile, die nur dieselbe Textspalte belegt, kann als umgebrochener
  Zellinhalt an die vorherige Tabellenzeile angehängt werden; diese heuristische
  Entscheidung wird mit `WRAPPED_ROW_GUESS` markiert.
- Leere Zellen bleiben als leere Strings erhalten.
- Mehrdeutige Zuordnungen werden nicht still verschluckt, sondern im Resultat
  markiert und im Review hervorgehoben.

## CSV-/TSV-Serialisierung

`CsvEncoder` erhält einen expliziten `DelimitedTextDialect`:

- Trennzeichen: `,`, `;` oder `\t`
- Zeilenende: `\r\n`
- Encoding: UTF-8
- UTF-8-BOM: standardmäßig aktiv für Excel unter Windows
- Quoting: Feld in `"` einschließen, wenn es Trennzeichen, `"`, CR oder LF enthält;
  innere `"` als `""` schreiben

Die BOM wird einmal pro Datei als Bytes geschrieben, nicht als Bestandteil des ersten
Zellwerts. Der Encoder schreibt direkt auf einen `OutputStream`, damit die
Speichernutzung nicht von der Tabellengröße abhängt.

### Schutz vor Spreadsheet-Formeln

OCR-Inhalt ist nicht automatisch vertrauenswürdig. Der Review bietet deshalb
„Formeln sicher behandeln“, standardmäßig aktiv. Beginnt der erste sichtbare
Zellinhalt mit `=`, `+`, `-` oder `@`, wird für den Export ein Apostroph vorangestellt.
Der Review-Text selbst bleibt unverändert. Der Nutzer kann die Option für einen
bytegetreuen Datenexport abschalten.

Diese Transformation wird separat getestet und im UI erklärt, weil sie bei negativen
Zahlen dazu führt, dass Tabellenprogramme den Wert als Text behandeln.

## UI/UX

### Einstieg und Navigation

- Neue Aktion `ScanAction.ExportTableCsv` im Export-Abschnitt von
  `DocumentEditSheet`.
- Sichtbar nur bei mindestens einer Seite und unverschlüsseltem Dokument.
- Unabhängig von `showTextExportActions`, weil gespeicherter OCR-Text nicht verwendet
  wird.
- Neue Route `Screen.TableExport("table-export/{scanId}")` mit `createRoute`.
- Home-Dispatcher, Navigation-Callbacks, `AppNavHost`, Viewer-`when` und Previews
  werden wegen der exhaustiven `ScanAction`-Verwendung mit aktualisiert.

### Zustände des Screens

- Laden: Modellstatus und separater Seitenfortschritt.
- Kein Ergebnis: verständlicher Hinweis, Sprachauswahl und „Erneut erkennen“; kein
  irreführender Einspaltenexport.
- Ergebnis: Seite/Pager mit Tabelle, Qualitätswarnungen und Seitenauswahl.
- Fehler: eigener `StateFlow<String?>` → AlertDialog.
- Erfolg: Snackbar; der Screen bleibt für einen weiteren Export geöffnet.

### Review

- Horizontal und vertikal scrollbare Tabelle mit stabiler gemeinsamer Spaltenbreite
  pro Seite.
- Voller Zellinhalt ist zugänglich; Ellipsis darf nicht die einzige
  Zugriffsmöglichkeit sein.
- Tap auf Zelle öffnet einen Editierdialog.
- Zeilen können ein-/ausgeschlossen werden; unsichere Zellen sind visuell und über
  Semantics gekennzeichnet.
- Seitenauswahl, Delimiter-Dropdown, Formel-Schutz und Zurücksetzen sind sichtbar.
- Große Schrift, Landscape und RTL werden manuell geprüft. Geometrische
  Spaltenreihenfolge bleibt in v1 links nach rechts und wird als Einschränkung
  dokumentiert.

**Compose-Umsetzung des Grids (verbindlich):**

- `LazyColumn` für die Zeilen mit stabilen Keys; jede Zeile ist eine flache `Row`
  aus Zell-Composables fester Breite (nur `Text` + Rahmen, keine `TextField`s im
  Grid — Editieren läuft ausschließlich über den Dialog).
- **Ein** gemeinsamer `rememberScrollState()` für `Modifier.horizontalScroll` über
  Kopfzeile und alle Zeilen, sonst laufen die Zeilen beim Scrollen horizontal
  auseinander.
- Spaltenbreiten werden **im ViewModel vorberechnet**: pro Spalte aus der maximalen
  Zeichenzahl geschätzt, auf ein Min/Max geclampt (z. B. 64–240 dp). Deterministisch
  und JVM-testbar. `SubcomposeLayout` ist verworfen: es müsste zur Breitenmessung
  alle Zeilen komponieren und würde die Laziness zerstören.

**Editierdialog:**

- Ersttastatur per Heuristik: sieht der bestehende Zellwert strikt numerisch aus
  (Ziffern, Vorzeichen, Dezimal-/Tausendertrennzeichen), startet der Dialog mit
  `KeyboardType.Decimal` (zeigt das Dezimaltrennzeichen der IME), sonst
  `KeyboardType.Text`.
- Keine Eingabe- oder Wertetransformation durch die App: was der Nutzer tippt, steht
  in der Zelle und später bytegleich (bis auf Quoting/Formel-Schutz) in der CSV.

### Entwurfs-Persistenz (Process Death)

- Nach erfolgreicher Rekonstruktion und danach debounced nach jeder Nutzeränderung
  schreibt das ViewModel den Draft über `TableDraftStore` (JSON, `cacheDir/table_drafts/`,
  eine Datei pro `scanId`, atomar via Temp-Datei + Rename).
- `SavedStateHandle` trägt nur die `scanId` (steckt ohnehin in der Route) — nie
  Tabellendaten.
- Beim Öffnen des Screens: existiert ein Draft zur `scanId`, fragt ein Dialog
  „Vorherigen Entwurf fortsetzen?" (Fortsetzen / Neu erkennen). Das deckt sowohl
  Prozess-Wiederherstellung als auch versehentliches Verlassen ab, ohne
  Spezialfall-Logik für die Navigationsquelle.
- Gelöscht wird der Draft bei erfolgreichem Export, bei „Neu erkennen" und per
  Alters-Cleanup (> 24 h) beim nächsten Öffnen des Features.
- Kein Room, keine Migration, keine neue persistente Datenkategorie — `cacheDir`
  bleibt vom OS räumbar und ist von Backups ohnehin ausgeschlossen.

### Speichern und Teilen

- „In Downloads speichern“ schreibt alle ausgewählten Seiten transaktional soweit
  über MediaStore möglich; bei einem Fehler werden bereits erzeugte Einträge gelöscht.
- „Teilen“ schreibt in `cacheDir/csv/` und verwendet:
  - eine Datei → `ACTION_SEND`
  - mehrere Dateien → `ACTION_SEND_MULTIPLE`
- MIME-Typ und Dateiendung folgen dem gewählten Dialekt.
- `file_paths.xml` erhält `<cache-path name="csv" path="csv/" />`.
- Alle Uris bekommen `FLAG_GRANT_READ_URI_PERMISSION`.

## Fehlerfälle und Grenzen

- Verschlüsselte PDFs: Aktion ausgeblendet und im Workflow zusätzlich abgefangen.
- Fehlende/ungültige Datei: bestehendes Workflow-Fehlermapping.
- OCR-Modell nicht verfügbar: vorhandene lokalisierte Model-Download-Meldung.
- OCR liefert Text, aber keine belastbare Tabelle: eigener No-Table-Zustand.
- Verbundene Zellen: werden der Spalte mit größter Überlappung zugeordnet und markiert.
- Mehrere Tabellen pro Seite: v1 exportiert nur die bestbewertete.
- Gemischte Schreibrichtung/RTL: geometrische Reihenfolge links nach rechts.
- Arabisch: bestehender Latin-Fallback mit entsprechend begrenzter Qualität.
- Sehr große Dokumente: sequentielles Bitmap-Processing; ViewModel-State enthält nur
  Geometrie und editierte Tabellen.
- Prozess-Kill im Hintergrund während des Reviews: Edits werden aus dem Draft
  wiederhergestellt (siehe Entwurfs-Persistenz); räumt das OS den Cache, startet der
  Nutzer die Erkennung neu — dokumentierte, akzeptierte Restgrenze.

## Datenschutz und Dokumentation

OCR bleibt on-device; gespeichert oder geteilt wird nur auf ausdrückliche Aktion.
Es entstehen keine neuen persistenten App-Daten und keine neue Netzwerkübertragung.
Zu aktualisieren:

- Hilfe und Info-Screen,
- `docs/Bedienungsanleitung.md`,
- Feature-Strings in allen zehn Locales,
- Datenschutzerklärung nur gegenprüfen; eine inhaltliche Änderung ist voraussichtlich
  nicht nötig.

## Teststrategie

### Phase-0-Fixtures

Vor dem Festschreiben der Heuristik werden Geometrie-Fixtures aus mehreren realen,
anonymisierten Dokumenttypen erzeugt:

- Rechnung mit mehrzeiliger Beschreibung,
- Kontoauszug mit engen Zahlenspalten,
- Lieferschein mit leeren Zellen,
- Seite mit Briefkopf und Fußzeile,
- leicht gedrehter Scan,
- Dokument ohne Tabelle.

Die Fixtures enthalten nur Domain-Geometrie und Testtext, keine ML-Kit-Objekte oder
personenbezogenen Originalinhalte. Synthetische Raster allein reichen für die
Bewertung der Heuristik nicht.

### JVM-Unit-Tests

- `TableReconstructorTest`
  - perfektes Raster,
  - y-Jitter und ungeordnete Eingabe,
  - leere Zellen,
  - enge Spalten mit Element-Fallback,
  - mehrzeilige Beschreibung,
  - Rotation/Deskew,
  - Briefkopf + Haupttabelle + Fußzeile,
  - konkurrierende Tabellenregionen,
  - Fließtext ergibt „keine Tabelle“,
  - mehrdeutige Zellen erzeugen Issues.
- `CsvEncoderTest`
  - Komma, Semikolon und Tab,
  - Quotes, CR/LF, Leerzellen,
  - UTF-8/BOM exakt einmal,
  - CRLF,
  - Formel-Schutz an/aus,
  - `.csv`/`.tsv`-Dialektmetadaten.
- `DefaultDelimiterTest`
  - Dezimalkomma → Semikolon,
  - Dezimalpunkt und unbekannt → Komma.
- `ExtractTableUseCaseTest` und `ExtractTableWorkflowTest`
  - fehlende, verschlüsselte und tabellenlose Dokumente,
  - Status, Seitenfortschritt und Cancellation.
- `ExportTableCsvUseCaseTest`
  - Ein-/Mehrseiten-Namen,
  - eine Datei pro Seite,
  - Rollback bei Fehler in Datei n.
- `TableExportViewModelTest`
  - Laden, Retry mit Sprache, Editieren, Zeilen-/Seitenauswahl,
  - Reset, Speichern, Teilen und Fehlerpfade,
  - Draft-Restore: vorhandener Draft → Fortsetzen-Dialog → Zustand vollständig
    wiederhergestellt; „Neu erkennen" verwirft den Draft,
  - Draft-Schreiben ist debounced (kein Write pro Tastendruck).
- `TableDraftCodecTest`
  - JSON-Roundtrip inkl. Edits, Auswahl und Dialekt,
  - defekter/veralteter Draft wird verworfen statt zu crashen.
- `ColumnWidthEstimatorTest`
  - Breitenschätzung aus Zeichenzahlen, Min/Max-Clamp, leere Spalten.

### Instrumentation und manuelle Prüfung

- Kleines Fixture-PDF mit echter ML-Kit-Erkennung; Modellverfügbarkeit über die
  Produktionskette sicherstellen, sonst Test nachvollziehbar skippen.
- FileProvider-Share für eine und mehrere Dateien prüfen.
- Export in Excel unter Windows und LibreOffice prüfen:
  Umlaute/CJK, Delimiter, Zeilenumbrüche, BOM und Formel-Schutz.
- Dark Mode, große Schrift, Landscape, TalkBack und RTL prüfen.

## Phasen und Aufwand

| Phase | Inhalt | Aufwand |
|---|---|---:|
| 0 | Reale/anonymisierte Geometrie-Fixtures, messbare Mindestqualität festlegen | 1–2 PT |
| 1 | Domain-Modelle, Tabellenbereichserkennung, Rekonstruktion, Element-Fallback, Tests | 3–4 PT |
| 2 | OCR-Unterbau inkl. 220-DPI-Tabellen-Scale, Seitenmetadaten/-fortschritt, Gateway, Workflow, DI, Tests | 1,5–2 PT |
| 3 | Streaming-CSV/TSV, lokalisierter Default, Formel-Schutz, Download/Share, Tests | 1,5–2 PT |
| 4 | Review-UI (LazyColumn-Grid, vorberechnete Spaltenbreiten), Zelltext-Edit, Zeilen-/Seitenauswahl, Draft-Persistenz + Restore, Navigation, Locales | 3,5–5 PT |
| 5 | Instrumentation, Excel/LibreOffice-QA, Process-Death-Test, Tuning, Hilfe/Info/Handbuch | 2–3 PT |

**Realistische Gesamtschätzung: 13–18 Personentage.**

Die ursprünglichen 7–9 PT decken einen Prototyp mit synthetischer Heuristik und
nicht editierbarer Vorschau ab, nicht aber die nun festgelegte v1 mit
Tabellenbereichserkennung, Element-Fallback, Mehrdatei-Export, sicherem Teilen und
Review-Korrekturen.

## Abnahmekriterien

- Der Export verwendet einen frischen, vollständig lokalen OCR-Lauf und keine
  persistierten Bounding-Boxes.
- Seitenindex, Bitmap-Abmessungen, Modellstatus und Seitenfortschritt bleiben korrekt.
- Briefkopf/Fließtext werden nicht global als Tabellenspalten interpretiert.
- Enge Spalten können über Elements aufgeteilt werden.
- Nutzer können Zelltext korrigieren und irrelevante Zeilen/Seiten ausschließen.
- Der Default ist bei Dezimalkomma Semikolon, sonst Komma, und bleibt überschreibbar.
- Mehrere Seiten erzeugen getrennte, eindeutig benannte Dateien.
- CSV/TSV ist UTF-8 mit BOM, korrekt gequotet und in Excel/LibreOffice geprüft.
- Speichern und Teilen funktionieren für eine und mehrere Dateien.
- Keine Room-Migration, keine Geometriepersistenz und kein Cloud-Datenfluss;
  der Entwurfs-Cache liegt ausschließlich in `cacheDir` und übersteht einen
  Prozess-Kill im Hintergrund (manuell prüfbar via Entwickleroption „Aktivitäten
  nicht behalten" bzw. `adb shell am kill`).
- Das Review-Grid bleibt bei Tabellen mit mehreren hundert Zeilen flüssig scrollbar
  (LazyColumn, keine TextFields im Grid, vorberechnete Spaltenbreiten).
- Unit-Tests, `:app:compileDebugKotlin`, Instrumentation-Smoke-Test und Lint laufen
  erfolgreich.

## Geklärte Fragen

1. **Delimiter:** format-lokal abhängig über das Dezimaltrennzeichen; nicht immer `;`
   und keine Sprachcode-Liste.
2. **Mehrseiten:** eine CSV/TSV pro ausgewählter Seite.
3. **Editierbarkeit:** Zelltext und Zeilen-Ein/Ausschluss in v1; strukturelle
   Tabellenbearbeitung später.
4. **Element-Verfeinerung:** gezielter Fallback bereits in v1.
5. **Process Death:** JSON-Draft in `cacheDir/table_drafts/` mit Restore-Dialog;
   `SavedStateHandle` trägt nur die `scanId`.
6. **Render-Auflösung:** eigener Tabellen-Scale mit 220 DPI (nicht 300 — Cap und
   RAM-Peak, siehe OCR-Unterbau); Volltextpfad bleibt bei 150 DPI.
7. **Grid-Rendering:** LazyColumn + gemeinsamer horizontaler Scroll-State +
   im ViewModel vorberechnete Spaltenbreiten; kein SubcomposeLayout.
8. **Editier-Tastatur:** `KeyboardType.Decimal` bei numerisch aussehendem
   Zellwert, sonst `Text`; keine app-seitige Umschreibung von Eingaben oder
   Trennzeichen.
