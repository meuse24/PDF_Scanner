# Umsetzungsplan: Verschluesseltes Backup

## Fortschritt

Stand: 2026-06-04

Umgesetzt:

- Versionierte Backup-Domain-Modelle angelegt: `BackupArchiveHeader`, KDF-/KeyWrap-/Payload-Header, `BackupManifest`, Folder-/Document-/File-/OCR-Eintraege, Export-/Importoptionen, Fortschritt und typisierte Fehler.
- Clean-Architecture-Ports angelegt: `BackupArchiveCodec` und `BackupKeyDeriver` in der Domain-Gateway-Schicht. Die Domain bleibt frei von Tink, Argon2Kt, Room und Android-Framework-APIs.
- Dependencies aufgenommen: Tink Android, Argon2Kt und Kotlinx Serialization inklusive Serialization-Plugin.
- `TinkBackupArchiveCodec` implementiert:
  - schreibt `M24PDFBACKUP` Magic Bytes, Big-Endian-Headerlaenge, klaren JSON-Header und danach den verschluesselten Streaming-Payload,
  - erzeugt pro Backup ein zufaelliges Tink-StreamingAead-Keyset mit `AES256_GCM_HKDF_1MB`,
  - wrappt das serialisierte Tink-Keyset mit AES-256-GCM,
  - bindet Magic Bytes und SHA-256 des finalen Header-JSON als Associated Data an den Streaming-Payload,
  - liest und validiert Header, App-ID, Formatversion und Algorithmen,
  - mappt falsche Passphrase, korruptes Archiv und nicht unterstuetztes Format auf typisierte Fehler.
- `Argon2BackupKeyDeriver` als produktiver KDF-Kandidat hinter `BackupKeyDeriver` angelegt. Passphrasen werden als `CharArray` angenommen; temporaere UTF-8-Bytes und KEK-Bytes werden nach Nutzung genullt.
- Hilt-Bindings fuer `BackupArchiveCodec` und `BackupKeyDeriver` ergaenzt.
- Reviewer-Korrekturen eingearbeitet:
  - KDF-Parameter aus dem Header werden vor Argon2 strikt begrenzt: Salt-Laenge, Memory-Kosten, Iterationen, Parallelism, KEK-Ausgabegroesse, KeyWrap-Nonce und Payload-AAD-Version.
  - OCR-Daten sind nicht mehr doppelt im Dokumentmanifest modelliert. `BackupDocumentEntry` enthaelt nur eine `ocr`-Dateireferenz; `extractedText`, `ocrPageTextJson`, `ocrConfidence` und `ocrLanguage` liegen als Source-of-Truth in `ocr/<backupDocumentId>.json`.
- Auch OCR-Qualitaet und OCR-Sprache wurden aus dem Dokumentmanifest entfernt, damit es fuer OCR-Daten keine zweite Quelle gibt.
- `CancellationException` wird im Codec nicht mehr in `BackupArchiveException` gewrappt.
- Stream-Ownership und EOF-Anforderung sind am `BackupArchiveCodec` dokumentiert.
- Meilenstein 2 weitergefuehrt:
  - Domain-Snapshot-Modelle fuer exportierbare Backup-Daten ergaenzt: `BackupExportData`, `BackupExportFolder`, `BackupExportDocument`.
  - Ports ergaenzt: `BackupDataSource` und `BackupPayloadWriter`.
  - `RoomBackupDataSource` implementiert. Sie liest aktive Dokumente, optional Papierkorb-Dokumente und Ordner als einmaligen Snapshot ueber bestehende DAO-Flows, ohne neue DAO-Testoberflaeche zu erzwingen.
  - `BackupZipPayloadWriter` implementiert. Er schreibt `manifest.json`, `documents/<id>.pdf`, optionale `thumbnails/<id>.jpg` und optionale `ocr/<id>.json`.
  - PDF- und Thumbnail-Dateien werden als `ZipEntry.STORED` geschrieben; Groesse, CRC und SHA-256 werden vorab berechnet.
  - Fehlende PDF-Dateien brechen den Export mit `BackupFailure.MissingDocumentFile` ab. Fehlende optionale Thumbnails werden aus dem Manifest ausgelassen.
  - Manifest enthaelt keine absoluten lokalen Dateipfade und keinen OCR-Volltext.
  - OCR-JSON-Bytes werden nicht fuer alle Dokumente gleichzeitig gehalten. Der Writer berechnet fuer das Manifest pro Dokument die OCR-JSON-Statistik und serialisiert beim ZIP-Schreiben pro Dokument erneut.
  - Bekannte Tradeoffs: STORED-Dateien werden fuer Checksumme/CRC und Inhalt zweimal gelesen; `isSearchable` bleibt die Eigenschaft der PDF-Datei. Wenn OCR-Text beim Export ausgeschlossen wird, muss Restore/UI klar behandeln, dass der lokale FTS-Index ohne OCR-Payload nicht wieder aufgebaut werden kann.
