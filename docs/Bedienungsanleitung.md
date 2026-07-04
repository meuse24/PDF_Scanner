---
title: "M24 PDF Scanner"
subtitle: "Vollständige Bedienungsanleitung"
date: "Version 2.1 · Juni 2026"
lang: de-DE
toc: true
toc-depth: 3
numbersections: true
geometry:
  - left=2.8cm
  - right=2.5cm
  - top=3.2cm
  - bottom=3cm
fontsize: 11pt
linestretch: 1.25
colorlinks: true
linkcolor: blue
urlcolor: blue
toccolor: black
header-includes:
  - \usepackage{fancyhdr}
  - \usepackage{xcolor}
  - \usepackage{booktabs}
  - \usepackage{longtable}
  - \usepackage{array}
  - \usepackage{mdframed}
  - \usepackage{enumitem}
  - \definecolor{hinweisblau}{RGB}{0,90,180}
  - \definecolor{tippgruen}{RGB}{0,130,80}
  - \definecolor{warngelb}{RGB}{180,120,0}
  - \definecolor{hellgrau}{RGB}{245,245,248}
  - \definecolor{mittelgrau}{RGB}{120,120,130}
  - \pagestyle{fancy}
  - \fancyhf{}
  - \fancyhead[L]{\small\textcolor{mittelgrau}{M24 PDF Scanner · Bedienungsanleitung}}
  - \fancyhead[R]{\small\textcolor{mittelgrau}{Version 2.1}}
  - \fancyfoot[C]{\small\thepage}
  - \renewcommand{\headrulewidth}{0.4pt}
  - \renewcommand{\footrulewidth}{0pt}
  - \setlength{\parskip}{6pt}
  - \newmdenv[backgroundcolor=hellgrau,linecolor=hinweisblau,linewidth=2pt,topline=false,bottomline=false,rightline=false,innerleftmargin=10pt,innerrightmargin=8pt,innertopmargin=6pt,innerbottommargin=6pt]{hinweis}
  - \newmdenv[backgroundcolor=hellgrau,linecolor=tippgruen,linewidth=2pt,topline=false,bottomline=false,rightline=false,innerleftmargin=10pt,innerrightmargin=8pt,innertopmargin=6pt,innerbottommargin=6pt]{tipp}
  - \newmdenv[backgroundcolor=hellgrau,linecolor=warngelb,linewidth=2pt,topline=false,bottomline=false,rightline=false,innerleftmargin=10pt,innerrightmargin=8pt,innertopmargin=6pt,innerbottommargin=6pt]{warnung}
---

\newpage

# Überblick

## Was ist M24 PDF Scanner?

M24 PDF Scanner ist eine datenschutzorientierte Android-App zum Scannen, Verwalten, Bearbeiten und Schützen von PDF-Dokumenten – vollständig offline auf dem Gerät. Alle Dokumente, erkannter Text, Metadaten und verschlüsselte Backups verbleiben im internen Gerätespeicher.

**Kernfunktionen auf einen Blick:**

| Bereich | Funktionen |
|---|---|
| **Erfassen** | Kamera-Scan, PDF-Import, Bilder zu PDF, Teilen aus anderen Apps |
| **Lesen** | Integrierter PDF-Viewer, Zoom, Seiten-Navigation |
| **Texterkennung** | OCR in 13 Sprachen, durchsuchbare PDFs, TXT-Export, Auto-Tags |
| **Übersetzung** | On-device-Übersetzung in 10 Sprachen via ML Kit |
| **Seiten** | Umsortieren, Drehen, Anhängen, Aufteilen, Löschen, Duplizieren |
| **Bearbeiten** | PDF-Formulare ausfüllen, Annotieren, Signieren, Wasserzeichen, Seitenzahlen, Schwärzen |
| **Exportieren** | Word (.docx), Graustufen, Komprimieren, Als JPG |
| **Analyse** | QR-Codes, Visitenkarten mit vCard-Export |
| **Sicherheit** | PDF-Passwortschutz, App-Lock (Biometrie), verschlüsselte Backups |
| **Organisation** | Ordner, Favoriten, Tags, Mehrfachauswahl, Papierkorb |

## Datenschutz auf einen Blick

- **Kein Cloud-Upload** – kein Konto, keine Serververbindung für Dokumente
- **Lokale Verarbeitung** – OCR, Übersetzung und alle Bearbeitungen laufen auf dem Gerät
- **Exportkontrolle** – Dateien verlassen die App nur durch explizite Benutzeraktion (Teilen, Exportieren, Backup)
- **App-Lock** – lokale UI-Sperre mit Biometrie oder Geräte-PIN; verschlüsselt keine Daten
- **Backups** – werden ausschließlich auf explizite Benutzeraktion erstellt, sind mit Argon2id+Tink verschlüsselt und enthalten keine wiederherstellbaren Passwörter
- **ML Kit** – OCR- und Übersetzungsmodelle werden von Google Play Services geladen; die Verarbeitung selbst läuft lokal

## Systemanforderungen

- Android 10 oder neuer (API 29+)
- Google Play Services (für ML Kit)
- Rund 50 MB freier Speicher für die App; OCR-Modelle und Übersetzungsmodelle belegen zusätzlich je 15–30 MB und werden bei Bedarf heruntergeladen

\newpage

# Dokumente hinzufügen

## Dokument scannen

Tippen Sie auf der Startseite auf den runden **Scan-Button** (Kamera-Symbol) unten rechts. Die Scan-Oberfläche von Google ML Kit Document Scanner öffnet sich.

