# Implementierungsplan: Top-5-Features

Status: Entwurf · Autor: Claude · Datum: 2026-04-24

Dieser Plan beschreibt die Umsetzung von fünf neuen Features für den M24 PDF-Scanner unter strikter Einhaltung der bestehenden Clean-Architecture-Regeln (siehe `CLAUDE.md`):

- `domain/` bleibt frei von Android-Framework-Abhängigkeiten, mit Ausnahme bereits bestehender Konventionen (Hilt-Module sind in `di/`, nicht in `domain/`).
- Datenzugriff läuft über `DocumentRepository` / `TrashDocumentRepository`; UseCases koordinieren Geschäftslogik, ViewModels koordinieren State.
- Externe Android-Einstiegspunkte (Shortcut, Tile, Widget, Share-Target) laufen über **eine** Root-Action-Bridge in der Presentation-Schicht; `MainActivity` dekodiert nur Intents und startet keine Fachlogik direkt.
- Alle UI-Strings gehen in `strings_<feature>.xml` und werden in alle 10 Locales gespiegelt.
- Fehler-Mapping via `WorkflowErrorMapper` bzw. ViewModel-eigene `_error: StateFlow<String?>`.

Reihenfolge ist nach Aufwand × Risiko sortiert — **Feature 1 zuerst**, da keine DB-Migration, danach schrittweise Features mit wachsender Tragweite.

---

## Feature 1 — App-Shortcuts, Quick-Settings-Tile & Home-Screen-Widget

Drei Einstiegspunkte, die alle denselben Code-Pfad nutzen: „direkter Scan-Start" und „direkt Bild-Import". Keine DB-Änderung, kein UseCase-Layer.

### Architektur-Einordnung

Reine Presentation-/Framework-Schicht. Kein neuer Domain-Code. Statt `MainActivity` direkt mit `HomeViewModel` zu koppeln, wird eine root-scope **`AppEntryAction`-Bridge** eingeführt (z. B. `ui/entry/AppEntryActionViewModel.kt`). `MainActivity` übersetzt Intents nur in diese Aktion; `HomeScreenEffects` und `AppNavHost` konsumieren sie, sobald die UI bereit ist. Dieselbe Bridge wird in Feature 5 für `ACTION_SEND` wiederverwendet.

### Änderungen

**1.1 Deep-Link-Actions in `MainActivity.kt`**

```kotlin
sealed interface AppEntryAction {
    data object ScanNew : AppEntryAction
    data object ImportImages : AppEntryAction
    data object OpenTrash : AppEntryAction
    // Feature 5 erweitert dies später um SharePdf / ShareImages
}

object AppEntryActionCodec {
    const val EXTRA_KEY = "info.meuse24.pdf_scanner.ACTION"
    const val VALUE_SCAN_NEW = "scan_new"
    const val VALUE_IMPORT_IMAGES = "import_images"
    const val VALUE_OPEN_TRASH = "open_trash"
}
```

`MainActivity.onNewIntent()` + Initial-Intent lesen das Extra und rufen z. B. `appEntryActionViewModel.offer(AppEntryAction.ScanNew)` auf. `HomeScreenEffects` triggert daraus `rememberHomeScreenLaunchers(...)`, `AppNavHost` navigiert bei `OpenTrash`, und die Aktion wird danach konsumiert.

**1.2 Statische App-Shortcuts — `res/xml/shortcuts.xml`**

```xml
<shortcuts xmlns:android="http://schemas.android.com/apk/res/android">
  <shortcut android:shortcutId="scan_new"
            android:enabled="true"
            android:icon="@drawable/ic_shortcut_scan"
            android:shortcutShortLabel="@string/shortcut_scan_short"
            android:shortcutLongLabel="@string/shortcut_scan_long">
    <intent android:action="android.intent.action.VIEW"
            android:targetPackage="info.meuse24.pdf_scanner"
            android:targetClass="info.meuse24.pdf_scanner.MainActivity">
      <extra android:name="info.meuse24.pdf_scanner.ACTION"
             android:value="scan_new"/>
    </intent>
  </shortcut>
  <!-- analog: import_images, open_trash -->
</shortcuts>
```

Registrierung in `AndroidManifest.xml` unter `<activity android:name=".MainActivity">`:

```xml
<meta-data android:name="android.app.shortcuts"
           android:resource="@xml/shortcuts"/>
```

**1.3 Quick-Settings-Tile — `ScanTileService`**

Neue Klasse `ui/tile/ScanTileService.kt`:

```kotlin
class ScanTileService : TileService() {
    override fun onClick() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(AppEntryActionCodec.EXTRA_KEY, AppEntryActionCodec.VALUE_SCAN_NEW)
        }
        startActivityAndCollapse(pendingIntentImmutable(intent))
    }
}
```

