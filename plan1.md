# OCR-Verbesserungsplan fuer PDF_Scanner

## Zweck dieses Dokuments

Dieses Dokument beschreibt einen begruendeten Verbesserungsplan fuer die OCR-Faehigkeiten der App auf Basis von:

- dem aktuellen Projektstand
- der analysierten Implementierung im Repository
- den offiziellen ML Kit Text Recognition v2 Dokumentationen
- den offiziellen Empfehlungen zu Modellinstallation, APK-Groesse und Google Play Services

Der Plan fokussiert auf drei Ziele:

1. bessere OCR-Qualitaet und konsistentere Ergebnisse
2. schlankere App durch bedarfsgerechten Modell-Download
3. einfachere UX durch "Automatisch + Manuell" statt hartem Sprachzwang

Der Plan orientiert sich an Android/Kotlin- und Clean-Architecture-Prinzipien: UseCases bleiben fachliche Einstiegspunkte, Implementierungsdetails fuer OCR, Modellinstallation und Fallbacks werden in klar getrennte Services bzw. Datenquellen ausgelagert.

## Umsetzungsstand

Stand dieses Dokuments: 30. März 2026

- **Phase 1–6 sind funktional umgesetzt (Phase 7–9 teilweise).**
- Gemeinsame OCR-Pipeline, Auto-Fallback, Modellinstallation, Sprachabdeckung (inkl. Koreanisch), Metadaten-Nutzung und CJK-Guard für Searchable PDF sind implementiert.
- Offen: App-eigene Font-Assets (Phase 6), differenzierte Fehler-UX (Phase 7), Backfill-Strategie (Phase 8), Hilfe-/Privacy-Texte (Phase 9).

### ✅ Phase 1: OCR-Pfade zusammenführen (Umgesetzt)
- Gemeinsame `OcrPipeline.kt` für Textextraktion und Searchable PDF.
- Einheitlicher Renderstandard (150 DPI).
- `TextRecognizerRunner.kt` als zentrale Ausführungsschicht.

### ✅ Phase 2: Automatisch + Manuell (Umgesetzt)
- `Automatisch` ist Default in allen OCR-Dropdowns.
- Intelligente Fallback-Kaskade bei `auto`.
- Manuelle Übersteuerung bleibt für Grenzfälle erhalten.

### ✅ Phase 3: Modellinstallation über Google Play Services (Umgesetzt)
- Integration des `ModuleInstallClient`.
- Transparente UI für Modell-Download und Installation.
- App bleibt schlank (unbundled Modelle für Nicht-Latin).

### ✅ Offene Punkte aus Reviews (Abgeschlossen)
1. **Android-Instrumentation**: `OcrPipelineInstrumentedTest` + `SearchableAndRoundTripInstrumentedTest` abgenommen (11 Tests grün).
2. **Metadaten-Nutzung**: `confidence`, `angle` und `recognizedLanguage` fließen jetzt in `OcrResultStats` ein und steuern Fallback-Entscheidungen sowie Textlayer-Rotation.
3. **Searchable PDF Grenzen**: Font-Strategie dokumentiert; ZH/JA-Guard im Workflow (Phase 6) umgesetzt.

---

## Kurzfazit

Die App nutzt bereits ein solides OCR-Backend: Google ML Kit Text Recognition v2. Die Nutzung ist aber nur teilweise ausgeschoepft.

Staerken des aktuellen Standes:

- on-device OCR ueber ML Kit
- Searchable-PDF-Erzeugung mit OCR-Textlayer
- Sprachwahl vorhanden
- Google Play Services wird fuer einige OCR-Skripte bereits genutzt

Hauptprobleme des aktuellen Standes:

- zwei getrennte OCR-Pfade mit unterschiedlicher Qualitaet
- manuelle Sprachwahl ist fuer viele Faelle ueberfluessig, aber fuer Spezialfaelle trotzdem wichtig
- unbundled Modelle werden nicht explizit vorinstalliert oder kontrolliert nachgeladen
- erweiterte OCR-Metadaten von ML Kit werden kaum genutzt
- Sprachunterstuetzung im UI ist enger als das technisch moegliche Backend

Empfohlene Zielrichtung:

- eine gemeinsame OCR-Pipeline fuer alle OCR-bezogenen Funktionen
- Standardmodus "Automatisch", mit manueller Uebersteuerung
- Hybrid-Strategie fuer Modellinstallation:
  - Kernpfad klein und robust halten
  - optionale Sprachmodelle ueber Google Play Services bei Bedarf laden
  - Downloads aktiv steuern statt auf den ersten OCR-Fehlversuch zu warten

## Analysierter Ist-Zustand im Projekt

### 1. Es gibt zwei OCR-Pfade

Text extrahieren:

- `ExtractTextUseCase.kt`
- rendert PDF-Seiten ueber `PdfPageInputImageLoader.kt`
- nutzt danach `TextRecognizerRunner.kt`
- verwendet fuer Extraktion effektiv nur `result.text`

Durchsuchbar machen:

- `MakeSearchableUseCase.kt`
- delegiert an `SearchablePdfBuilder.kt`
- rendert PDF-Seiten selbst
- fuehrt OCR direkt im Builder aus
- schreibt danach per PdfBox einen unsichtbaren Textlayer ins PDF

Folge:

- beide Features nutzen zwar denselben ML-Kit-Grundbaustein
- sie nutzen aber nicht dieselbe End-to-End-Codebasis
- dadurch koennen OCR-Ergebnisse qualitativ voneinander abweichen

Zusatzbefund:

- die Textextraktion besitzt aktuell einen Thumbnail-Fallback fuer fehlende PDF-Dateien
- diese Faehigkeit ist produktrelevant und darf bei einer Vereinheitlichung nicht versehentlich verloren gehen

### 2. Sprachwahl steuert heute echte Fachlogik

Die Sprachwahl ist aktuell nicht nur kosmetisch.

`OcrManager.kt` waehlt je nach Sprachcode unterschiedliche Recognizer:

- `zh` -> ChineseTextRecognizerOptions
- `ja` -> JapaneseTextRecognizerOptions
- `hi` -> DevanagariTextRecognizerOptions
- alles andere -> Latin Default Recognizer

Zusatzlich beeinflusst die Sprache in `SearchablePdfBuilder.kt`:

- Fontwahl fuer den PDF-Textlayer
- RTL-Ankerung fuer Arabisch

Das heisst:

- fuer die reine Textextraktion ist die Auswahl fuer viele Sprachen aktuell nicht zwingend notwendig
- fuer Searchable PDF ist Sprache weiterhin relevanter

### 3. Aktuelle Abhaengigkeiten sind bereits gemischt

In `app/build.gradle.kts` ist heute bereits ein Mischbetrieb vorhanden:

- Latin OCR ist bundled (`com.google.mlkit:text-recognition`)
- Chinesisch, Japanisch und Devanagari sind ueber Google Play Services eingebunden

Das ist grundsaetzlich sinnvoll, aber noch nicht sauber zu Ende implementiert.

### 4. Es fehlt ein kontrollierter Download-Flow

Im Manifest gibt es aktuell keine ML-Kit-Installationsmetadaten fuer OCR-Modelle.
Auch ein expliziter Download-Flow via Google Play Services `ModuleInstallClient` ist im Code nicht erkennbar.

Folge:

- unbundled Modelle koennen beim ersten OCR-Versuch fehlen
- der erste Versuch kann leer oder fehlerhaft sein
- Nutzer bekommen keine verstaendliche Modell-Download-UX

### 5. ML Kit Metadaten werden nur teilweise genutzt

ML Kit Text Recognition v2 liefert nicht nur Text, sondern auch:

- Block-, Zeilen-, Wort- und Symbolstruktur
- Bounding Boxes
- Corner Points
- Rotationswinkel
- Confidence
- erkannte Sprache pro Region

Im Projekt werden aktuell im Wesentlichen genutzt:

- reiner Volltext
- Wortebene plus Bounding Box fuer Searchable PDF

Kaum oder gar nicht genutzt werden:

- Confidence
- Angle
- Corner Points
- erkannte Sprache aus OCR-Ergebnis
- Symbol-Ebene

### 6. OCR ist nicht nur ein Einzel-Feature, sondern Teil mehrerer Workflows

OCR wirkt im Projekt nicht isoliert, sondern in mehreren Produktpfaden:

- beim Import neuer Scans
- beim nachtraeglichen "durchsuchbar machen"
- beim Redaction-Follow-up
- indirekt bei Workflows, die Searchable-Status bewusst entfernen

Wichtige Folge fuer den Plan:

- OCR muss als zentrale Faehigkeit mit mehreren Einstiegspunkten behandelt werden
- UI, Fehlerbehandlung und Modell-Download duerfen nicht nur fuer die Home-Extraktion gedacht werden
- die Orchestrierung muss Import-, Bulk-, Einzel- und Follow-up-Pfade mitdenken

### 7. Es gibt bestehende Produktgrenzen, die im Plan explizit gefuehrt werden muessen

Laut Projektwissen gilt aktuell:

- Chinesisch und Japanisch sind fuer OCR-Text relevant, aber derzeit nicht sauber fuer Searchable PDF freigegeben
- der Kerngrund liegt in der Font-/Einbettungsproblematik fuer den Textlayer

Folge:

- der Plan darf "mehr Automatik" nicht mit "alle Skripte sind automatisch auch searchable" verwechseln
- fuer bestimmte Skripte bleibt eine getrennte Produktentscheidung fuer "Text extrahieren" versus "durchsuchbar machen" notwendig

## Recherchierte fachliche Erkenntnisse

### 1. ML Kit Text Recognition v2 kann mehr als reine Textausgabe

Offizielle ML-Kit-Dokumentation bestaetigt:

- OCR-Ergebnisse werden als `Text -> TextBlock -> Line -> Element -> Symbol` geliefert
- auf diesen Ebenen sind Geometrie- und Qualitaetsdaten verfuegbar
- auf Element-Ebene sind insbesondere `getBoundingBox()`, `getCornerPoints()`, `getAngle()`, `getConfidence()` und `getRecognizedLanguage()` relevant

Praktische Bedeutung fuer diese App:

- bessere Textlayer-Positionierung
- bessere Entscheidung, ob ein Ergebnis "gut genug" ist
- bessere Auto-Erkennung und bessere Fallbacks

### 2. Es gibt keinen universellen automatischen OCR-Super-Recognizer

ML Kit bietet verschiedene Recognizer fuer verschiedene Skripte.
Es gibt keine einfache Ein-Klassen-Loesung, die intern fuer alle Schriftsysteme immer automatisch den richtigen Recognizer waehlt und die Modellwahl vollstaendig uebernimmt.

Praktische Folge:

- Auto-Erkennung muss in der App als Strategie gebaut werden
- besonders fuer Searchable PDF bleibt manuelle Uebersteuerung wichtig

### 3. Spracherkennung ist moeglich, aber nicht als kompletter Ersatz fuer Script-Auswahl

Es gibt zwei relevante Quellen fuer "automatische Sprache":

