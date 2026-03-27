# Analyse der Menüstruktur

## Scope

Analysiert wurden:

- Hamburger-Menü / Hauptnavigation in `app/src/main/java/info/meuse24/pdf_scanner/ui/navigation/AppNavigation.kt`
- Auswahlmodus und Bulk-Aktionsleiste in `app/src/main/java/info/meuse24/pdf_scanner/ui/home/HomeScreen.kt`
- Auswahl-Titelleiste in `app/src/main/java/info/meuse24/pdf_scanner/ui/home/components/SelectionTitleBar.kt`
- Bulk-Aktionsleiste in `app/src/main/java/info/meuse24/pdf_scanner/ui/home/components/BulkActionBar.kt`
- Dreipunkt-Menü für Einzel-PDFs in `app/src/main/java/info/meuse24/pdf_scanner/ui/home/components/ScanItem.kt`
- Benennungen in `app/src/main/res/values-de/strings.xml`

Es wurden keine Codeänderungen an der App vorgenommen.

## Zentrale Findings

### 1. Drawer ist für die bestehende IA zu schwergewichtig

Das Hamburger-Menü enthält nur eine echte Hauptdestination (`Ablage`) plus sekundäre Seiten (`Hilfe`, `Info`, `Datenschutz`). Zusätzlich liegt dort mit `Scanner starten` eine Aktion, die keine eigene Destination ist und bereits über die FAB verfügbar ist.

Probleme:

- Navigation und Aktion sind im selben Menü vermischt.
- `Scanner starten` ist doppelt erreichbar.
- Für eine App mit einer zentralen Hauptansicht ist ein Drawer meist unnötig komplex.

Betroffene Stellen:

- `AppNavigation.kt`: Drawer-Inhalte und Navigation
- `strings.xml`: `nav_archive`, `nav_start_scanner`, `nav_help`, `nav_info`, `nav_privacy`

### 2. Drawer-Gesten sind auf zu vielen Screens aktiv

Der Drawer ist aktuell auf fast allen Screens per Geste erreichbar; nur `annotate/` ist explizit ausgenommen.

Probleme:

- Bearbeitungsscreens sollten eine klare Hierarchie haben: zurück statt seitlich ausklappender Globalnavigation.
- Das erhöht die Gefahr versehentlicher Drawer-Öffnungen während Task-Flows.

Best Practice:

- Drawer nur auf Top-Level-Screens erlauben.
- Auf Detail- und Bearbeitungsscreens ausschließlich Back-Navigation.

Betroffene Stelle:

- `AppNavigation.kt`: `gesturesEnabled = currentRoute?.startsWith("annotate/") != true`

### 3. Auswahlmodus ist nicht als klarer contextual action mode umgesetzt

Im Auswahlmodus bleibt die normale TopAppBar sichtbar. Zusätzlich erscheint oben eine eigene Auswahlleiste und unten eine Bulk-Aktionsleiste.

Probleme:

- Drei Steuerzonen konkurrieren gleichzeitig miteinander:
  - normale TopAppBar
  - SelectionTitleBar
  - BulkActionBar
- Das erschwert Orientierung und wirkt visuell unruhig.

Best Practice:

- Auswahlmodus sollte die normale TopAppBar ersetzen.
- Typisch wäre eine kontextuelle AppBar mit:
  - `Schließen`
  - `x ausgewählt`
  - `Alle auswählen`
  - wichtigste 1 bis 2 Aktionen
  - optional `Mehr`

Betroffene Stellen:

- `AppNavigation.kt`: globale TopAppBar
- `HomeScreen.kt`: Auswahlmodus und Bulk-Leiste
- `SelectionTitleBar.kt`

### 4. Bulk-Aktionsleiste ist icon-only und semantisch zu dicht

Die Bulk-Leiste zeigt sechs Aktionen nur als Icons: Teilen, Exportieren, Zusammenführen, Text extrahieren, Durchsuchbar machen, Löschen.

Probleme:

- `Merge`, `Text extrahieren`, `Durchsuchbar machen` und `Exportieren` sind ohne Textlabel nicht sofort selbsterklärend.
- Seltene und komplexe Aktionen stehen gleichrangig neben häufigen Aktionen.
- Die Leiste ist funktional dicht, aber kognitiv teuer.

Best Practice:

- Direkt sichtbar nur die häufigsten Bulk-Aktionen.
- Komplexere oder seltenere Aktionen in ein `Weitere Aktionen`-Menü oder Bottom Sheet verschieben.
- Actions in der Auswahl eher mit Text oder Icon+Text anbieten.

Empfohlene sichtbare Primäraktionen:

- `Teilen`
- `Löschen`

Empfohlene Sekundäraktionen:

- `Exportieren`
- `Zusammenführen`
- `Text extrahieren`
- `Durchsuchbar machen`