Manifest-Eintrag mit `android.permission.BIND_QUICK_SETTINGS_TILE` und `action.QS_TILE`.

**1.4 Home-Screen-Widget — `ScanWidgetProvider`**

`AppWidgetProvider` mit einer einzigen Action-Button-Fläche (Compose Glance ist möglich, aber `RemoteViews` reicht hier). Tippen löst denselben `MainActivity`-Intent wie Shortcut `scan_new` aus.

Dateien:
- `ui/widget/ScanWidgetProvider.kt`
- `res/xml/scan_widget_info.xml` (Größe 2×1, resizable)
- `res/layout/widget_scan.xml` (Icon + Label)
- `res/drawable/widget_scan_background.xml` + day/night Farben; keine Theme-Attribute direkt in `RemoteViews`

### Neue/geänderte Dateien

| Datei | Typ |
|---|---|
| `AndroidManifest.xml` | geändert — Service, Receiver, Meta-Data |
| `MainActivity.kt` | geändert — Intent-Handling |
| `ui/entry/AppEntryActionViewModel.kt` | neu — root-scope Action-Bridge |
| `ui/navigation/AppNavHost.kt` | geändert — `OpenTrash` konsumieren |
| `ui/home/HomeScreenEffects.kt` | geändert — Scanner-/Import-Actions konsumieren |
| `ui/tile/ScanTileService.kt` | neu |
| `ui/widget/ScanWidgetProvider.kt` | neu |
| `res/xml/shortcuts.xml` | neu |
| `res/xml/scan_widget_info.xml` | neu |
| `res/layout/widget_scan.xml` | neu |
| `res/drawable/widget_scan_background.xml` | neu |
| `res/drawable/ic_shortcut_*.xml` | neu (3 Stück) |
| `res/values*/strings_shortcuts.xml` | neu (10 Locales) |

### Tests

- `MainActivityIntentTest` (Robolectric oder Instrumentation): Intent mit `SCAN_NEW` → `AppEntryActionViewModel.pendingAction` = `ScanNew`
- Widget/Tile werden manuell verifiziert (Framework-Contracts sind hart dokumentiert).

### Risiken / Entscheidungen

- **Tile-Service-Dispatcher-Quirk:** Vor Android 14 empfiehlt sich `startActivityAndCollapse(PendingIntent)` (ab 14 Pflicht). Wir zielen auf minSdk 29, müssen also beide Pfade bedienen.
- **Widget-Permission:** Keine neuen Permissions nötig.

---

## Feature 2 — Ordner & Favoriten

Ordnerstruktur + Favoriten-Flag in der Ablage. Größter UX-Hebel, erfordert DB-Migration 7→8.

### Architektur-Einordnung

Neues Aggregat **`Folder`** in `domain/model/`. Neuer Aggregat-Root im Domain-Layer, eigenes Repository-Interface. `Document` bekommt optionale Referenz auf `folderId` und ein `isFavorite`-Flag. Zusätzlich wird **`DocumentRepository`** um folder-/favorite-bezogene Observe- und Update-Operationen erweitert; die UI bleibt dadurch bei Repository-/UseCase-APIs und greift nicht direkt auf DAOs zu. Die bestehende Methode `getAllScans()` bleibt unverändert (keine Umbenennung), neue Methoden werden additiv ergänzt. Der FTS-Index bleibt unverändert (Ordner-Name ist kein Suchziel).

```
domain/model/Folder.kt                      (neu)
domain/model/Document.kt                    (folderId + isFavorite ergänzen)
domain/repository/FolderRepository.kt       (neu)
domain/repository/DocumentRepository.kt     (additiv: getScansInFolder, getFavoriteScans, moveDocumentsToFolder, setFavorite)
domain/usecase/CreateFolderUseCase.kt       (neu)
domain/usecase/RenameFolderUseCase.kt       (neu)
domain/usecase/DeleteFolderUseCase.kt       (neu) — entkoppelt Dokumente zuerst via moveDocumentsToFolder(ids, null), löscht dann den Ordner
domain/usecase/MoveDocumentsUseCase.kt      (neu) — Bulk-Aktion
domain/usecase/ToggleFavoriteUseCase.kt     (neu)
data/local/FolderEntity.kt                  (neu, @Entity tableName="folders")
data/local/FolderDao.kt                     (neu)
data/mapper/FolderMappers.kt                (neu)
data/repository/FolderRepositoryImpl.kt     (neu, Mapper wie existing)
di/DatabaseModule.kt                        (neu: folderDao + Migration_7_8)
di/RepositoryModule.kt                      (neu: @Binds FolderRepository)
```

### Datenmodell

**Domain**

```kotlin
data class Folder(
    val id: Long = 0,
    val name: String,
    val colorArgb: Int? = null,    // optional, MaterialYou-Akzent
    val createdAt: Long = System.currentTimeMillis()
)
```