- erkannte Sprache innerhalb des OCR-Ergebnisses
- separate ML-Kit Language Identification auf bereits extrahiertem Text

Diese Verfahren sind sinnvoll fuer:

- Plausibilisierung
- Auto-Vorauswahl
- Fallback-Entscheidungen
- UI-Hinweise

Sie ersetzen aber nicht sauber die Frage, welches OCR-Modell fuer ein bestimmtes Skript geladen und verwendet werden muss.

### 4. Google empfiehlt fuer optionale Modelle den unbundled Weg

ML Kit beschreibt zwei Installationspfade:

- bundled: groesser, aber sofort verfuegbar
- unbundled ueber Google Play Services: kleiner, aber Modell muss geladen werden

Google weist ausdruecklich darauf hin:

- unbundled Modelle koennen beim ersten Aufruf noch nicht vorhanden sein
- Anfragen vor abgeschlossenem Download koennen leer bleiben
- fuer bessere Nutzererfahrung soll der Download aktiv gesteuert werden

### 5. Best Practice fuer schlanke Apps

Google empfiehlt fuer APK-/App-Bundle-Groesse:

- Android App Bundle verwenden
- optionale ML-Funktionen nach Moeglichkeit in on-demand Pfade oder optionale Module auslagern
- fuer nicht-kernige ML-Features die Nutzer nicht mit allen Modellen vorab belasten

Fuer diese App bedeutet das:

- OCR ist zwar wichtig, aber nicht jede Sprache ist Kernfunktion fuer jeden Nutzer
- deswegen ist ein Hybrid-Ansatz sinnvoller als "alles bundled"

### 6. Dependency- und Versionsstrategie muss Teil des Plans sein

Die Recherche zeigt:

- Google dokumentiert fuer die Play-Services-Variante von Latin Text Recognition aktuell eine andere Hauptversionslinie als die hier im Projekt verwendete bundled Latin-Variante
- das Projekt kombiniert bereits bundled und unbundled OCR-Abhaengigkeiten

Praktische Folge:

- der Verbesserungsplan sollte nicht nur Architektur und UX behandeln
- er muss auch einen expliziten Dependency-Review-Schritt enthalten
- Ziel ist keine blinde Versionsanhebung, sondern eine bewusste Entscheidung ueber:
  - bundled versus unbundled pro Skript
  - Versionseinheitlichkeit soweit sinnvoll
  - Testmatrix bei einem eventuellen Wechsel auf staerker Google-Play-Services-basierte OCR

## Zielbild

### Produktziel

Die App soll fuer OCR folgenden Zustand erreichen:

- Standardverhalten ist einfach: Nutzer sehen zuerst "Automatisch"
- manuelle Sprachwahl bleibt verfuegbar
- OCR arbeitet in allen OCR-Features mit derselben Kernpipeline
- Searchable PDF und Text-Extraktion liefern moeglichst konsistente Ergebnisse
- optionale Sprachmodelle werden schlank und kontrolliert ueber Google Play Services nachgeladen
- UI macht Modellstatus transparent und verhindert "stille" OCR-Fehler
- bestehende OCR-Folgeworkflows bleiben funktional korrekt
- bekannte Produktgrenzen fuer Searchable PDF werden sauber kommuniziert statt implizit versteckt

### Technisches Ziel

Einfuehrung einer gemeinsamen OCR-Domaenenlogik mit klaren Schichten:

- Domain:
  - OCR-Anfrage, OCR-Ergebnis, OCR-Modellstatus, OCR-Strategie
- Data/Service:
  - Recognizer-Auswahl
  - Modellinstallation via Google Play Services
  - PDF-Seitenrendering
  - OCR-Ausfuehrung
- Presentation:
  - Standardmodus "Automatisch"
  - manuelle Auswahl als Uebersteuerung
  - Modell-Download-Status
  - Retry-/Fallback-UI

## Empfohlene Produktentscheidung

## Empfehlung A: "Automatisch + Manuell" als Standard

Dies ist die empfohlene Hauptoption.

### Begruendung

- besser fuer UX als die heutige Pflichtauswahl
- robuster als "nur automatisch"
- kompatibel mit unterschiedlichen Skripten und Searchable-PDF-Beduerfnissen
- technisch realistisch mit ML Kit und Google Play Services

### Konkretes Verhalten

Im UI gibt es drei Zustaende:

1. `Automatisch` als Default
2. `Manuell` mit expliziter Sprachauswahl
3. `Modell wird geladen` falls ein benoetigtes Play-Services-Modell fehlt

Fuer Nutzer bedeutet das:

- Standardfall ohne Zusatzentscheidung
- Eingriff nur bei Bedarf
- kein Rate-Spiel, warum OCR gerade nichts liefert

### Warum "Dropdown erst nur bei Fehler anzeigen" nicht optimal ist

Das waere besser als heute, aber nicht Best Practice.

Nachteile:

- die manuelle Option waere versteckt, obwohl sie in Grenzfaellen wichtig ist
- Nutzer verstehen bei Fehlklassifikation schlechter, wie sie eingreifen koennen
- fuer Searchable PDF sollte die Uebersteuerung immer bewusst erreichbar bleiben

Besser ist:

- Default `Automatisch`
- manuelle Auswahl immer verfuegbar, aber dezent

## Empfehlung B: Hybrid-Modellinstallation

Dies ist die empfohlene Installationsstrategie.

### Kerngedanke

- haeufige oder kritische Modelle duerfen sofort verfuegbar sein
- optionale Modelle werden ueber Google Play Services bei Bedarf nachgeladen
- Download wird aktiv per API angestossen, nicht passiv dem ersten OCR-Fehler ueberlassen

