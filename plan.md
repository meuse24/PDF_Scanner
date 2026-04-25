# Verbesserungsplan PDF-Scanner

Vier technische Pakete, priorisiert nach Risiko und Nutzen. Jeder Befund wurde
gegen den aktuellen Code verifiziert; Korrekturen und Ergänzungen gegenüber den
ursprünglichen Vorschlägen sind als **Präzisierung** markiert.

---

## 1 — Suche und Archivliste performanter machen

**Aufwand:** mittel | **Risiko:** niedrig

### Befund

`filteredScansFlow` (HomeViewModel.kt:103) kombiniert den vollständig geladenen
`scansFlow` mit `_searchQuery` und filtert in `filterScans` (HomeViewModel.kt:644)
per `extractedText?.contains(…)` im Speicher. Bei großen Archiven mit langen
OCR-Volltexten und Page-JSON-Blobs bedeutet das:

- Alle Zeilen inkl. großer TEXT-Felder wandern per SQL in den Heap.
- Jede Eingabe im Suchfeld triggert Kotlin-seitiges Durchsuchen aller Strings.

`searchScansFlow` (ScanDao.kt:29) mit FTS4-MATCH existiert bereits, wird
produktiv aber nicht genutzt.

Die `ScanRecord`-Entity hat nur `Index("folder_id")` – für `deleted_at`,
`timestamp` und `is_favorite`, die in jeder Listenabfrage im WHERE/ORDER BY
stehen, fehlen Indizes.

### Geplante Änderungen

#### 1a – FTS-Suche im ViewModel aktivieren

`HomeViewModel` soll bei nichtleerem Query statt `getAllScans` + Kotlin-Filter
direkt `searchScansFlow(ftsQuery)` aus dem Repository nutzen:

```kotlin
// HomeViewModel
private val filteredScansFlow = combine(archiveFilterFlow, _searchQuery, _sortOrder)
    { filter, rawQuery, sortOrder ->
        val query = rawQuery.trim()
        if (query.isBlank()) {
            baseScansFor(filter)   // vorhandene Logik
        } else {
            repository.searchScans(buildFtsQuery(query))
        }
    }.flatMapLatest { flow -> flow }
     .map { sortScans(it, _sortOrder.value) }
     .stateIn(…)
```

`buildFtsQuery` escaped Sonderzeichen und hängt `*` an (`"foo bar"` → `"foo* bar*"`).

#### 1b – Leichte Projektion für die Archivliste

**Präzisierung:** `searchScansFlow` liefert heute noch vollständige `ScanRecord`-
Zeilen mit `extracted_text` und `ocr_page_text_json`. Für die Listenansicht
werden diese Felder nicht benötigt.

Lösung: ein schlankes `ScanListItem`-DTO + eigene DAO-Query:

```sql
SELECT id, filename, filepath, timestamp, pageCount, fileSize,
       thumbnail_path, is_searchable, is_encrypted, deleted_at,
       folder_id, is_favorite
FROM   scan_records
WHERE  deleted_at IS NULL
ORDER  BY timestamp DESC
```

Volltextfelder (`extracted_text`, `ocr_page_text_json`, `ocr_confidence`,
`ocr_language`, `tags`) bleiben im vollständigen `ScanRecord`, das nur beim
OCR-Review oder im Viewer geladen wird.

#### 1c – Fehlende DB-Indizes ergänzen

In `ScanRecord.kt` die `@Entity`-Annotation erweitern:

```kotlin
@Entity(
    tableName = "scan_records",
    indices = [
        Index("folder_id"),
        Index("deleted_at"),
        Index("timestamp"),
        Index("is_favorite")
    ]
)
```

Das erfordert eine Room-Migration (Version 8 → 9) mit:

```sql
CREATE INDEX index_scan_records_deleted_at  ON scan_records(deleted_at);
CREATE INDEX index_scan_records_timestamp   ON scan_records(timestamp);
CREATE INDEX index_scan_records_is_favorite ON scan_records(is_favorite);
```

---

## 2 — Domain-Layer von Android/ML-Kit entkoppeln

**Aufwand:** mittel | **Risiko:** niedrig-mittel

### Befund

