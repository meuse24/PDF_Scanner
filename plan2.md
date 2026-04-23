# Implementierungsplan: Papierkorb, Dokument-Anhängen, OCR-Qualitätsansicht

## Stand

Stand: 2026-04-23. Noch nicht begonnen. Dieser Plan beschreibt drei
unabhängige Features, die in dieser Reihenfolge ausgeliefert werden sollen:

1. **Papierkorb mit Wiederherstellen** (F1)
2. **An bestehendes Dokument anhängen** (F2)
3. **OCR-Qualitätsansicht** (F3)

Jedes Feature ist ein eigenständiger Release-Kandidat. F1 sollte zuerst
landen, weil es DB-Schema und Löschpfade berührt — F2 und F3 bauen
anschließend sauber darauf auf.

## Allgemeine Regeln

- Keine nutzer-sichtbaren Literal-Strings im Kotlin-Code — ausschließlich
  `R.string.*`. Interne Exception-/Log-Texte sind nur OK, wenn sie nie direkt
  in der UI landen; Workflow-/UseCase-Fehler werden über
  `WorkflowErrorMapper` oder `ResourceProvider` gemappt.
- Neue Strings in alle 10 Locales (`values/`, `-de`, `-es`, `-fr`, `-pt`,
  `-zh-rCN`, `-ar`, `-ja`, `-ru`, `-hi`).
- Keine neuen Netzwerk- oder Cloud-Berechtigungen.
- `docs/privacy-policy.html` + In-App-Privacy prüfen, falls sich
  Datenflüsse ändern.
- Neue Regressionstests als JVM-Unit-Test oder Instrumentation-Test —
  keine reinen Fake-Tests, wo echte PDF-/Renderer-Pfade beteiligt sind.

## Übergreifende Verbesserungsvorschläge

Diese Punkte sollten vor oder während der Feature-Implementierung in die
jeweiligen Phasen aufgenommen werden:

1. **Room-Migrationen vollständig verdrahten.** Neue `MIGRATION_*`-Objekte
   nicht nur in `AppDatabase.kt` anlegen, sondern auch in
   `di/DatabaseModule.kt` bei `.addMigrations(...)` registrieren. Sonst
   kompiliert die Migration zwar, wird aber in der App nicht ausgeführt.
2. **Migrationstests brauchen eine neue Test-Abhängigkeit.** Der Plan nutzt
   `MigrationTestHelper`; dafür `androidx.room:room-testing` in
   `libs.versions.toml` und `androidTestImplementation(...)` ergänzen.
3. **DAO-Testfakes zuerst entschärfen.** Viele Unit- und Instrumentation-Tests
   implementieren `ScanDao` direkt. Jede neue DAO-Methode bricht sonst viele
   Fakes. Vor F1/F3 einen gemeinsamen `FakeScanDaoBase` in `testutil` und
   einen kleinen androidTest-Gegenpart einführen, der neue Methoden mit
   No-op/defaults implementiert.
4. **Bulk-DAO-Methoden bevorzugen.** Für Trash/Restore/OCR-Stats nicht pro
   Record eine DB-Query feuern, wenn Room `WHERE id IN (:ids)` sauber kann.
   Das reduziert UI-Latenz und macht Undo/Purge einfacher testbar.
5. **PDF-Atomik zentral halten.** Neue PDF-Operationen sollen die vorhandenen
   `PdfEditor`-Pfade verwenden, die `Files.move(..., ATOMIC_MOVE,
   REPLACE_EXISTING)` nutzen. Kein neuer manueller `renameTo()`-Swap in
   UseCases, wenn `PdfEditor` dieselbe Operation kapseln kann.
6. **OCR-Ergebnisse strukturieren, bevor UI gebaut wird.** Für die
   Qualitätsansicht reicht `Pair<String, OcrResultStats?>` langfristig nicht:
   Review, Seitentrenner und per-Dokument-Persistenz brauchen ein
   strukturiertes Ergebnis mit Record-ID, Volltext, optionalen Seitentexten
   und Stats.

---

# F1 — Papierkorb mit Wiederherstellen

## Ziel

Löschen in der App ist nicht mehr endgültig. Ein gelöschter Eintrag
landet im Papierkorb, bleibt dort 30 Tage sichtbar und wird danach
automatisch entfernt. Der Nutzer kann einzelne oder alle Einträge
wiederherstellen oder sofort endgültig löschen.

## Nicht-Ziele