1. Halten Sie die Kamera über das Dokument – die App erkennt Kanten automatisch
2. Tippen Sie auf den Auslöser oder nutzen Sie den Auto-Modus
3. Scannen Sie weitere Seiten, oder bestätigen Sie mit **Fertig**
4. Die App speichert das Dokument als mehrseitiges PDF

\begin{tipp}
\textbf{Tipp:} Für beste Ergebnisse sorgen Sie für gleichmäßige Beleuchtung und legen Sie das Dokument auf einem kontraststarken Untergrund ab.
\end{tipp}

## PDF importieren

1. Tippen Sie auf das **Import-Symbol** (Datei mit Pfeil) in der App-Leiste
2. Der Systemdatei-Picker öffnet sich – wählen Sie eine oder mehrere PDF-Dateien aus
3. Die App kopiert die Datei(en) sofort in das interne Archiv

\begin{hinweis}
\textbf{Hinweis:} Importierte Dateien werden in den internen Speicher der App kopiert. Die Originaldatei an ihrem ursprünglichen Ort bleibt unverändert.
\end{hinweis}

## Bilder zu PDF

Mit dieser Funktion erstellen Sie ein PDF aus Fotos der Galerie:

1. Öffnen Sie das **Seitenmenü** (Hamburger-Symbol) → **Bilder zu PDF**
2. Wählen Sie ein oder mehrere Bilder aus der Galerie
3. Wählen Sie das Seitenlayout:
   - **1 Bild pro Seite** – maximale Größe
   - **2 Bilder pro Seite** – zwei Bilder nebeneinander
   - **4 Bilder pro Seite** – Rasteranordnung
4. Vergeben Sie einen Dateinamen und bestätigen Sie mit **Erstellen**

## Teilen aus anderen Apps

Dateien können direkt aus anderen Android-Apps an M24 PDF Scanner gesendet werden:

- **Einzelne PDF:** Über „Teilen" einer beliebigen PDF-fähigen App
- **Bilder:** Über „Teilen" aus Galerie oder Dateimanager
- **Mehrere Bilder gleichzeitig:** Mehrfachauswahl in der Galerie → Teilen
- **PDF direkt öffnen:** Langes Tippen auf eine PDF-Datei → „Öffnen mit" → M24 PDF Scanner

Die App öffnet in allen Fällen einen Bestätigungsdialog, bevor die Datei ins Archiv übernommen wird.

## App-Shortcuts und Schnellzugriff

M24 PDF Scanner bietet drei Schnellzugriff-Möglichkeiten außerhalb der App:

| Methode | Zugriff | Funktion |
|---|---|---|
| **App-Shortcut** | Langes Tippen auf das App-Icon | „Neuer Scan" direkt starten |
| **Quick-Settings-Kachel** | Benachrichtigungsleiste erweitern → Kachel hinzufügen | Scan aus dem Schnellmenü starten |
| **Startbildschirm-Widget** | Lange auf den Startbildschirm tippen → Widget | Scan-Shortcut auf dem Homescreen |

\newpage

# Das Archiv

## Übersicht und Navigation

Beim Start der App öffnet sich das Archiv mit der Liste aller Dokumente. Jedes Dokument zeigt:

- Vorschau-Thumbnail
- Dateiname
- Datum und Uhrzeit des Imports oder Scans
- Seitenzahl und Dateigröße
- Automatisch erkannte Tags (falls OCR ausgeführt wurde)

Über das **Seitenmenü** (Hamburger-Symbol oben links) erreichen Sie:

- **Archiv** – Hauptansicht aller Dokumente
- **Papierkorb** – gelöschte Dokumente
- **Hilfe** – integrierte Hilfe
- **Info** – App-Version, Bibliotheken, Datenschutz
- **Datenschutz** – Datenschutzrichtlinie

## Sortierung

Tippen Sie auf das **Sortier-Symbol** in der App-Leiste, um die Reihenfolge zu ändern:

- Nach Datum (neueste zuerst / älteste zuerst)
- Nach Name (A–Z / Z–A)
- Nach Größe
- Nach Seitenzahl

## Suche

Tippen Sie auf das **Lupe-Symbol** in der App-Leiste. Die Suche durchsucht gleichzeitig:

- Dateinamen
- Gespeicherten OCR-Text (sofern Texterkennung ausgeführt wurde)

Die Ergebnisse werden in Echtzeit gefiltert. Ein leeres Suchfeld zeigt alle Dokumente.

## Tags und Tag-Filter

Wenn die **automatische Tagging-Funktion** aktiv ist, analysiert die App nach einer Texterkennung den OCR-Text und vergibt passende Tags. Folgende Tags werden erkannt:

| Tag | Inhaltsbeispiele |
|---|---|
| **Rechnung** | Rechnungsnummer, Betrag, MwSt. |
| **Vertrag** | Vertragsparteien, Unterschriften, Laufzeit |
| **Versicherung** | Police, Prämie, Versicherungsnehmer |
| **Zertifikat** | Bescheinigung, Zertifizierung, Prüfung |
| **Bank** | IBAN, Kontoauszug, Transaktion |
| **Lieferung** | Sendungsverfolgung, Paket, Lieferschein |

Tag-Filter-Chips erscheinen oberhalb der Dokumentliste, sobald mindestens ein Dokument mit Tags vorhanden ist. Tippen Sie auf einen Chip, um nur Dokumente mit diesem Tag anzuzeigen.

\begin{tipp}
\textbf{Tipp:} Auto-Tagging kann in den Einstellungen aktiviert oder deaktiviert werden. Mit „Rückwirkend taggen" (Einstellungen) werden bestehende OCR-Dokumente nachträglich ausgewertet.
\end{tipp}

## Ordner

Dokumente können in Ordnern organisiert werden:

1. Öffnen Sie das Seitenmenü → **Archiv** oder tippen Sie auf **Ordner verwalten**
2. Erstellen, umbenennen oder löschen Sie Ordner
3. Um ein Dokument in einen Ordner zu verschieben, öffnen Sie das Aktions-Sheet des Dokuments (langes Tippen oder Drei-Punkte-Menü) → **In Ordner verschieben**

Im Ordner-Ansichtsmodus zeigt die Seitenleiste alle Ordner; ein Tap auf „Alle Dokumente" zeigt das vollständige Archiv.

## Favoriten

Tippen Sie auf das **Stern-Symbol** eines Dokuments, um es als Favoriten zu markieren. Favoriten können separat gefiltert werden.

## Mehrfachauswahl und Massenaktionen

Um mehrere Dokumente gleichzeitig zu bearbeiten:

1. **Tippen Sie auf das Kontrollkästchen-Symbol** (rechts am Dokument) – der Auswahlmodus startet
2. Wählen Sie weitere Dokumente durch Antippen
3. In der oberen Leiste erscheint **„X ausgewählt"** mit Auswahlmodus-Aktionen:
   - **„Alle"** – alle sichtbaren Dokumente auswählen

Die **Massenaktionen-Leiste** am unteren Bildschirmrand bietet folgende Aktionen für die Auswahl:

| Symbol | Aktion |
|---|---|
| Teilen | Ausgewählte PDFs über andere Apps teilen |
| Ordner | In Ordner verschieben |
| OCR-Menü | Texterkennung starten oder durchsuchbare PDFs erstellen |
| Mehr (···) | Word-Export, OCR-TXT-Export, Zusammenführen, Löschen |

4. Drücken Sie **Zurück** oder tippen Sie auf **X**, um den Auswahlmodus zu beenden

\newpage

# Dokumente öffnen und lesen

## Integrierter PDF-Viewer

Tippen Sie auf ein beliebiges Dokument im Archiv, um es im integrierten Viewer zu öffnen.

**Navigation:**
- **Scrollen** – Seiten senkrecht wischen
- **Pinch-Zoom** – zwei Finger zum Hineinzoomen, auseinanderziehen zum Herauszoomen
- Beim Zoomen rendert die App die aktuelle Seite in höherer Auflösung

**Viewer-Aktionsleiste** (oben):

| Symbol | Aktion |
|---|---|
| Zurück-Pfeil | Viewer schließen, zurück zum Archiv |
| Drei Punkte (···) | Weitere Aktionen (Teilen, Drucken, In externem Viewer öffnen, …) |

## Dokument-Aktionen aus dem Viewer

Über die **Drei-Punkte-Schaltfläche** im Viewer oder durch Öffnen des Aktions-Sheets sind alle Bearbeitungsfunktionen erreichbar, die auch aus dem Archiv heraus verfügbar sind (OCR, Übersetzen, Seiten bearbeiten, Sicherheit usw.).

## In externem Viewer öffnen

Wenn Sie das Dokument in einer anderen App öffnen möchten (z. B. Adobe Acrobat), tippen Sie im Viewer auf **Drei Punkte → In externem Viewer öffnen**. Die App zeigt eine App-Auswahl an.

## Drucken

Tippen Sie im Viewer auf **Drei Punkte → Drucken**. Daraufhin öffnet sich der Android-Systemdruck-Dialog.

\newpage

# Texterkennung (OCR)

## Was ist OCR?

OCR (Optical Character Recognition) liest den Text aus gescannten PDF-Seiten aus und speichert ihn intern. Damit werden folgende Funktionen ermöglicht:

- **Volltextsuche** im Archiv
- **Durchsuchbare PDFs** (unsichtbare Textschicht)
- **Text-Export** als TXT-Datei
- **Word-Export** (.docx)
- **Übersetzung** in andere Sprachen
- **Automatische Tags** für die Archivfilterung

## OCR starten

### Einzelnes Dokument

1. Tippen Sie auf das Dokument und öffnen Sie das **Aktions-Sheet** (langes Tippen oder Drei-Punkte-Menü)
2. Im Abschnitt **„Analyse"** → **„Text extrahieren"**
3. Die App öffnet den **OCR-Review-Screen** mit Ergebnis

### Mehrere Dokumente gleichzeitig

1. Aktivieren Sie den **Mehrfachauswahl-Modus**
2. Wählen Sie die gewünschten Dokumente
3. Tippen Sie auf **OCR-Menü** → **Text extrahieren**
4. Bei mehr als einem Dokument erscheint ein **kombiniertes Ergebnis-Sheet**

## Sprache auswählen

M24 PDF Scanner unterstützt folgende OCR-Sprachen:

| Sprachcode | Sprache | Modell |
|---|---|---|
| `de` | Deutsch | Integriert |
| `en` | Englisch | Integriert |
| `es` | Spanisch | Integriert |
| `fr` | Französisch | Integriert |
| `it` | Italienisch | Integriert |
| `pt` | Portugiesisch | Integriert |
| `hi` | Hindi | On-Demand |
| `zh` | Chinesisch | On-Demand |
| `ja` | Japanisch | On-Demand |
| `ko` | Koreanisch | On-Demand |

Auf-Demand-Modelle werden über Google Play Services geladen, wenn Sie die jeweilige Sprache erstmals manuell auswählen.

Der Automatikmodus verwendet ausschließlich das integrierte, offline verfügbare Latin-Modell. Für Hindi, Chinesisch, Japanisch oder Koreanisch wählen Sie die Sprache manuell unter **Aktions-Sheet → Analyse → OCR-Sprache**. Bei einer entsprechenden Systemsprache wird dieses Modell als Standard vorbelegt.

## OCR-Review-Screen