`ExtractTextUseCase.kt` importiert direkt
`com.google.mlkit.vision.common.InputImage` und
`com.google.mlkit.vision.text.TextRecognizer` (Zeilen 3–4).
Die privaten Methoden `extractFromRecordsWithStats` und
`extractFromRecordWithStats` (Zeilen 68, 87) nehmen `TextRecognizer` als
konkreten Parameter, was den ML-Kit-Typ tief in die Use-Case-Logik zieht.

`ImagePdfBuilder.kt` hält `android.content.Context` und `android.net.Uri`
(Zeilen 3–4) auf Domain-Ebene.

**Präzisierung:** Die Kapselung ist bereits halbfertig –
`OcrInputImageLoader`, `PdfPageInputImageLoader` und `TextRecognizerRunner`
existieren als Wrapper in `util/`. Der eigentliche Leak ist, dass `TextRecognizer`
noch als konkreter Typ durch die privaten Methoden fließt, weil
`OcrPipeline.runWithFallback` den Recognizer direkt an den Lambda übergibt.
Der Vorschlag, neue Ports zu definieren, ist korrekt; die notwendige Änderung
ist kleiner als er andeutet.

### Geplante Änderungen

#### 2a – `TextRecognizer` aus Use Case entfernen

`TextRecognizerRunner` (util/) erhält eine Methode, die das Iterieren über Pages
kapselt, sodass `ExtractTextUseCase` nicht mehr selbst mit `TextRecognizer`
hantiert:

```kotlin
// TextRecognizerRunner (util/) — neue Methode
suspend fun processPages(
    pdfFile: File,
    recognizer: TextRecognizer,
    onPage: suspend (text: String, stats: OcrResultStats?) -> Unit
)
```

`extractFromRecordWithStats` in `ExtractTextUseCase` arbeitet dann nur noch mit
dem `TextRecognizerRunner`-Interface; der `TextRecognizer`-Typ und der
`InputImage`-Import verschwinden aus der Use-Case-Klasse.

#### 2b – Port `PdfImageRenderer` für `ImagePdfBuilder`

Neues Interface in `domain/pdf/`:

```kotlin
interface PdfImageRenderer {
    suspend fun decodeBitmapBytes(uri: Any, maxDimension: Int): ByteArray?
}
```

`ImagePdfBuilder` injiziert `PdfImageRenderer` statt `Context` direkt.
Die Android-Implementierung (`BitmapPdfImageRenderer`) lebt in `util/`.
`Uri` kann vorläufig als `Any` übergeben werden, bis ein vollständiges Port-
Modell sinnvoll ist.

---

## 3 — Cancellation und Fehlerbehandlung in langen Jobs härten

**Aufwand:** klein | **Risiko:** niedrig

### Befund

`DocumentWorkflowGuard.kt:35`:

```kotlin
} catch (throwable: Throwable) {
    exceptionMapper(throwable)?.let { … }
    when (throwable) {
        is IOException -> WorkflowResult.Failure(ScanWorkflowError.StorageWriteFailed(throwable))
        else           -> WorkflowResult.Failure(failureMapper(throwable))
    }
}
```

`MakeSearchableWorkflow.kt:60`:

```kotlin
} catch (t: Throwable) {
    WorkflowResult.Failure(ScanWorkflowError.OcrFailed(t))
}
```

Beide Blöcke fangen `kotlinx.coroutines.CancellationException`. Das Kotlin-
Coroutine-Protokoll verlangt dessen sofortiges Rethrow. Wird es verschluckt,
läuft ein Job nach Scope-Cancel weiter, und `Job.join()` kehrt nie zurück.

### Geplante Änderungen

#### 3a – `DocumentWorkflowGuard` absichern

```kotlin
} catch (throwable: Throwable) {
    if (throwable is CancellationException) throw throwable
    exceptionMapper(throwable)?.let { return WorkflowResult.Failure(it) }
    when (throwable) {
        is IOException -> WorkflowResult.Failure(ScanWorkflowError.StorageWriteFailed(throwable))
        else           -> WorkflowResult.Failure(failureMapper(throwable))
    }
}
```

#### 3b – `MakeSearchableWorkflow` absichern

