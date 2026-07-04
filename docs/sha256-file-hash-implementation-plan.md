# Implementierungsplan: SHA-256-Hash einer PDF-Datei

## Ziel

Für ein Dokument im Archiv soll der SHA-256-Hash der zugrunde liegenden PDF-Datei lokal berechnet, angezeigt und in die Zwischenablage kopiert werden können. Die PDF wird dabei nicht im Viewer oder mit einem PDF-Parser geöffnet; es werden ausschließlich ihre Rohbytes als Stream gelesen.

Der ausgegebene Wert ist ein 64-stelliger, kleingeschriebener Hex-String. Dieses Format kann direkt für die Suche bei Diensten wie VirusTotal oder für lokale Vergleiche verwendet werden.

## Funktionsumfang

- Einstieg über das Drei-Punkte-Menü eines Dokuments im Archiv.
- Neue Aktion **„SHA-256 berechnen“** im Abschnitt **„Dokument“** des bestehenden `DocumentEditSheet`.
- Während der Berechnung erscheint sofort ein eindeutiger Ladezustand; die Berechnung selbst blockiert den Main-Thread nicht.
- Nach erfolgreicher Berechnung erscheint ein Dialog mit:
  - Dateiname,
  - Algorithmus `SHA-256`,
  - vollständig sicht- und auswählbarem Hash,
  - Schaltflächen **„Kopieren“** und **„Schließen“**.
- **„Kopieren“** schreibt ausschließlich den 64-stelligen Hash in die Android-Zwischenablage und bestätigt dies per Snackbar.
- Fehlende oder nicht lesbare Dateien erzeugen eine verständliche Fehlermeldung über den bestehenden Home-Fehlerfluss.
- Die Aktion ist auch für passwortgeschützte PDFs verfügbar, da der Hash über die verschlüsselten Rohbytes gebildet wird.
- Es gibt keinen Upload, keinen VirusTotal-API-Aufruf und keinen Netzwerkzugriff.

Nicht Bestandteil dieses ersten Umfangs:

- Hash-Berechnung für eine Mehrfachauswahl.
- Persistieren oder Cachen des Hashes in Room.
- Automatischer VirusTotal-Aufruf.
- Weitere Algorithmen wie MD5, SHA-1 oder SHA-512.
- Hash-Aktion im bereits geöffneten Viewer. Das gemeinsam verwendete Aktions-Sheet erhält dafür einen Sichtbarkeitsparameter; der Viewer blendet die neue Aktion zunächst aus.

## Technische Leitentscheidungen

### On-Demand statt Datenbankfeld

Der Hash wird bei jedem Aufruf neu aus der aktuellen Datei berechnet. Dadurch kann nach Annotieren, Komprimieren, Signieren, Umbenennen oder anderen Dateioperationen kein veralteter Wert aus der Datenbank angezeigt werden. Eine Room-Migration ist nicht erforderlich.

### Streaming statt `readBytes()`

Die Datei wird mit einem gepufferten `InputStream` in Blöcken gelesen und direkt in `MessageDigest.getInstance("SHA-256")` eingespeist. Der Speicherbedarf bleibt damit unabhängig von der PDF-Größe konstant.

Die Schleife prüft regelmäßig auf Coroutine-Abbruch, damit eine laufende Berechnung beim Verlassen des Screens oder beim Beenden des ViewModels sauber abgebrochen werden kann. `CancellationException` wird nicht in einen Anwendungsfehler umgewandelt.

### Klare Schichtentrennung

- Domain: reine Hash-Berechnung und Dateivalidierung.
- ViewModel: Start, Ladezustand, Ergebnis und Fehlerzustand.
- Compose-UI: Darstellung und Android-Zwischenablage.

Die Domain-Schicht erhält keinen Android- oder Compose-Typ. Für den UseCase sind `java.io.File`, `java.security.MessageDigest` und der vorhandene `DispatcherProvider` ausreichend.

## Umsetzungsschritte

### 1. Domain-UseCase für SHA-256

Neue Datei:

`app/src/main/java/info/meuse24/pdf_scanner/domain/usecase/CalculateSha256UseCase.kt`

Aufgaben:

- Klasse mit `@Inject constructor` anlegen.
- `DispatcherProvider` injizieren und die gesamte Dateioperation mit `withContext(dispatcherProvider.io)` ausführen.
- Als Eingabe ein `Document` verwenden und dessen `filepath` auflösen.
- Vor dem Lesen prüfen:
  - Pfad existiert,
  - Pfad ist eine reguläre Datei,
  - Datei ist lesbar.
- Datei gepuffert und blockweise lesen.
- Pro gelesenem Block den Digest aktualisieren und Coroutine-Abbruch berücksichtigen.
- Digest als kleingeschriebenen Hex-String mit exakt zwei Zeichen pro Byte ausgeben.
- Keine PDFBox-/Renderer-/ContentResolver-API verwenden.
- Erwartbare I/O-Probleme als konkrete Exception nach oben geben; die Übersetzung in UI-Texte erfolgt im ViewModel.

Eine zusätzliche Gateway- oder Hilt-Modul-Bindung ist nicht nötig, da Hilt den konkret injizierten UseCase über dessen `@Inject`-Konstruktor erstellen kann.

### 2. Eigenen UI-Zustand modellieren

Datei:

`app/src/main/java/info/meuse24/pdf_scanner/ui/home/HomeUiState.kt`

Einen eindeutigen Hash-Zustand ergänzen als versiegeltes Interface mit dem Namen `HomeHashUiState`:

- `Idle`
- `Calculating(filename)`
- `Success(filename, sha256)`

Der Zustand wird als eigener `StateFlow` im `HomeViewModel` angeboten. Dadurch bleibt die Hash-Funktion unabhängig von OCR-, Import- und Edit-Ladezuständen und ein Ergebnis kann nicht versehentlich als allgemeine Erfolgsmeldung verloren gehen.

### 3. HomeViewModel anbinden

Datei:

`app/src/main/java/info/meuse24/pdf_scanner/ui/home/HomeViewModel.kt`

Änderungen:

- `CalculateSha256UseCase` injizieren.
- `_hashUiState` als `MutableStateFlow` und öffentliches read-only `StateFlow` ergänzen.
- Methode `calculateSha256(document: Document)` hinzufügen:
  - parallelen zweiten Start verhindern,
  - Zustand auf `Calculating` setzen,
  - UseCase in `viewModelScope` aufrufen,
  - bei Erfolg `Success` mit Dateiname und Hash setzen,
  - `CancellationException` weiterwerfen,
  - bei fehlender/nicht lesbarer Datei den Zustand auf `Idle` zurücksetzen und über `_error` einen lokalisierten Fehler melden.
- Methode `dismissHashResult()` ergänzen, die auf `Idle` zurücksetzt.

Der ViewModel-Zustand hält keinen Android-Clipboard-Typ. Das Kopieren bleibt eine UI-Nebenwirkung.

### 4. Dokumentaktion erweitern

Dateien:

- `app/src/main/java/info/meuse24/pdf_scanner/ui/components/DocumentEditSheet.kt`
- `app/src/main/java/info/meuse24/pdf_scanner/ui/home/HomeActionDispatcher.kt`
- `app/src/main/java/info/meuse24/pdf_scanner/ui/home/HomeScreen.kt`
- `app/src/main/java/info/meuse24/pdf_scanner/ui/viewer/PdfViewerScreen.kt`

Änderungen:

- `ScanAction.CalculateSha256` ergänzen.
- Im Abschnitt **„Dokument“** einen `SheetItem` mit passendem Symbol ergänzen. Bevorzugt wird `Fingerprint`; falls dessen Extended-Icon-Quelle im Build nicht verfügbar ist, wird das bereits vorhandene `FindInPage` ohne zusätzliche Abhängigkeit verwendet.
- `DocumentEditSheet` um `showHashAction: Boolean = true` erweitern.
- Die Hash-Aktion unabhängig von `record.isEncrypted` aktiv lassen.
- `HomeScanActionNavigator` um `onCalculateSha256: (Document) -> Unit` ergänzen.
- In `dispatchHomeScanAction` den neuen Zweig `ScanAction.CalculateSha256 -> navigator.onCalculateSha256(record)` eintragen — der exhaustive `when` kompiliert sonst nicht.
- Im `HomeScreen` den Callback mit `viewModel::calculateSha256` verbinden.
- Im Viewer `showHashAction = false` setzen und den neuen `ScanAction` im exhaustiven `when` explizit als nicht erreichbaren No-op-Zweig aufnehmen.
- Falls `Fingerprint` nicht zu den Material-Core-Icons gehört, anschließend `tools/generate-material-icon-subset.ps1` ausführen und die generierte lokale Icon-Teilmenge mit committen.