- Meilenstein 3 begonnen:
  - Domain-Modell `BackupStagingResult` und Port `BackupPayloadStagingReader` ergaenzt.
  - `BackupZipStagingReader` implementiert. Er liest den entschluesselten ZIP-Payload in ein dediziertes Staging-Verzeichnis und gibt Manifest + staged Files zurueck.
  - Validierungen im Staging-Reader:
    - `manifest.json` ist Pflicht und darf nur einmal vorkommen,
    - erlaubte ZIP-Pfade sind nur `manifest.json`, `documents/`, `thumbnails/`, `ocr/`,
    - absolute Pfade, Backslashes, `.`/`..`, leere Segmente und Windows-Drive-Pfade werden blockiert,
    - Entry-Count, Manifestgroesse, Einzeldateigroesse und dekomprimierte Gesamtgroesse sind begrenzt,
    - `manifestVersion` und `sourceDatabaseVersion` duerfen nicht aus der Zukunft sein,
    - alle im Manifest referenzierten PDF-/Thumbnail-/OCR-Dateien muessen vorhanden sein,
    - nicht referenzierte Dateien werden abgelehnt,
    - Groesse und SHA-256 jeder referenzierten Datei werden gegen das Manifest geprueft,
    - Staging wird bei Fehlern geloescht,
    - IO-Fehler beim Entpacken oder Schreiben ins Staging werden als `BackupFailure.Io` gemappt, nicht als korruptes Archiv.
- Meilenstein 4 begonnen:
  - Domain-Port `BackupRestoreWriter` und Ergebnis `BackupRestoreSummary` ergaenzt.
  - `RoomBackupRestoreStore` kapselt die Room-Transaktion und stellt dem Restore-Writer nur die benoetigten DAO-Operationen bereit.
  - `RoomBackupRestoreWriter` implementiert Merge-Restore aus dem Staging ins lokale Archiv:
    - Ordner werden case-insensitive per Name wiederverwendet; fehlende Ordner werden mit `colorArgb` und `createdAt` neu angelegt,
    - Zielnamen werden mit `domain/common/resolveUniqueFilename` erzeugt; PDF und Thumbnail nutzen denselben sicheren Basisnamen,
    - Backup-Dateinamen werden vor der Zielpfadbildung normalisiert, damit Manifest-Filenames keine Pfadsegmente einschleusen koennen,
    - PDFs und vorhandene Backup-Thumbnails werden ins Scan-Verzeichnis kopiert,
    - fehlende Thumbnails werden best-effort ueber `PdfRenderingOps.generateThumbnail` regeneriert,
    - OCR-JSON wird erst dokumentweise beim DAO-Insert aus dem Staging gelesen, damit Restore nicht den OCR-Text aller Dokumente gleichzeitig im Speicher haelt,
    - `extractedText`, `ocrPageTextJson`, `ocrConfidence` und `ocrLanguage` werden im `ScanRecord` gesetzt,
    - Dokumente werden ueber `ScanDao.insert` geschrieben, damit Room die FTS-Trigger fuer `scan_records_fts` ausloest,
    - bei DB-/Dateifehlern oder Cancel werden bereits kopierte Dateien kompensierend geloescht,
    - das Staging-Verzeichnis wird im `finally` auch im Erfolgspfad geloescht.
  - `resolveUniqueFilename` kann jetzt mehrere kollidierende Erweiterungen pruefen, damit ein vorhandenes `.jpg` denselben Basisnamen ebenfalls blockiert.
  - `FolderDao.getFoldersOnce()` als dedizierte One-Shot-Query ergaenzt. Restore verwendet innerhalb der Room-Transaktion keine observable Flow-Query mehr; Export-Snapshot nutzt ebenfalls die One-Shot-Abfrage.
  - Merge-Semantik: namengleiche Ordner im Backup werden case-insensitive zusammengefuehrt, genau wie Backup-Ordner mit bereits vorhandenen lokalen Ordnern.
- Unit-Test `TinkBackupArchiveCodecTest` angelegt:
  - Round-trip fuer gestreamten Payload,
  - falsches Passwort,
  - abgeschnittenes Archiv,
  - ungueltige Magic Bytes.
  - manipulierten Header,
  - gefaehrliche KDF-Parameter ohne Key-Derivation,
  - manipulierten Payload,
  - Multi-MiB-Streaming-Round-trip.
- Unit-Test `BackupZipPayloadWriterTest` angelegt:
  - Manifest + PDF + Thumbnail + OCR-JSON werden geschrieben,
  - lokale absolute Pfade und OCR-Volltext stehen nicht im Manifest,
  - Checksummen und STORED-Eintraege werden geprueft,
  - fehlende optionale Thumbnails werden ausgelassen,
  - fehlende PDFs liefern einen typisierten Fehler.
- Unit-Test `BackupZipStagingReaderTest` angelegt:
  - gueltiger Payload wird ins Staging geschrieben,
  - Pfad-Traversal wird abgelehnt und Staging geloescht,
  - Checksum-Manipulation wird abgelehnt und Staging geloescht,
  - nicht referenzierte Dateien werden abgelehnt,
  - IO-Fehler beim Schreiben ins Staging liefern `BackupFailure.Io`,
  - zukuenftige Datenbankversion wird als Future-Version abgelehnt.
- Unit-Test `RoomBackupRestoreWriterTest` angelegt:
  - Re-Import verwendet vorhandene Ordner per Name wieder,
  - PDF und Thumbnail bekommen einen gemeinsamen eindeutigen Ziel-Basisnamen,
  - OCR-Felder landen im eingefuegten `ScanRecord` und damit im DAO-/FTS-Pfad,
  - fehlende Thumbnails werden regeneriert,
  - DB-Fehler loeschen kopierte Dateien und Staging.

Verifiziert:

- `./gradlew.bat :app:compileDebugKotlin`
- `./gradlew.bat :app:testDebugUnitTest --tests info.meuse24.pdf_scanner.data.backup.TinkBackupArchiveCodecTest`
- `./gradlew.bat :app:testDebugUnitTest --tests info.meuse24.pdf_scanner.data.backup.BackupZipPayloadWriterTest`
- `./gradlew.bat :app:testDebugUnitTest --tests info.meuse24.pdf_scanner.data.backup.BackupZipStagingReaderTest`
- `./gradlew.bat :app:testDebugUnitTest --tests info.meuse24.pdf_scanner.data.backup.RoomBackupRestoreWriterTest --no-configuration-cache`
- `./gradlew.bat :app:testDebugUnitTest --tests info.meuse24.pdf_scanner.data.backup.* --no-configuration-cache`
- `./gradlew.bat :app:testDebugUnitTest --no-configuration-cache`
- `./gradlew.bat :app:lint --no-configuration-cache`
- `./gradlew.bat :app:assembleDebug --no-configuration-cache`
- `./gradlew.bat :app:testDebugUnitTest --tests "info.meuse24.pdf_scanner.data.backup.BackupZipStagingReaderTest" --tests "info.meuse24.pdf_scanner.data.backup.RoomBackupRestoreWriterTest" --no-configuration-cache`
- `./gradlew.bat :app:assembleRelease --no-configuration-cache`

Bekannter Verifikations-Blocker:

- `./gradlew.bat :app:compileDebugAndroidTestKotlin --no-configuration-cache` scheitert aktuell an bestehenden aelteren, nicht backupbezogenen Instrumentation-Tests/Fakes. Der neue Backup-Instrumentation-Test ist angelegt, kann aber erst laufen, wenn dieser Source-Set modernisiert ist.

Hinweis:

- Debug- und Release-Build paketieren `libargon2jni.so` und `libargon2native.so` ungestripped, weil die Libraries nicht gestripped werden konnten. Das blockiert den Build nicht, bleibt aber als Packaging-Warnung fuer Argon2Kt dokumentiert.

M5 (Orchestrierung + SAF + UI) ist umgesetzt:

- `BackupArchiveException` in die Domain (`domain/backup`) verschoben, damit die UseCases die typisierte `BackupFailure` fangen koennen, ohne `data`-Typen zu importieren (Clean-Architecture-Grenze gewahrt).
- Domain-Ports `BackupLocationFactory`/`BackupExportTarget`/`BackupImportSource` und `BackupManifestReader`; Modelle `BackupRestorePreview`, `BackupRestorePrepareResult`, `BackupRestoreResult`; `BackupResult.Failure.leftoverFileMayRemain`.
- `ExportBackupUseCase`: laedt Snapshot, schreibt verschluesselten Container an die SAF-Zieldatei, liest das Backup probeweise zurueck (Header+Keyset+manifest.json), kompensiert die Zieldatei bei Fehler/Abbruch und nullt die Passphrase.
- `RestoreBackupUseCase`: zweiphasig (`prepare` decrypt+stage+validate -> Preview, `confirm` Merge, `cancel` Staging loeschen).
- `BackupManifestZipReader` (Probe) und `AndroidBackupLocationFactory` (SAF-`content://` -> framework-freie Handles, `DocumentsContract.deleteDocument`-Kompensation).
- UI: `BackupViewModel` (State-Maschine), `BackupSettingsSection` als Slot im `SettingsScreen` (Screen bleibt praesentational), `CreateDocument`/`OpenDocument`-Launcher, Optionen-/Passwort-/Summary-Dialoge, Fortschritts-Overlay mit Abbrechen, Ergebnis-Snackbar.
- Strings in allen 10 Locales (`strings_backup.xml`). DI-Bindings ergaenzt.
- Tests: `ExportBackupUseCaseTest`, `RestoreBackupUseCaseTest` (echte Tink-/ZIP-Pfade mit Test-KDF), `BackupViewModelTest` (State-Maschine mit Fakes). `testDebugUnitTest`, `lint`, `assembleDebug`, `installDebug` + ADB-Start gruen.
- Nach M5-Review ergaenzt:
  - `BackupFailure.InsufficientStorage` als typisierte Fehlerursache.
  - Free-Space-Checks beim Staging-Entpacken und vor/waehrend der finalen Restore-Kopie ins Scan-Verzeichnis. Fehler werden jetzt klar als zu wenig Speicher gemappt, nicht als korruptes Archiv oder generischer IO-Fehler.
  - Unit-Tests fuer zu wenig Speicher im Staging und beim finalen Restore.
- Instrumentation-Test `RoomBackupRestoreWriterInstrumentedTest` fuer echten Room-Restore: FTS-Aufbau aus OCR-Text und Rollback bei Fehler in einem spaeteren Dokument. Der Test ist angelegt, aber der gesamte bestehende `androidTest`-Source-Set kompiliert aktuell wegen aelterer, nicht backupbezogener Instrumentation-Tests noch nicht.
- README, Markdown-Datenschutzerklaerung und HTML-Privacy-Seite um verschluesselte Backups, Passwortverlust, Restore-Staging, lokale Ablage nach Restore, Checksummenrolle und Standard-Krypto/Play-Console-Hinweis erweitert.
- `assembleRelease` mit R8/Minify laeuft erfolgreich. Nach Deinstallation der Debug-App wurde `app-release.apk` auf dem physischen SM-A536B installiert und gestartet; im geprueften Logcat-Ausschnitt kein FATAL/AndroidRuntime-Crash.
- Manueller Release-E2E-Test durch den Nutzer auf einem physischen Smartphone war erfolgreich: Release-Build aus Android Studio installiert, verschluesseltes Backup erstellt und wieder zurueckgespielt. Damit ist der produktive Argon2id/Tink/SAF/Restore-Pfad auf echter arm64-Hardware bestaetigt.
- In-App-Hilfe und Datenschutz-Screen enthalten jetzt kurze Backup-Hinweise; der Privacy-Footer ist auf den Stand 4. Juni 2026 aktualisiert.

