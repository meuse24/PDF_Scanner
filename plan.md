# Implementierungsplan: Konsolidierung und Ausbau des Markierungs- und Kommentierungsscreens

## Zielbild
Der Annotate-Screen wird zu einer konsistenten, farbfähigen und erweiterbaren Annotationsoberfläche ausgebaut. Der Fokus liegt auf drei Punkten:

1. Ein sauberes Annotate-Datenmodell, das Highlighting nicht unnötig mitzieht.
2. Eine kompakte Compose-UI mit Werkzeugwahl, Farbpalette und Attributen ohne Verlust an Canvas-Fläche.
3. Ein PDF-Export, der Farben, Shapes und Textkommentare reproduzierbar in die Ausgabedatei schreibt.

## Konsolidierter Umfang

### Bewusste Korrekturen gegenüber dem Ursprungsplan
- Der bestehende Highlight-Flow bleibt unverändert. Die neue Modellierung wird nur im Annotate-Flow eingeführt, um Regressionen auf dem Highlight-Screen zu vermeiden.
- Outline-Rechtecke und Outline-Ovale benötigen eine persistente Strichbreite. Deshalb erhalten `AnnotationRect` und `AnnotationOval` zusätzlich `strokeWidthFraction`.
- Für Shapes reicht ein gemeinsames Style-Enum aus. Statt getrennter Rect-/Oval-Enums wird `AnnotationShapeStyle` mit `FILLED` und `FRAME` verwendet.
- Es existierte noch kein dedizierter Unit-Test für `AnnotatePdfWorkflow`. Dieser wird als Teil der Umsetzung ergänzt.

### Umzusetzende Features
- Farbige Marker, Rechtecke, Ovale und Textkommentare im Annotate-Flow
- Werkzeug-Dropdown mit sieben Modi:
  - Marker
  - Text
  - Rechteck gefüllt
  - Rechteck Kontur
  - Oval gefüllt
  - Oval Kontur
  - Zoom
- Zweizeilige Toolbar oben:
  - Zeile 1: Werkzeugwahl, optional Snap, Zoom-Reset
  - Zeile 2: Farbpalette und Breiten-/Größenwahl
- Live-Preview für Rechteck- und Oval-Drafts
- PDF-Export für:
  - farbige Striche
  - gefüllte und umrandete Rechtecke
  - gefüllte und umrandete Ovale
  - farbige Textkommentare
- Fortschrittsdokumentation und Code-Review-Zusammenfassungen direkt in dieser Datei

## Technischer Zuschnitt

### Domänenmodell
- Neue Datei: `app/src/main/java/info/meuse24/pdf_scanner/domain/usecase/AnnotationModel.kt`
- Enthält:
  - `AnnotationStroke`
  - `AnnotationRect`
  - `AnnotationOval`
  - `AnnotationText`
  - `AnnotationShapeStyle`
  - Default-Farb- und Default-Breitenkonstanten

### Änderungsstrecke
1. `AnnotationModel.kt` anlegen
2. `PdfEditor.applyAnnotations(...)` auf neue Typen erweitern
3. `AnnotatePdfUseCase` auf neue Typen erweitern
4. `AnnotatePdfWorkflow` auf neue Typen erweitern
5. `DocumentEditViewModel.applyAnnotations(...)` auf neue Typen erweitern
6. `AnnotateScreen.kt` auf neues UI- und Zustandsmodell umstellen
7. Tests ergänzen und anpassen

## Implementierungsphasen

### Phase 1: Modell- und Backend-Migration
Status: Erledigt

- Annotate-spezifische Modelle einführen
- `PdfEditor.applyAnnotations(...)` um Ovale, Farben und Shape-Stile erweitern
- Textkommentare farbfähig machen
- Signaturkette `UseCase -> Workflow -> ViewModel` umstellen

### Phase 2: Compose-UI und Interaktion
Status: Erledigt

- `AnnotateTool` einführen
- Dropdown-basierte Werkzeugwahl bauen
- Attributleiste mit Farben und Breiten erstellen
- Shape-Drafts und Live-Preview implementieren
- Snap-Verhalten mit aktueller Farbe verbinden

### Phase 3: Testabdeckung und Absicherung
Status: Erledigt

- `AnnotatePdfWorkflowTest` neu anlegen
- `DocumentEditViewModelTest` auf neue Typen erweitern
- `ImportAndPdfEditorInstrumentedTest` für farbige Shapes/Ovale ergänzen
- Relevante Unit-Tests ausführen

