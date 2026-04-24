# Implementierungsplan: Übersetzungsfeature (ML Kit Translate)

Status: Entwurf · Datum: 2026-04-24

---

## Ziel

Nutzer können den OCR-Text eines Dokuments mit einem Tippen in eine Zielsprache übersetzen. Das Ergebnis erscheint in einem Review-Screen (Original + Übersetzung), kann kopiert und geteilt werden. Alles läuft vollständig On-Device.

---

## Ausgangslage im Projekt

| Vorhandener Baustein | Nutzung |
|---|---|
| `Document.extractedText` / `pageTexts` | direkter Input, kein neuer OCR-Lauf nötig |
| `Document.ocrLanguage` | Hinweis auf Quellsprache |
| `ExtractTextUseCase` | Fallback wenn noch kein OCR-Text vorhanden |
| `OcrModelInstaller` / `ModuleInstall` | Blueprint für Translation-Modell-Download |
| `OcrReviewScreen` / `OcrReviewViewModel` | Blueprint für UI und ViewModel-Struktur |
| `DispatcherProvider` + `ResourceProvider` | Standard-Utilities, wie in allen ViewModels |
| `Icons.filled.Translate` | Icon bereits in Compose-Materialicons vorhanden |
| `DocumentEditSheet` (Sektion „Analysieren & Text") | Einstiegspunkt |

---

## Neue Abhängigkeit

```toml
# gradle/libs.versions.toml
mlkitTranslate = "17.0.3"
mlkit-translate = { group = "com.google.mlkit", name = "translate", version.ref = "mlkitTranslate" }
```

```kotlin
// app/build.gradle.kts
implementation(libs.mlkit.translate)
```

Das SDK selbst ist klein (~1 MB). Sprachmodelle (~30 MB pro Sprache) werden unbundled heruntergeladen und lokal gecacht.

---

## Architektur

### Domain-Modell

```
domain/model/TranslationResult.kt
```

```kotlin
data class TranslationResult(
    val sourceLanguage: String,        // BCP-47, z. B. "de", "en"
    val targetLanguage: String,
    val pageTranslations: List<String> // eine Übersetzung pro Seite
) {
    val fullTranslation: String get() = pageTranslations.joinToString("\n\n")
}
```

### UseCase

```
domain/usecase/TranslateTextUseCase.kt
```

Ablauf:
1. `Document.pageTexts` nutzen (gecacht). Falls leer: `ExtractTextUseCase` triggern.
2. `TranslationModelManager.ensureModelsAvailable(source, target)` — lädt Modelle falls nötig.
3. `TranslatorClient.translate(pageTexts)` — Seite für Seite, Progress-Callbacks.
4. `TranslationResult` zurückgeben.

```kotlin
class TranslateTextUseCase @Inject constructor(
    private val extractTextUseCase: ExtractTextUseCase,
    private val translationModelManager: TranslationModelManager,
    private val dispatcherProvider: DispatcherProvider
) {
    suspend operator fun invoke(
        document: Document,
        targetLanguage: String,
        onProgress: (Int, Int) -> Unit = { _, _ -> }
    ): TranslationResult
}
```

### Modell-Management

```
util/TranslationModelManager.kt
```

Analog `OcrModelInstaller`, aber mit `RemoteModelManager` (ML Kit Translate nutzt einen eigenen Manager, nicht `ModuleInstallClient`):

```kotlin
@Singleton
class TranslationModelManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    suspend fun ensureModelsAvailable(
        sourceLanguage: String,
        targetLanguage: String,
        onStatus: (String) -> Unit = {}
    )

    suspend fun deleteModel(languageCode: String)

    fun getDownloadedModels(): List<String>   // für zukünftige Verwaltungs-UI
}
```

ML Kit Translate routet intern über Englisch als Pivot — es müssen also bis zu **zwei** Modelle heruntergeladen werden (Source-Sprache + Target-Sprache), wenn keine der beiden Englisch ist.

---

## Presentation

### Screen + ViewModel

```
ui/translation/TranslationReviewScreen.kt
ui/translation/TranslationReviewViewModel.kt
```

**ViewModel UiState (analog `OcrReviewViewModel.UiState`):**

```kotlin
data class UiState(
    val record: Document? = null,
    val result: TranslationResult? = null,
    val sourceLanguage: String = "auto",
    val targetLanguage: String = "de",
    val isLoading: Boolean = false,
    val progress: Pair<Int, Int>? = null,   // (aktuelle Seite, Gesamtseiten)
    val modelDownloading: Boolean = false,
    val error: String? = null
)
```

**Screen-Layout:**

- Oben: zwei Language-Pickers (Quell-/Zielsprache) + „Übersetzen"-Button
- Mitte: `LazyColumn` mit abwechselnden Kacheln Original / Übersetzung, Seite für Seite
- Unten: Floating Action Row — „Volltext kopieren" + „Teilen" (wie OcrReviewScreen)
- Ladeindikator mit Seitenfortschritt während Modell-Download und Translation

### Navigation

```
ui/navigation/Screen.kt   →  data class Translation(val scanId: Long) : Screen("translation/{scanId}")
ui/navigation/AppNavHost.kt  →  composable für Translation-Route
```

### Einstiegspunkt

`DocumentEditSheet.kt` — Sektion „Analysieren & Text":

```
ScanAction.TranslateText   (enabled = pageCount >= 1 && !isEncrypted)
```

`HomeActionDispatcher.kt` → navigiert zu `Screen.Translation(scanId)`.

---

## Neue / geänderte Dateien

| Datei | Typ |
|---|---|
| `gradle/libs.versions.toml` | geändert — `mlkitTranslate` |
| `app/build.gradle.kts` | geändert — neue Dependency |
| `domain/model/TranslationResult.kt` | neu |
| `domain/usecase/TranslateTextUseCase.kt` | neu |
| `util/TranslationModelManager.kt` | neu |
| `ui/translation/TranslationReviewScreen.kt` | neu |
| `ui/translation/TranslationReviewViewModel.kt` | neu |
| `ui/navigation/Screen.kt` | geändert |
| `ui/navigation/AppNavHost.kt` | geändert |
| `ui/home/HomeActionDispatcher.kt` | geändert |
| `ui/components/DocumentEditSheet.kt` | geändert — neuer Sheet-Eintrag |
| `res/values*/strings_translation.xml` | neu (10 Locales) |

---

## Stufenplan

### Stufe 1 — MVP (1 Tag)

- Dependency + `TranslationModelManager` + `TranslateTextUseCase`
- `TranslationReviewScreen` mit Quell-/Ziel-Picker, Volltext-Ansicht, Kopieren/Teilen
- Einstieg via `DocumentEditSheet`
- Language-Liste: 10 Sprachen für Picker (DE, EN, FR, ES, PT, ZH, JA, RU, AR, HI) — passt zu den bestehenden App-Locales
- Privacy-Hinweis: Modell-Download-Dialog vor erstem Gebrauch (analog OCR)

### Stufe 2 — Qualität (0.5 Tage extra)

- Seitenweise Ansicht (Original neben Übersetzung, nicht nur Volltext)
- Fortschrittsindikator mit Seite X / Y
- Gespeicherte Zielsprache in `AppSettings` (Standardeinstellung)
- Fehlermeldungen für: kein Text vorhanden, Modell-Download fehlgeschlagen, Sprache nicht erkannt

### Stufe 3 — TXT-Export (0.5 Tage extra)

- Übersetzung als `.txt`-Datei nach `cacheDir/translations/` schreiben
- Export via FileProvider + `MediaStore.Downloads` (wie `ExportScanUseCase`)
- Button „Als Textdatei exportieren" im Review-Screen

---

## Tests

- `TranslateTextUseCaseTest` — gecachter OCR-Text bevorzugt (kein `ExtractTextUseCase`-Aufruf), Fallback auf Extraktion wenn `pageTexts` leer.
- `TranslationModelManagerTest` — Modell bereits vorhanden → kein Download, Pivot-Logik (DE→FR erfordert DE+FR-Modell).
- `TranslationReviewViewModelTest` — Loading-State, Success, Error, clearError.
- Manuelle Verifikation auf Gerät: Deutsch→Englisch auf einem bekannten Testdokument.

---

## Risiken / Entscheidungen

| Punkt | Entscheidung |
|---|---|
| **Quellsprache auto-detect** | ML Kit Translate kann Sprache selbst erkennen — `BCP47LanguageCode("und")` als Quelle nutzen, Picker zeigt erkannte Sprache nach erstem Translate-Aufruf an. |
| **Pivot über Englisch** | Für den Nutzer unsichtbar; Qualität bei DE↔FR via EN-Pivot ist für Fließtext akzeptabel. |
| **Privacy** | Modell-Download über GMS-Server (wie OCR-unbundled). Einmalig vor dem ersten Übersetzen mit Dialog hinweisen. `PrivacyScreen` + `docs/privacy-policy.html` aktualisieren. |
| **CJK/Devanagari** | Diese Sprachen können übersetzt werden (Textausgabe), aber kein eigenes PDF erzeugt. MVP bleibt bei Textausgabe. |
| **Keine Persistenz in DB** | Übersetzungsergebnisse werden **nicht** in `ScanRecord` gespeichert — sie sind flüchtig. Stufe 3 (TXT-Export) ist der persistente Pfad. Kein Schema-Change. |
| **Modellgröße** | Bis zu 2×30 MB pro Sprachpaar. Nutzer darüber informieren (Download-Dialog mit Größenangabe). |
| **Entschlüsselte PDFs** | Verschlüsselte Dokumente → Einstieg im Sheet deaktiviert (`enabled = !isEncrypted`), analog Annotate. |

---

## Änderungshistorie

- **2026-04-24:** Initialversion.