Betroffene Stelle:

- `BulkActionBar.kt`

### 5. Das Einzel-PDF-Menü ist überladen und verschachtelt

Das Dreipunkt-Menü enthält viele direkte Einträge plus zwei Submenüs (`Seitenstruktur`, `Schutz & Passwort`).

Probleme:

- Verschachtelte Dropdown-Menüs sind auf Mobile unübersichtlich.
- Viele Aktionen konkurrieren in einer langen Liste.
- Die Gruppen sind nicht durchgehend fachlich sauber.

Beispiel:

- `Textlayer entfernen` liegt unter `Schutz & Passwort`, gehört aber inhaltlich eher zu OCR / Suchbarkeit.

Best Practice:

- Auf Mobile lieber ein `ModalBottomSheet` mit klaren Abschnittsüberschriften statt verschachtelten Dropdowns.

Empfohlene Gruppierung:

- `Bearbeiten`: Annotieren, Markieren, Signieren
- `Seiten`: Drehen, Neu anordnen, Aufteilen, Seiten löschen, Seiten extrahieren, Seiten duplizieren
- `Ausgabe`: Seitennummern, Wasserzeichen, Als JPG exportieren, Komprimieren
- `Schutz`: Passwort setzen, Öffnungspasswort entfernen, Einschränkungen entfernen, Nutzungsrechte einschränken
- `OCR`: Textlayer entfernen

Betroffene Stelle:

- `ScanItem.kt`

### 6. Auswahl wird dauerhaft über Checkboxen exponiert

Jeder Eintrag zeigt permanent eine Checkbox, unabhängig davon, ob gerade ausgewählt wird.

Probleme:

- Jede Zeile wird visuell voller.
- Der Scan-Listencharakter leidet.
- Standardmuster auf Android sind eher:
  - normaler Tap öffnet
  - Long-Press startet Auswahlmodus
  - Checkboxen erscheinen erst im Auswahlmodus

Best Practice:

- Checkboxen nur im Auswahlmodus anzeigen.
- Long-Press auf Karten zum Einstieg in die Mehrfachauswahl.

Betroffene Stelle:

- `ScanItem.kt`

### 7. Discoverability und IA sind inkonsistent

`Highlight` existiert als Action und Navigation Route, ist aber im Dreipunkt-Menü nicht sichtbar und im `when`-Handler in `HomeScreen` nicht umgesetzt.

Probleme:

- Funktionsumfang und sichtbare IA laufen auseinander.
- Nutzer sehen nicht alle vorhandenen Möglichkeiten.
- Das wirkt unfertig oder inkonsistent.

Best Practice:

- Entweder Funktion sauber sichtbar in die IA aufnehmen
- oder vorläufig vollständig aus IA und Handler entfernen, bis sie produktionsreif ist

Betroffene Stellen:

- `ScanItem.kt`: `ScanAction.Highlight`
- `HomeScreen.kt`: `ScanAction.Highlight -> {}`
- `AppNavigation.kt`: `Screen.Highlight`

### 8. Benennung und Informationsarchitektur können konsistenter werden

Die Begriffe sind grundsätzlich brauchbar, aber die Struktur könnte stringenter werden.

Beobachtungen:

- `Ablage` ist als Hauptbereich klar.
- In der TopBar wird auf Home aber primär der App-Name gezeigt statt klarer Bereichsname.
- Die Hilfetexte referenzieren stark das Hamburger-Menü, obwohl dieses Muster in der App nicht ideal ist.

Best Practice:

- Wenn `Ablage` der zentrale Bereich ist, sollte dieser Bereich auch klar als solcher geführt werden.
- Begriffliche Gruppen sollten fachlich sauber sein.
- Hilfe-Texte sollten der finalen IA folgen und nicht historische UI-Muster verfestigen.

## Konkrete Verbesserungsvorschläge

### A. Globalnavigation vereinfachen

Empfehlung:

- `Scanner starten` aus dem Drawer entfernen
- FAB als primären Scan-Einstieg beibehalten
- `Hilfe`, `Info`, `Datenschutz` in TopAppBar-Overflow oder einfaches `Mehr`-Sheet verschieben
- Drawer nur behalten, wenn künftig mehrere echte Hauptbereiche hinzukommen

Begründung:

- Weniger Ebenen
- klarere Trennung zwischen Navigation und Aktion
- geringere Interaktionskosten

### B. Auswahlmodus als echte kontextuelle AppBar gestalten

Empfehlung:

- Normale TopBar im Auswahlmodus ersetzen
- Kontextleiste mit:
  - `Schließen`
  - `x ausgewählt`
  - `Alle auswählen`
  - `Teilen`
  - `Mehr`

Optional:

- `Löschen` direkt sichtbar, falls es zu den häufigsten Aktionen gehört
- weitere Aktionen in Overflow / Bottom Sheet