### Phase 4: Release-Härtung
Status: Erledigt

- vollständige Debug-Unit-Test-Suite ausführen
- vollständige verbundene Android-Instrumentation-Suite auf realem Gerät ausführen
- Abschlussstatus in dieser Datei dokumentieren

### Phase 5: Interaktions-Parität vor dem Speichern
Status: Erledigt

- Drag auf bestehendem unsaved Element verschiebt das Element statt ein neues anzulegen
- Verhalten gilt für Marker, Rechtecke, Ovale und Textkommentare
- Single-Tap auf freie Fläche legt je aktivem Werkzeug ein neues Element an
- Verhalten vor dem Speichern wieder auf den früheren Kommentar-Flow angleichen

### Phase 6: Auswahl und nachträgliche Bearbeitung vor Save
Status: Erledigt

- Bestehende unsaved Elemente per Tap selektierbar machen
- Blauen Punkt zu einem generischen Selektions-Henkel für alle Elementtypen ausbauen
- Eigenschaften des selektierten Elements im Editor nachträglich ändern:
  - Farbe
  - Stiftbreite bzw. Textgröße
  - Kommentartext bei Text-Elementen
- Selektiertes Element gezielt löschen
- Undo nach nachträglicher Mutation defensiv behandeln, solange keine stabilen Element-IDs existieren

## Fortschrittslog

### Schritt 1: Plan konsolidiert
Status: Erledigt

Ergebnis:
- Ursprungsplan auf tatsächliche Codebasis abgeglichen
- Annotate-only-Modellierung festgelegt
- fehlende Outline-Strichbreite als Architekturloch geschlossen
- Testlücke bei `AnnotatePdfWorkflow` aufgenommen

Code-Review-Zusammenfassung:
- Der Plan trennt Highlight und Annotate jetzt sauber, wodurch unnötige Regressionen im bestehenden Highlight-Screen vermieden werden.
- Das Modell ist gegenüber dem Ursprungsplan vollständiger, weil Outline-Shapes ohne persistente Strichbreite fachlich unvollständig gewesen wären.
- Die Umsetzungsreihenfolge folgt jetzt der realen Abhängigkeit im Code: erst Domain/PdfEditor, dann Compose, dann Tests.

### Schritt 2: Modell- und Backend-Migration
Status: Erledigt

Ergebnis:
- `AnnotationModel.kt` mit `AnnotationStroke`, `AnnotationRect`, `AnnotationOval`, `AnnotationText` und `AnnotationShapeStyle` eingeführt
- `PdfEditor.applyAnnotations(...)` auf farbige Strokes, Rechtecke, Ovale und Textkommentare erweitert
- `AnnotatePdfUseCase`, `AnnotatePdfWorkflow` und `DocumentEditViewModel.applyAnnotations(...)` auf die neuen Typen und Ovale erweitert
- Highlight-Flow bewusst unverändert gelassen

Code-Review-Zusammenfassung:
- Die neue Annotate-Modellierung ist sauber vom alten Highlight-Modell getrennt; dadurch bleiben bestehende Highlight-Pfade stabil.
- Die Exportlogik ist jetzt vollständig genug für das neue UI, weil sie nicht nur Farben, sondern auch Shape-Stile und Ovale abdeckt.
- Ein wichtiger Architekturgewinn ist die persistente Strichbreite auf Outline-Shapes; ohne diese wäre der UI-Zustand fachlich nicht vollständig reproduzierbar gewesen.

### Schritt 3: UI- und Interaktionsumbau
Status: Erledigt

Ergebnis:
- `AnnotateScreen` in kleinere UI-Bausteine zerlegt
- Werkzeug-Dropdown für Marker, Text, Rechteck/Oval in Fill-/Frame-Variante und Zoom umgesetzt
- zweizeilige Toolbar mit Farbpalette und Breiten-/Größenwahl umgesetzt
- Live-Preview für Shape-Drafts, farbige Kommentare und farbige Marker integriert
- Undo/Clear/Reset auf Strokes, Rects, Ovals und Kommentare erweitert