Nach der Texterkennung zeigt der Review-Screen für jede Seite:

- **Erkannter Text** (kopierbar)
- **Erkannte Sprache** mit Qualitäts-Badge (Gut / Mittel / Schwach)
- **Seiten-Tabs** zum Wechseln zwischen den Seiten

Verfügbare Aktionen im Review:

| Schaltfläche | Funktion |
|---|---|
| Kopieren | Gesamten Text in die Zwischenablage |
| Teilen | Text über andere Apps teilen |
| TXT exportieren | Text als `.txt`-Datei in Downloads speichern |

## Durchsuchbare PDF erstellen

Eine durchsuchbare PDF enthält eine unsichtbare Textschicht, die Suche in externen PDF-Readern ermöglicht.

1. **Aktions-Sheet → Analyse → Durchsuchbar machen**
2. Die App führt OCR durch und bettet die Textschicht in die PDF ein
3. Die verarbeitete PDF wird lokal gespeichert

\begin{hinweis}
\textbf{Hinweis:} Durchsuchbare PDFs unterstützen lateinische Schriften und Hindi. Für Arabisch, Chinesisch, Japanisch und Koreanisch kann OCR-Text über „Text extrahieren“ für die App-Suche gespeichert werden, es wird jedoch keine PDF-Textschicht eingebettet.
\end{hinweis}

Mit der Funktion **„Textschicht entfernen"** (Aktions-Sheet → Analyse) können Sie eine vorhandene Textschicht wieder aus der PDF löschen.

## OCR-Text als TXT exportieren

1. **Aktions-Sheet → Analyse → Text extrahieren** → **TXT exportieren**
2. Die Datei wird in den Ordner **Downloads** des Geräts geschrieben

Alternativ bei Mehrfachauswahl: **Massenaktionen-Menü → OCR-TXT-Export**. Nur Dokumente mit bereits gespeichertem OCR-Text werden dabei exportiert; bei fehlenden Texten wird kein automatischer OCR-Lauf gestartet.

## Automatische Tags (Auto-Tagging)

Wenn Auto-Tagging aktiv ist, vergibt die App nach jeder Texterkennung automatisch passende Inhaltskategorien. Die Tags erscheinen als farbige Chips unter dem Dokumentnamen und ermöglichen gezielte Archivfilterung (→ Abschnitt 3.4).

\newpage

# Übersetzung

## Voraussetzungen

Die Übersetzungsfunktion nutzt **Google ML Kit Translate** und verarbeitet alles lokal auf dem Gerät. Voraussetzung ist, dass das Dokument bereits OCR-Text enthält – führen Sie andernfalls zuerst eine Texterkennung aus (→ Abschnitt 5).

\begin{hinweis}
\textbf{Hinweis:} Beim ersten Einsatz einer Sprachkombination lädt die App das passende Sprachmodell (~15–30 MB) automatisch herunter. Für den Download wird eine Internetverbindung benötigt; danach arbeitet die Übersetzung vollständig offline.
\end{hinweis}

## Übersetzung starten

1. Öffnen Sie das **Aktions-Sheet** des Dokuments (langes Tippen oder Drei-Punkte-Menü)
2. Im Abschnitt **„Analyse"** → **„PDF übersetzen"**
3. Der **Übersetzungs-Screen** öffnet sich

## Sprachen auswählen

Im Übersetzungs-Screen sehen Sie zwei Auswahlfelder:

- **Quellsprache** – Sprache des Originaltexts (wird automatisch aus der OCR-Erkennung vorbelegt, sofern bekannt)
- **Zielsprache** – Sprache, in die übersetzt werden soll

Unterstützte Sprachen:

| Code | Sprache |
|---|---|
| EN | Englisch |
| DE | Deutsch |
| ES | Spanisch |
| FR | Französisch |
| PT | Portugiesisch |
| RU | Russisch |
| AR | Arabisch |
| HI | Hindi |
| ZH | Chinesisch (vereinfacht) |
| JA | Japanisch |

\begin{tipp}
\textbf{Tipp:} Die Quellsprache wird automatisch aus dem OCR-Ergebnis übernommen. Die Zielsprache wird automatisch so gewählt, dass Quell- und Zielsprache unterschiedlich sind – z. B. Quellsprache Deutsch → Zielsprache Englisch.
\end{tipp}

## Übersetzung ausführen

Tippen Sie auf die Schaltfläche **„Übersetzen"**.

Während der Übersetzung zeigt die App den Fortschritt an:

- **„Sprachmodell wird heruntergeladen…"** – beim ersten Einsatz dieser Sprache
- **„Seite X von Y wird übersetzt…"** – während der seitenweisen Verarbeitung

## Ergebnis lesen

Nach der Übersetzung erscheint das Ergebnis in der unteren Karte. Bei mehreren Seiten wird jede Seite separat mit Seitennummer und Sprachpaar angezeigt. Zusätzlich sehen Sie jeweils den Originaltext zum Vergleich.

Der gesamte übersetzte Text ist auswählbar und kann direkt markiert und kopiert werden.

## Übersetzung kopieren und teilen

| Schaltfläche | Funktion |
|---|---|
| **Alles kopieren** | Gesamte Übersetzung in die Zwischenablage |
| **Teilen** | Übersetzungstext über andere Apps teilen (E-Mail, Notizen, …) |

Für eine Neuübersetzung mit anderen Sprachen wählen Sie einfach neue Sprachen und tippen erneut auf **„Übersetzen"**.

\newpage

# Seiten bearbeiten

Alle Seitenbearbeitungsfunktionen erreichen Sie über das **Aktions-Sheet** eines Dokuments im Abschnitt **„Seiten"**.

## Seiten umsortieren