Damit ist die Funktion direkt aus dem Archiv erreichbar, ohne zuerst die PDF zu öffnen.

### 5. Ergebnisdialog und Zwischenablage

Neue Datei:

`app/src/main/java/info/meuse24/pdf_scanner/ui/home/components/HomeHashDialog.kt`

UI-Verhalten:

- Für `Calculating` den bestehenden Stil von `HomeLoadingDialog` mit dem Text **„SHA-256 wird berechnet …“** verwenden.
- Für `Success` einen Material-3-`AlertDialog` anzeigen.
- Hash in einer `SelectionContainer` darstellen.
- Für bessere Lesbarkeit eine Monospace-Schrift und einen kontrollierten Zeilenumbruch verwenden; kopiert wird trotzdem der unveränderte Hash ohne Leerzeichen oder Zeilenumbrüche.
- **„Kopieren“** als primäre Aktion und **„Schließen“** als sekundäre Aktion anbieten.
- `HomeHashDialog` erhält einen reinen `onCopy: (String) -> Unit`-Callback. `HomeScreen` führt die Android-Nebenwirkung in seinem länger lebenden Coroutine-Scope aus, damit das Entfernen des Dialogs eine bereits gestartete Clipboard-Operation nicht abbricht.
- Kopieren via `clipboard.setClipEntry(ClipData.newPlainText("", hash).toClipEntry())` — konsistent mit dem bereits in `HomeImportOverlays.kt` verwendeten `LocalClipboard`-Pattern.
- Beim Kopieren den Dialog sofort schließen und nach erfolgreichem Clipboard-Aufruf die lokalisierte Snackbar **„SHA-256-Hash kopiert”** anzeigen.
- Das Schließen des Dialogs ruft `dismissHashResult()` auf.

Der Dialog zeigt den Hash vollständig an; Ellipsis ist für diesen Wert nicht zulässig.

### 6. Lokalisierung

Neue Feature-Dateien:

`app/src/main/res/values*/strings_hash.xml`

Die Strings werden gemäß Projektregel in allen zehn vorhandenen Locales ergänzt:

- Default/Englisch
- Deutsch
- Spanisch
- Französisch
- Portugiesisch
- Chinesisch (`zh-rCN`)
- Arabisch
- Japanisch
- Russisch
- Hindi

Benötigte Inhalte:

- Aktionsbezeichnung,
- Dialogtitel,
- Algorithmusbezeichnung bzw. Label,
- Ladehinweis,
- Kopieren,
- Kopierbestätigung,
- Fehler „Datei nicht gefunden“,
- Fehler „Datei konnte nicht gelesen werden“.

Allgemeine Strings wie **„Schließen“** werden als `action_close` in den gemeinsamen `strings.xml`-Dateien aller Locales gepflegt und wiederverwendet. Es werden keine Literal-UI-Strings in Kotlin eingeführt.

### 7. Tests

Neue Datei:

`app/src/test/java/info/meuse24/pdf_scanner/domain/usecase/CalculateSha256UseCaseTest.kt`

Abzudeckende Fälle:

- Bekannter Testvektor, z. B. `abc` →
  `ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad`.
- Leere Datei liefert `e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855`.
- Binärdatei, die größer als ein Lesepuffer ist, wird vollständig und korrekt gehasht.
- Ergebnis besteht aus genau 64 kleingeschriebenen Hex-Zeichen.
- Fehlende Datei schlägt kontrolliert fehl.
- Ein beliebiger Nicht-PDF-Byteinhalt ist hashbar; damit ist abgesichert, dass kein PDF-Parser beteiligt ist.

Zu erweiternde Datei:

`app/src/test/java/info/meuse24/pdf_scanner/ui/home/HomeViewModelTest.kt`

Abzudeckende Fälle:

- `Idle` → `Calculating` → `Success`.
- Erfolgszustand enthält Dateiname und unveränderten Hash.
- `dismissHashResult()` setzt den Zustand auf `Idle`.
- Fehlende oder nicht lesbare Datei setzt den Hash-Zustand zurück und veröffentlicht die richtige Fehlermeldung.
- Ein zweiter Aufruf während `Calculating` startet keine konkurrierende Berechnung.