Code-Review-Zusammenfassung:
- Der Screen ist deutlich wartbarer, weil Toolbar, Canvas-Preview und Saveable-Modelle getrennt sind.
- Die Toolbar ist funktional dichter, ohne die Canvas-Fläche weiter unten zu zerschneiden.
- Die größte Verhaltensänderung liegt im neuen Toolmodell; dafür ist die Screen-Logik jetzt konsistenter als die frühere reine MARK/WRITE/ZOOM-Aufteilung.

### Schritt 4: Tests und Abschluss
Status: Erledigt

Ergebnis:
- Neuer `AnnotatePdfWorkflowTest` ergänzt
- `DocumentEditViewModelTest` um Annotate-Erfolgsfall erweitert
- `ImportAndPdfEditorInstrumentedTest` auf neue Annotate-Typen migriert und um Oval-Render-Test ergänzt
- Verifikation durchgeführt:
  - `./gradlew testDebugUnitTest --tests "info.meuse24.pdf_scanner.domain.workflow.AnnotatePdfWorkflowTest" --tests "info.meuse24.pdf_scanner.ui.documentaction.DocumentEditViewModelTest"`
  - `./gradlew compileDebugAndroidTestKotlin`
  - `./gradlew connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=info.meuse24.pdf_scanner.ImportAndPdfEditorInstrumentedTest'`
- Realer Gerätelauf erfolgreich:
  - Gerät: `SM-A536B`
  - Android-Version: `16`
  - Ergebnis: `15/15` Instrumentation-Tests bestanden
- Erweiterte Release-Härtung erfolgreich:
  - `./gradlew testDebugUnitTest`
  - `./gradlew connectedDebugAndroidTest`
  - Ergebnis: `25/25` Android-Instrumentation-Tests bestanden auf `SM-A536B / Android 16`
- Kleine Nacharbeiten aus dem Review umgesetzt:
  - Default-Entscheidung für `AnnotationRect.style` im Modell dokumentiert
  - Oval-Filled-Pfad im `PdfEditor` um explizites `setLineJoinStyle(1)` ergänzt

Code-Review-Zusammenfassung:
- Die Testlücke im Annotate-Workflow ist geschlossen; Fehlerpfade und Erfolgspfad sind jetzt explizit abgesichert.
- Der reale Gerätelauf bestätigt, dass der neue Annotate-Pfad nicht nur kompiliert, sondern auf echter Android-Hardware stabil durchläuft.
- Die Review-Nacharbeiten sind klein, aber sinnvoll: Die Modellentscheidung ist jetzt explizit dokumentiert und der Oval-Exportzustand ist konsistenter gesetzt.
- Die vollständige Debug-Regression ist grün; damit gibt es aktuell keinen offenen technischen Restpunkt mehr innerhalb des bearbeiteten Scopes.

### Schritt 5: Interaktions-Parität vor Save
Status: Erledigt

Ergebnis:
- Zielverhalten ergänzt:
  - Drag auf bestehendem unsaved Element verschiebt dieses
  - Tap auf freie Fläche legt ein neues Element an
  - Verhalten wird für Marker, Shapes und Text vereinheitlicht
- Implementiert:
  - generisches Hit-Testing für Stroke, Rect, Oval und Kommentar
  - Drag-Move vor Save für alle unsaved Elementtypen
  - Single-Tap-Neuanlage:
    - Marker: Punkt-/Mini-Markierung
    - Rect/Oval: Default-Shape um den Tap-Punkt
    - Text: neuer Kommentar-Dialog
- Absicherung:
  - neuer Unit-Test `AnnotateInteractionHelpersTest`
  - `./gradlew --no-configuration-cache testDebugUnitTest --tests "info.meuse24.pdf_scanner.ui.annotate.AnnotateInteractionHelpersTest"`
  - `./gradlew connectedDebugAndroidTest`
  - Ergebnis: `25/25` Android-Instrumentation-Tests weiter grün auf `SM-A536B / Android 16`

Code-Review-Zusammenfassung:
- Dieser Nachtrag ist bewusst auf den Pre-Save-Editor beschränkt und ändert weder Exportmodell noch Persistenz.
- Das Risiko liegt primär in Hit-Testing und Gesture-Auflösung; deshalb wird der Schritt mit Helper-Tests und einem gezielten Gerätelauf abgeschlossen.
- Die Interaktionsparität zum früheren Kommentar-Flow ist wiederhergestellt und auf alle Annotate-Typen erweitert.
- Die Änderung bleibt lokal im Editor-Verhalten; Persistenz- und Exportpfade bleiben unverändert, was das Regressionsrisiko deutlich begrenzt.