```kotlin
// Document.kt — zusätzliche Felder
val folderId: Long? = null,
val isFavorite: Boolean = false,
```

**Room**

```kotlin
@Entity(tableName = "folders")
data class FolderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    @ColumnInfo(name = "color_argb") val colorArgb: Int? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long
)

// ScanRecord.kt — @Entity-Annotation muss den Index explizit deklarieren,
// sonst wirft Room beim Start nach MIGRATION_7_8 eine
// "Migration didn't properly handle …"-Exception (Schema-Diff).
@Entity(
    tableName = "scan_records",
    indices = [Index("folder_id")]
)
data class ScanRecord(
    // … bestehende Felder …
    @ColumnInfo(name = "folder_id") val folderId: Long? = null,
    @ColumnInfo(name = "is_favorite", defaultValue = "0") val isFavorite: Boolean = false,
)
```

**Kein Foreign-Key-Constraint** zwischen `scan_records.folder_id` und `folders.id`. Cascade-Verhalten beim Löschen eines Ordners wird stattdessen im `DeleteFolderUseCase` explizit umgesetzt (siehe unten) — das hält die Migration simpel und vermeidet ON-DELETE-Semantik-Überraschungen mit Room.

**Migration 7→8** (in `AppDatabase.kt`):

```kotlin
val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS folders (
                id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                color_argb INTEGER,
                created_at INTEGER NOT NULL
            )
        """.trimIndent())
        db.execSQL("ALTER TABLE scan_records ADD COLUMN folder_id INTEGER")
        db.execSQL("ALTER TABLE scan_records ADD COLUMN is_favorite INTEGER NOT NULL DEFAULT 0")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_scan_records_folder_id ON scan_records(folder_id)")
    }
}
```

`@Database(version = 8)`, `MIGRATION_7_8` in `DatabaseModule.kt` ergänzen.

**ScanDao-Erweiterungen**

```kotlin
@Query("SELECT * FROM scan_records WHERE deleted_at IS NULL AND folder_id IS :folderId ORDER BY timestamp DESC")
fun getScansInFolder(folderId: Long?): Flow<List<ScanRecord>>

@Query("SELECT * FROM scan_records WHERE deleted_at IS NULL AND is_favorite = 1 ORDER BY timestamp DESC")
fun getFavoriteScans(): Flow<List<ScanRecord>>

@Query("UPDATE scan_records SET folder_id = :folderId WHERE id IN (:ids)")
suspend fun moveScans(ids: List<Long>, folderId: Long?)

@Query("UPDATE scan_records SET is_favorite = :favorite WHERE id IN (:ids)")
suspend fun setFavorite(ids: List<Long>, favorite: Boolean)
```

### Presentation

`HomeArchiveUiState` erweitern um `currentFolder: Folder?`, `folders: List<Folder>`, `favoritesFilter: Boolean`. `HomeViewModel.setFolder(folderId: Long?)` schaltet den Archiv-Flow per `flatMapLatest` zwischen `DocumentRepository.getAllScans()` (bestehend), `getScansInFolder(folderId)` und `getFavoriteScans()` (beide neu) um.