- Keine Cloud-Synchronisation von Trash-State.
- Keine Pro-Ordner-Retention (einheitlich 30 Tage).
- Keine Vorschau gelöschter Inhalte mit Volltext-/OCR-Zugriff
  (Papierkorb ist reiner „Undo"-Puffer).
- Kein Rückgängig-Stack für Nicht-Lösch-Aktionen.

## Aktueller Kontext

- `data/local/ScanRecord.kt` enthält derzeit kein Soft-Delete-Feld.
- `data/local/ScanDao.kt` hat `@Delete suspend fun delete(record)` und
  `searchScansFlow(query)` über FTS4. `getAllScans()` liefert alle
  Records ungefiltert.
- `data/local/AppDatabase.kt` steht auf Version 5 mit MIGRATION_4_5 inkl.
  FTS-Setup und Triggern.
- `domain/usecase/DeleteScansUseCase.kt` löscht sofort Datei, Thumbnail
  und DB-Eintrag.
- `ui/home/HomeViewModel.deleteScans(records)` ruft den UseCase direkt.
- `ui/home/components/HomeBulkDeleteDialog.kt` bestätigt Multi-Delete;
  `HomeDeleteDialog` bestätigt Einzel-Delete.
- **WorkManager ist aktuell nicht als Abhängigkeit eingebunden**
  (`gradle/libs.versions.toml`). Das beeinflusst die Scheduling-Variante
  (siehe unten).

## Architektur

### DB-Schema (Version 6)

Neue Spalte auf `scan_records`:

```sql
ALTER TABLE scan_records ADD COLUMN deleted_at INTEGER
```

- `null` → aktiver Datensatz.
- `Long` (epoch ms) → gelöschter Datensatz; Zeitpunkt des Soft-Deletes.

Migration **MIGRATION_5_6** in `AppDatabase.kt`:

```kotlin
db.execSQL("ALTER TABLE scan_records ADD COLUMN deleted_at INTEGER")
```

Zusätzlich in `DatabaseModule.provideDatabase(...)`:

```kotlin
.addMigrations(
    AppDatabase.MIGRATION_1_2,
    AppDatabase.MIGRATION_2_3,
    AppDatabase.MIGRATION_3_4,
    AppDatabase.MIGRATION_4_5,
    AppDatabase.MIGRATION_5_6
)
```

FTS4-Trigger müssen **nicht** angepasst werden — der `INSERT/UPDATE`-
Trigger indexiert unabhängig vom `deleted_at`-Wert. Die Filterung
passiert auf der Query-Ebene: gelöschte Records tauchen in `searchScansFlow`
nicht auf, weil der DAO sie explizit ausschließt.

`ScanRecord`:

```kotlin
@ColumnInfo(name = "deleted_at") val deletedAt: Long? = null
```

### DAO-Änderungen

`ScanDao.kt` wird auf Soft-Delete-Semantik umgestellt — existierende
Aufrufer bleiben ohne Anpassung „aktive Records":

```kotlin
@Query("SELECT * FROM scan_records WHERE deleted_at IS NULL ORDER BY timestamp DESC")
fun getAllScans(): Flow<List<ScanRecord>>

@Query("""
    SELECT * FROM scan_records
    WHERE deleted_at IS NULL
      AND id IN (SELECT docid FROM scan_records_fts WHERE scan_records_fts MATCH :query)
    ORDER BY timestamp DESC
""")
fun searchScansFlow(query: String): Flow<List<ScanRecord>>

@Query("SELECT * FROM scan_records WHERE deleted_at IS NOT NULL ORDER BY deleted_at DESC")
fun getTrashedScans(): Flow<List<ScanRecord>>

@Query("SELECT * FROM scan_records WHERE id IN (:ids)")
suspend fun getScansByIds(ids: List<Long>): List<ScanRecord>

@Query("UPDATE scan_records SET deleted_at = :timestamp WHERE id IN (:ids) AND deleted_at IS NULL")
suspend fun softDelete(ids: List<Long>, timestamp: Long)

@Query("UPDATE scan_records SET deleted_at = NULL WHERE id IN (:ids) AND deleted_at IS NOT NULL")
suspend fun restore(ids: List<Long>)

@Query("SELECT * FROM scan_records WHERE deleted_at IS NOT NULL AND deleted_at < :threshold")
suspend fun findExpiredTrash(threshold: Long): List<ScanRecord>
```

`@Delete` bleibt bestehen (wird von der Purge-Logik verwendet).

**Verbesserung:** Die Soft-Delete-/Restore-Methoden arbeiten bewusst mit
ID-Listen. Das passt zum geplanten Undo (`lastTrashed` speichert IDs) und
verhindert, dass gelöschte Records erst wieder aus `getAllScans()` geladen
werden müssen, wo sie nach der Filterung nicht mehr auftauchen.

### Repository-API

`ScanRepository.kt` ergänzt:

```kotlin
fun getTrashedScans(): Flow<List<ScanRecord>>
suspend fun getScansByIds(ids: List<Long>): List<ScanRecord>
suspend fun softDelete(ids: List<Long>, timestamp: Long)
suspend fun restore(ids: List<Long>)
suspend fun findExpiredTrash(threshold: Long): List<ScanRecord>
```

### UseCases

**Aufteilung in zwei UseCases — klare Semantik:**

`TrashScansUseCase` (Soft-Delete, Dateien bleiben liegen):

```kotlin
class TrashScansUseCase @Inject constructor(
    private val repository: ScanRepository
) {
    suspend operator fun invoke(records: List<ScanRecord>) {
        if (records.isEmpty()) return
        val now = System.currentTimeMillis()
        repository.softDelete(records.map { it.id }, now)
    }
}
```

Dateien (`filepath`, `thumbnailPath`) bleiben **bewusst** im
`filesDir/scans/` liegen — sonst wäre Restore unmöglich. Das ist OK,
weil der Purge-Pfad dieselbe Löschlogik wie der alte
`DeleteScansUseCase` anwendet.

`RestoreScansUseCase`:

```kotlin
class RestoreScansUseCase @Inject constructor(
    private val repository: ScanRepository
) {
    suspend operator fun invoke(ids: List<Long>) {
        if (ids.isEmpty()) return
        repository.restore(ids)
    }
}
```

**Verbesserung:** `RestoreScansUseCase` nimmt IDs statt `ScanRecord`s. Das
macht Snackbar-Undo trivial und vermeidet widersprüchliche Daten, wenn sich
Trash-Inhalte zwischen Snackbar-Anzeige und Klick geändert haben.

`PurgeTrashUseCase` (Hard-Delete, ersetzt `DeleteScansUseCase`-Aufrufer
aus Trash-Kontext **und** Auto-Cleanup):

```kotlin
class PurgeTrashUseCase @Inject constructor(
    private val repository: ScanRepository,
    private val deleteScansUseCase: DeleteScansUseCase
) {
    suspend fun purgeSelected(records: List<ScanRecord>): Boolean =
        deleteScansUseCase(records)

    suspend fun purgeExpired(retentionMillis: Long): Int {
        val threshold = System.currentTimeMillis() - retentionMillis
        val expired = repository.findExpiredTrash(threshold)
        deleteScansUseCase(expired)
        return expired.size
    }
}
```

Der bestehende `DeleteScansUseCase` bleibt unverändert und wird als
interne Primitive wiederverwendet. Kein neuer PDF-Code.

### Retention-Konstante

Zentral in `util/TrashConstants.kt`:

```kotlin
object TrashConstants {
    const val RETENTION_DAYS = 30
    val RETENTION_MILLIS = TimeUnit.DAYS.toMillis(RETENTION_DAYS.toLong())
}
```

Hart verdrahtet. Kein Setting — zwingend gleich für alle Nutzer, damit
die Datenschutz-Zeile „Dateien verbleiben bis zu 30 Tage im Papierkorb"
eindeutig bleibt.

### Auto-Cleanup-Strategie

**Empfehlung: App-Start-basierter Cleanup ohne WorkManager.**

Im `PdfScannerApp.onCreate()` wird ein einmaliger Hintergrund-Job
gestartet, der abgelaufene Einträge endgültig löscht:

```kotlin
class PdfScannerApp : Application() {
    @Inject lateinit var purgeTrashUseCase: PurgeTrashUseCase
    @Inject lateinit var dispatcherProvider: DispatcherProvider

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        PDFBoxResourceLoader.init(this)
        appScope.launch(dispatcherProvider.io) {
            purgeTrashUseCase.purgeExpired(TrashConstants.RETENTION_MILLIS)
        }
    }
}
```

Zusätzlich bei jedem Öffnen des `TrashScreen`: Purge nochmal aufrufen,
damit ein lang offenes App-Leben ohne Restart trotzdem aktuell ist.

**Begründung gegen WorkManager für den MVP:**

- Neue Abhängigkeit (`androidx.work:work-runtime-ktx` +
  `androidx.hilt:hilt-work`) nur für einen 30-Tage-Cleanup ist
  overkill.
- Retention ist nicht sicherheitskritisch — wenn der Nutzer die App
  30 Tage nicht startet, bleibt der Papierkorb eben länger liegen.
  Dateien sind lokal; kein Datenschutzrisiko.
- Bei App-Start läuft der Purge innerhalb von Millisekunden (nur
  DB-Query + File-Delete), ohne merklichen Startup-Impact.

**Fallback-Plan (wenn später doch gewünscht):** `WorkManager` +
`PeriodicWorkRequest(1 day)` + Hilt-`@HiltWorker`. Isoliert in einer
neuen Datei `util/TrashCleanupWorker.kt` nachrüstbar, ohne den UseCase
anzufassen.

## Datenschutz-Hinweis für F1

F1 ändert die Löschsemantik sichtbar: „Löschen" bedeutet zunächst 30 Tage
lokale Aufbewahrung im Papierkorb. Deshalb nicht nur allgemein prüfen,
sondern konkret aktualisieren:

- `docs/privacy-policy.html`
- `PRIVACY.md`, falls dort Löschung/Aufbewahrung beschrieben ist
- In-App-Privacy-Screen (`ui/privacy/PrivacyScreen.kt`)

Akzeptanz für F1 enthält damit auch: Datenschutztext sagt klar, dass lokal
gelöschte Dokumente bis zu 30 Tage im Papierkorb verbleiben können und erst
bei „endgültig löschen" oder Cleanup entfernt werden.

## UI-Konzept

### Integration im HomeViewModel

`deleteScan(record)` und `deleteScans(records)` rufen künftig
`TrashScansUseCase` statt `DeleteScansUseCase`. Dabei:

- `_success.value = resourceProvider.getQuantityString(R.plurals.trash_moved, n, n)`
  oder eigener `ResourceProvider`-Plural-Wrapper. Nicht mit einem
  Singular-String plus Zahl arbeiten, weil 10 Locales betroffen sind.
- **Undo-Mechanismus:** neue `lastTrashed: StateFlow<List<Long>>` hält
  die IDs des letzten Soft-Delete-Batches. Der HomeScreen zeigt eine
  Snackbar mit Button „Rückgängig". Klick → `restoreLastTrashed()` →
  `RestoreScansUseCase` → `lastTrashed` leeren.
- Selection-Mode wird nach Soft-Delete normal verlassen.

Die Dialoge (`HomeDeleteDialog`, `HomeBulkDeleteDialog`) ändern ihren
Text von „Dauerhaft löschen?" zu „In den Papierkorb verschieben?".
Strings: `confirm_trash_single`, `confirm_trash_multi`,
`action_move_to_trash`.

### Neuer TrashScreen

Route: `Screen.Trash : Screen("trash")`.

Einstiegspunkt: Eintrag im `ModalNavigationDrawer` (unter „Ablage",
über „Hilfe"). Icon `Icons.Default.Delete`.

```kotlin
// ui/trash/TrashScreen.kt
// ui/trash/TrashViewModel.kt
```

`TrashViewModel`:

- `trashedScans: StateFlow<List<ScanRecord>>` aus
  `repository.getTrashedScans()`.
- `init { purgeTrashUseCase.purgeExpired(...) }` — aktualisiert direkt
  beim Öffnen.
- `restore(records)`, `purge(records)`, `emptyTrash()` als Operationen.
- `_error`, `_success` wie im HomeViewModel.

`TrashScreen`:

- Scaffold mit TopBar-Titel „Papierkorb" + TopBar-Overflow „Alle endgültig löschen".
- LazyColumn identisch zu Archiv, aber **schreibgeschützt**:
  Thumbnail + Dateiname + „In X Tagen endgültig gelöscht" (aus
  `record.deletedAt + RETENTION_MILLIS - now`).
- Pro-Item-Aktionen über Sheet:
  - **Wiederherstellen** → zurück in Archiv, Toast.
  - **Endgültig löschen** → Confirm-Dialog.
- Auswahlmodus analog Archiv-Selection: Multi-Restore + Multi-Purge.
- Empty-State: Illustration + „Papierkorb ist leer".

### Viewer + Edit-Screens

Gelöschte Records werden von `getAllScans()` ausgeblendet. Wenn ein
Viewer gerade einen Record anzeigt, der gerade soft-gelöscht wurde
(theoretisch möglich bei Multi-Device-Kopie — aber wir haben keins):
Viewer zeigt dann `pdf_viewer_file_missing`, weil der Record nicht
mehr im Stream ist. Kein gesonderter Code nötig.

## Fehlerfälle

- **Restore eines Records, dessen Datei gelöscht wurde** (sollte nicht
  vorkommen, weil Soft-Delete Dateien nicht anfasst): Restore sollte
  fehlschlagen und `error_restore_missing_file` zeigen, statt den kaputten
  Record zurück ins Archiv zu holen. Optional kann die UI danach „Endgültig
  löschen" anbieten. Dafür `RestoreScansUseCase` vor dem `UPDATE` die
  Records per `getScansByIds(ids)` laden und `File(filepath).exists()`
  prüfen.
- **Storage voll bei Purge**: `File.delete()` kann fehlschlagen. Die
  bestehende `DeleteScansUseCase`-Logik gibt `false` zurück → UI zeigt
  `error_delete_failed`.
- **DB-Konflikt bei Restore** (z. B. neuer Record mit gleichem
  `filepath` wurde zwischenzeitlich angelegt): `filepath` ist nicht
  UNIQUE, kein Konflikt. Zwei Records mit gleichem Pfad ist zwar
  merkwürdig, aber der Purge-Pfad regelt das später.

## Tests

`domain/usecase/TrashScansUseCaseTest.kt`:

- setzt `deletedAt` für alle übergebenen Records auf einen Zeitstempel.
- aktualisiert nichts, wenn Liste leer ist.

`domain/usecase/RestoreScansUseCaseTest.kt`:

- setzt `deletedAt` zurück auf `null`.

`domain/usecase/PurgeTrashUseCaseTest.kt`:

- `purgeExpired(retention)` delegiert an `DeleteScansUseCase` nur für
  Records, deren `deletedAt < now - retention`.
- nicht-abgelaufene Records bleiben erhalten.
- Rückgabe = Anzahl gelöschter Records.

`ui/trash/TrashViewModelTest.kt` (UnconfinedTestDispatcher):

- laden der Liste aus Repository-Flow.
- Restore löscht Records aus Trash-Flow.
- Purge delegiert korrekt.

`androidTest/` — Migration 5→6:

- Bestehender Record ohne `deleted_at` wird nach Migration als `null`
  gelesen (Room-Migration-Test mit `MigrationTestHelper`).
- Vorher `androidx.room:room-testing` ergänzen; aktuell ist diese
  Dependency nicht eingebunden.
- Alle `ScanDao`-Fakes in Unit-/Instrumentation-Tests auf die neuen
  DAO-Methoden bringen oder vorher über einen gemeinsamen Fake-Basistyp
  konsolidieren.

## Rollout

### Phase 1.1 — DB + Soft-Delete

- Migration 5→6 + `ScanRecord.deletedAt`.
- `MIGRATION_5_6` in `DatabaseModule.addMigrations(...)` registrieren.
- DAO-Queries filtern `deleted_at IS NULL`.
- `TrashScansUseCase`, `RestoreScansUseCase` + Repository-API.
- `ResourceProvider` bei Bedarf um `getQuantityString(...)` erweitern
  und Tests/Fakes nachziehen.
- Home-Delete-Pfad auf Trash umstellen.
- Snackbar-Undo im Home.
- **Akzeptanz:** Löschen verschiebt, Undo holt zurück, Archiv zeigt
  nur aktive Records.

### Phase 1.2 — TrashScreen

- Route + ViewModel + Screen.
- Drawer-Eintrag + Strings (10 Locales).
- Multi-Select, Restore, Purge, EmptyTrash.
- **Akzeptanz:** Nutzer kann gelöschte Dokumente wiederherstellen
  oder endgültig entfernen.

### Phase 1.3 — Auto-Cleanup

- `PurgeTrashUseCase.purgeExpired` + `TrashConstants`.
- App-Start-Cleanup in `PdfScannerApp.onCreate()`.
- Zusätzlicher Cleanup beim Öffnen des TrashScreen.
- **Akzeptanz:** Record > 30 Tage im Papierkorb wird beim App-Start
  endgültig gelöscht.

## Fallstricke

1. **FTS-Index enthält Soft-Deleted-Records.** Filterung muss in der
   SQL-Query geschehen (`WHERE deleted_at IS NULL AND id IN (...)`),
   nicht nachträglich im Kotlin-Code — sonst sieht die Ergebnisliste
   vor dem Filter inkonsistent aus.
2. **Purge reicht Datei- und Thumbnail-Löschung.** `DeleteScansUseCase`
   übernimmt das bereits; nicht duplizieren.
3. **`@Delete` bleibt im DAO.** Room nutzt es für den Purge-Pfad. Nicht
   entfernen.
4. **Undo-Snackbar muss Coroutine-Scope-safe sein.** Nach Navigation
   weg vom Home verschwindet die Snackbar normal — der `_success`-Flow
   bleibt; beim Rückkehren nicht erneut triggern.
5. **Migration registrieren, nicht nur definieren.** `AppDatabase` allein
   reicht nicht; ohne `DatabaseModule.addMigrations(MIGRATION_5_6)` crasht
   ein Upgrade von DB 5 auf 6.
6. **Restore über IDs halten.** Undo darf nicht von aktiven Listen abhängen,
   weil gelöschte Records dort absichtlich nicht mehr erscheinen.

---

# F2 — An bestehendes Dokument anhängen

## Ziel

Im Viewer und im Dokument-Sheet gibt es eine neue Aktion „Seiten
anhängen". Der Nutzer kann wählen:

- **Scan anhängen** → GmsDocumentScanner öffnet, neue Seiten werden
  an das offene Dokument angehängt.
- **Bilder anhängen** → Image-Picker, Bilder werden als Seiten
  angehängt (analog `ImagesToPdf`).
- **PDF anhängen** → `OpenDocument`-Picker, existierende PDF wird
  angehängt.

Das Zieldokument wird **in-place** überschrieben (atomarer Swap). Der
Datensatz behält `id`, `filename`, `filepath`, aktualisiert aber
`pageCount`, `fileSize`, `thumbnailPath` (Thumbnail der Seite 1 kann
sich ändern, wenn vorne angehängt wird — hier aber nur hinten).
`isSearchable` und `extractedText` werden invalidiert.

## Nicht-Ziele

- Kein Anhängen an die Mitte oder den Anfang eines Dokuments in
  Version 1 (bestehender `ReorderPagesScreen` deckt Sortierung ab).
- Keine Vorschau der angehängten Seiten vor dem Speichern.
- Keine OCR-Auto-Rerun nach Append (Nutzer kann später
  „Durchsuchbar machen" aufrufen).
- Keine verschlüsselten Zieldateien (analog anderer Edits).

## Aktueller Kontext

- `util/PdfEditor.mergePdfs(files, dest)` existiert und wird bereits in
  `MergePdfsUseCase` verwendet.
- `domain/usecase/CreatePdfFromImagesUseCase.kt` erzeugt aus Bildern
  ein PDF mit A4-Layout.
- `ui/home/HomeScreen.kt` startet den GmsDocumentScanner (`launchScanner`)
  und liefert das Ergebnis über `PendingImport.Scan` an `saveScan`.
- `domain/usecase/ImportScanUseCase.kt` kopiert die Scan-Ausgabe nach
  `filesDir/scans/`.
- **Atomares Überschreiben** ist im Projekt Standard (siehe
  `PdfEditor.writePdf(...)`, `mergePdfs(...)`, `reorderPages(...)`):
  Ausgabe in Temp-Datei, danach `Files.move(..., ATOMIC_MOVE,
  REPLACE_EXISTING)`.

## Architektur

### Neuer Workflow-Schritt

`domain/workflow/AppendToPdfWorkflow.kt`:

```kotlin
sealed interface AppendSource {
    data class Scan(val pdfUri: Uri, val pageCount: Int) : AppendSource
    data class Pdf(val pdfUri: Uri) : AppendSource
    data class Images(val uris: List<Uri>, val layout: ImagePageLayout) : AppendSource
}

class AppendToPdfWorkflow @Inject constructor(
    private val appendToPdfUseCase: AppendToPdfUseCase
) {
    suspend operator fun invoke(
        target: ScanRecord,
        source: AppendSource
    ): WorkflowResult<AppendResult>
}
```

`AppendSource.Scan.pageCount` ist nur UI-/Progress-Metadatum. Die DB wird
nach dem Merge mit `pdfEditor.getPageCount(targetFile)` aktualisiert, damit
falsche Scanner- oder Picker-Metadaten keinen dauerhaften Drift erzeugen.

`WorkflowErrorMapper` erhält neue Fälle:

- `ScanWorkflowError.AppendFailed`
- `ScanWorkflowError.AppendTargetEncrypted`

### Neuer UseCase

`domain/usecase/AppendToPdfUseCase.kt` — konsumiert die Primitiven, die
es bereits gibt:

```kotlin
class AppendToPdfUseCase @Inject constructor(
    private val fileUtil: FileUtil,
    private val pdfEditor: PdfEditor,
    private val repository: ScanRepository,
    private val imagePdfBuilder: ImagePdfBuilder // neue Hilfsklasse, ohne DB-Insert
) {
    suspend operator fun invoke(
        target: ScanRecord,
        source: AppendSource
    ): AppendResult {
        val targetFile = File(target.filepath)
        require(targetFile.exists())
        if (target.isEncrypted) throw AppendTargetEncryptedException()

        val sourcePdfFile: File = when (source) {
            is AppendSource.Pdf  -> fileUtil.copyToTemp(source.pdfUri, suffix = ".pdf")
            is AppendSource.Scan -> fileUtil.copyToTemp(source.pdfUri, suffix = ".pdf")
            is AppendSource.Images -> imagePdfBuilder.createTempPdf(source.uris, source.layout)
        }
        try {
            if (pdfEditor.isPdfEncrypted(sourcePdfFile)) {
                throw AppendSourceEncryptedException()
            }

            // Wichtig: PdfEditor.mergePdfs schreibt über temp + ATOMIC_MOVE.
            // Output darf hier das Ziel selbst sein; kein zusätzlicher renameTo-Swap.
            pdfEditor.mergePdfs(listOf(targetFile, sourcePdfFile), targetFile)

            val newPageCount = pdfEditor.getPageCount(targetFile)
            val newSize = targetFile.length()

            repository.invalidateAfterAppend(
                id = target.id,
                pageCount = newPageCount,
                fileSize = newSize
            )

            return AppendResult(newPageCount, newSize)
        } finally {
            sourcePdfFile.delete()
        }
    }
}
```

**Wichtig:** `isSearchable` muss auf `false` zurückgesetzt werden und
`extractedText` auf `null`. Dafür eine neue DAO-Query nötig:

```kotlin
@Query("""
    UPDATE scan_records
    SET is_searchable = 0,
        extracted_text = NULL,
        tags = NULL,
        ocr_confidence = NULL,
        ocr_language = NULL,
        fileSize = :fileSize,
        pageCount = :pageCount
    WHERE id = :id
""")
suspend fun invalidateAfterAppend(id: Long, fileSize: Long, pageCount: Int)
```

Plus Wrapper im Repository.

**Verbesserung:** Die Query enthält schon die F3-Spalten. Wenn F2 vor F3
landet, Variante ohne `ocr_*`-Spalten verwenden und beim F3-Merge erweitern.
Wichtig ist die Semantik: Jede Content-Änderung invalidiert Searchability,
Volltext, Tags und OCR-Qualitätsdaten gemeinsam.

### FileUtil-Erweiterung

`util/FileUtil.kt` bekommt `copyToTemp(uri: Uri, suffix: String): File` —
kopiert den URI-Inhalt in eine eindeutige Datei in
`storageProvider.tempDir()` (`cacheDir/temp`). Wird nach dem Merge in
`finally` gelöscht.

Alternative (empfohlen): `ImportFileUseCase` hat bereits die URI→File-
Copy-Logik. Wir extrahieren die reine Copy-Funktion in eine öffentliche
Hilfsfunktion auf `FileUtil`, aber ohne Dateinamen-Reservierung in
`scansDir()` und ohne DB-Eintrag.

**Wichtig:** `CreatePdfFromImagesUseCase` darf für den Append-Images-Pfad
nicht direkt verwendet werden, weil er ein neues Archiv-Dokument speichert.
Die gemeinsame Logik besser in eine kleine Hilfsklasse extrahieren:

```kotlin
class ImagePdfBuilder @Inject constructor(
    @ApplicationContext private val context: Context,
    private val pdfEditor: PdfEditor,
    private val storageProvider: StorageProvider
) {
    suspend fun createTempPdf(uris: List<Uri>, layout: ImagePageLayout): File
}
```

`CreatePdfFromImagesUseCase` und `AppendToPdfUseCase` nutzen danach beide
diese Hilfsklasse; nur `CreatePdfFromImagesUseCase` legt einen neuen
`ScanRecord` an.

### UI-Einstiegspunkte

**Im DocumentEditSheet (`ui/components/DocumentEditSheet.kt`):**

Neuer Eintrag in Section „Seiten":

```kotlin
data object AppendPages : ScanAction
```

`SheetItem(Icons.Default.PostAdd, R.string.action_append_pages, notEncrypted)`

**Im Viewer und HomeScreen:** `ScanAction.AppendPages` →
`onNavigateToAppend(record.id)`.

### Neuer Screen: AppendScreen

Route: `Screen.AppendPages : Screen("append-pages/{scanId}")`.

`ui/append/AppendScreen.kt` + `ui/append/AppendViewModel.kt`.

Screen-Layout:

- `ActionScreenContent` (analog anderer Aktions-Screens) mit
  `ScanPreviewCard` für das Ziel-Dokument.
- Drei große Buttons (analog `HomeAddDocumentSheet`):
  - „Scan anhängen" → startet `GmsDocumentScanner`.
  - „Bilder anhängen" → `GetMultipleContents`-Launcher + Layout-
    Auswahl (reuse `ImagesToPdfScreen` — siehe unten).
  - „PDF anhängen" → `OpenDocument`-Launcher (`application/pdf`).
- Nach erfolgreicher Aktion: Workflow ausführen → Navigation zurück
  mit Success-Toast „N Seiten angehängt".

**Bilder-Pfad:** Hier wird ein neuer Modus nötig. Nicht den bestehenden
`ImagesToPdfScreen` hart umbiegen, weil dessen URI-Bridge aktuell über den
`HomeViewModel`-Backstack läuft. Besser:

- Layout-Auswahl und Thumbnail-Grid in ein wiederverwendbares
  `ImagesPdfOptionsContent` extrahieren.
- `ImagesToPdfScreen` nutzt dieses Content-Composable weiterhin für
  „neues PDF".
- `AppendScreen` hält die gewählten Bild-URIs im `AppendViewModel` und
  nutzt dasselbe Content-Composable inline oder als Child-Route.
- `AppendViewModel.appendImages(uris, layout)` ruft `AppendToPdfUseCase`;
  nur der bestehende Create-Pfad ruft `CreatePdfFromImagesUseCase`.

Damit bleibt die bestehende Home-URI-Bridge unangetastet und der Append-Pfad
hängt nicht implizit vom Home-Backstack ab.

**Scan-Pfad:** Der Scanner ist stateless — wir können den existierenden
Launcher direkt im `AppendScreen` verwenden (Duplikat der 10 Zeilen
aus `HomeScreen.launchScanner`). Alternativ: extrahieren in
`util/DocumentScannerStarter.kt` — lohnt nur, wenn der HomeScreen-Pfad
auch darauf umgestellt wird (optionaler Refactor).

### AppendViewModel

```kotlin
@HiltViewModel
class AppendViewModel @Inject constructor(
    private val repository: ScanRepository,
    private val appendWorkflow: AppendToPdfWorkflow,
    private val workflowErrorMapper: WorkflowErrorMapper,
    private val resourceProvider: ResourceProvider,
    private val savedStateHandle: SavedStateHandle
) : ViewModel()
```

- `record: StateFlow<ScanRecord?>` aus `repository.getAllScans()` +
  `find { it.id == scanId }`.
- `_editLoading`, `_error`, `_success` analog anderer Edit-ViewModels.
- `appendScan(pdfUri, pageCount)`, `appendImages(uris, layout)`,
  `appendPdf(uri)`.

## Fehlerfälle

- **Zieldatei gelöscht/nicht mehr da** → `AppendFailed` (allg. Fehler).
- **Ziel ist verschlüsselt** → `AppendTargetEncrypted` mit Hinweis
  „Passwort entfernen". Sheet-Eintrag ist bereits via `notEncrypted`
  deaktiviert; Screen-Level-Check als Sicherheitsnetz.
- **Quell-PDF korrupt / leer** → `AppendFailed`; User sieht
  „Anhängen fehlgeschlagen".
- **Disk voll bei Merge** → `PdfEditor.mergePdfs(..., targetFile)` wirft
  `IOException`; die interne temp-Datei wird gelöscht und das Ziel bleibt
  intakt. Kein manueller `renameTo` im UseCase.
- **Quelle gleich Ziel** (Nutzer wählt dasselbe Dokument aus dem
  Picker): `pdfEditor.mergePdfs` mit `listOf(targetFile, targetFile)`
  funktioniert durchaus, ist aber verwirrend. Check: Quell-URI-Pfad
  mit Ziel-Pfad vergleichen (falls über FileProvider geliefert) →
  warnen oder Kopie zulassen.

## Datenschutz

- Keine neuen Datenflüsse: Scan bleibt GMS-lokal, Bilder/PDFs kommen
  aus Nutzer-Picker.
- `docs/privacy-policy.html` bleibt unverändert — der Text „Bilder und
  PDFs werden lokal verarbeitet" deckt Append mit.

## Tests

`domain/usecase/AppendToPdfUseCaseTest.kt` (JVM, echter PDFBox via
`PdfTestFixtures`):

- Append eines 2-Seiten-PDFs an 3-Seiten-PDF → 5 Seiten.
- Append von 1 Bild an 1-Seiten-PDF → 2 Seiten (über
  `ImagePdfBuilder`-/`PdfEditor.createPdfFromImages`-Pfad, ohne neuen
  `ScanRecord`).
- Atomarer Swap: bei simulierter IOException während Merge bleibt das
  Original intakt und `repository.invalidateAfterAppend` wird nicht gerufen.
- `isSearchable`, `extractedText`, `tags` und später `ocr_*` werden nach
  Append invalidiert.
- PageCount wird aus dem Ergebnis-PDF gelesen, nicht aus
  `target.pageCount + source.pageCount` geraten.
- Temp-Quellen werden im Erfolgs- und Fehlerfall gelöscht.
- Verschlüsseltes Ziel wirft Exception.

`domain/workflow/AppendToPdfWorkflowTest.kt`:

- Datei fehlt → `AppendFailed`.
- Ziel verschlüsselt → `AppendTargetEncrypted`.
- Erfolg → `WorkflowResult.Success`.

`ui/append/AppendViewModelTest.kt`:

- `editLoading`-Guard, Success/Failure-Mapping, `clearError`.

`androidTest/` — Scanner-Pfad ist schwer zu automatisieren (GMS-UI).
Der Image- und PDF-Pfad lässt sich aber als Instrumentation-Test
fahren (`content://`-URI aus Assets-Kopie).

## Rollout

### Phase 2.1 — UseCase + Workflow

- `AppendToPdfUseCase`, `AppendToPdfWorkflow`,
  `WorkflowErrorMapper`-Fälle.
- `FileUtil.copyToTemp(...)` über `StorageProvider.tempDir()`.
- `ImagePdfBuilder` extrahieren, damit Images-Append keine neuen
  Archiv-Records erzeugt.
- DAO-Query `invalidateAfterAppend`.
- JVM-Tests.

### Phase 2.2 — UI: Append-Screen

- `Screen.AppendPages` + Navigation.
- `AppendScreen` + `AppendViewModel`.
- `ScanAction.AppendPages` + Sheet-Eintrag.
- Launcher für Scan/Bilder/PDF.

### Phase 2.3 — Integration Bilder

- `ImagesPdfOptionsContent` aus `ImagesToPdfScreen` extrahieren.
- Append-spezifische URI-Bridge im `AppendViewModel`, nicht im
  `HomeViewModel`.
- Success-Navigation zurück zum Viewer statt Archiv.

## Fallstricke

1. **Atomarer Swap muss wirklich atomar sein.** `File.renameTo()` ist
   nicht der bevorzugte neue Pfad. `PdfEditor.writePdf(...)` und
   `PdfEditor.mergePdfs(...)` verwenden bereits `Files.move` mit
   `ATOMIC_MOVE` und `REPLACE_EXISTING`; Append soll diese zentrale
   Implementierung wiederverwenden.
2. **`isSearchable` invalidieren, nicht erhalten.** Der angehängte
   Bereich hat keinen Text-Layer. Das Dokument als durchsuchbar zu
   markieren würde die Suche verfälschen.
3. **`extractedText` aufräumen.** Sonst findet FTS alten Text zu
   Seiten, die nicht mehr vorn liegen (das ist weniger schlimm, weil
   Text inhaltlich gleich ist — trotzdem: wir setzen ihn bewusst auf
   `null`, damit eine Re-OCR konsistente Ergebnisse liefert).
4. **Bitmap-Cache des Viewers.** Der Viewer öffnet bei `filepath`+
   `fileSize`-Wechsel neu (`onRecordChanged`). Append ändert
   `fileSize` → Cache/Handle werden korrekt neu aufgebaut.
5. **Quell-URI-Persistenz.** Bei `OpenDocument` muss
   `takePersistableUriPermission` NICHT aufgerufen werden — wir
   kopieren sofort und verwerfen den URI.
6. **Thumbnail-Konsistenz.** Solange wir nur hinten anhängen, bleibt
   Seite 1 gleich → Thumbnail muss nicht neu generiert werden. Wenn
   später „vorne anhängen" kommt, muss `PdfEditor.generateThumbnail`
   neu laufen.
7. **Images-Append darf keinen zweiten Record erzeugen.** Alles, was
   aus Bildern ein temporäres PDF baut, muss vor dem Repository-Insert
   getrennt werden. `CreatePdfFromImagesUseCase` direkt aufzurufen
   wäre hier falsch.
8. **PageCount nicht addieren, sondern messen.** Scanner- und Picker-
   Metadaten können falsch oder unvollständig sein. Nach dem Merge ist
   `pdfEditor.getPageCount(targetFile)` die Quelle der Wahrheit.

---

# F3 — OCR-Qualitätsansicht

## Ziel

Nach einer OCR-Operation sieht der Nutzer in einem Review-Screen:

- den erkannten Text mit Seitentrennern,
- die durchschnittliche ML-Kit-Konfidenz als Prozent-Badge,
- die erkannte Sprache (ML Kit liefert das pro TextBlock),
- eine Warnung bei niedriger Qualität (<30 %),
- Aktionen „Mit anderer Sprache neu erkennen" und „Kopieren/Teilen".

Zusätzlich zeigt jedes Archiv-Dokument, das durchsuchbar ist, dezent
die OCR-Qualität an (Quality-Badge auf der `ScanItem`-Card).

## Nicht-Ziele

- Keine Text-Bearbeitung. Der Review-Screen ist read-only (Textauswahl
  + Copy reichen).
- Keine pro-Seite-Detailansicht mit Bounding-Boxes.
- Keine OCR-Korrektur per Sprachmodell.
- Kein Wechsel des Text-Layers eines bereits durchsuchbaren PDFs
  (Re-OCR überschreibt den Text-Layer, nicht Einzelfelder).

## Aktueller Kontext

- `util/OcrPipeline.kt` liefert bereits `OcrResultStats(confidence,
  recognizedLanguage, angle)`.
- `util/OcrThresholds` kennt bereits Warn-Schwellen
  (`LOW_CONFIDENCE_WARNING = 0.3f`, `AUTO_DETECTION_UNCERTAIN = 0.6f`).
- `HomeViewModel.maybeReportOcrWarning(...)` zeigt bereits eine
  Toast-Warnung nach OCR.
- `ExtractTextUseCase` gibt `Pair<String, OcrResultStats?>` zurück.
- `MakeSearchableUseCase` wirft Stats derzeit weg (ruft nur
  `searchablePdfBuilder.makeSearchable`, das intern Stats kennt).
- `HomeOcrResultSheet` zeigt Text an, aber ohne Qualitätsinfo.
- `ScanRecord` hat `extractedText`, aber keine Konfidenz/Sprache.

## Architektur

### Persistenz der OCR-Stats

**Empfehlung: persistieren, nicht on-demand neu rechnen.**

Neue Spalten in `scan_records` (DB Version 7, zweite Migration in F3):

```sql
ocr_confidence REAL         -- nullable, 0.0-1.0
ocr_language TEXT           -- nullable, ISO-Code aus ML Kit
ocr_page_text_json TEXT     -- nullable, JSON-Liste der Seitentexte für Review
```

Migration `MIGRATION_6_7`:

```kotlin
db.execSQL("ALTER TABLE scan_records ADD COLUMN ocr_confidence REAL")
db.execSQL("ALTER TABLE scan_records ADD COLUMN ocr_language TEXT")
db.execSQL("ALTER TABLE scan_records ADD COLUMN ocr_page_text_json TEXT")
```

Auch `MIGRATION_6_7` in `DatabaseModule.addMigrations(...)` registrieren.

`ScanRecord`:

```kotlin
@ColumnInfo(name = "ocr_confidence") val ocrConfidence: Float? = null,
@ColumnInfo(name = "ocr_language")   val ocrLanguage: String? = null,
@ColumnInfo(name = "ocr_page_text_json") val ocrPageTextJson: String? = null,
```

**Begründung:** Re-OCR eines langen PDFs dauert Sekunden. Wenn der
Nutzer das Qualitätsbadge im Archiv sehen soll, müssen Stats ohne
erneutes OCR verfügbar sein. Einmal schreiben, oft lesen.

**Verbesserung:** Die zusätzliche `ocr_page_text_json`-Spalte ist nötig,
wenn der Review-Screen echte Seitentrenner aus dem Cache zeigen soll.
`extractedText` bleibt der normalisierte Volltext für FTS/Suche; die
Seitendarstellung bleibt UI-spezifisch und kann lokalisiert werden.

### DAO + Repository

Bestehende `markSearchableWithContent`-Signatur erweitern:

```kotlin
@Query("""
    UPDATE scan_records
    SET is_searchable = 1,
        fileSize = :fileSize,
        extracted_text = :text,
        tags = :tags,
        ocr_confidence = :confidence,
        ocr_language = :language,
        ocr_page_text_json = :pageTextJson
    WHERE id = :id
""")
suspend fun markSearchableWithContent(
    id: Long,
    fileSize: Long,
    text: String?,
    tags: String?,
    confidence: Float?,
    language: String?,
    pageTextJson: String?
)
```

Aufrufer: `MakeSearchableUseCase`, `ImportScanUseCase`.

Zusätzlich für reine Textextraktion:

```kotlin
@Query("""
    UPDATE scan_records
    SET extracted_text = :text,
        ocr_confidence = :confidence,
        ocr_language = :language,
        ocr_page_text_json = :pageTextJson
    WHERE id = :id
""")
suspend fun updateExtractedTextAndOcrStats(
    id: Long,
    text: String?,
    confidence: Float?,
    language: String?,
    pageTextJson: String?
)
```

### Strukturierte OCR-Ergebnisse

Vor `MakeSearchableUseCase` und Review-UI eine gemeinsame Ergebnisform
einführen:

```kotlin
data class OcrDocumentResult(
    val recordId: Long,
    val fullText: String,
    val pageTexts: List<String>,
    val stats: OcrResultStats?
)

data class SearchableResult(
    val extractedText: String,
    val pageTexts: List<String>,
    val stats: OcrResultStats?
)
```

`pageTexts` wird als JSON persistiert. Dafür keine neue große
Serialisierungsabhängigkeit einführen; `org.json.JSONArray` reicht für eine
Liste von Strings.

### MakeSearchableUseCase

Gibt zusätzlich den `OcrResultStats` zurück. Dafür muss
`SearchablePdfBuilder.makeSearchable(...)` in eine Form, die Stats
sichtbar macht. Bestehende Tests einmalig anpassen.

```kotlin
suspend fun makeSearchable(pdfFile: File, lang: String, onProgress, onStatus)
    : SearchableResult
data class SearchableResult(
    val extractedText: String,
    val pageTexts: List<String>,
    val stats: OcrResultStats?
)
```

Der Builder sammelt PageData ohnehin seitenweise; diese Information nicht
wieder zu einem untrennbaren String reduzieren.

### ExtractTextUseCase

Aktuell `Pair<String, OcrResultStats?>`; das ist für F3 zu grob. Für
Review-Screen und Persistenz auf strukturierte Ergebnisse umstellen:

```kotlin
suspend operator fun invoke(
    records: List<ScanRecord>,
    languageCode: String,
    onStatus: (OcrPipelineStatus) -> Unit = {}
): List<OcrDocumentResult>
```

Der ViewModel-Pfad kann daraus weiterhin einen kombinierten Text für
Bulk-Anzeige/Share bauen. Persistiert wird aber pro Record über
`updateExtractedTextAndOcrStats(...)`, selbst wenn `is_searchable = false`.
Sauber trennen von `markSearchableWithContent`, weil `extractText`
Searchability nicht ändert.

### Qualitäts-Mapping

`util/OcrQuality.kt`:

```kotlin
enum class OcrQuality { HIGH, MEDIUM, LOW, UNKNOWN }

fun OcrResultStats?.toQuality(): OcrQuality = when {
    this == null -> OcrQuality.UNKNOWN
    confidence >= 0.7f -> OcrQuality.HIGH
    confidence >= OcrThresholds.LOW_CONFIDENCE_WARNING -> OcrQuality.MEDIUM
    else -> OcrQuality.LOW
}

fun Float?.toQuality(): OcrQuality = when {
    this == null -> OcrQuality.UNKNOWN
    this >= 0.7f -> OcrQuality.HIGH
    this >= OcrThresholds.LOW_CONFIDENCE_WARNING -> OcrQuality.MEDIUM
    else -> OcrQuality.LOW
}

fun Float.toQualityPercent(): Int = (this * 100).roundToInt()
```

Mapping-Test: `OcrQualityTest.kt`.

### UI-Integration

**1. Review-Screen ersetzt `HomeOcrResultSheet`.**

Neuer Screen: `Screen.OcrReview : Screen("ocr-review/{scanId}")`.

Warum Screen statt Sheet: Mehr Platz für Stats, Re-OCR-Aktion braucht
einen Sprach-Dropdown, und bei langen Texten ist ein eigener Screen
lesbarer. Sheet bleibt aus Kompatibilität, aber der Default-Pfad
„Text extrahieren" navigiert auf den Review-Screen.

`ui/ocr/OcrReviewScreen.kt`:

- Top: Dateiname + Seitenanzahl.
- Quality-Card:
  - Konfidenz-Badge farbig (grün / gelb / rot).
  - Erkannte Sprache (lokalisierter Name über `Locale.forLanguageTag`).
  - Warntext bei `LOW` (aus `R.string.ocr_review_low_warning`).
- Text-Body: `SelectionContainer` + `Text(text)`, scrollbar.
  Wenn `pageTexts` vorhanden ist, rendert die UI lokalisierte
  Seitentrenner zur Laufzeit (`R.string.ocr_review_page_header`), statt
  lokalisierte Trenner in der DB zu speichern.
- Action-Row:
  - „Mit anderer Sprache neu erkennen" → öffnet Sprach-Dropdown
    (`buildOcrLanguageOptions`) + Button „OCR starten".
    → ruft `extractText(record, lang)` auf, Screen aktualisiert.
  - „Kopieren" → Clipboard.
  - „Teilen" → `ACTION_SEND` mit Plain-Text.

`ui/ocr/OcrReviewViewModel.kt`:

```kotlin
@HiltViewModel
class OcrReviewViewModel @Inject constructor(
    private val repository: ScanRepository,
    private val extractTextUseCase: ExtractTextUseCase,
    private val resourceProvider: ResourceProvider,
    private val dispatcherProvider: DispatcherProvider,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    data class UiState(
        val record: ScanRecord? = null,
        val text: String? = null,
        val pageTexts: List<String> = emptyList(),
        val stats: OcrResultStats? = null,
        val quality: OcrQuality = OcrQuality.UNKNOWN,
        val loading: Boolean = false,
        val error: String? = null
    )

    val uiState: StateFlow<UiState>

    init {
        // 1) Record laden
        // 2) Wenn record.extractedText != null → direkt anzeigen
        //    (Stats aus record.ocrConfidence/ocrLanguage rekonstruieren,
        //     pageTexts aus ocrPageTextJson lesen; bei Altdaten fallback: ein Textblock)
        // 3) Sonst: extractText auslösen
    }

    fun reExtract(languageCode: String)
}
```

**2. Quality-Badge in ScanItem.**

`ui/home/components/ScanItem.kt` zeigt für `record.isSearchable` ein
kleines Badge neben der Seitenzahl:

```kotlin
if (record.isSearchable) {
    OcrQualityBadge(record.ocrConfidence.toQuality())
}
```

`OcrQualityBadge`: Chip mit Icon + Prozentwert, Farbe nach Quality.
Bei `LOW` eine orange Tonfarbe + `Warning`-Icon.

**3. Einstieg vom OCR-Result-Button:**

`HomeScreen` ruft bei Klick auf ein OCR-Ergebnis künftig
`onNavigateToOcrReview(record.id)` statt das Sheet zu öffnen.

Das Sheet `HomeOcrResultSheet` wird entfernt. Single Source of Truth
ist der Review-Screen.

### Warnungen verbessern

`HomeViewModel.maybeReportOcrWarning` entfällt. Niedrige Konfidenz
wird künftig im Review-Screen durch den Badge angezeigt, nicht als
Error-Toast. Das reduziert die ungewollte „Das war ein Fehler!"-
Optik bei OCR mit moderater Qualität.

**Ausnahme:** Auto-Mode + Konfidenz < `AUTO_DETECTION_UNCERTAIN` +
keine erkannte Sprache → weiterhin als Toast, weil der Nutzer hier
direkt Feedback zur Sprachauswahl braucht, bevor er den Review
überhaupt sieht.

## Fehlerfälle

- **ExtractText schlägt fehl (`OcrNoTextException`)** → Review-Screen
  zeigt Empty-State „Kein Text erkannt" + Sprachwahl-CTA.
- **Re-OCR schlägt fehl** → Review-Screen behält alten Text, zeigt
  Error-Banner.
- **Record ohne `extractedText` aber mit `isSearchable = true`
  (Altdaten)** → Screen zeigt Spinner + triggert `extractText` einmal.
- **Konfidenz `null` (Altdaten ohne Stats)** → Badge zeigt
  `UNKNOWN`-Placeholder, keine Warnung.

## Tests

`util/OcrQualityTest.kt`:

- Mapping für alle Grenzwerte.
- `null`-Input → `UNKNOWN`.

`ui/ocr/OcrReviewViewModelTest.kt`:

- Laden aus `extractedText`-Cache ohne OCR.
- Laden aus `ocr_page_text_json` zeigt lokalisierbare Seitentrenner.
- Re-OCR mit anderer Sprache aktualisiert Text + Stats.
- `OcrNoTextException` → `error`-State.

`domain/usecase/ExtractTextUseCaseTest.kt` (wenn nötig anpassen):
- Rückgabe enthält pro Record `fullText`, `pageTexts` und Stats.
- Persistenz wird im ViewModel/Workflow-Test geprüft, nicht in einer
  reinen OCR-Extraktion, falls `ExtractTextUseCase` bewusst repository-frei
  bleibt.

`androidTest/ImportAndPdfEditorInstrumentedTest` erweitern um Pfad
„Nach OCR sind `ocr_confidence`, `ocr_language` und
`ocr_page_text_json` in der DB gesetzt".

Migrationstest 6→7.

## Datenschutz

- ML Kit läuft lokal (bereits so). Keine neuen Datenflüsse.
- `docs/privacy-policy.html` unverändert.

## Rollout

### Phase 3.1 — Persistenz

- Migration 6→7 + Spalten + `ScanRecord`-Felder.
- `MIGRATION_6_7` in `DatabaseModule.addMigrations(...)` registrieren.
- DAO-Signaturen + Repository-Wrapper.
- `OcrDocumentResult` / `SearchableResult` mit `pageTexts` einführen.
- `ExtractTextUseCase` / `MakeSearchableUseCase` liefern strukturierte
  Stats; Persistenz pro Record über Repository.
- `SearchablePdfBuilder`-Rückgabe erweitern.
- Silent Backfill in `HomeViewModel.triggerSilentBackfill()` auf die neuen
  Stats-Felder erweitern oder bewusst bei `UNKNOWN` belassen.
- Unit-Tests.

### Phase 3.2 — Review-Screen

- `Screen.OcrReview` + Navigation.
- `OcrReviewScreen` + `OcrReviewViewModel`.
- OCR-Result-Pfad von Sheet auf Screen umstellen.
- `HomeOcrResultSheet` entfernen.
- 10-Locale-Strings.

### Phase 3.3 — Quality-Badge im Archiv

- `OcrQuality`-Mapping.
- `OcrQualityBadge`-Composable.
- `ScanItem` zeigt Badge für durchsuchbare Records.

## Fallstricke

1. **`SearchablePdfBuilder` wird von bestehenden Tests gemockt** (siehe
   `MakeSearchableUseCaseTest` → `FakeSearchablePdfBuilder`). Wenn wir
   die Rückgabe auf `SearchableResult` ändern, müssen die Fakes
   nachgezogen werden. Sonst brechen 5+ Unit-Tests.
2. **ML-Kit-Confidence schwankt** über unterschiedliche Sprachen
   (Latin vs. CJK). Das Badge darf nicht so tun, als sei 60 % in
   Chinesisch gleich schlecht wie 60 % in Deutsch. Wir zeigen die Zahl
   bewusst nur als grober Qualitätsindikator, ohne absolute Skala im
   UI zu behaupten — Text hinter dem Badge lautet „Erkennungsqualität",
   nicht „Trefferquote".
3. **Pro-Seite-Sprache vs. Dokument-Sprache.** ML Kit liefert
   `recognizedLanguage` pro TextBlock; wir speichern nur eine Sprache
   für das ganze Dokument (die der ersten erfolgreichen Seite, analog
   `OcrPipeline`). Das ist eine bewusste Vereinfachung — im Review-
   Screen als „dominante Sprache" deklarieren.
4. **Re-OCR invalidiert den Text-Layer** bei durchsuchbaren PDFs.
   Das ist **schon heute** so (`MakeSearchableUseCase` mit
   `force = true`). Neu ist nur, dass der Review-Screen diese Aktion
   explizit ausführt.
5. **Badge für Record ohne Stats**: wir zeigen `UNKNOWN` ohne Farbe,
   kein Warn-Icon. Wichtig für Altdaten aus Version < 7.
6. **DB-Migration auf Version 7 läuft hinter F1 (6).** Reihenfolge
   beim Ausrollen: erst F1 (Version 6), dann F3 (Version 7). Wenn F3
   ohne F1 rausgeht, Version direkt 6.
7. **`maybeReportOcrWarning` entfernen**, aber den Auto-Mode-Fall
   behalten. Sonst verschwindet das Feedback „Sprachautomatik unsicher".
8. **Seitentrenner brauchen Seitendaten.** Nicht versuchen, Seiten im
   Review-Screen aus einem flachen `extractedText` zurückzuerraten.
   Entweder `ocr_page_text_json` befüllen oder bei Altdaten ohne Page-JSON
   bewusst einen einzigen Textblock anzeigen.
9. **Bulk-OCR darf Stats nicht nur für den ersten Record speichern.**
   Die aktuelle `Pair<String, OcrResultStats?>`-Form verliert pro Record
   die Qualität. Für F3 muss Persistenz auf Record-Ebene passieren.
10. **Sprache `und`/leer behandeln.** ML Kit kann unbekannte Sprache als
   leer oder `und` liefern; UI zeigt dafür `UNKNOWN`/„Unbekannt" statt
   `Locale.forLanguageTag("und")` blind zu verwenden.

---

## Übergreifende Fragen (vor Implementierung klären)

- **DB-Versionierung:** F1 → Version 6, F3 → Version 7. Bestätigt?
- **WorkManager:** Wirklich erst später einführen, oder gleich für
  Trash-Cleanup? Empfehlung: später.
- **Append-Thumbnail:** In Version 1 nur hinten anhängen (Thumbnail
  unverändert). Vorne/Mitte kommt später.
- **OCR-Review ersetzt Sheet oder ergänzt?** Plan: ersetzt (Single
  Source of Truth). Bestätigt?
- **Badge-Darstellung im Archiv:** Dezent (nur bei `LOW`) oder
  immer sichtbar? Empfehlung: immer, aber nur bei `LOW` mit
  Warn-Farbe.
- **OCR-Seitentexte persistieren?** Empfehlung: ja, mit
  `ocr_page_text_json`; sonst kann der Review-Screen aus gecachten
  Ergebnissen keine echten Seitentrenner anzeigen.
- **DAO-Testfakes konsolidieren?** Empfehlung: vor F1 erledigen, weil
  F1/F3 mehrere DAO-Methoden hinzufügen und sonst viele Tests mehrfach
  angepasst werden müssen.