### Empfohlene Auspraegung fuer dieses Projekt

Variante 1, konservativ und robust:

- Latin bundled lassen
- Chinesisch, Japanisch, Devanagari, spaeter Koreanisch unbundled

Variante 2, maximal schlank:

- auch Latin auf Google Play Services unbundled umstellen
- alle Modelle bei Bedarf laden

Empfehlung fuer dieses Projekt:

- zuerst Variante 1

Begruendung:

- die App wirkt fuer westliche Nutzer sofort schnell und robust
- seltenere Skripte bleiben schlank und on-demand
- geringeres Risiko beim Umbau

Wenn App-Groesse spaeter hoehere Prioritaet bekommt:

- kann in einer zweiten Stufe auch Latin unbundled gemacht werden

## Empfohlene technische Architektur

### 1. Gemeinsame OCR-Fassade einfuehren

Empfohlene neue zentrale Komponente:

- `OcrEngine` oder `OcrPipeline`

Aufgabe:

- nimmt eine OCR-Anfrage entgegen
- bestimmt Modus: automatisch oder manuell
- prueft Modellverfuegbarkeit
- laedt Modell bei Bedarf
- rendert Eingabe in geeignete Bilder
- fuehrt OCR aus
- liefert strukturierte OCR-Ergebnisse zurueck

### 2. Domain-Modelle einfuehren

Empfohlene neue Modelle:

- `OcrRequest`
- `OcrMode`
- `OcrScript`
- `OcrResult`
- `OcrPageResult`
- `OcrWord`
- `OcrModelState`
- `OcrExecutionState`
- `OcrCapability`

Beispielhafte Inhalte:

- `OcrMode.Automatic`
- `OcrMode.Manual(languageCode)`
- `OcrModelState.Available`
- `OcrModelState.Downloading`
- `OcrModelState.Missing`
- `OcrModelState.Failed`
- `OcrExecutionState.Idle`
- `OcrExecutionState.PreparingModel`
- `OcrExecutionState.Recognizing`
- `OcrExecutionState.BuildingSearchablePdf`

### 3. Saubere Trennung der Verantwortlichkeiten

Empfohlene Verteilung:

- `OcrRecognizerFactory`
  - baut den richtigen ML-Kit-Recognizer fuer das gewaehlte Skript
- `OcrModelInstaller`
  - kapselt Google Play Services `ModuleInstallClient`
- `PdfOcrImageRenderer`
  - rendert PDF-Seiten fuer OCR in definierter Qualitaet
- `OcrExecutor`
  - ruft ML Kit auf und mappt das Ergebnis in App-Modelle
- `OcrLanguageHeuristics`
  - bewertet Auto-Detect und Fallbacks

Das folgt den Clean-Architecture-Prinzipien:

- UseCases bleiben leicht
- Android-/Google-Play-Services-Details liegen in Infrastrukturklassen
- Tests werden einfacher

## Verbesserungsplan im Detail

### Phase 1: OCR-Pfade zusammenfuehren

Status:

- ✅ abgeschlossen

Ziel:

- Text extrahieren und Searchable PDF sollen dieselbe OCR-Quelle verwenden

Empfehlung:

- `SearchablePdfBuilder` nicht mehr als Sonderpfad mit eigener OCR-Logik behandeln
- stattdessen gemeinsame OCR-Pipeline aufrufen
- Searchable-PDF-Erzeugung nur noch als Nachverarbeitung eines gemeinsamen OCR-Ergebnisses

Konkrete Folgen:

- gleiche Renderqualitaet
- gleiche Wortdaten
- gleiche Fallback-Logik
- bessere Testbarkeit
- konsistente Nutzung in Import-, Bulk- und Follow-up-Workflows
- dieser Schritt ist inzwischen im Code erfolgt; offene Restarbeit betrifft vor allem Android-nahe Verifikation und weitere Entkopplung von Searchable-PDF-Nachverarbeitung

Wichtige fachliche Entscheidung:

- fuer PDF-basierte OCR einen einheitlichen Renderstandard definieren

Empfehlung:

- `ARGB_8888`
- weisser Hintergrund
- definierte Ziel-DPI fuer OCR, z. B. 150 DPI als Startwert
- spaetere Einstellbarkeit nur falls wirklich noetig

Warum das wichtig ist:

- ML Kit weist explizit auf Bildqualitaet und ausreichende Pixelgroesse hin
- unterschiedliche Renderpfade erzeugen unterschiedliche OCR-Qualitaet

### Phase 2: "Automatisch + Manuell" einfuehren

Status:

- ✅ abgeschlossen

Ziel:

- bessere UX ohne Kontrollverlust

Empfohlene UX:

- Default ist `Automatisch`
- daneben kleine Schaltflaeche oder Sekundaeroption `Manuell`
- bei `Manuell` erscheint das bekannte Dropdown

Empfohlene Auto-Strategie fuer diese App:

1. auf PDF-/Bildmaterial zuerst mit Standard-Latin beginnen
2. Ergebnis qualitaetsbewerten
3. wenn Ergebnis leer, sehr kurz oder unplausibel:
   - alternative Skript-Recognizer pruefen
4. OCR-Sprache aus Ergebnis plausibilisieren
5. bei Unsicherheit manuelle Auswahl hervorheben

Umgesetzt:

- `auto` prueft jetzt nicht mehr nur Latin, sondern nutzt eine Fallback-Kaskade ueber mehrere Recognizer
- fuer `SEARCHABLE_PDF` ist die Auto-Kaskade bewusst enger als fuer reine Textextraktion