UI-Ergänzungen:
- **Drawer:** Sektion „Ordner" mit dynamischer Liste (`AppDrawerContent.kt`) + Eintrag „Favoriten ⭐" + „Alle Dokumente".
- **HomeScreen-Topbar:** aktueller Ordner als Breadcrumb.
- **ScanItem:** Stern-Icon (Toggle) links neben dem MoreVert-Button.
- **BulkActionBar:** neue Aktion „In Ordner verschieben" → `FolderPickerSheet`.
- **AddDocumentSheet:** Zielordner-Auswahl beim Scan/Import (Default: aktueller Ordner).
- **Neues Sheet:** `FolderPickerSheet` (Liste aller Ordner + „Neuer Ordner").
- **Neuer Screen:** `FolderManagementScreen` (Umbenennen, Löschen, Farbe) — erreichbar aus Drawer-Longpress.

Neue `ScanAction.MoveToFolder(folderId: Long?)` + `ScanAction.ToggleFavorite`.

### Neue/geänderte Dateien

Zusätzlich zu den oben gelisteten Domain-/Daten-Dateien:

| Datei | Typ |
|---|---|
| `ui/home/HomeUiState.kt` | geändert — `currentFolder`, `folders`, `favoritesFilter` |
| `ui/home/HomeViewModel.kt` | geändert — Folder-Flow, Move/Favorite-Actions |
| `ui/home/HomeActionDispatcher.kt` | geändert — neue ScanActions |
| `ui/home/components/ScanItem.kt` | geändert — Star-Icon, Ordner-Badge |
| `ui/home/components/HomeSheets.kt` | geändert — `FolderPickerSheet` |
| `ui/navigation/AppDrawerContent.kt` | geändert — Ordnerliste |
| `ui/navigation/AppNavHost.kt` + `Screen.kt` | geändert — Route `FolderManagement` |
| `ui/folders/FolderManagementScreen.kt` | neu |
| `ui/folders/FolderManagementViewModel.kt` | neu |
| `res/values*/strings_folders.xml` | neu (10 Locales) |

### Tests

- `FolderRepositoryImplTest` — CRUD + Cascade-Verhalten beim Löschen (Dokumente → root).
- `MoveDocumentsUseCaseTest` — verschieben, leere ID-Liste, ungültige Folder-ID.
- `ToggleFavoriteUseCaseTest`.
- `HomeViewModelTest` erweitern: `setFolder` wechselt Flow, Favoriten-Filter, Bulk-Move.
- **AndroidTest** — Room-Migration 7→8 via `MigrationTestHelper` (neue Datei `app/src/androidTest/.../Migration7To8Test.kt`).

### Risiken / Entscheidungen

- **Papierkorb + Ordner:** Soft-gelöschte Dokumente behalten `folder_id`. Restore stellt in den ursprünglichen Ordner zurück; fehlt der Ordner (weil zwischenzeitlich gelöscht), fällt es auf root (`folder_id = NULL`).
- **Ordner-Löschung ist zweistufig, nicht cascaded.** `DeleteFolderUseCase` macht explizit (in einer Transaktion): `UPDATE scan_records SET folder_id = NULL WHERE folder_id = :id` → `DELETE FROM folders WHERE id = :id`. Kein `FOREIGN KEY ... ON DELETE SET NULL` in der Migration, da wir keine implizite Kopplung im Schema wollen.
- **FTS bleibt unverändert.** Ordnername ist kein Suchziel. Falls später gewünscht, neue FTS-Spalte in eigener Migration.
- **Keine verschachtelten Ordner** in V1 — explizit flach, sonst explodiert die UX. Flag dafür im Plan offen.

---

## Feature 3 — App-Lock per Biometrie

Opt-in-Biometrie/PIN auf dem App-Start; fügt sich in `SettingsScreen` ein. Kein neuer Datenfluss, nur neuer Lifecycle-Gate.

### Architektur-Einordnung

Kein UseCase — das ist reines Framework-Handling (BiometricPrompt). Aber: Einstellung wird in `AppSettingsRepository` persistiert (bestehendes Interface erweitern), sodass Tests + DI konsistent bleiben.

### Änderungen

**3.1 Domain — Settings erweitern**

Das bestehende Settings-Modell wird erweitert, statt ein zweites Settings-System einzuführen:

- `util/AppSettings.kt`: `appLockEnabled: Boolean`, optional `appLockTimeoutSeconds: Int = 30`
- `domain/repository/AppSettingsRepository.kt`: **non-suspend** `fun updateAppLockEnabled(enabled: Boolean)` und optional `fun updateAppLockTimeoutSeconds(seconds: Int)` — konsistent zum bestehenden Stil (`updateThemeMode`, `updateDefaultMakeSearchable`, `updateDefaultOcrLanguage`, `updateDefaultSortOrder` sind ebenfalls non-suspend).
- `data/repository/SettingsRepository.kt` + `util/AppSettingsPreferences.kt`: Persistenz **wie bisher über SharedPreferences**, nicht über einen DataStore-Neubau. Neue Keys in `AppSettingsPreferences.load()`/`save()` ergänzen, parallel zu den existierenden.

**3.2 Auth-Gate — `AppLockManager` in `util/`**

```kotlin
@Singleton
class AppLockManager @Inject constructor(
    private val settings: AppSettingsRepository,
    @ApplicationContext private val context: Context
) {
    val isLocked: StateFlow<Boolean>     // true = UI muss blockiert werden

    fun onAppForegrounded()              // vom ProcessLifecycleOwner
    fun onAppBackgrounded()              // setzt isLocked=true wenn Feature aktiv
    suspend fun authenticate(activity: FragmentActivity): AuthResult
}
```

`PdfScannerApp.onCreate()` registriert `ProcessLifecycleOwner.get().lifecycle.addObserver(appLockManager)`. Wichtig: Externe `AppEntryAction`s aus Feature 1/5 werden zwar angenommen, aber erst **nach erfolgreichem Unlock** konsumiert.

**3.3 UI-Gate**

In `MainActivity.setContent {}` wird vor `AppNavigation` ein `AppLockGate(appLockManager)` gezogen. Solange `isLocked == true && appLockEnabled == true`, wird ein vollflächiger Lock-Screen (`ui/lock/AppLockScreen.kt`) angezeigt, der auf Button-Click `BiometricPrompt.authenticate(...)` aufruft. Erfolg → `appLockManager.unlock()`.

**3.4 Settings-Toggle**

`SettingsScreen.kt` / `SettingsViewModel.kt` bekommen Schalter „App mit Biometrie schützen". Aktivieren verlangt sofortige Erfolgs-Authentifizierung (Schutz gegen versehentliches Aktivieren ohne Biometric-Enrollment).

### Neue/geänderte Dateien

| Datei | Typ |
|---|---|
| `gradle/libs.versions.toml` | neu/erweitert — Biometric-Dependency im Version Catalog |
| `app/build.gradle.kts` | geändert — Alias einbinden |
| `util/AppSettings.kt` | erweitert — `appLockEnabled`, optional Timeout |
| `util/AppSettingsPreferences.kt` | erweitert — SharedPreferences-Keys |
| `domain/repository/AppSettingsRepository.kt` | erweitert |
| `data/repository/SettingsRepository.kt` | erweitert |
| `util/AppLockManager.kt` | neu |
| `ui/lock/AppLockScreen.kt` | neu |
| `MainActivity.kt` | `AppLockGate` wrap |
| `PdfScannerApp.kt` | Lifecycle-Observer registrieren |
| `ui/settings/SettingsScreen.kt` | Toggle ergänzen |
| `ui/settings/SettingsViewModel.kt` | Toggle-State + Enrollment-Check |
| `res/values*/strings_lock.xml` | neu (10 Locales) |

### Tests

- `AppLockManagerTest` — Lifecycle-Transitions, Settings off → kein Lock, authenticate-Success → unlock.
- `SettingsViewModelTest` erweitern — Aktivieren ohne Enrollment → Fehler-State.
- Lock-UI-Smoke-Test als Instrumentation-Test (aktivieren, Prozess beenden, neu starten, erwartet Lock-Screen).

### Risiken / Entscheidungen

- **Keine geräteverlassende Secret-Ableitung.** Der Lock ist UI-Gate, kein Krypto-Layer. Datenbank und PDFs bleiben unverschlüsselt — wir beanspruchen das nicht als Datenverschlüsselung (wichtig für `PrivacyScreen`-Wording).
- **`FragmentActivity` nötig** für `BiometricPrompt` — `MainActivity` erbt aktuell von `ComponentActivity`. Wechsel auf `FragmentActivity` oder `AppCompatActivity` ist technisch nötig, Compose-`setContent {}` bleibt dabei nutzbar.
- **Externe Einstiegspunkte dürfen den Lock nicht umgehen.** Shortcut-/Share-Actions werden gepuffert und erst nach Unlock abgearbeitet.
- **Fallback auf Geräte-PIN/Muster** via `setAllowedAuthenticators(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)` — Nutzer ohne Biometrie werden nicht ausgesperrt.

---

## Feature 4 — Visitenkarten-Scan → vCard

Neuer UseCase, neuer Screen, neue Action. Baut auf `ExtractTextUseCase` + ML Kit Text Recognition (bereits vorhanden) auf.

### Architektur-Einordnung

Feature 4 braucht **Domain + Data + Presentation**. Parsing und vCard-Erzeugung bleiben pure Kotlin; nur das Schreiben in `cacheDir/` und das Teilen per FileProvider ist Android-/Data-Schicht.

```
domain/model/BusinessCard.kt               (neu: name, org, title, emails, phones, urls, addresses)
domain/service/BusinessCardParser.kt       (neu) — deterministischer Parser, pure Kotlin
domain/service/VCardBuilder.kt             (neu) — vCard 3.0 String-Builder, pure Kotlin
domain/repository/VCardExportRepository.kt (neu) — Interface für Dateiexport
domain/usecase/ScanBusinessCardUseCase.kt  (neu) — Input: Document, Output: BusinessCard
domain/usecase/ExportVCardUseCase.kt       (neu) — BusinessCard → File
data/export/VCardExportRepositoryImpl.kt   (neu) — schreibt nach cacheDir/vcards/
ui/businesscard/BusinessCardScreen.kt      (neu)
ui/businesscard/BusinessCardViewModel.kt   (neu)
ui/navigation/Screen.kt                    (neu: Route BusinessCard/{scanId})
```

### Parser-Strategie (deterministisch, testbar)

`ScanBusinessCardUseCase` verwendet bevorzugt bereits vorhandene OCR-Daten (`Document.pageTexts.firstOrNull()` bzw. `extractedText`) und fällt nur bei fehlendem Text auf `ExtractTextUseCase` zurück. Für Visitenkarten wird **nur Seite 1** ausgewertet und anschließend strukturiert geparst:

```kotlin
data class BusinessCard(
    val fullName: String?,
    val organization: String?,
    val jobTitle: String?,
    val emails: List<String>,
    val phones: List<String>,
    val urls: List<String>,
    val address: String?
)

object BusinessCardParser {
    private val EMAIL = Regex("""[\w.+-]+@[\w-]+\.[\w.-]+""")
    private val PHONE = Regex("""[+(]?\d[\d\s().\-/]{6,}""")
    private val URL = Regex("""(?i)(https?://|www\.)[^\s]+""")

    fun parse(text: String): BusinessCard { /* ... */ }
}
```

- **Name-Heuristik:** erste Zeile mit ≥2 Wörtern, die keine E-Mail/Telefon/URL matched und nicht ausschließlich UPPERCASE > 4 Wörter (dann eher Org).
- **Organization-Heuristik:** Zeile mit Endungen wie `GmbH`, `AG`, `Ltd`, `Inc`, `LLC`, `SE` oder die UPPERCASE-Zeile nach dem Namen.
- **Job-Title:** Zeile mit Keywords (Manager/Developer/CEO/…); Wörterbuch als Ressource in 10 Sprachen ist für V1 zu teuer — wir bleiben bei Englisch+Deutsch.

Alles Heuristik — der User bestätigt/editiert im `BusinessCardScreen` vor dem Export.

### vCard-Export

`VCardBuilder` erzeugt vCard 3.0 (breiteste Kompatibilität):

```
BEGIN:VCARD
VERSION:3.0
FN:Max Mustermann
ORG:ACME GmbH
TITLE:Lead Engineer
TEL;TYPE=CELL:+49 151 1234567
EMAIL:max@acme.de
URL:https://acme.de
END:VCARD
```

`ExportVCardUseCase` delegiert an `VCardExportRepository`. Dessen Android-Implementierung schreibt nach `context.cacheDir/vcards/{sanitized_name}.vcf`; die UI erzeugt daraus via FileProvider `${applicationId}.fileprovider` einen Share-/View-Intent für den Contacts-Dialog.

### Presentation

Neuer Eintrag in `ScanAction` → `ScanAction.ScanBusinessCard`. In `DocumentEditSheet` eingetragen in Sektion „Analysieren & Text" (enabled nur wenn `pageCount >= 1 && !isEncrypted`).

`BusinessCardScreen` zeigt parsed Felder als editierbare `TextField`s (User kann korrigieren), unten zwei Buttons: „Zu Kontakten hinzufügen" (öffnet vCard-Intent) und „vCard teilen" (Share-Sheet).

### Neue/geänderte Dateien

| Datei | Typ |
|---|---|
| `domain/model/BusinessCard.kt` | neu |
| `domain/service/BusinessCardParser.kt` | neu |
| `domain/service/VCardBuilder.kt` | neu |
| `domain/repository/VCardExportRepository.kt` | neu |
| `domain/usecase/ScanBusinessCardUseCase.kt` | neu |
| `domain/usecase/ExportVCardUseCase.kt` | neu |
| `data/export/VCardExportRepositoryImpl.kt` | neu |
| `ui/businesscard/BusinessCardScreen.kt` | neu |
| `ui/businesscard/BusinessCardViewModel.kt` | neu |
| `ui/navigation/Screen.kt` + `AppNavHost.kt` | geändert |
| `ui/home/HomeActionDispatcher.kt` | `ScanBusinessCard` → navigate |
| `ui/components/DocumentEditSheet.kt` | Sheet-Eintrag |
| `res/values*/strings_businesscard.xml` | neu (10 Locales) |
| `res/xml/file_paths.xml` | Eintrag für `cacheDir/vcards/` |

### Tests

- `BusinessCardParserTest` — 10 Fixture-Strings (DE/EN), Edge-Cases: nur Telefon, nur E-Mail, MixedCase-Name, UPPERCASE-Org, mehrzeilige Adresse, internationale Telefonnummern.
- `VCardBuilderTest` — Sonderzeichen (Kommas, Umlaute) werden korrekt escaped (`\,`, `\n`).
- `ScanBusinessCardUseCaseTest` — cached OCR wird bevorzugt, Fallback auf `ExtractTextUseCase` nur wenn nötig.
- `ExportVCardUseCaseTest` — mit Fake-`VCardExportRepository`.
- `BusinessCardViewModelTest` — Editier-State, Export-Success/-Failure.

### Risiken / Entscheidungen

- **Keine ML Kit Entity Extraction** als Pflichtabhängigkeit — das würde die APK-Größe deutlich aufblasen. Heuristik reicht, Nutzer editiert. Später kann man Entity-Extraction als unbundled-Modul nachschieben, analog zu OCR-Sprachen.
- **Keine eigene Kamera-UI** — es wird ein vorhandener Scan verwendet (gleicher Entry-Point wie Annotate).

---

## Feature 5 — Android Print + Share-Target

Zwei kleine Integrationen, die beide die Android-Framework-APIs nutzen. Kein UseCase-Layer nötig, nur dünne Adapter.

### 5a — Android Print

**Architektur:** Reiner Presentation-Layer-Helper. Neuer `util/PdfPrintHelper.kt`:

```kotlin
object PdfPrintHelper {
    fun print(context: Context, pdf: File, jobName: String) {
        val printManager = context.getSystemService(PrintManager::class.java)
        val adapter = PdfPrintDocumentAdapter(pdf)
        printManager.print(jobName, adapter, PrintAttributes.Builder().build())
    }
}

private class PdfPrintDocumentAdapter(private val pdf: File) : PrintDocumentAdapter() {
    override fun onLayout(/* ... */) { /* single document, pageCount = UNKNOWN_PAGE_COUNT oder PdfRenderer-Count */ }
    override fun onWrite(/* ... */) {
        FileInputStream(pdf).use { input ->
            FileOutputStream(destination.fileDescriptor).use { output -> input.copyTo(output) }
        }
        callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
    }
}
```

Aufruf per neuer `ScanAction.Print`:
- Einzel-Dokument: aus `DocumentEditSheet` (Sektion „Export & Umwandeln").
- Viewer: neuer Icon-Button in der Action-Bar.
- Bulk: **nicht** in V1 — Android Print unterstützt kein Multi-Job-UI.

### 5b — Share-Target (ACTION_SEND Receiver)

**Architektur:** `MainActivity` bekommt weitere Intent-Filter, dekodiert `ACTION_SEND` / `ACTION_SEND_MULTIPLE` aber nur in die bereits in Feature 1 eingeführte `AppEntryAction`-Bridge. **Keine direkten `HomeViewModel`-Aufrufe aus `MainActivity`.** Die Home-UI konsumiert die Aktion dann wie folgt:

- PDF → vorhandenen `PendingImport.File(uri, displayName)`-Pfad + `HomeSaveImportDialog` wiederverwenden
- Bilder → vorhandenen `pendingImageUris`-/`ImagesToPdfScreen`-Pfad wiederverwenden

Damit bleibt die bestehende Import-UX konsistent und die App-Lock-Gate aus Feature 3 kann externe Shares sauber puffern.

**Manifest:**

```xml
<activity android:name=".MainActivity" android:exported="true">
    <intent-filter>
        <action android:name="android.intent.action.MAIN"/>
        <category android:name="android.intent.category.LAUNCHER"/>
    </intent-filter>
    <intent-filter>
        <action android:name="android.intent.action.SEND"/>
        <category android:name="android.intent.category.DEFAULT"/>
        <data android:mimeType="application/pdf"/>
        <data android:mimeType="image/*"/>
    </intent-filter>
    <intent-filter>
        <action android:name="android.intent.action.SEND_MULTIPLE"/>
        <category android:name="android.intent.category.DEFAULT"/>
        <data android:mimeType="image/*"/>
    </intent-filter>
</activity>
```

**Intent-Dispatch in `MainActivity.handleShareIntent(intent)`:**

```kotlin
when (val action = intent.toAppEntryAction()) {
    is AppEntryAction.SharePdf -> appEntryActionViewModel.offer(action)
    is AppEntryAction.ShareImages -> appEntryActionViewModel.offer(action)
    null -> Unit
}
```

Die Home-UI liest dann bei `SharePdf` den `DISPLAY_NAME` via `queryDisplayName(...)`, erstellt `PendingImport.File` und öffnet das bestehende Save-Dialog-Sheet. `ShareImages` nutzt denselben Pfad wie der bestehende Images-to-PDF-Launcher und navigiert in den `ImagesToPdfScreen`.

### Neue/geänderte Dateien

| Datei | Typ |
|---|---|
| `AndroidManifest.xml` | Intent-Filter + ggf. Print-Service-Permissions (keine nötig) |
| `MainActivity.kt` | `handleShareIntent()` in `onCreate` + `onNewIntent` |
| `util/PdfPrintHelper.kt` | neu |
| `ui/entry/AppEntryActionViewModel.kt` | wiederverwendet/erweitert |
| `ui/home/HomeScreen.kt` + `HomeScreenEffects.kt` | `PendingImport.File` / `pendingImageUris` aus externen Shares befüllen |
| `ui/home/HomeActionDispatcher.kt` | `ScanAction.Print` |
| `ui/viewer/PdfViewerScreen.kt` | Print-Icon-Button |
| `ui/components/DocumentEditSheet.kt` | Print-Sheet-Eintrag |
| `res/values*/strings_print_share.xml` | neu (10 Locales) |

### Tests

- `PdfPrintHelperTest` — dünner Smoke-Test oder Robolectric-Test auf `PrintManager.print(...)`.
- `MainActivityShareIntentTest` (Instrumentation) — `ACTION_SEND` + PDF-URI → `AppEntryAction.SharePdf` wird angeboten.
- Manuell: tatsächliches Teilen aus Chrome/Gallery → Scan landet in Ablage.

### Risiken / Entscheidungen

- **Print-Job mit verschlüsselten PDFs** — Android Print kann passwortgeschützte PDFs nicht rendern. Guard im ViewModel: `isEncrypted && pageCount > 0` → Snackbar „Bitte zuerst Passwort entfernen".
- **Share-Target + FileProvider-Lifetime:** Eingehende `content://`-URIs sind nur so lange lesbar, wie die Source-App es gewährt. Persistenz muss deshalb **früh auf IO** erfolgen, idealerweise direkt nach Unlock und Bestätigung im bestehenden Import-Dialog.
- **Share-Intent-Permission:** `Intent.FLAG_GRANT_READ_URI_PERMISSION` ist vom Absender gesetzt — keine Extra-Permission auf unserer Seite nötig.

---

## Reihenfolge, Abhängigkeiten, Zeitbudget

| # | Feature | DB-Migration | Neue Deps | Geschätzter Aufwand |
|---|---|---|---|---|
| 1 | Shortcuts + Tile + Widget | nein | nein | 0.5 Tage |
| 3 | App-Lock Biometrie | nein | androidx.biometric | 1 Tag |
| 5 | Print + Share-Target | nein | nein | 0.5 Tage |
| 4 | Business-Card → vCard | nein | nein | 1.5 Tage |
| 2 | Ordner + Favoriten | **ja (7→8)** | nein | 2.5 Tage |

Empfohlene Umsetzungsreihenfolge: **1 → 3 → 5 → 4 → 2**. Feature 5 baut dann direkt auf der Action-Bridge aus Feature 1 auf und respektiert das App-Lock-Gate aus Feature 3; Feature 2 bleibt zuletzt, weil es als einziges eine DB-Migration mit Schema-Änderung an `scan_records` einführt.

## Cross-Cutting-Todos

- Für **jedes** Feature: `HelpScreen.kt` + `InfoScreen.kt` + `docs/privacy-policy.html` aktualisieren (CLAUDE.md-Regel: Privacy-Texte konsistent halten).
- Für **jedes** Feature: alle 10 Locales (`values/`, `-de`, `-es`, `-fr`, `-pt`, `-zh-rCN`, `-ar`, `-ja`, `-ru`, `-hi`) befüllen.
- Für **jedes** Feature: `./gradlew :app:compileDebugKotlin` + `./gradlew testDebugUnitTest` + `./gradlew lint` grün, außerdem `./gradlew installDebug` + ADB-Start zur Live-Verifikation.
- Für **Feature 1 + 5 + 3** gemeinsam: Externe Actions immer über dieselbe `AppEntryAction`-Bridge führen und erst nach einem aktiven App-Lock konsumieren.

## Offene Fragen (vor Implementation klären)

- **Feature 2:** Sollen Dokumente beim Scan sofort in den aktuellen Ordner wandern, oder gibt es einen Pflicht-Picker? — Empfehlung: Auto-Zuweisung zum aktuellen Ordner, Picker nur optional als Sheet-Eintrag „In Ordner verschieben".
- **Feature 3:** Darf der Lock auch bei jedem App-Wechsel (Multitasking) greifen, oder nur beim Kaltstart? — Empfehlung: konfigurierbar, Default = beim App-Wechsel nach >30 s im Hintergrund.
- **Feature 4:** Sollen Visitenkarten-Scans als eigener Dokumenttyp in der Ablage markiert werden? — Empfehlung: **nein in V1**. Das reaktiviert sonst das derzeit absichtlich brachliegende `tags`-Feld. Wenn wir später Typisierung wollen, dann mit explizitem Fachfeld statt implizitem Tag-Reuse.

---

## Änderungshistorie

- **2026-04-24 (Review-Pass 2):** Vier Präzisierungen gegen die reale Codebasis eingepflegt:
  1. `DocumentRepository` bleibt bei bestehender `getAllScans()`-Signatur, neue Folder-/Favorites-Observer werden additiv ergänzt (keine Umbenennung).
  2. Feature 2 `ScanRecord`-Entity erhält `@Entity(indices = [Index("folder_id")])`, damit Room-Schema-Vergleich nach `MIGRATION_7_8` nicht fehlschlägt.
  3. Feature 2 `DeleteFolderUseCase` beschreibt das Detach-dann-Delete-Pattern explizit; kein `FOREIGN KEY` in der Migration.
  4. Feature 3 `updateAppLockEnabled` ist non-suspend, konsistent zum bestehenden `AppSettingsRepository`-Stil.