Noch offen:

- `assembleDebug`/`assembleRelease` paketieren `libargon2jni.so`/`libargon2native.so` ungestripped. Das ist aktuell eine Packaging-Warnung, kein Build-Fehler.
- Android-Instrumentation-Source-Set modernisieren: bestehende aeltere Tests/Fakes muessen an aktuelle DAO-/UseCase-/Bitmap-Signaturen angepasst werden, damit der neue Backup-Room/FTS-Instrumentation-Test und spaeter ein SAF-End-to-End-Test auf dem Geraet laufen koennen.
- Restore-Folgeschritte: explizite Manifest-Migrationen. Die harte Staging-Gesamtgrenze liegt fuer v1 bei 2 GiB und kann legitime sehr grosse Archive ablehnen; bei Bedarf spaeter konfigurierbar/grosszuegiger machen.
- Optionaler Dokument-Dedup per PDF-Checksumme ist noch nicht umgesetzt; Merge-Restore importiert Dokumente additiv mit eindeutigen Dateinamen.
- SAF-End-to-End-Export/Restore-Instrumentation-Test gegen echtes Geraet/Test-Provider.
- Play-Console-Export-Compliance in der Play Console final beantworten; README/Privacy dokumentieren die Einordnung als Standard-Krypto fuer Datei-/Backup-Schutz.

## Ziel

Ein portables, passwortgeschuetztes Backup-Format fuer die PDF-Scanner-App, das Dokumente, Thumbnails und Metadaten ausserhalb der Android-App-Sandbox sicher speichern und auf einem anderen Geraet wiederherstellen kann.

Der erste Schnitt konzentriert sich bewusst nur auf Backups. Lokale Vault-Verschluesselung, SQLCipher und kryptografisch gebundener App-Lock sind nicht Teil dieses Plans.

## Gepruefte Ausgangslage

- Android Auto Backup ist bereits global deaktiviert: `android:allowBackup="false"` in `app/src/main/AndroidManifest.xml`.
- Dokumentdateien liegen aktuell unter `context.filesDir/scans`; temporaere Dateien unter `context.cacheDir/temp`.
- Room ist aktuell Schema-Version 9 mit `ScanRecord`, `ScanRecordFts` und `FolderEntity`.
- `ScanRecordFts` ist als `@Fts4(contentEntity = ScanRecord::class)` an `ScanRecord` gekoppelt. Restore muss deshalb ueber Room-DAO-Insert laufen und `extractedText` im `ScanRecord` mitschreiben; Raw-SQL-Insert oder fehlender OCR-Text wuerde den Suchindex leer oder falsch lassen.
- `FolderEntity` enthaelt neben Name auch `colorArgb` und `createdAt`; diese Felder muessen im Backup erhalten bleiben.
- Scan-Metadaten enthalten unter anderem Dateiname, Dateipfad, Zeitstempel, Seitenzahl, Dateigroesse, Thumbnail-Pfad, PDF-Passwortstatus, OCR-Text, Tags, OCR-Qualitaetsdaten, Ordner und Favorit.
- Es gibt noch keine Tink-, AndroidX-Security-Crypto-, Argon2- oder SQLCipher-Abhaengigkeit.
- Bestehende PDF-Passwortfunktionen bleiben getrennt vom Backup-Feature. Ein passwortgeschuetztes PDF wird im Backup als Datei gesichert, aber das Backup-Passwort ist davon unabhaengig.

## Threat Model

Das verschluesselte Backup schuetzt die exportierte Backup-Datei, nachdem sie die App-Sandbox verlaesst, z.B. in Downloads, Nextcloud, USB-Speicher, Mail-Anhaengen oder manuellen Kopien.

Es schuetzt nicht gegen:

- ein bereits kompromittiertes, entsperrtes Geraet,
- Malware mit Zugriff auf den laufenden App-Prozess,
- Nutzer, die das Backup-Passwort weitergeben oder verlieren,
- Klartextdaten nach erfolgreicher Wiederherstellung in der normalen App-Ablage.

Das Backup-Passwort ist nicht zuruecksetzbar. Wenn es verloren geht, ist das Backup technisch nicht wiederherstellbar.

## Format

Dateiendung: `.m24backup`

MIME-Type:

```text
application/vnd.info.meuse24.pdf-scanner.backup
```

Dateiaufbau:

```text
magic bytes: M24PDFBACKUP
header length: uint32 big endian
header json: cleartext, minimal, nicht vertraulich
encrypted payload: Tink StreamingAead ciphertext
```

Klartext-Header:

```json
{
  "formatVersion": 1,
  "appId": "info.meuse24.pdf_scanner",
  "createdAtEpochMillis": 0,
  "kdf": {
    "algorithm": "argon2id",
    "saltBase64": "",
    "memoryKiB": 65536,
    "iterations": 3,
    "parallelism": 1,
    "outputBytes": 32
  },
  "keyWrap": {
    "algorithm": "AES-256-GCM",
    "nonceBase64": "",
    "encryptedStreamingKeysetBase64": ""
  },
  "payload": {
    "algorithm": "TINK_STREAMING_AEAD",
    "keyTemplate": "AES256_GCM_HKDF_1MB",
    "associatedDataVersion": 1
  }
}
```