Wichtiger Punkt:

- fuer `de/en/es/fr/pt/ru/ar` landet der aktuelle Code ohnehin im Latin-Recognizer
- deshalb ist Auto dort besonders sinnvoll

Sonderfall Arabisch:

- weil Searchable PDF RTL-Positionierung und passende Fontlogik braucht, muss bei arabischem Ergebnis die Nachverarbeitung sauber umschalten

Sonderfall Chinesisch und Japanisch:

- Auto-Erkennung darf diese Skripte fuer Textextraktion gern erkennen und bedienen
- fuer Searchable PDF muss der Plan aber die derzeitige Font-/Einbettungsgrenze beruecksichtigen
- die UX sollte in diesem Fall nicht still scheitern, sondern eine klare produktseitige Rueckmeldung geben

### Phase 3: Modellinstallation sauber ueber Google Play Services steuern

Status:

- ✅ abgeschlossen

Ziel:

- App schlank halten
- dennoch keine stillen Erstfehler bei optionalen Sprachen

Empfohlene Implementierung:

- neue Komponente `PlayServicesOcrModelInstaller`
- Nutzung von Google Play Services `ModuleInstallClient`
- Download vor OCR ausloesen, sobald klar ist, welches unbundled Modell noetig ist

Umgesetzt:

- `AndroidOcrModelInstaller` ueber `ModuleInstall.getClient(context)`
- Vorpruefung per `areModulesAvailable(...)`
- bedarfsgesteuerter Download per `installModules(...)`
- Statusbeobachtung per `InstallStatusListener`
- UI-Anbindung fuer Modellvorbereitung, Download und Installation im `HomeViewModel`

Empfohlene API-Nutzung innerhalb dieser Komponente:

- `areModulesAvailable(...)`
  - vor OCR pruefen, ob das benoetigte Modul bereits vorhanden ist
- `installModules(...)`
  - verwenden, wenn das Modell jetzt sofort benoetigt wird
- `deferredInstall(...)`
  - verwenden, wenn ein Modell wahrscheinlich bald gebraucht wird, aber noch nicht sofort
- `InstallStatusListener`
  - fuer Fortschritt, Statuswechsel und Abschluss
- `getInstallModulesIntent(...)`
  - nur dann nutzen, wenn ein expliziter nutzergefuehrter Installationsfluss erforderlich ist
- `releaseModules(...)`
  - vorerst nicht priorisieren; moegliche spaetere Optimierung, wenn viele seltene Modelle angesammelt werden

Empfohlene Regeln:

- wenn Nutzer manuell `ja`, `zh`, `hi` oder spaeter `ko` waehlt:
  - Modellpruefung sofort
  - falls noetig Download starten
  - UI zeigt Fortschritts- bzw. Ladezustand
  - OCR startet nach erfolgreichem Abschluss automatisch

- wenn Auto-Detect auf ein Spezialskript deutet:
  - Modellpruefung ebenfalls sofort
  - bei fehlendem Modell kontrollierter Download
  - kein "blindes" OCR mit spaeterem unklaren Fehlschlag

Zusatzempfehlung:

- Downloadstatus cachen
- letzte erfolgreiche Skriptwahl pro Nutzer lokal merken
- bei wiederholter Nutzung derselben Sprache schneller reagieren

Empfohlene Produktregeln fuer die drei Downloadarten:

- Sofortiger Download per `installModules(...)`
  - wenn Nutzer manuell eine Sprache auswaehlt und OCR unmittelbar ausfuehren will
- Hintergrund-Download per `deferredInstall(...)`
  - wenn Auto-Erkennung eine hohe Wahrscheinlichkeit fuer ein Spezialskript sieht oder ein Nutzer eine Sprache wiederholt nutzt
- Nur Verfuegbarkeitspruefung per `areModulesAvailable(...)`
  - wenn vorerst nur UI vorbereitet oder ein naechster Schritt entschieden werden soll

Ergaenzende UX-Anforderung:

- Downloadstatus darf nicht mit OCR-Fortschritt vermischt werden
- das Projekt sollte getrennte UI-Zustaende fuer:
  - Modellvorbereitung
  - OCR-Verarbeitung
  - Searchable-PDF-Erzeugung
  vorsehen

Begruendung:

- derzeit gibt es bereits einen gemeinsamen OCR-Ladezustand
- mit Google-Play-Services-Downloads wird dieser Zustand fachlich mehrdeutig, wenn keine Trennung erfolgt

### Phase 4: Sprachabdeckung korrigieren

Status:

- ✅ abgeschlossen; zh/ja/ko vollständig in UI-Dropdown, OcrManager und OcrScript eingebunden; KoreanTextRecognizerOptions integriert

Ziel:

- UI und technische Faehigkeit zusammenbringen

Aktueller Befund:

- eingebunden sind Chinese, Japanese und Devanagari
- UI listet diese nicht vollstaendig
- Koreanisch ist laut ML Kit verfuegbar, aber im Projekt nicht eingebunden

Empfehlung:

- UI-Liste und Backend synchronisieren
- unterstuetzte Sprachen/Schriften explizit dokumentieren
- Koreanisch evaluieren und bei Bedarf aufnehmen

Wichtig:

- nicht jede von ML Kit "mapped" Sprache muss einzeln im UI auftauchen
- fuer das Produkt genuegt eine saubere Kuratierung

Empfohlene UI-Prioritaet:

- `Automatisch`
- haeufige Sprachen
- Spezialschriften nur wenn technisch wirklich sauber unterstuetzt

Ergaenzende Produktregel:

- Sprachlisten sollen nicht nur nach theoretischer ML-Kit-Faehigkeit gebaut werden
- sie muessen auch die tatsaechliche Produktfaehigkeit fuer Searchable PDF, Fontsupport und UX-Klarheit widerspiegeln

### Phase 5: Mehr aus dem OCR-Ergebnis nutzen

Status:

- ✅ weitgehend abgeschlossen; confidence, angle und recognizedLanguage in OcrResultStats; steuern Fallback-Schwellen (auto-Modus) und Textlayer-Rotation; cornerPoints und Symbol-Ebene bewusst nicht priorisiert

Ziel:

- OCR-Qualitaet und Fallback-Intelligenz verbessern

Empfohlene Nutzung von ML-Kit-Metadaten:

- `confidence`
  - fuer Qualitaetsschwellen
  - fuer Entscheidung, ob Auto-Ergebnis akzeptiert wird
- `angle`
  - fuer schraege Dokumente
  - fuer bessere Platzierung im Textlayer
- `cornerPoints`
  - fuer genauere Geometrie als nur `Rect`
- `recognizedLanguage`
  - fuer Auto-Plausibilisierung
- `symbols`
  - optional spaeter fuer feinere Textausrichtung

Pragmatische Priorisierung:

1. Confidence
2. RecognizedLanguage
3. Angle
4. CornerPoints
5. Symbol-Ebene

Warum diese Reihenfolge:

- Confidence und erkannte Sprache liefern den groessten Nutzen fuer Auto/Manuell
- Angle und CornerPoints verbessern danach vor allem Searchable PDF
- Symbol-Ebene ist wertvoll, aber nicht der erste Hebel

### Phase 6: Searchable-PDF-Qualitaet verbessern

Status:

- ⏳ teilweise umgesetzt; CJK-Guard in MakeSearchableWorkflow (zh/ja/ko liefern ScanWorkflowError.SearchableUnsupportedForScript); App-eigene Font-Subsets in assets/ noch ausstehend

Ziel:

- bessere Textauswahl, weniger Geometriefehler, robustere Schriftbehandlung

Empfohlene Verbesserungen:

- Searchable-PDF-Erzeugung auf gemeinsames OCR-Ergebnis umstellen
- moeglichst nicht nur `Rect`, sondern perspektivische Geometrie auswerten
- Qualitaetsschwelle einbauen:
  - wenn OCR zu schwach, Nutzer warnen oder manuelle Sprache empfehlen
- Fontstrategie konsolidieren

Empfohlene Fontentscheidung:

- bestehende systemfontbasierte Fallbacks kurzfristig weiter nutzen
- mittelfristig lieber kontrollierte App-eigene Fonts/Subsets fuer relevante Skripte ausliefern

Begruendung:

- Systemfontsuche ist geraeteabhaengig
- Searchable PDF ist sensibler als reine Textanzeige
- reproduzierbare Ergebnisse sind hier wichtiger

Ergaenzende fachliche Regel:

- fuer Skripte, die aktuell nicht verlaesslich in einen durchsuchbaren Textlayer ueberfuehrt werden koennen, muss die App explizit zwischen:
  - OCR-Text extrahieren
  - Searchable PDF erzeugen
  unterscheiden

Das verhindert:

- falsche Produkterwartungen
- uneinheitliche Ergebnisse
- spaetere Supportprobleme

### Phase 7: Fehlermodell und UX verbessern

Status:

- ⏳ teilweise umgesetzt; SearchableUnsupportedForScript als neuer Fehlertyp mit Übersetzungen in allen 10 Locales; differenzierte Meldungen für "auto-Erkennung unsicher" und "Qualität zu niedrig" noch ausstehend

Ziel:

- keine generischen "OCR failed"-Meldungen mehr

Empfohlene neue Fehlerklassen:

- Modell fehlt und Download laeuft
- Modell fehlt und Download fehlgeschlagen
- OCR liefert zu wenig plausiblen Text
- gemischtes oder nicht sicher erkennbares Skript
- Searchable PDF fuer gewaehltes Skript/Fontroute nicht sinnvoll moeglich

Empfohlene UX-Meldungen:

- "Sprachmodell fuer Japanisch wird geladen"
- "Automatische Erkennung ist unsicher. Bitte Sprache manuell waehlen."
- "Text erkannt, aber Qualitaet fuer durchsuchbares PDF ist moeglicherweise niedrig."

Zusatzanforderung:

- Fehlermeldungen muessen je Einstiegspunkt angepasst werden
- Import, Bulk-OCR, Einzel-OCR und Redaction-Follow-up brauchen teils unterschiedliche Handlungsangebote

### Phase 8: Daten- und Backfill-Strategie definieren

Status:

- ⏳ offen; force-Parameter in MakeSearchableUseCase vorhanden (erlaubt Re-Processing bereits durchsuchbarer Records); proaktiver Lazy-Backfill-Mechanismus noch nicht implementiert

Ziel:

- alte Datensaetze nicht vom neuen OCR-Modell abkoppeln

Empfehlung:

- keine aggressive Massen-Neuberechnung beim Update
- stattdessen lazy backfill bei sinnvoller Gelegenheit:
  - beim Oeffnen eines Dokuments
  - beim erneuten "durchsuchbar machen"
  - bei explizitem Nutzerwunsch

Wichtige Faelle:

- `isSearchable = true`, aber `extractedText` fehlt oder ist schwach
- alte OCR-Ergebnisse mit anderer Pipelinequalitaet
- Dokumente, deren Searchable-Status in anderen Workflows bewusst entfernt wurde

Begruendung:

- vermeidet teure Hintergrundmigrationen
- reduziert Risiko fuer grosse Bibliotheken
- passt besser zu lokaler, on-device Verarbeitung

### Phase 9: Dokumentation, Hilfe und Privacy-Texte nachziehen

Status:

- ⏳ offen; Hilfe-/Info-/Privacy-Texte spiegeln OCR-Auto-Modus, Play-Services-Download, Sprachmodell-Grenzen und CJK-Einschränkungen noch nicht wider

Ziel:

- Produktverhalten und rechtliche/technische Kommunikation angleichen

Empfehlung:

- Help-, Info- und Privacy-Texte im selben Release wie die OCR-Umstellung anpassen

Zu dokumentieren:

- Standardmodus `Automatisch`
- manuelle Sprachwahl als Fallback
- Nachladen optionaler Sprachmodelle ueber Google Play Services
- Grenzen einzelner Skripte bei Searchable PDF
- lokale Speicherung von extrahiertem OCR-Text, soweit fachlich relevant

## Empfohlene Optionen im Vergleich

### Option 1: Alles wie heute, nur etwas aufraeumen

Vorteile:

- geringster Aufwand

Nachteile:

- Sprach-UX bleibt unnoetig sperrig
- zwei OCR-Pfade bleiben bestehen
- Modell-Download bleibt unsauber

Bewertung:

- nicht empfohlen

### Option 2: Vollautomatisch ohne manuelle Auswahl

Vorteile:

- einfachste Oberflaeche

Nachteile:

- fuer Spezialschriften zu fehleranfaellig
- fuer Searchable PDF zu unkontrolliert
- schwerer zu debuggen und zu supporten

Bewertung:

- nicht empfohlen

### Option 3: Automatisch + Manuell, Hybrid-Downloads, gemeinsame Pipeline

Vorteile:

- beste Balance aus UX, Robustheit und App-Groesse
- zukunftssicher
- gut testbar
- kompatibel mit Google-Play-Services-Best-Practice

Nachteile:

- mittlerer Implementierungsaufwand

Bewertung:

- klar empfohlen

## Empfohlene konkrete Umsetzung fuer dieses Projekt

### A. Neue oder angepasste Komponenten

Empfohlene neue Klassen:

- `domain/ocr/OcrRequest.kt`
- `domain/ocr/OcrResult.kt`
- `domain/ocr/OcrMode.kt`
- `util/ocr/OcrPipeline.kt`
- `util/ocr/OcrRecognizerFactory.kt`
- `util/ocr/PlayServicesOcrModelInstaller.kt`
- `util/ocr/OcrLanguageHeuristics.kt`
- `util/ocr/PdfOcrRenderer.kt`
- `util/ocr/OcrCapabilityResolver.kt`

Empfohlene refactorte Klassen:

- `ExtractTextUseCase.kt`
- `MakeSearchableUseCase.kt`
- `ImportScanUseCase.kt`
- `SearchablePdfBuilder.kt`
- `OcrManager.kt`
- `HomeViewModel.kt`
- `HomeScreen.kt`
- `ui/ocr/OcrLanguageOptions.kt`
- `ui/redact/RedactScreen.kt`
- `domain/workflow/RedactPdfWorkflow.kt`

### B. Build-/Dependency-Plan

Empfohlene Entscheidungen:

- bestehende Google-Play-Services-OCR-Dependencies fuer optionale Skripte beibehalten
- Koreanisch evaluieren und bei Bedarf ergaenzen
- optional Language Identification hinzunehmen, wenn Auto-Plausibilisierung ueber OCR-eigene Sprachattribute hinaus noetig wird
- `playstore-dynamic-feature-support` nur dann aufnehmen, wenn spaeter wirklich mit Dynamic Feature Modules gearbeitet wird
- expliziten Dependency-Review fuer ML-Kit-OCR-Artefakte und Versionslinien durchfuehren

Wichtig:

- `ModuleInstallClient` ist fuer kontrollierte Google-Play-Services-Downloads der primaere Best-Practice-Hebel
- Dynamic Feature Modules sind eine spaetere Optimierungsstufe, nicht zwingend Phase 1

### C. Manifest-/Installationsstrategie

Empfehlung:

- kein pauschales Vorabladen aller OCR-Modelle per Manifest
- Manifest-Preload nur fuer Modelle, die wirklich fast alle Nutzer benoetigen
- fuer optionale Modelle expliziten Downloadflow bevorzugen

Begruendung:

- schlanker Basis-Download
- bessere Kontrolle ueber Nutzererfahrung
- weniger unnnoetige Vorabkosten fuer seltene Sprachen

### D. Rollout-Reihenfolge

#### Stufe 1

Status:

- in Arbeit

- gemeinsame OCR-Pipeline
- `Automatisch + Manuell`
- Latin als robuster Standard
- expliziter Download fuer `zh`, `ja`, `hi`
- getrennte UI-Zustaende fuer Modellvorbereitung und OCR

#### Stufe 2

Status:

- noch nicht begonnen

- Confidence-/Sprach-Plausibilisierung
- bessere Fehlermeldungen
- Searchable-PDF-Qualitaetsbewertung
- Backfill-Strategie und Follow-up-Workflows integrieren

#### Stufe 3

Status:

- noch nicht begonnen

- Koreanisch und weitere kuratierte Sprachen
- optional Language ID
- optional spaetere Dynamic-Feature-Optimierung
- Dokumentation, Hilfe und Privacy final angleichen

## Teststrategie

### Unit-Tests

Abdecken:

- Recognizer-Auswahl pro OCR-Modus
- Auto-Detect-Heuristik
- Modellstatus-Entscheidungen
- Fallback-Kaskade
- Fehlerabbildung auf UI-States