```kotlin
} catch (e: CancellationException) {
    throw e
} catch (e: IOException) {
    WorkflowResult.Failure(ScanWorkflowError.StorageWriteFailed(e))
} catch (t: Throwable) {
    WorkflowResult.Failure(ScanWorkflowError.OcrFailed(t))
}
```

#### 3c – Alle anderen Workflows prüfen

Grep-Suche nach `catch.*Throwable` im `domain/workflow/`-Paket; jeder Treffer
bekommt denselben CancellationException-Guard. Ergebnis als Einzel-Commit.

---

## 4 — Room-Schema und Datenintegrität absichern

**Aufwand:** mittel | **Risiko:** mittel (DB-Migration erforderlich)

### Befund

`AppDatabase.kt:8`: `exportSchema = false` – Room generiert keine Schema-JSON-
Dateien, Migrationstests können das Schema nicht gegen eine Golden-Datei prüfen.

`ScanRecord.kt`: `folder_id` ist nur per Index abgebildet, kein `@ForeignKey`-
Constraint. Wird ein Ordner gelöscht, bleiben verwaiste `scan_records`-Zeilen
mit ungültigem `folder_id` in der DB.

### Geplante Änderungen

#### 4a – `exportSchema = true` aktivieren

In `AppDatabase.kt`:

```kotlin
@Database(…, version = 9, exportSchema = true)
```

In `build.gradle.kts` (app-Modul) das KSP-Argument ergänzen:

```kotlin
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}
```

Den erzeugten `schemas/`-Ordner committen (kein `.gitignore`-Eintrag).

**Präzisierung:** Die Versions-Erhöhung auf 9 ist ohnehin nötig (Indizes aus
Punkt 1c). Eine `MIGRATION_8_9` kann beide Änderungen – Indizes und FK –
bündeln.

#### 4b – Foreign Key für `folder_id`

**Präzisierung:** SQLite erlaubt kein `ALTER TABLE … ADD CONSTRAINT`. Die
Migration muss das klassische Copy-Rename-Pattern durchführen:

```sql
CREATE TABLE scan_records_new (
    -- alle Spalten, wie in der Entity definiert
    FOREIGN KEY(folder_id) REFERENCES folders(id) ON DELETE SET NULL
);
INSERT INTO scan_records_new SELECT * FROM scan_records;
DROP TABLE scan_records;
ALTER TABLE scan_records_new RENAME TO scan_records;
-- Indizes neu anlegen (s. Punkt 1c)
```

In `ScanRecord.kt`:

```kotlin
@Entity(
    tableName = "scan_records",
    foreignKeys = [ForeignKey(
        entity = FolderEntity::class,
        parentColumns = ["id"],
        childColumns = ["folder_id"],
        onDelete = ForeignKey.SET_NULL
    )],
    indices = [
        Index("folder_id"),
        Index("deleted_at"),
        Index("timestamp"),
        Index("is_favorite")
    ]
)
```

`ON DELETE SET NULL` ist korrekt: ein gelöschter Ordner setzt `folder_id` auf
NULL, das Dokument bleibt erhalten und landet im Root-Archiv.

#### 4c – Migrationstests erweitern

`AppDatabaseMigrationTest` (bereits vorhanden) um Test-Case für Migration 8→9
ergänzen; Schema-JSON der neuen Version als Golden-File einsetzen.

---

## Priorisierung

| # | Paket | Aufwand | Nutzen | Empfehlung |
|---|-------|---------|--------|------------|
| 3 | Cancellation-Härtung | 1–2 h | Bug-Verhütung (Coroutine-Korrektheit) | **zuerst** |
| 1 | FTS + Projektion + Indizes | 1 Tag | Spürbare Perf.-Verbesserung bei wachsendem Archiv | als nächstes |
| 4 | Room exportSchema + FK | 0,5 Tage | Release-Sicherheit, Datenintegrität | danach |
| 2 | Domain-Entkopplung | 1–2 Tage | Testbarkeit, Modularisierung | bei Gelegenheit |

Paket 3 hat das beste Aufwand-Nutzen-Verhältnis und kein Migrations-Risiko –
als Erstes umsetzen.