Wichtig: Nur KDF-Parameter, Formatversion und das verschluesselte Tink-Keyset stehen im Klartext. Dokumentnamen, OCR-Text, Tags, Ordnernamen und Dateiinhalte liegen nur im verschluesselten Payload.

Payload-Inhalt ist ein ZIP-Stream:

```text
manifest.json
documents/<backupDocumentId>.pdf
thumbnails/<backupDocumentId>.jpg
ocr/<backupDocumentId>.json
```

`manifest.json` enthaelt:

- `manifestVersion`
- `sourceDatabaseVersion` aktuell `9`
- `exportedAtEpochMillis`
- App-Version und Build-Infos, soweit verfuegbar
- Exportoptionen
- Folder-Liste mit stabilen Backup-IDs, Name, `colorArgb` und `createdAt`
- Dokumentliste mit stabilen Backup-IDs
- je Dokument: filename, timestamp, pageCount, fileSize, isSearchable, isEncrypted, tags, folderBackupId, isFavorite, deletedAt optional und eine Referenz auf `ocr/<backupDocumentId>.json`, falls OCR-Text enthalten ist
- Datei-Checksummen fuer PDF, Thumbnail und OCR-JSON

`ocr/<backupDocumentId>.json` enthaelt explizit `extractedText`, `ocrPageTextJson`, `ocrConfidence`, `ocrLanguage` und bei Bedarf seitenbezogene OCR-Daten. Restore schreibt diese Werte in `ScanRecord.extractedText`, `ScanRecord.ocrPageTextJson`, `ScanRecord.ocrConfidence` und `ScanRecord.ocrLanguage` zurueck. Das ist Voraussetzung dafuer, dass Room die FTS-Trigger korrekt ausloest und die OCR-Suche nach Restore funktioniert.

Checksummen sind Robustheitschecks fuer Dateikopie, ZIP-Verarbeitung und Implementierungsfehler. Die kryptografische Integritaet des Payloads kommt von StreamingAead; Checksummen duerfen in UI/Dokumentation nicht als separater Sicherheitsnachweis verkauft werden.

Versionsnummern:

- `formatVersion`: aeusseres `.m24backup`-Containerformat inklusive Headerstruktur und Krypto-Wrapping.
- `manifestVersion`: JSON-Schema des entschluesselten `manifest.json`.
- `sourceDatabaseVersion`: Room-Schema-Version der exportierenden App, aktuell `9`.
- `associatedDataVersion`: nur erhoehen, wenn sich die AAD-Zusammensetzung fuer StreamingAead aendert.

## Kryptografie

1. Pro Backup wird ein zufaelliges Tink-StreamingAead-Keyset erzeugt.
2. Aus der Nutzer-Passphrase wird mit Argon2id ein 32-Byte Key Encryption Key abgeleitet.
3. Das Tink-Keyset wird mit diesem Key Encryption Key per AES-256-GCM gewrappt und im Header gespeichert.
4. Der ZIP-Payload wird mit Tink StreamingAead verschluesselt.
5. Associated Data bindet Magic Bytes und einen Hash des finalen Klartext-Headers ein.

Begruendung:

- Argon2id ist fuer passwortbasierte Backups geeigneter als PBKDF2, weil es GPU-Angriffe durch Memory-Hardness besser verteuert.
- Tink StreamingAead ist fuer grosse Dateien/Streams ausgelegt und vermeidet einen ByteArray-Ansatz fuer komplette Backups.
- Das Tink-Keyset wird nicht aus der Passphrase direkt konstruiert, sondern zufaellig erzeugt und passwortgeschuetzt gewrappt. Das passt besser zu Tinks normalem Keyset-Modell und erleichtert spaetere Formatmigrationen.
- Passphrasen werden in ViewModel/UseCase-Pfaden als `CharArray` oder `ByteArray` weitergereicht und nach KDF-Ableitung nach Moeglichkeit genullt. UI-Strings koennen auf Android nie perfekt bereinigt werden, aber neue Domain-/Krypto-APIs sollen keine langlebigen `String`-Passwoerter erzwingen.

Abgeschlossener Spike/Check:

- Tink-Android API fuer `StreamingAead` und Keyset-Serialisierung ist im Codec umgesetzt und getestet.
- Argon2id-Bibliothek ist integriert; `assembleRelease`/Minify und App-Start auf echtem arm64-Geraet sind gruen.
- Manueller Release-E2E auf echter arm64-Hardware ist erfolgreich: verschluesseltes Backup erstellt und wiederhergestellt.
- KDF-Parameter fuer v1: 64 MiB, 3 Iterationen, Parallelism 1, 32 Byte Output; Header-Parameter werden beim Restore begrenzt und validiert.

## Domain- und Datenmodell

Neue Domain-Modelle:

- `BackupExportOptions`
- `BackupImportOptions`
- `BackupManifest`
- `BackupDocumentEntry`
- `BackupFolderEntry`
- `BackupProgress`
- `BackupResult`
- `BackupFailure`

Neue Ports:

- `BackupArchiveCodec`
  - `writeBackup(outputStream, passphrase, payloadWriter, progressCallback)`
  - `readBackup(inputStream, passphrase, payloadReader, progressCallback)`