1. **Aktions-Sheet → Seiten → Umsortieren**
2. Im Editor können Sie Seiten durch **Ziehen** an eine neue Position verschieben
3. Bestätigen Sie mit **Speichern** oder verwerfen Sie mit **Abbrechen**

## Seiten drehen

1. **Aktions-Sheet → Seiten → Drehen**
2. Wählen Sie die Seiten, die gedreht werden sollen (einzeln oder alle)
3. Tippen Sie auf den Drehpfeil: je Tap 90° im Uhrzeigersinn
4. Bestätigen Sie mit **Speichern**

## Seiten anhängen

Fügen Sie zusätzliche Inhalte am Ende (oder an beliebiger Position) ein:

1. **Aktions-Sheet → Seiten → Seiten anhängen**
2. Wählen Sie die Quelle:
   - **Kamera** – neue Seiten scannen
   - **Galerie** – Fotos als Seiten einfügen
   - **PDF** – Seiten aus einem anderen PDF

## Seiten extrahieren

Speichern Sie ausgewählte Seiten als neue PDF-Datei:

1. **Aktions-Sheet → Seiten → Seiten extrahieren**
2. Wählen Sie die gewünschten Seiten über Seitenbereiche (z. B. `1-3, 5`)
3. Geben Sie einen Namen ein und bestätigen Sie

Das Originaldokument bleibt unverändert; die extrahierten Seiten bilden ein neues Dokument im Archiv.

## Seiten duplizieren

1. **Aktions-Sheet → Seiten → Seiten duplizieren**
2. Wählen Sie Seiten und die Anzahl der Kopien
3. Bestätigen Sie – die duplizierten Seiten werden eingefügt

## Dokument aufteilen

Teilen Sie ein Dokument an einer oder mehreren Seitengrenzen:

1. **Aktions-Sheet → Seiten → Aufteilen**
2. Geben Sie Trennstellen ein (z. B. nach Seite 3 und Seite 7)
3. Bestätigen Sie – es entstehen mehrere neue Dokumente im Archiv

## Seiten löschen

1. **Aktions-Sheet → Seiten → Seiten löschen**
2. Wählen Sie die zu löschenden Seiten
3. Bestätigen Sie den Lösch-Dialog

\begin{warnung}
\textbf{Achtung:} Das Löschen von Seiten kann nicht rückgängig gemacht werden. Erstellen Sie vorher ein Backup, wenn Sie die Originalseiten erhalten möchten.
\end{warnung}

\newpage

# Annotieren und Bearbeiten

## Annotieren

Der Annotations-Editor ermöglicht das Hinzufügen visueller Markierungen:

1. **Aktions-Sheet → Bearbeiten → Annotieren**

**Verfügbare Werkzeuge:**

| Werkzeug | Beschreibung |
|---|---|
| **Markierung** | Farbige Textmarkierung (Highlight) |
| **Rechteck** | Rechteckige Hervorhebung oder Rahmen |
| **Oval** | Elliptischer Rahmen |
| **Textnotiz** | Freier Text an beliebiger Position |

Für jedes Werkzeug können Farbe und Stärke angepasst werden. Der Editor unterstützt **Zoom**, damit Sie auch kleine Bereiche präzise annotieren können.

Tippen Sie auf **Speichern**, um die Annotationen fest in die PDF zu rendern.

## Signieren

Fügen Sie eine handschriftliche Unterschrift ein:

1. **Aktions-Sheet → Bearbeiten → Unterschreiben**
2. Zeichnen Sie Ihre Unterschrift auf dem Unterschriften-Pad
3. Positionieren und skalieren Sie die Unterschrift auf der Zielseite
4. Bestätigen Sie mit **Einfügen**

## PDF-Formulare ausfüllen

Enthält das geöffnete Dokument ein unterstütztes AcroForm, erscheint im PDF-Viewer die Aktion **Formular ausfüllen**.

1. Öffnen Sie das PDF im integrierten Viewer
2. Tippen Sie auf **Formular ausfüllen**
3. Füllen Sie Textfelder, Kontrollkästchen, Optionsfelder sowie Auswahl- und Listenfelder aus
4. Wechseln Sie bei mehrseitigen Formularen mit den Pfeilen zwischen den Seiten
5. Aktivieren Sie optional **Formularfelder festschreiben**, wenn die Werte danach nicht mehr bearbeitbar sein sollen
6. Tippen Sie auf **Kopie speichern**

Das ausgefüllte Formular wird als neues Dokument im Archiv gespeichert; das Original bleibt unverändert. Alle Verarbeitung erfolgt lokal auf dem Gerät.

Für Formulareingaben mit Devanagari, Arabisch, Chinesisch, Japanisch oder Koreanisch bettet die App bei Bedarf einen passenden Font in die erzeugte PDF-Kopie ein. Dadurch kann die Ausgabedatei insbesondere bei ostasiatischen Schriften deutlich größer werden.

\begin{hinweis}
\textbf{Hinweis:} Dynamische XFA-Formulare werden nicht unterstützt. Bereits digital signierte Formulare sind schreibgeschützt, weil jede Änderung die vorhandene Signatur ungültig machen würde.
\end{hinweis}

## Seitenzahlen

1. **Aktions-Sheet → Bearbeiten → Seitenzahlen hinzufügen**
2. Wählen Sie Position (Kopfzeile / Fußzeile, links / mitte / rechts), Schriftgröße und Startseite
3. Bestätigen Sie – die Seitenzahlen werden in alle Seiten eingebettet

## Wasserzeichen