### Schritt 6: Auswahl- und Bearbeitungslogik
Status: Erledigt

Ergebnis:
- Tap auf ein bestehendes unsaved Element selektiert dieses statt es sofort neu anzulegen oder zu überschreiben
- Ein generischer blauer Henkel markiert die aktuelle Auswahl für Stroke, Rect, Oval und Text
- Farb- und Breitenleiste arbeitet bei aktiver Auswahl direkt auf dem selektierten Element
- Kommentare erhalten zusätzlich eine explizite Text-Bearbeitungsaktion
- Selektiertes Element kann gezielt gelöscht werden
- Die Undo-Historie der betroffenen Seite wird nach nachträglichem Editieren oder Löschen defensiv geleert, damit ohne stabile Element-IDs kein falsches Element per Undo entfernt wird
- Zusätzlich umgesetzt:
  - Kommentar-Hit-Testing über den sichtbaren Textbereich statt nur über den Anchor
  - Selektionszustand per `rememberSaveable`, damit Auswahl bei Rotation nicht sofort verloren geht
  - Selektions-Helfer für Farbe, Breite, Text-Update, Delete und Handle-Frame zentralisiert
  - Toolbar-Resync bei wiederhergestellter Selektion explizit per `LaunchedEffect` abgesichert, damit Rotation kein implizites Invariant mehr bleibt
  - Inkonsistenter ausgeschriebener `AnnotationShapeStyle`-Pfad in `createTapRect` und `createTapOval` bereinigt
- Verifikation:
  - `./gradlew --no-configuration-cache compileDebugKotlin`
  - `./gradlew --no-configuration-cache testDebugUnitTest --tests "info.meuse24.pdf_scanner.ui.annotate.AnnotateInteractionHelpersTest"`
  - `./gradlew --no-configuration-cache connectedDebugAndroidTest`
  - Ergebnis: `25/25` Android-Instrumentation-Tests weiter grün auf `SM-A536B / Android 16`

Code-Review-Zusammenfassung:
- Der Eingriff bleibt auf den unsaved Editorzustand begrenzt und vermeidet bewusst Änderungen an Export, Persistenz und Domänenmodell.
- Die zentrale Architekturentscheidung ist defensiv: lieber Undo nach Mutation invalidieren als inkonsistente Rücknahmen auf Basis einer ID-losen History zuzulassen.
- Der blaue Punkt wird nicht als Sonderfall für Kommentare behalten, sondern als einheitliches Selektionssignal für alle Elementtypen verwendet.
- Die Selektions- und Änderungslogik ist bewusst in Helper-Funktionen ausgelagert; dadurch bleibt `AnnotateScreen` trotz zusätzlicher Zustände testbar und die Mutation der einzelnen Elementtypen konsistent.
- Der zuvor nur implizit korrekte Rotationspfad ist jetzt explizit abgesichert; damit hängt Toolbar-State nicht mehr davon ab, dass der Benutzer immer über denselben Interaktionspfad in die Selektion gelangt.

### Schritt 7: Toolbar für Zoom und Reset verdichten
Status: Erledigt

Ergebnis:
- `Zoom` aus dem Werkzeug-Dropdown entfernt
- eigene Lupen-Aktion neben dem Dropdown eingeführt
- `Reset` von Textbutton auf reine Symbolaktion reduziert
- letzter Nicht-Zoom-Modus bleibt erhalten, damit die Lupen-Aktion zwischen Zoom und dem vorherigen Werkzeug umschaltet

Code-Review-Zusammenfassung:
- Die Änderung ist rein im Toolbar-Layout und in der Werkzeugumschaltung verankert; Canvas-, Export- und Selektionslogik bleiben unberührt.
- Der kleine zusätzliche Zustand `selectedDrawTool` löst das UI-Problem sauber, ohne das bestehende interne `AnnotateTool.ZOOM` aus dem Editor herauszureißen.

### Schritt 8: Mehr Symbolik in der Annotate-UI
Status: Erledigt

Ergebnis:
- Breitenwahl `Dünn / Mittel / Dick` auf visuelle Linien-Previews mit unterschiedlicher Strichstärke umgestellt
- untere Aktionsleiste `Undo / Seite / Alles` auf Icon-Aktionen reduziert
- für die icon-basierten Aktionen explizite Accessibility-Strings ergänzt