- `BackupDataSource`
  - liest exportierbare Dokumente, Ordner und optional Papierkorb-Dokumente
- `BackupRestoreWriter`
  - validiert Staging-Daten und schreibt sie in App-Dateien + Room

Implementierungen:

- Domain-Schnittstellen in `domain/gateway` oder `domain/backup`
- Krypto-/ZIP-Implementierung in `data/backup`
- Datenzugriff in `data/backup`
- UI/ViewModel in `ui/settings` oder eigenem `ui/backup`

## Export-Flow

1. Nutzer waehlt `Einstellungen > Sicherheit & Backup > Verschluesseltes Backup erstellen`.
2. App zeigt Optionen:
   - Alle aktiven Dokumente sichern
   - Papierkorb einschliessen optional
   - OCR-Text einschliessen standardmaessig an
   - Thumbnails einschliessen standardmaessig an
3. Nutzer gibt Backup-Passwort zweimal ein.
4. App startet `ActivityResultContracts.CreateDocument` mit `.m24backup`.
5. Export-UseCase sammelt Daten aus Room und validiert Datei-Existenz.
6. Bei fehlenden PDF-Dateien bricht der Export mit einer konkreten Liste ab.
7. Writer erzeugt Header, Keyset, ZIP-Payload und schreibt streaming verschluesselt in den SAF-OutputStream.
8. Nach dem Schreiben wird das Backup, wenn moeglich, ueber dieselbe URI probeweise geoeffnet:
   - Header lesen
   - Keyset entschluesseln
   - erstes Payload-Segment entschluesseln
   - `manifest.json` lesen und Checksumme pruefen
9. UI meldet Erfolg oder konkrete Fehlerursache.

Bei Export-Fehler oder Nutzerabbruch nach `CreateDocument` muss die bereits angelegte SAF-Zieldatei geloescht werden, z.B. ueber `DocumentsContract.deleteDocument` oder `ContentResolver.delete`, sofern der Provider das erlaubt. Falls Loeschen nicht moeglich ist, zeigt die UI einen Hinweis, dass eine unvollstaendige Backup-Datei im Zielordner verbleiben kann.

ZIP-Eintraege fuer PDFs und Thumbnails werden bevorzugt mit `ZipEntry.STORED` geschrieben. PDFs sind bereits komprimiert; DEFLATED kostet CPU und bringt wenig. Da fuer STORED Groesse und CRC vorab noetig sind, werden diese Werte zusammen mit den Checksummen in der Export-Vorbereitung berechnet.

Pipeline:

```text
App-Daten -> ZipOutputStream -> Tink encrypting stream -> SAF OutputStream
```

## Restore-Flow

Default: Merge in bestehendes Archiv. Replace wird im ersten Schnitt nicht angeboten, weil es hoehere Datenverlust-Risiken hat.

1. Nutzer waehlt `Verschluesseltes Backup wiederherstellen`.
2. App startet `ActivityResultContracts.OpenDocument`.
3. Nutzer gibt Backup-Passwort ein.
4. Reader liest Header, leitet Key ab, entschluesselt Keyset und oeffnet den StreamingAead-Decrypt-Stream.
5. ZIP wird in ein dediziertes Staging-Verzeichnis entpackt:

```text
cacheDir/backup_restore/<restoreSessionId>/
```

6. Manifest wird validiert:
   - `appId`
   - `formatVersion`
   - `manifestVersion`
   - `sourceDatabaseVersion <= 9`
   - Pflichtfelder
   - Checksummen
   - Dateitypen und Dateigroessen
   - keine Pfad-Traversal-Eintraege
   - dekomprimierte Gesamtgroesse unter harter Obergrenze
   - Anzahl ZIP-Eintraege unter harter Obergrenze
   - verfuegbarer Speicher fuer Staging und finale Kopie ausreichend
7. Bei zukuenftiger Formatversion bricht Restore mit Hinweis auf App-Update ab.
8. Bei aelterer Manifest-Version laufen explizite Manifest-Migrationen.
9. Finalisierung:
   - Ziel-Dateinamen je Dokument mit bestehender `resolveUniqueFilename`-Logik eindeutig machen.
   - Dateien aus Staging nach `scansDir()` kopieren.
   - fuer Dateinamen die frameworkfreie `domain/common/FilenameUtils.kt`-Variante von `resolveUniqueFilename` nutzen, nicht die interne util/PdfEditorCore-Variante.
   - Thumbnails passend zum finalen PDF-Dateinamen kopieren oder `thumbnailPath = null` setzen, wenn Thumbnail im Backup fehlt oder ausgeschlossen wurde.
   - vorhandene Ordner per Name wiederverwenden und nur fehlende Ordner neu anlegen; `colorArgb` und `createdAt` werden bei neu angelegten Ordnern erhalten.
   - Dokumente standardmaessig additiv importieren. Optionaler Dedup per PDF-Checksumme kann in M4 umgesetzt werden; wenn nicht umgesetzt, muss UI/Doku klar sagen, dass ein erneuter Merge Duplikate erzeugen kann.
   - neue Room-Records mit neuen lokalen IDs ueber die DAO schreiben, inklusive `extractedText` und `ocrPageTextJson`.
   - FTS wird nur dann ueber bestehende Room/FTS-Trigger korrekt aufgebaut, wenn der DAO-Insert den vollstaendigen `ScanRecord` einschliesslich `extractedText` schreibt.
10. Bei Fehlern oder Abbruch nach Dateikopie werden bereits kopierte Dateien kompensierend geloescht.
11. Staging-Verzeichnis wird immer geloescht.