1. **Aktions-Sheet → Bearbeiten → Textwasserzeichen**
2. Geben Sie den Wasserzeichentext ein (z. B. „ENTWURF" oder „VERTRAULICH")
3. Wählen Sie Schriftgröße, Farbe, Deckkraft und Winkel
4. Bestätigen Sie – das Wasserzeichen wird auf alle Seiten gerendert

## Schwärzen (Redact)

Mit der Schwärz-Funktion werden Inhalte dauerhaft und unwiderruflich aus der PDF entfernt:

1. **Aktions-Sheet → Bearbeiten → Schwärzen**
2. Markieren Sie die zu schwärzenden Bereiche durch Tippen und Ziehen
3. Optional: Wählen Sie, ob nach dem Schwärzen eine neue Textschicht per OCR erstellt werden soll
4. Bestätigen Sie – die geschwärzten Bereiche werden durch schwarze Rechtecke ersetzt und der Originalinhalt vernichtet

\begin{warnung}
\textbf{Achtung:} Geschwärzte Inhalte können nicht wiederhergestellt werden. Prüfen Sie die Auswahl sorgfältig, bevor Sie bestätigen.
\end{warnung}

\newpage

# Exportieren und Konvertieren

## Als Word-Dokument exportieren (.docx)

Exportiert den gespeicherten OCR-Text als bearbeitbares Word-Dokument:

1. **Aktions-Sheet → Export → Als Word exportieren**
2. Die Datei wird unter dem Dokumentnamen mit der Endung `.docx` in den Ordner **Downloads** gespeichert

\begin{hinweis}
\textbf{Hinweis:} Der Word-Export enthält ausschließlich den OCR-Text. Layout, Bilder und Formatierung des Original-PDFs werden nicht übertragen. Für ein layoutgetreues Dokument nutzen Sie stattdessen die Funktion „Durchsuchbar machen".
\end{hinweis}

Bei Mehrfachauswahl: **Massenaktionen → Mehr → Word-Export**. Dokumente ohne OCR-Text werden übersprungen; die App fragt, ob für Dokumente ohne Text zuerst OCR ausgeführt werden soll.

## Graustufen konvertieren

Reduziert die Dateigröße bei farbigen Scans:

1. **Aktions-Sheet → Export → Graustufen**
2. Bestätigen Sie – das Dokument wird in Graustufen konvertiert und überschreibt die aktuelle Version

## PDF komprimieren

Verringert die Dateigröße durch Bildkomprimierung:

1. **Aktions-Sheet → Export → Komprimieren**
2. Wählen Sie die Qualitätsstufe (Standard / Stark)
3. Bestätigen Sie – die komprimierte Version wird gespeichert

## Als JPG exportieren

Exportiert alle Seiten des PDFs als einzelne JPEG-Bilder:

1. **Aktions-Sheet → Dokument → Als JPG exportieren**
2. Die Bilder werden in den Ordner **Downloads** exportiert (eine Datei pro Seite)

## PDF-Metadaten bearbeiten

1. **Aktions-Sheet → Dokument → PDF-Metadaten**
2. Bearbeiten Sie Titel, Autor, Betreff und Stichwörter
3. Bestätigen Sie – die Metadaten werden in die PDF-Datei geschrieben

## SHA-256-Prüfsumme berechnen

1. Öffnen Sie im Archiv das Drei-Punkte-Menü des Dokuments.
2. Wählen Sie unter **Dokument → SHA-256 berechnen**.
3. Die App zeigt die SHA-256-Prüfsumme an; mit **Kopieren** übernehmen Sie den vollständigen Wert in die Zwischenablage.

Die Prüfsumme wird lokal direkt aus den Dateibytes berechnet, ohne die PDF im Viewer zu öffnen. Sie können den Wert anschließend lokal vergleichen oder bei einem Dienst wie VirusTotal suchen. Die App selbst lädt weder die PDF noch den Hash hoch.

\newpage

# Analyse

## QR-Codes scannen

M24 PDF Scanner erkennt QR-Codes und Barcodes innerhalb von PDF-Seiten:

1. **Aktions-Sheet → Analyse → QR-Codes scannen**
2. Die App analysiert alle Seiten und listet gefundene Codes auf
3. Für jeden Code werden angezeigt:
   - Typ (URL, WLAN, Kontakt, Text, …)
   - Rohinhalt
   - Bei URL: Schaltfläche zum Öffnen im Browser
   - Bei WLAN-QR: Schaltfläche zum direkten Verbinden

\begin{tipp}
\textbf{Tipp:} Die QR-Code-Erkennung funktioniert auch in gescannten Dokumenten mit guter Bildqualität.
\end{tipp}

## Visitenkarten

Extrahiert Kontaktdaten aus gescannten Visitenkarten und exportiert sie als vCard:

1. **Aktions-Sheet → Analyse → Visitenkarte scannen**
2. Die App führt OCR auf dem Dokument aus und erkennt strukturierte Kontaktdaten:
   - Name (Vor- und Nachname)
   - Organisation und Position
   - Telefonnummer(n)
   - E-Mail-Adressen
   - Postanschrift
   - Webseite
3. Überprüfen Sie die erkannten Felder und korrigieren Sie bei Bedarf
4. Tippen Sie auf **vCard exportieren** – die Datei wird im Downloads-Ordner gespeichert und kann in der Kontakte-App importiert werden

\newpage

# Sicherheit

## PDF-Passwortschutz

### Passwort setzen

1. **Aktions-Sheet → Sicherheit → PDF schützen**
2. Geben Sie ein Passwort ein und bestätigen Sie es
3. Bestätigen Sie mit **Schützen**
4. Das Dokument ist ab sofort mit diesem Passwort verschlüsselt

\begin{warnung}
\textbf{Achtung:} Passwörter können nicht wiederhergestellt werden. Notieren Sie das Passwort an einem sicheren Ort.
\end{warnung}