Begründung:

- klares Android-Muster
- weniger konkurrierende UI-Zonen
- bessere Fokussierung auf den aktuellen Modus

### C. Bulk-Aktionen priorisieren

Empfehlung:

- Primär:
  - `Teilen`
  - `Löschen`
- Sekundär in Bottom Sheet:
  - `Exportieren`
  - `Zusammenführen`
  - `Text extrahieren`
  - `Durchsuchbar machen`

Begründung:

- häufige Aufgaben schneller
- seltene Funktionen bleiben erreichbar, aber überladen nicht die Hauptoberfläche

### D. Einzel-PDF-Aktionen als Bottom Sheet statt Submenüs

Empfehlung:

- Dreipunkt-Button öffnet ein `ModalBottomSheet`
- Aktionen in logisch getrennten Gruppen mit Textlabels und Icons
- keine verschachtelten Menüs

Begründung:

- deutlich bessere Mobile-Usability
- bessere Scanbarkeit
- verständlichere IA bei wachsender Funktionszahl

### E. Auswahl per Long-Press starten

Empfehlung:

- normaler Tap: PDF öffnen
- Long-Press: Auswahlmodus starten und erstes Dokument selektieren
- Checkboxen nur im Auswahlmodus anzeigen

Begründung:

- ruhigere Listenoptik
- gängiges Android-Muster
- klarere Trennung zwischen Öffnen und Auswählen

### F. Verfügbarkeit von Aktionen klarer steuern

Empfehlung:

- Aktionen wenn möglich eher ausblenden als disabled anzeigen, wenn sie im jeweiligen Kontext eindeutig nicht sinnvoll sind
- Disabled nur dort, wo die Sichtbarkeit selbst hilfreich für das Verständnis ist

Beispiele:

- `Komprimieren` nur zeigen, wenn fachlich möglich
- Seitenaktionen nur bei mehrseitigen PDFs

Begründung:

- weniger visuelle Last
- weniger Frustration durch viele nicht anklickbare Menüpunkte

## Priorisierte Umsetzungsempfehlung

### Priorität 1

- Drawer vereinfachen oder vollständig entfernen
- `Scanner starten` nicht mehr im Drawer führen

### Priorität 2

- Auswahlmodus zu einer kontextuellen AppBar umbauen
- Bulk-UI auf eine primäre Steuerzone reduzieren

### Priorität 3

- Dreipunkt-Menü durch ein sectioned Bottom Sheet ersetzen
- Submenüs auflösen

### Priorität 4

- Auswahl per Long-Press einführen
- Checkboxen nur im Auswahlmodus zeigen

### Priorität 5

- Inkonsistenzen bereinigen:
  - `Highlight` sauber einbinden oder ausblenden
  - Gruppennamen und Hilfetexte an finale IA anpassen

## Empfohlenes Zielbild

### Home / Ablage

- TopBar mit Titel `Ablage`
- Suchfeld
- Liste der Dokumente
- FAB `Scan starten`
- optional Overflow für `Hilfe`, `Info`, `Datenschutz`

### Auswahlmodus

- kontextuelle AppBar statt normaler TopBar
- Titel: `x ausgewählt`
- Primäraktionen: `Teilen`, `Löschen`
- Weitere Aktionen in Bottom Sheet

### Einzel-PDF

- Karteneintrag mit Tap zum Öffnen
- Long-Press zum Selektieren
- Dreipunkt-Menü öffnet Bottom Sheet mit klaren Gruppen

## Wireframe in Textform

### 1. Home / Ablage

```text
+--------------------------------------------------+
| Ablage                                  [Mehr ⋮] |
+--------------------------------------------------+
| [ Dokumente durchsuchen...                    ] |
+--------------------------------------------------+
| [Thumbnail] Rechnung_Maerz_2026.pdf       [⋮]   |
|             27.03.26 · 3 S. · 1.8 MB            |
|             [Durchsuchbar] [Rechnung]           |
+--------------------------------------------------+
| [Thumbnail] Vertrag_Mobilfunk.pdf          [⋮]  |
|             21.03.26 · 8 S. · 2.4 MB            |
|             [Vertrag]                           |
+--------------------------------------------------+
|                                                  |
|                                      [ + Scan ]  |
+--------------------------------------------------+
```

Verhalten:

- Tap auf Karte öffnet PDF
- Long-Press auf Karte startet Auswahlmodus
- Checkboxen sind hier nicht sichtbar
- `Mehr ⋮` enthält nur sekundäre App-Bereiche:
  - `Hilfe`
  - `Info`
  - `Datenschutz`

### 2. Auswahlmodus