Pipeline:

```text
SAF InputStream -> Tink decrypting stream -> ZipInputStream -> Staging -> scansDir + Room
```

## Transaktionalitaet

Room-Transaktionen koennen Dateisystemoperationen nicht atomar einschliessen. Deshalb wird Restore zweiphasig gebaut:

1. Vollstaendige Validierung im Staging ohne Aenderung am Archiv.
2. Kontrollierte Finalisierung mit Kompensation:
   - kopierte Zieldateien und angelegte Ordner-/Dokument-IDs protokollieren,
   - DB-Insert in `withTransaction`,
   - bei DB-Fehler oder Cancel kopierte Dateien loeschen,
   - bei Dateifehler keine DB-Aenderung starten.

Der erste Schnitt ist Merge-only. Ein spaeterer Replace-Modus muss vorher ein internes Safety-Snapshot oder eine explizite Export-Warnung bekommen.

## UI-Aenderungen

Bestehenden Settings-Screen erweitern:

- Gruppe `Sicherheit & Backup`
- Button `Verschluesseltes Backup erstellen`
- Button `Backup wiederherstellen`
- Kurzer Hinweis:
  - Backup-Dateien sind passwortgeschuetzt.
  - Das Passwort kann nicht wiederhergestellt werden.
  - Lokale App-Daten werden dadurch nicht zusaetzlich verschluesselt.

Dialoge:

- Passwort setzen mit Wiederholung und Mindestlaenge
- Passwort eingeben fuer Restore
- Exportoptionen
- Restore-Zusammenfassung vor Import:
  - Anzahl Dokumente
  - Anzahl Ordner
  - Gesamtgroesse
- Backup-Datum
- App-/Schema-Version
- Hinweis, ob Merge additiv importiert und ob Duplikate entstehen koennen
- Fortschrittsdialog mit Dokumentzaehler und Abbrechen
- Fehlerdialog mit konkreter Ursache

Strings muessen in alle vorhandenen Locales aufgenommen werden.

## Datenschutz und Dokumentation

README, Hilfe und Privacy-Dokumente aktualisieren:

- Backups verlassen die App nur durch explizite Nutzeraktion.
- Backups sind mit dem Nutzerpasswort verschluesselt.
- Ohne Passwort ist kein Restore moeglich.
- Backup schuetzt die exportierte Datei, nicht automatisch die lokale Ablage nach Restore.
- Per-Datei-Checksummen dienen der Fehlererkennung; die Sicherheitsintegritaet kommt von der authentifizierten Verschluesselung.
- Keine eigene Cloud, kein eigener Server.

Play-Store-Check:

- Standard-Krypto-Nutzung fuer Datei-/Backup-Schutz ist in README, Markdown-Datenschutzerklaerung und HTML-Privacy-Seite dokumentiert.
- Export-Compliance-Frage in Play Console final beantworten und als Standard-Krypto fuer Datei-/Backup-Schutz deklarieren, falls abgefragt.

## Tests

Unit-Tests fuer `BackupArchiveCodec`:

- Round-trip: Export -> Import -> Manifest und Dateien byte-identisch.
- Falsches Passwort schlaegt fehl.
- Abgeschnittene Datei schlaegt fehl.
- Manipulierter Header schlaegt fehl.
- Manipulierter Payload schlaegt fehl.
- Zukuenftige Formatversion schlaegt mit Update-Hinweis fehl.
- Alte Manifest-Version wird ueber Testmigration akzeptiert.
- Grosses Fixture wird streaming verarbeitet, ohne kompletten Payload in Memory zu laden.
- Pfad-Traversal im ZIP wird blockiert.
- ZIP-Bomb-Backup mit zu vielen Eintraegen oder zu grosser dekomprimierter Gesamtgroesse wird vor/waehrend Staging abgebrochen.

Unit-Tests fuer Restore-Mapping:

- doppelte Dateinamen werden eindeutig aufgeloest.
- Ordner-ID-Mapping funktioniert und Ordner werden per Name de-dupliziert.
- Ordnerfarbe und Erstellzeit bleiben erhalten.
- Favoriten, Tags, `extractedText`, `ocrPageTextJson`, OCR-Sprache und OCR-Qualitaet bleiben erhalten.
- Nach Restore findet FTS-Suche einen Begriff aus `extractedText`.
- Fehlende oder ausgeschlossene Thumbnails setzen `thumbnailPath = null` oder werden konsistent regeneriert.
- Papierkorb wird nur importiert, wenn Option aktiv ist.
- Fehlende Dokumentdatei im Backup bricht Restore vor DB-Aenderung ab.
- Cancel waehrend Restore-Finalisierung laeuft durch denselben Kompensationspfad wie ein Fehler.

Instrumentation-Tests:

- SAF-Export/Import ueber Test-URI oder Temp-Provider.
- Restore in eine echte Room-Testdatenbank.
- Migration von Backup-Manifest `sourceDatabaseVersion = 9`.

Regression-Tests:

- Bestehende Import-/Export-/Viewer-/OCR-Tests duerfen unveraendert weiterlaufen.

Verifikationskommandos:

```bash
./gradlew.bat testDebugUnitTest
./gradlew.bat lint
./gradlew.bat assembleDebug
```

## Implementierungsreihenfolge

### Meilenstein 1: Krypto- und Format-Spike