### Instrumentation-Tests

Abdecken:

- PDF-Renderqualitaet fuer OCR
- erstmaliger Modell-Download ueber Google Play Services
- Searchable-PDF-Ergebnis nach Auto-Detect
- arabische RTL-Faelle
- mehrseitige Dokumente

### Regressions-Tests

Wichtig fuer:

- Text extrahieren
- Searchable PDF
- Import mit OCR
- Redaction-Follow-up mit OCR
- Thumbnail-Fallback
- Workflows, die Searchable-Status bewusst zuruecksetzen

## Risiken und Gegenmassnahmen

### Risiko 1: Auto-Erkennung ist bei schlechten Scans unzuverlaessig

Gegenmassnahme:

- manuelle Uebersteuerung immer erreichbar halten
- Confidence/Laengen-/Sprachheuristiken verwenden

### Risiko 2: Modell-Downloads fuehlen sich langsam oder fehlerhaft an

Gegenmassnahme:

- expliziter Download mit UI-Status
- Wiederanlauf nach Downloadabschluss
- klare Fehlermeldung bei fehlenden Google Play Services

### Risiko 3: Searchable PDF hat trotz OCR Textlayer-Fehler

Gegenmassnahme:

- gemeinsame OCR-Geometrie
- bessere Fontstrategie
- Qualitaetswarnung bei schwachem OCR-Ergebnis

### Risiko 4: Auto-Modus verspricht mehr als die Produktfaehigkeit einzelner Skripte

Gegenmassnahme:

- Capabilities explizit modellieren
- Searchable-PDF-Unterstuetzung pro Skript klar trennen
- UX-Texte und Optionen daran ausrichten

### Risiko 5: Mehr Komplexitaet in der Architektur

Gegenmassnahme:

- klare Schichtentrennung
- OCR-Infrastruktur zentralisieren
- UseCases schlank halten

## Endgueltige Empfehlung

Die empfohlene Gesamtstrategie fuer diese App lautet:

1. `Automatisch + Manuell` als neue Standard-UX einfuehren
2. eine gemeinsame OCR-Pipeline fuer Extraktion, Searchable PDF und OCR-bezogene Folgeworkflows bauen
3. haeufige Kernfaelle robust halten, optionale Sprachmodelle ueber Google Play Services on-demand laden
4. Downloads aktiv steuern, bevorzugt ueber `ModuleInstallClient`
5. ML-Kit-Metadaten schrittweise staerker fuer Qualitaetsbewertung und Fallbacks nutzen
6. Produktgrenzen fuer Searchable PDF pro Skript explizit modellieren und kommunizieren
7. OCR als Querschnittsfaehigkeit fuer Import, Bulk und Follow-up-Workflows behandeln

Das ist fachlich die beste Balance aus:

- OCR-Qualitaet
- App-Groesse
- Wartbarkeit
- Android-Best-Practice
- Nutzerfreundlichkeit

## Quellen

Offizielle Quellen, die fuer diesen Plan herangezogen wurden:

- ML Kit Text Recognition v2 Android:
  - https://developers.google.com/ml-kit/vision/text-recognition/v2/android
- ML Kit Text Recognition v2 Supported Languages:
  - https://developers.google.com/ml-kit/vision/text-recognition/v2/languages
- ML Kit Text.TextBlock API:
  - https://developers.google.com/android/reference/com/google/mlkit/vision/text/Text.TextBlock
- ML Kit Text.Element API:
  - https://developers.google.com/android/reference/com/google/mlkit/vision/text/Text.Element
- ML Kit Language Identification Android:
  - https://developers.google.com/ml-kit/language/identification/android
- ML Kit App Size / APK Size Empfehlungen:
  - https://developers.google.com/ml-kit/tips/reduce-app-size
- Google Play services Module Install APIs:
  - https://developers.google.com/android/guides/module-install-apis
- Google Play services ModuleInstallClient Referenz:
  - https://developers.google.com/android/reference/com/google/android/gms/common/moduleinstall/ModuleInstallClient
- Google Play services ModuleInstall Referenz:
  - https://developers.google.com/android/reference/com/google/android/gms/common/moduleinstall/ModuleInstall
- Google Play services ModuleInstallStatusUpdate Referenz:
  - https://developers.google.com/android/reference/com/google/android/gms/common/moduleinstall/ModuleInstallStatusUpdate
- Google Play services ModuleInstallStatusCodes Referenz:
  - https://developers.google.com/android/reference/com/google/android/gms/common/moduleinstall/ModuleInstallStatusCodes
- ML Kit Release Notes:
  - https://developers.google.com/ml-kit/release-notes

## Anhang: Projektnahe Leitentscheidungen

### Leitentscheidung 1

Die App soll OCR nicht laenger als zwei getrennte Implementierungen behandeln, sondern als eine gemeinsame Faehigkeit mit mehreren Ausgabemodi.

### Leitentscheidung 2

Die App soll die Nutzer nicht mehr standardmaessig mit einem Sprachdropdown belasten, aber die Kontrolle fuer Grenzfaelle nicht wegnehmen.

### Leitentscheidung 3

Google Play Services soll fuer optionale Sprachmodelle bewusst genutzt werden, damit die App schlank bleibt und Modelle nur dann geladen werden, wenn sie wirklich benoetigt werden.

### Leitentscheidung 4

Searchable PDF ist qualitativ anspruchsvoller als reine Textanzeige. Deshalb darf dieser Pfad nicht nur "mitlaufen", sondern braucht saubere Qualitaets- und Geometrieentscheidungen.