Code-Review-Zusammenfassung:
- Der Eingriff bleibt rein präsentationsseitig; Zustandsmodell und Interaktionslogik werden nicht verändert.
- Die Breitenwahl ist jetzt dichter und direkter verständlich, weil sie das Ergebnis visuell statt textuell zeigt.
- Die Aktionsleiste nutzt kompaktere Symbolik, bleibt aber durch dedizierte Content-Descriptions screenreader-tauglich.

### Schritt 9: Klare Modustrennung zwischen Zoom und Bearbeiten
Status: Erledigt

Ergebnis:
- eigener reduzierter Kopfbereich für `Zoom`
- im Zoom-Modus kein Werkzeug-Dropdown
- im Zoom-Modus keine `Undo / Seite / Alles`-Aktionsleiste
- Rückkehr aus `Zoom` direkt über das Symbol des zuletzt aktiven Bearbeitungswerkzeugs
- Seitennavigation bleibt auch im Zoom-Modus sichtbar

Code-Review-Zusammenfassung:
- Die Trennung ist jetzt nicht nur funktional, sondern visuell klar: `Zoom` wirkt wie ein eigener Navigationsmodus statt wie nur ein weiteres Werkzeug.
- Der bestehende interne `AnnotateTool.ZOOM` bleibt erhalten; damit ist die Änderung UI-seitig und risikoarm.
- Der zuletzt aktive Bearbeitungsmodus wird bewusst weiterverwendet, damit der Wechsel aus `Zoom` wieder an genau dieselbe Arbeitsstelle zurückführt.

Nachtrag:
- `Zoom Reset` wird im Bearbeiten-Modus nicht mehr angezeigt und bleibt ausschließlich dem Zoom-Modus vorbehalten.

### Schritt 10: Sofortselektion nach Neuanlage
Status: Erledigt

Ergebnis:
- Ursache analysiert: Der Selektions-Callback im `pointerInput` arbeitete mit einem veralteten Editor-Snapshot, bis der Tool-Block neu aufgebaut wurde
- Fix umgesetzt: Selektions-Sync im Gesture-Pfad auf den aktuellen `rememberUpdatedState`-Snapshot umgebogen
- Auto-Selektion neuer Elemente wieder entfernt
- Ergebnis: Neue Elemente bleiben nach dem Anlegen unmarkiert, bestehende Elemente lassen sich aber unmittelbar per Antippen selektieren

Code-Review-Zusammenfassung:
- Der eigentliche Fehler lag nicht im Hit-Testing, sondern in einem stale Snapshot innerhalb des Gesture-Callbacks.
- Der Fix ist präziser als die zwischenzeitliche Sofortselektion, weil er das ursprüngliche Interaktionsmodell beibehält: Selektiert wird erst beim Antippen eines bestehenden Elements.
- Die Änderung bleibt lokal im Editor-State; Export- und Persistenzpfade werden nicht beeinflusst.

### Schritt 11: Symbolik in der Selektionsleiste vereinheitlichen
Status: Erledigt

Ergebnis:
- `Delete` und `Clear` in der Selektionsleiste auf dieselben kompakten Icon-Buttons wie die untere Aktionsleiste umgestellt
- `Text bearbeiten` bleibt als Textaktion erhalten, weil die Funktion weniger selbsterklärend ist als Löschen oder Auswahl aufheben

Code-Review-Zusammenfassung:
- Die Selektionsleiste folgt jetzt derselben visuellen Sprache wie die restliche Annotate-Steuerung.
- Die Umstellung bleibt rein präsentationsseitig und ändert keine Interaktionslogik.

### Schritt 12: Selektionsaktionen in die untere Icon-Zeile integrieren
Status: Erledigt

Ergebnis:
- `Delete` und `Clear` aus der separaten Selektionsleiste in die gemeinsame untere Icon-Zeile verschoben
- `Selected: …` bleibt als kleine Hinweiszeile darüber stehen
- Kommentar-Bearbeitung ebenfalls als Icon in derselben Zeile integriert

Code-Review-Zusammenfassung:
- Die untere Steuerfläche ist kompakter, weil Selektions- und Seitenaktionen nicht mehr auf zwei getrennte Zeilen verteilt sind.
- Die Änderung bleibt rein präsentationsseitig; Zustands- und Selektionslogik wurden nicht verändert.