- Tink-Android dependency in Version Catalog aufnehmen.
- Argon2id-Kandidaten evaluieren.
- Minimalen `BackupArchiveCodec` ohne App-Daten bauen.
- Test: 100 MB Zufalls-/Fixture-Daten streaming verschluesseln und entschluesseln.
- Test: falsches Passwort und korrupte Datei.
- `assembleRelease` mit Argon2id/Tink-Abhaengigkeiten ausfuehren und auf einem echten arm64-Geraet einen KDF-Smoke-Test laufen lassen.

Akzeptanz:

- Kein kompletter Backup-Payload wird als ByteArray gehalten.
- Falsches Passwort endet deterministisch in einem typisierten Fehler.
- Header ist versioniert und laesst sich forward-compatible parsen.
- Native Argon2id-Bibliothek funktioniert in Release-Minify-Builds.

### Meilenstein 2: Manifest und Export-Datenquelle

- `BackupManifest` und JSON-Serializer einbauen.
- Exportdaten aus Room lesen.
- Dateien und Thumbnails validieren.
- Checksummen berechnen.
- ZIP-Payload mit Manifest + Dateien schreiben, bevorzugt mit STORED-Eintraegen fuer bereits komprimierte PDFs.
- Export-Fehler/Cancel loeschen die per SAF angelegte Zieldatei, sofern der Provider Loeschen erlaubt.

Akzeptanz:

- Ein Backup aller aktiven Dokumente wird aus einer Testdatenbank erzeugt.
- Manifest enthaelt keine absoluten lokalen Dateipfade.
- Dokumentinhalte, OCR-Text, Tags und Ordnernamen stehen nicht im Klartext ausserhalb des verschluesselten Payload.
- Manifest deckt `Folder.colorArgb`, `Folder.createdAt` und die OCR-JSON-Referenz ab; `ocr/<backupDocumentId>.json` deckt `extractedText`, `ocrPageTextJson`, OCR-Sprache und OCR-Qualitaet ab.

### Meilenstein 3: Restore in Staging

- Reader und ZIP-Validation bauen.
- Staging-Verzeichnis einfuehren.
- Checksummen und Pfad-Traversal pruefen.
- Entry-Count-, dekomprimierte Gesamtgroessen- und Speicherplatz-Grenzen pruefen.
- Manifest-Versionen pruefen.

Akzeptanz:

- Korrupte/manipulierte Backups werden vor Dateisystem-/DB-Aenderungen abgelehnt.
- Boesartige oder versehentlich riesige ZIP-Payloads koennen `cacheDir` nicht unlimitiert fuellen.
- Staging wird bei Fehlern geloescht. Bei Erfolg bleibt es fuer die Finalisierung erhalten und muss im M4-Erfolgspfad per `finally` geloescht werden.

### Meilenstein 4: Merge-Restore ins Archiv

- Ordner per Name de-duplizieren und Dokumente mit neuen lokalen IDs einspielen.
- Dateinamen eindeutig machen.
- DAO-basierten Insert inklusive `extractedText` und `ocrPageTextJson` erzwingen.
- FTS/Suche nach Restore pruefen.
- Kompensationslogik fuer Dateikopie/DB-Fehler bauen.

Akzeptanz:

- Restore eines Backups in ein nicht leeres Archiv erzeugt keine ID-, Ordnernamen- oder Dateinamenskollisionen.
- Suche nach wiederhergestelltem OCR-Text funktioniert.
- Restore-Fehler hinterlassen keine halben DB-Eintraege.
- Restore-Cancel waehrend der Finalisierung hinterlaesst keine halben Dateien/DB-Eintraege.

### Meilenstein 5: Settings-UI und SAF

- Settings-Gruppe fuer Backup ergaenzen.
- CreateDocument/OpenDocument Flows anbinden.
- Passwortdialoge, Exportoptionen, Restore-Summary und Progress anzeigen.
- Nach Export probeweise Manifest aus der geschriebenen Datei entschluesseln.
- UI dokumentiert, ob Merge additiv ist und ob Duplikate entstehen koennen.

Akzeptanz:

- Nutzer kann ein Backup erstellen und direkt wiederherstellen.
- Falsches Passwort zeigt eine klare Fehlermeldung.
- Keine App-Crashes bei Rotation, Abbruch oder fehlender URI-Berechtigung.

### Meilenstein 6: Dokumentation, Locales, Release-Checks

- README und Privacy-Dokumente aktualisieren.
- Alle String-Ressourcen in vorhandenen Locales ergaenzen.
- Export-Compliance in Release-Checkliste aufnehmen.
- Lint, Unit-Tests, Debug-Build und Release-Build ausfuehren.

Akzeptanz:

- Dokumentation beschreibt ehrlich, was das Backup schuetzt und was nicht.
- Alle bestehenden Locales kompilieren.
- `testDebugUnitTest`, `lint` und `assembleDebug` laufen erfolgreich.

## Nicht-Ziele fuer diesen Plan

- lokale Vault-Verschluesselung der aktiven App-Ablage,
- SQLCipher/verschluesselte Room-Datenbank,
- App-Lock mit `BiometricPrompt.CryptoObject`,
- automatische Cloud-Synchronisierung,
- automatisches Hintergrundbackup,
- AppSettings, ThemeMode und sonstige persoenliche UI-Einstellungen,
- Replace-Restore mit Loeschen des bestehenden Archivs.

Diese Themen koennen spaeter auf dem Backup-Format aufbauen, sollten aber nicht in den ersten verschluesselten Backup-Schnitt gemischt werden.
