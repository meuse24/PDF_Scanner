# Implementierungsplan: bestehenden JPG-Export auf Unterordner umstellen

## Entscheidung

Es bleibt genau eine Benutzeraktion **„Als JPG-Ordner exportieren“**. Der bisherige
flache Export wird vollständig durch den Unterordner-Export ersetzt:

```text
Vorher:
Downloads/Rechnung_p1.jpg
Downloads/Rechnung_p2.jpg

Nachher:
Downloads/Rechnung/page_1.jpg
Downloads/Rechnung/page_2.jpg
```

Zwei parallele JPG-Exporte wären fachlich redundant, würden die Aktionsliste
verlängern und dauerhaft doppelte Use Cases, Meldungen, Übersetzungen und Tests
erfordern.

## Architektur

```text
DocumentEditSheet
  -> ScanAction.ExportAsJpg
  -> HomeActionDispatcher
  -> HomeViewModel.exportAsJpg()
  -> ExportAsJpgUseCase
       -> sanitizeDownloadFolderName()
       -> PdfPageJpgRenderer
       -> DownloadsStorage.writeDownloadToSubfolder()
            -> AndroidDownloadsStorage / MediaStore RELATIVE_PATH
```

Die bestehende Aktion, der bestehende Dispatcher-Pfad und der bestehende
Use-Case-Name bleiben erhalten. Nur das Exportverhalten und sein Ergebnis werden
gezielt geändert.

## Kritische technische Festlegungen

1. `DownloadsStorage` erhält eine explizite Operation
   `writeDownloadToSubfolder(...)`. Bestehende Root-Downloads und deren Fakes
   bleiben unverändert.
2. Jede Seite heißt konsistent `page_<n>.jpg`, auch bei einseitigen PDFs
   (`page_1.jpg`).
3. Die vorhandene `sanitizeFilename(...)`-Logik wird durch
   `sanitizeDownloadFolderName(...)` wiederverwendet. Dadurch werden ungültige
   Zeichen, Steuerzeichen, leere Namen und abschließende Punkte behandelt.
4. Der Android-Adapter validiert zusätzlich, dass der Unterordner nur ein
   einzelnes Pfadsegment ist.
5. Der Use Case liefert `JpgExportResult(folderName, pageCount)`, damit das
   ViewModel eine genaue, lokalisierte Zielmeldung anzeigen kann.
6. Bei einem Fehler werden alle bereits durch den aktuellen Export erzeugten
   MediaStore-Einträge gelöscht.
7. Bestehende gleichnamige Dateien werden nicht überschrieben oder gelöscht.
   MediaStore darf neue Namen kollisionsfrei vergeben.

## Umsetzungsschritte

1. Domain-Gateway um `writeDownloadToSubfolder(...)` erweitern.
2. `AndroidDownloadsStorage` mit
   `RELATIVE_PATH = "Download/<bereinigter Name>"` erweitern.
3. `sanitizeDownloadFolderName(...)` in `FilenameUtils.kt` ergänzen.
4. `ExportAsJpgUseCase` vom flachen Export auf den Unterordner-Export umstellen.
5. `HomeViewModel.exportAsJpg()` auf das strukturierte Ergebnis und neue
   Erfolgs-/Fehlermeldungen umstellen.
6. Keine zweite `ScanAction`, kein zweiter Use Case und kein zweiter
   Dispatcher-Callback einführen.
7. Hilfe und Info um das neue Verhalten ergänzen; neue Texte in allen zehn
   Locales bereitstellen.
8. Bestehende JPG-Unit-Tests auf Ordnerbereinigung, Seitennamen, fehlende Quelle
   und Rollback migrieren.
9. Bestehende MediaStore-Instrumentierungstests auf `RELATIVE_PATH` und
   dekodierbare `page_<n>.jpg`-Dateien migrieren.
10. Unit-Tests, Debug-Kompilierung, Android-Test-Kompilierung und Lint ausführen.

## Akzeptanzkriterien

- In der UI existiert nur eine JPG-Exportaktion.
- Es gibt nur einen JPG-Use-Case und einen Dispatcher-Pfad.
- Der Export erzeugt ausschließlich
  `Downloads/<bereinigter Dokumentname>/page_1.jpg` usw.
- Einseitige PDFs erzeugen `page_1.jpg`.
- Jede erzeugte Datei ist ein dekodierbares JPEG.
- Ein fehlgeschlagener Lauf entfernt seine bereits geschriebenen Seiten.
- Verschlüsselte PDFs zeigen die Aktion weiterhin nicht an.
- Erfolgs-/Fehlermeldungen, Hilfe und Info sind in allen zehn Locales vorhanden.
- Unit-Tests, Hauptcode, Android-Testcode und Lint sind grün.