Optional sinnvoll:

- Kleiner Unit-Test für `dispatchHomeScanAction`, der belegt, dass `CalculateSha256` das vollständige `Document` an den richtigen Callback weitergibt.
- Compose-Test oder manuelle Accessibility-Prüfung für vollständige Hash-Anzeige, auswählbaren Text und Kopierbutton.

### 8. Dokumentation

Dateien:

- `app/src/main/java/info/meuse24/pdf_scanner/ui/help/HelpScreen.kt`
- `app/src/main/java/info/meuse24/pdf_scanner/ui/info/InfoScreen.kt`
- `docs/Bedienungsanleitung.md`

In der In-App-Hilfe wird die neue Aktion im Abschnitt **„Dokument“** mit demselben Symbol wie im Aktions-Sheet ergänzt. Der Hilfetext erklärt die lokale Rohbyte-Berechnung, das Kopieren sowie die Eignung zum externen Vergleich, ohne einen Upload durch die App zu suggerieren.

Im Info-Bereich wird die lokale SHA-256-Prüfsummenfunktion in der Funktionsübersicht ergänzt. Der zugehörige Text wird wie alle Hash-Strings in allen zehn Locales bereitgestellt.

Unter den Dokumentaktionen kurz ergänzen:

1. Drei-Punkte-Menü des Dokuments öffnen.
2. **„SHA-256 berechnen“** auswählen.
3. Hash anzeigen oder kopieren.
4. Klarstellen, dass die Berechnung lokal erfolgt und kein Upload stattfindet.

Eine Änderung der Datenschutzerklärung ist voraussichtlich nicht nötig, weil weder Netzwerkzugriff noch neue persistierte Daten hinzukommen. Dies wird beim Implementieren noch einmal gegen `PRIVACY.md` und `docs/privacy-policy.html` geprüft.

## Verifikation

Nach der Implementierung:

```powershell
.\gradlew.bat testDebugUnitTest --tests "info.meuse24.pdf_scanner.domain.usecase.CalculateSha256UseCaseTest" --tests "info.meuse24.pdf_scanner.ui.home.HomeViewModelTest"
.\gradlew.bat :app:compileDebugKotlin
.\gradlew.bat :app:lintDebug
```

Manuelle Prüfung auf einem Gerät oder Emulator:

1. Kleine PDF im Archiv hashen und Wert mit `Get-FileHash -Algorithm SHA256 <datei>` vergleichen.
2. Große PDF hashen und beobachten, dass die UI responsiv und der Speicherverbrauch stabil bleibt.
3. Hash kopieren und sicherstellen, dass exakt 64 Zeichen ohne Präfix, Leerzeichen oder Zeilenumbruch eingefügt werden.
4. Verschlüsselte PDF hashen.
5. PDF bearbeiten, erneut hashen und prüfen, dass sich der Wert ändert.
6. Datei testweise entfernen und die lokalisierte Fehlermeldung prüfen.
7. Hochformat, Querformat, Dark Mode, große Schrift und RTL-Darstellung prüfen.

## Akzeptanzkriterien

- Der SHA-256-Hash ist aus dem Archiv erreichbar, ohne die PDF im Viewer zu öffnen.
- Die Berechnung arbeitet vollständig lokal und ohne PDF-Parsing.
- Der Hash entspricht bytegenau dem Ergebnis eines externen SHA-256-Werkzeugs.
- Große Dateien werden gestreamt und nicht vollständig in den Arbeitsspeicher geladen.
- Das Ergebnis ist vollständig sichtbar, auswählbar und mit einem Klick kopierbar.
- Kopiert wird ein 64-stelliger kleingeschriebener Hex-String.
- Verschlüsselte PDFs können gehasht werden.
- Fehler werden verständlich angezeigt; Abbrüche werden nicht fälschlich als Fehler gemeldet.
- Es gibt keine Room-Migration, keine Hash-Persistenz und keinen Netzwerkzugriff.
- Alle neuen Texte sind in allen zehn Projektsprachen vorhanden.
- Die neuen Unit-Tests sowie Kotlin-Kompilierung und Lint laufen erfolgreich durch.