```text
+--------------------------------------------------+
| [X] 3 ausgewählt     [Alle] [Teilen] [Löschen] [⋮] |
+--------------------------------------------------+
| [✓] Rechnung_Maerz_2026.pdf                  [⋮] |
| [✓] Vertrag_Mobilfunk.pdf                    [⋮] |
| [✓] Zeugnis_2025.pdf                         [⋮] |
| [ ] Versicherung_Auto.pdf                    [⋮] |
+--------------------------------------------------+
```

Verhalten:

- Normale TopBar wird vollständig ersetzt
- `X` beendet Auswahlmodus
- `Alle` selektiert alle gefilterten Einträge
- Primär sichtbar:
  - `Teilen`
  - `Löschen`
- Overflow `⋮` öffnet Bulk-Aktionssheet mit:
  - `Exportieren`
  - `Zusammenführen`
  - `Text extrahieren`
  - `Durchsuchbar machen`

### 3. Bulk-Aktionssheet

```text
+----------------------------------+
| Ausgewählte Dokumente            |
| 3 Dokumente                      |
+----------------------------------+
| Exportieren                      |
| Zusammenführen                   |
| Text extrahieren                 |
| Durchsuchbar machen              |
+----------------------------------+
| Abbrechen                        |
+----------------------------------+
```

Verhalten:

- Sheet wird aus dem Overflow des Auswahlmodus geöffnet
- alle Einträge mit Textlabel, optional zusätzlich Icon
- nur Aktionen anzeigen, die für die aktuelle Auswahl sinnvoll sind

### 4. Einzel-PDF-Aktionssheet

```text
+----------------------------------------------+
| Rechnung_Maerz_2026.pdf                      |
| 3 Seiten · durchsuchbar                      |
+----------------------------------------------+
| Bearbeiten                                   |
| - PDF annotieren                             |
| - PDF markieren                              |
| - PDF signieren                              |
|                                              |
| Seiten                                       |
| - Seiten drehen                              |
| - Seiten neu anordnen                        |
| - Seiten aufteilen                           |
| - Seiten löschen                             |
| - Seiten extrahieren                         |
| - Seiten duplizieren                         |
|                                              |
| Ausgabe                                      |
| - Seitennummern hinzufügen                   |
| - Text-Wasserzeichen hinzufügen              |
| - Als JPG exportieren                        |
| - PDF komprimieren                           |
|                                              |
| Schutz                                       |
| - Öffnungspasswort setzen                    |
| - Öffnungspasswort entfernen                 |
| - Einschränkungen entfernen                  |
| - Nutzungsrechte einschränken               |
|                                              |
| OCR                                          |
| - Textlayer entfernen                        |
+----------------------------------------------+
| Abbrechen                                    |
+----------------------------------------------+
```

Verhalten:

- `⋮` auf Kartenzeile öffnet dieses Sheet
- keine verschachtelten Submenüs
- Abschnittsüberschriften helfen bei Orientierung
- nicht verfügbare Aktionen werden möglichst ausgeblendet

### 5. Sekundäres App-Menü statt Drawer

```text
+----------------------------------+
| Mehr                             |
+----------------------------------+
| Hilfe                            |
| Info                             |
| Datenschutz                      |
+----------------------------------+
| Abbrechen                        |
+----------------------------------+
```

Verhalten:

- nur auf Top-Level `Ablage`
- kein globaler Drawer nötig
- reduziert Seitennavigation auf selten genutzte sekundäre Ziele

### 6. Leerer Zustand

```text
+--------------------------------------------------+
| Ablage                                  [Mehr ⋮] |
+--------------------------------------------------+
|                                                  |
|              Keine Dokumente vorhanden           |
|      Scanne dein erstes Dokument mit +           |
|                                                  |
|                                      [ + Scan ]  |
+--------------------------------------------------+
```

Verhalten:

- `+ Scan` ist der primäre Einstieg
- kein zusätzlicher Menüpunkt `Scanner starten` erforderlich

### 7. Bearbeitungsscreen

```text
+--------------------------------------------------+
| [←] Seiten drehen                               |
+--------------------------------------------------+
|                                                  |
|                 Bearbeitungsinhalt               |
|                                                  |
+--------------------------------------------------+
| [Aktion ausführen]                               |
+--------------------------------------------------+
```

Verhalten:

- kein Drawer
- keine Drawer-Geste
- klare hierarchische Navigation mit Back-Button

## Fazit

Die aktuelle Menüstruktur ist funktional, aber in mehreren Bereichen zu dicht und nicht konsequent entlang typischer Android-Muster organisiert. Der größte Hebel liegt nicht in einzelnen Labels, sondern in einer klareren Trennung von:

- Globalnavigation
- kontextuellen Auswahlaktionen
- dokumentbezogenen Einzelaktionen

Die beste Vereinfachung wäre:

1. Drawer deutlich reduzieren
2. Auswahlmodus als echte Kontext-UI führen
3. Einzelaktionen in ein gruppiertes Bottom Sheet verlagern
