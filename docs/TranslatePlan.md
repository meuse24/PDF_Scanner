# Implementierungsplan: Übersetzungsfeature (ML Kit Translate)

Status: **Implementiert** · Datum: 2026-06-07

---

## Ziel

Nutzer können den OCR-Text eines Dokuments mit einem Tippen in eine Zielsprache übersetzen. Das Ergebnis erscheint in einem Review-Screen (Original + Übersetzung), kann kopiert und geteilt werden. Alles läuft vollständig On-Device.

---

## APK-Größe

`com.google.mlkit:translate` bündelt die Translate-Laufzeitbibliothek (`libtranslate_jni.so`, rund 16,4 MB für arm64). Google bietet dafür keine GMS-unbundled-Alternative an. Die eigentlichen Sprachmodelle (~15–30 MB pro Sprache) sind dagegen **nicht** im APK enthalten: Sie werden ausschließlich via `downloadModelIfNeeded()` on-demand von GMS-Servern geladen und gerätelokal gecacht. Das Feature bleibt unverändert erhalten; nur die benötigten Sprachmodelle werden nachgeladen.

---

## Ausgangslage im Projekt

| Vorhandener Baustein | Nutzung |
|---|---|
| `Document.extractedText` / `pageTexts` | direkter Input, kein neuer OCR-Lauf nötig |
| `Document.ocrLanguage` | Vorauswahl der Quellsprache im Picker |
| `ExtractTextUseCase` | nicht genutzt (UseCase gibt Fehler wenn kein Text) |
| `OcrModelInstaller` / `ModuleInstall` | Blueprint für Modell-Download-Pattern |
| `OcrReviewScreen` / `OcrReviewViewModel` | Blueprint für UI und ViewModel-Struktur |
| `DispatcherProvider` + `ResourceProvider` | Standard-Utilities, wie in allen ViewModels |
| `Icons.filled.Translate` | Icon bereits in Compose-Materialicons vorhanden |
| `DocumentEditSheet` (Sektion „Analyse") | Einstiegspunkt |
| `PlayServicesTasks.awaitTask()` | Coroutinen-Bridge für ML Kit Tasks |

---

## Neue Abhängigkeit

```toml
# gradle/libs.versions.toml
mlkitTranslate = "17.0.3"
mlkit-translate = { group = "com.google.mlkit", name = "translate", version.ref = "mlkitTranslate" }
```

```kotlin
// app/build.gradle.kts
// ML Kit Translate – runtime bundled; language models downloaded on demand
implementation(libs.mlkit.translate)
```

---

## Implementierte Architektur

### Domain-Modell

```
domain/model/TranslationResult.kt       ✅
```

```kotlin
data class TranslationResult(
    val sourceLanguage: String,
    val targetLanguage: String,
    val pageTranslations: List<String>
) {
    val fullText: String get() = pageTranslations.joinToString("\n\n")
}
```

### Domain-Gateway (Port)

```
domain/gateway/TextTranslator.kt        ✅
```

```kotlin
interface TextTranslator {
    suspend fun translate(
        pageTexts: List<String>,
        sourceLanguage: String,
        targetLanguage: String,
        onProgress: (current: Int, total: Int) -> Unit = { _, _ -> }
    ): List<String>
}
```

### UseCase

```
domain/usecase/TranslateTextUseCase.kt  ✅
```

Ablauf:
1. `Document.pageTexts` nutzen; Fallback auf `extractedText` als einzelne Seite.
2. Kein Text → `TranslationNoTextException`.
3. `TextTranslator.translate(...)` aufrufen.
4. `TranslationResult` zurückgeben.

### Util-Implementierung

```
util/MlKitTextTranslator.kt             ✅
```

- `Translation.getClient(options)` mit `setSourceLanguage` / `setTargetLanguage`
- Quellsprache `"auto"` → `"und"` (ML Kit-Undetermined, kein `TranslateLanguage.UNDETERMINED` in v17.0.3)
- `translator.downloadModelIfNeeded(DownloadConditions.Builder().build()).awaitTask()` — lädt Modelle on-demand
- Seitenweise Übersetzung mit `onProgress`-Callback
- `translator.close()` im `finally`-Block

> Hinweis: `TranslationModelManager` als eigene Klasse entfällt — `downloadModelIfNeeded()` übernimmt das Management direkt und ist idiomatischer.

### DI

```
di/AppProvidersModule.kt                ✅  (TextTranslator → MlKitTextTranslator)
```

### Presentation

```
ui/translation/TranslationReviewViewModel.kt   ✅
ui/translation/TranslationReviewScreen.kt      ✅
```

**ViewModel UiState:**

```kotlin
data class UiState(
    val record: Document? = null,
    val result: TranslationResult? = null,
    val isLoading: Boolean = false,
    val progress: Pair<Int, Int>? = null,   // null = Modell-Download, sonst Seite X/Y
    val error: String? = null
)
```

**Screen-Layout:**
- Dokument-Info-Card
- Einstellungs-Card: Quell-/Ziel-Picker (ExposedDropdownMenuBox) + Übersetzen-Button + Fortschrittsanzeige + Fehlertext
- Ergebnis-Card: Original-Text + Übersetzung pro Seite, bei Mehrseiter mit Seitenheader; Copy-all + Share

**Quellsprachen-Vorauswahl:** `Document.ocrLanguage` wird als Vorschlag übernommen, sofern in der Sprachliste enthalten.

---

## Navigation

```
ui/navigation/Screen.kt                 ✅  (Translation data object)
ui/navigation/AppNavHost.kt             ✅  (Route + Composable)
ui/home/HomeNavigationCallbacks.kt      ✅  (onTranslation)
ui/home/HomeActionDispatcher.kt         ✅  (HomeScanActionNavigator.onTranslateText + Dispatch)
ui/components/DocumentEditSheet.kt      ✅  (ScanAction.TranslateText, Sektion Analyse)
ui/viewer/PdfViewerScreen.kt            ✅  (TranslateText → Unit im when-Branch)
ui/home/HomeScreen.kt                   ✅  (onTranslateText = navigation.onTranslation)
```

**Einstieg:** Aktions-Sheet → Abschnitt „Analyse" → „PDF übersetzen" (Icon: `Translate`, enabled wenn `!isEncrypted && pageCount >= 1`)

---

## String-Ressourcen

```
res/values/strings_translation.xml          ✅  (EN – Basis)
res/values-de/strings_translation.xml       ✅
res/values-es/strings_translation.xml       ✅
res/values-fr/strings_translation.xml       ✅
res/values-pt/strings_translation.xml       ✅
res/values-zh-rCN/strings_translation.xml   ✅
res/values-ar/strings_translation.xml       ✅
res/values-ja/strings_translation.xml       ✅
res/values-ru/strings_translation.xml       ✅
res/values-hi/strings_translation.xml       ✅
```

---

## Build-Status

```
./gradlew :app:compileDebugKotlin   ✅  BUILD SUCCESSFUL (2026-06-07)
```

Nur vorbestehende Tink-Deprecation-Warnungen (unverändert).

---

## Abweichungen vom ursprünglichen Plan

| Plan-Entwurf | Tatsächliche Umsetzung | Begründung |
|---|---|---|
| `TranslationModelManager` als eigene Klasse | Direkt in `MlKitTextTranslator` via `downloadModelIfNeeded()` | Kein Mehrwert durch eigene Klasse; API übernimmt das Management |
| `modelDownloading: Boolean` als extra-Flag | Unified: `progress == null && isLoading` | Einfachere State-Logik, gleiche UX |
| `sourceLanguage = "auto"` im State | Vorbelegt mit `Document.ocrLanguage` wenn bekannt | Bessere UX, weniger manuelle Auswahl |
| `TranslateLanguage.UNDETERMINED` | `"und"` als Literal | Konstante existiert in v17.0.3 nicht |
| Stufe 3: TXT-Export | Noch nicht umgesetzt | Optional, bei Bedarf nachrüstbar |

---

## Offene Punkte (optional)

- **Stufe 3 – TXT-Export:** Übersetzung als `.txt` nach `MediaStore.Downloads` exportieren (analog `ExportOcrTextUseCase`).
- **Privacy-Screen:** Hinweis auf Modell-Download ergänzen (`docs/privacy-policy.html`).

---

## Änderungshistorie

- **2026-04-24:** Initialversion.
- **2026-06-07:** Vollständig implementiert. Alle Stufe-1- und Stufe-2-Punkte umgesetzt. Architektur gegenüber Entwurf vereinfacht.