### Passwort entfernen

1. **Aktions-Sheet → Sicherheit → Passwort entfernen**
2. Geben Sie das aktuelle Passwort ein
3. Bestätigen Sie – das Dokument ist danach unverschlüsselt

### Passwort ändern (Entsperren + neu schützen)

1. Zunächst **Passwort entfernen** (Schritt oben)
2. Anschließend **PDF schützen** mit dem neuen Passwort

## Nutzungsbeschränkungen

Schränken Sie ein, was andere mit dem PDF tun dürfen (DRM-Permissions):

1. **Aktions-Sheet → Sicherheit → Nutzungsbeschränkungen**
2. Legen Sie fest, ob das Dokument gedruckt, kopiert oder bearbeitet werden darf
3. Setzen Sie ein Besitzer-Passwort
4. Bestätigen Sie – die Einschränkungen werden in das PDF geschrieben

\begin{hinweis}
\textbf{Hinweis:} PDF-Nutzungsbeschränkungen hängen von der Compliance des Ziel-PDF-Readers ab und bieten keinen kryptografisch starken Schutz.
\end{hinweis}

## App-Lock

App-Lock sperrt die Benutzeroberfläche der App beim Wechsel in den Hintergrund:

### App-Lock aktivieren

1. Öffnen Sie **Einstellungen** (Zahnrad-Symbol in der App-Leiste)
2. Abschnitt **Sicherheit** → **App-Sperre**
3. Aktivieren Sie den Schalter
4. Wählen Sie die Authentifizierungsmethode:
   - **Biometrie** (Fingerabdruck, Gesichtserkennung)
   - **Geräte-PIN / Muster / Passwort**

Nach der Aktivierung wird beim nächsten Öffnen der App die Entsperrung angefordert.

\begin{hinweis}
\textbf{Hinweis:} App-Lock ist eine UI-Sperre. Es werden keine Dateien oder die Datenbank verschlüsselt. Die Dokumente sind weiterhin über den Dateimanager zugänglich, sofern der Gerätespeicher nicht anderweitig geschützt ist.
\end{hinweis}

\newpage

# Papierkorb

## Dokumente löschen

Wischen Sie ein Dokument nach links oder tippen Sie auf das **Löschen-Symbol** im Aktions-Sheet. Das Dokument landet im **Papierkorb** – es wird nicht sofort endgültig gelöscht.

Bei Mehrfachauswahl: **Massenaktionen → Mehr → Löschen**. Ein Bestätigungsdialog erscheint immer, bevor Dokumente in den Papierkorb verschoben werden.

## Papierkorb öffnen

Öffnen Sie das **Seitenmenü** → **Papierkorb**. Alle gelöschten Dokumente werden mit verbleibendem Aufbewahrungszeitraum angezeigt.

## Dokumente wiederherstellen

1. Tippen Sie im Papierkorb auf das Dokument
2. **Wiederherstellen** – das Dokument kehrt ins Archiv zurück

## Aufbewahrungsdauer und automatische Bereinigung

Dokumente im Papierkorb werden nach **30 Tagen** automatisch endgültig gelöscht. Die verbleibenden Tage werden beim Dokumenteintrag angezeigt.

## Papierkorb leeren

Tippen Sie auf **Papierkorb leeren** (oben rechts im Papierkorb-Screen), um alle enthaltenen Dokumente sofort endgültig zu löschen.

\begin{warnung}
\textbf{Achtung:} Endgültig gelöschte Dokumente können nicht wiederhergestellt werden.
\end{warnung}

\newpage

# Backup und Wiederherstellung

## Was wird gesichert?

Ein Backup enthält:

- Alle PDF-Dokumente aus dem Archiv
- Dateinamen, Importdatum, Seitenzahl
- Gespeicherten OCR-Text und OCR-Metadaten
- Automatische Tags
- Vorschau-Thumbnails
- Ordnerstruktur

**Nicht** im Backup enthalten:
- Dokumente im Papierkorb
- App-Einstellungen
- Passwörter für geschützte PDFs

## Backup erstellen

1. Öffnen Sie **Einstellungen → Backup & Wiederherstellung**
2. Tippen Sie auf **Backup erstellen**
3. Vergeben Sie ein starkes Passwort (wird zum Verschlüsseln des Backups verwendet)
4. Bestätigen Sie – der Systemdatei-Picker öffnet sich
5. Wählen Sie einen Speicherort (lokaler Speicher, USB, Cloud-Speicher via SAF)
6. Die Backup-Datei wird mit der Endung `.m24backup` gespeichert

\begin{warnung}
\textbf{Achtung:} Das Backup-Passwort kann nicht wiederhergestellt werden. Ohne das richtige Passwort ist das Backup nicht entschlüsselbar. Bewahren Sie das Passwort sicher auf.
\end{warnung}

## Backup wiederherstellen

1. Öffnen Sie **Einstellungen → Backup & Wiederherstellung**
2. Tippen Sie auf **Backup wiederherstellen**
3. Wählen Sie die `.m24backup`-Datei über den Systemdatei-Picker aus
4. Geben Sie das Backup-Passwort ein
5. Bestätigen Sie – die Dokumente werden ins Archiv importiert

\begin{hinweis}
\textbf{Hinweis:} Die Wiederherstellung funktioniert als Zusammenführung (Merge). Bestehende Dokumente werden nicht überschrieben oder gelöscht; bei Namenskollisionen werden neue Dokumente mit einem eindeutigen Suffix benannt (z. B. „Dokument\_2.pdf"). Das erneute Einspielen desselben Backups kann Duplikate erzeugen.
\end{hinweis}

## Technische Details zur Verschlüsselung

- **Verschlüsselungsverfahren:** Tink StreamingAead (AES-GCM-HKDF-4096)
- **Schlüsselableitung:** Argon2id aus dem Benutzerpasswort
- **Inhalt des Klartextheaders:** Nur Format- und KDF-Metadaten sowie der verschlüsselte Schlüsselsatz
- **Integrität:** Authentifizierte Verschlüsselung; zusätzliche Datei-Prüfsummen erkennen Übertragungsfehler

\newpage

# Einstellungen

Öffnen Sie **Einstellungen** über das Zahnrad-Symbol in der App-Leiste.

## Allgemein

| Einstellung | Beschreibung |
|---|---|
| **Erscheinungsbild** | Helles / Dunkles / Systemthema |
| **Sprache** | App-Sprache (folgt der Systemsprache) |

## OCR und Auto-Tagging

| Einstellung | Beschreibung |
|---|---|
| **Standard-OCR-Sprache** | Vorausgewählte Sprache für Texterkennung |
| **Auto-Tagging** | Automatische Inhaltskategorisierung nach OCR (ein/aus) |
| **Rückwirkend taggen** | Alle vorhandenen OCR-Dokumente nachträglich taggen |

## Sicherheit

| Einstellung | Beschreibung |
|---|---|
| **App-Sperre** | UI-Sperre mit Biometrie oder Geräte-PIN aktivieren |

## Backup & Wiederherstellung

| Einstellung | Beschreibung |
|---|---|
| **Backup erstellen** | Verschlüsseltes Backup aller Dokumente erstellen |
| **Backup wiederherstellen** | Backup-Datei importieren und Dokumente wiederherstellen |

## Info und Feedback

| Eintrag | Beschreibung |
|---|---|
| **Über die App** | Version, Bibliotheken, Datenschutzrichtlinie, Quellcode-Link |
| **Hilfe** | Integrierte Hilfe mit allen Funktionen |
| **Bewertung** | App im Play Store bewerten |

\newpage

# Shortcuts, Quick-Settings-Kachel und Widget

## App-Shortcuts

Halten Sie das App-Icon auf dem Startbildschirm oder im App-Drawer länger gedrückt. Es erscheint ein Kontextmenü mit:

- **Neuer Scan** – öffnet die Kamera direkt ohne Umweg über das Archiv

Shortcuts können auch als eigene Icons auf den Startbildschirm gezogen werden.

## Quick-Settings-Kachel

Die Quick-Settings-Kachel ermöglicht einen Scan-Start direkt aus der Benachrichtigungsleiste:

1. Wischen Sie die Statusleiste zweimal nach unten, um alle Kacheln anzuzeigen
2. Tippen Sie auf **Bearbeiten** (Stift-Symbol)
3. Suchen Sie die Kachel **„M24 PDF Scanner"** und ziehen Sie sie in den aktiven Bereich
4. Bestätigen Sie

Ein Tap auf die Kachel startet sofort die Scan-Oberfläche.

## Startbildschirm-Widget

Das Widget platziert einen direkten Scan-Button auf dem Startbildschirm:

1. Halten Sie auf dem Startbildschirm eine leere Stelle lang gedrückt
2. Wählen Sie **Widgets**
3. Suchen Sie **M24 PDF Scanner** und wählen Sie das Scan-Widget
4. Platzieren Sie es auf dem Startbildschirm

\newpage

# Häufige Fragen

## Warum erscheint nach dem Scan kein Text in der Suche?

Texterkennung (OCR) wird nicht automatisch nach jedem Scan ausgeführt. Starten Sie die OCR manuell über **Aktions-Sheet → Analyse → Text extrahieren**.

## Warum ist die Übersetzung langsam oder schlägt fehl?

Beim ersten Einsatz einer neuen Sprache muss das Sprachmodell (~15–30 MB) heruntergeladen werden. Stellen Sie sicher, dass eine Internetverbindung besteht. Nachfolgende Übersetzungen in dieselbe Sprache laufen vollständig offline.

## Kann ich ein vergessenes Backup-Passwort wiederherstellen?

Nein. Das Backup-Passwort wird weder gespeichert noch kann es zurückgesetzt werden. Ohne das richtige Passwort ist das Backup nicht entschlüsselbar.

## Warum sind manche OCR-Sprachen nicht sofort verfügbar?

Für Hindi, Chinesisch, Japanisch und Koreanisch werden On-Demand-Modelle von Google Play Services heruntergeladen. Der Download erfolgt bei der ersten manuellen Auswahl der Sprache und erfordert eine Internetverbindung.

## Werden meine Dokumente in der Cloud gespeichert?

Nein. Alle Dokumente verbleiben im internen App-Speicher des Geräts. Es gibt keine Server-Verbindung für Dokumente. Nur ML Kit-Modelle werden über das Netzwerk geladen.

## Wie entferne ich ein Dokument dauerhaft?

Tippen Sie auf **Löschen** → das Dokument landet im Papierkorb → öffnen Sie den Papierkorb und wählen Sie **Endgültig löschen** oder warten Sie, bis die 30-tägige Aufbewahrungsfrist abläuft.

## Was passiert, wenn ich die App deinstalliere?

Alle Dokumente im internen App-Speicher werden beim Deinstallieren der App gelöscht. Erstellen Sie vorher ein Backup, um Ihre Dokumente zu sichern.

---

\vspace{2em}
\begin{center}
\textcolor{mittelgrau}{\small M24 PDF Scanner · Version 2.1 · Juni 2026}\\
\textcolor{mittelgrau}{\small Datenschutzrichtlinie und Quellcode: \texttt{https://github.com/meuse24/PDF\_Scanner}}
\end{center}
