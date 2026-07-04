# Umsetzungsplan: Spezialoption "Fotoformat" fuer Bilder-zu-PDF

## Ziel

Die App soll im Flow "Bilder zu PDF" eine eigene Spezialoption fuer Fotos bekommen:

- Standardmodus bleibt unveraendert: A5/A4/A3/Letter, Hoch-/Querformat, Rand, 1/2/4 Bilder pro Seite.
- Neuer Spezialmodus: "Fotoformat" mit genau 1 Foto pro PDF-Seite.
- Jede erzeugte PDF-Seite bekommt ein Seitenverhaeltnis passend zum jeweiligen Foto.
- Die physische Seitengroesse wird nicht aus Pixeln abgeleitet, sondern innerhalb einer gewaehlten maximalen Papiergroesse berechnet.
- Beim Drucken wird keine automatische Skalierung erzwungen. Stattdessen gibt es optional einen Hinweis, wenn ein PDF variable oder nicht standardisierte Seitengroessen enthaelt.

## Rechercheergebnis

PDF erlaubt unterschiedliche Seitengroessen innerhalb eines Dokuments. Jede Seite kann eine eigene `MediaBox` haben. Die `MediaBox` beschreibt das physische Medium bzw. die natuerliche Seitengroesse der Seite; `CropBox` definiert optional den sichtbaren/gedruckten Ausschnitt und faellt standardmaessig auf `MediaBox` zurueck.

Androids `PdfRenderer.Page` bestaetigt ebenfalls, dass nicht garantiert ist, dass alle Seiten dieselbe Breite oder Hoehe haben. Adobe Acrobat bietet fuer PDFs mit gemischten Seitengroessen eigene Druckoptionen wie "Papierquelle anhand Seitengroesse der PDF-Datei waehlen" sowie Skalierungsoptionen wie "Fit" und "Actual size".

Quellen:

- Adobe PDF Reference 1.5, Page Object / Page Boundaries: https://opensource.adobe.com/dc-acrobat-sdk-docs/pdfstandards/pdfreference1.5_v6.pdf
- Android `PdfRenderer.Page`: https://developer.android.com/reference/android/graphics/pdf/PdfRenderer.Page
- Adobe Acrobat, gemischte Seitengroessen drucken: https://helpx.adobe.com/at/acrobat/desktop/print-documents/set-up-and-print-pdfs/mixed-sizes.html
- Adobe Acrobat, Seitengroesse und Skalierung beim Drucken: https://helpx.adobe.com/acrobat/desktop/print-documents/set-up-and-print-pdfs/page-size.html

Konsequenz:

- Der Fotoformat-Modus ist PDF-konform.
- Er ist fachlich sinnvoll fuer Foto-PDFs, aber nicht als Standard fuer Dokumente.
- Drucken bleibt potenziell drucker-/viewerabhaengig, weil die PDF-Seitengroessen echte physische Groessen sind.

## Architekturprinzipien

Die Umsetzung folgt der bestehenden Clean-Architecture-Struktur:

- `domain/` bleibt frameworkfrei und enthaelt Modelle, UseCases und Ports.
- `ui/` entscheidet ueber Darstellung, UI-State und Nutzerinteraktion.
- `util/` enthaelt die Android/PdfBox-Implementierungen der Domain-Ports.
- Keine Android-Framework-Typen in Domain-Modellen oder Domain-UseCases.
- Keine Businesslogik in Composables; Composables hoisten State und delegieren Aktionen.
- Bestehende Port-Signaturen bleiben nach Moeglichkeit stabil.

Bestehende Kette:

`ImagesToPdfScreen` -> `ImagesToPdfViewModel` -> `CreatePdfFromImagesUseCase` -> `ImagePdfBuilder` -> `PdfRenderingOps.createPdfFromImages()` -> `PdfEditor.createPdfFromImages()`

Wichtiges Finding:

`ImagePdfBuilder` dekodiert Bild-URIs zu `ByteArray?`. Die tatsaechlichen Bildabmessungen sind erst in `PdfEditor.createPdfFromImages()` bekannt, nachdem PdfBox `PDImageXObject` erzeugt hat. Die Berechnung der Foto-Seitengroessen gehoert deshalb in die PdfBox-Implementierung, nicht in UI, ViewModel, UseCase oder Builder.

## Produktentscheidung fuer Version 1

Name:

- Primaer: "Fotoformat"
- Untertitel/Hinweis im UI: "Eine Seite pro Foto"

Verhalten:

- `FIXED_PAGE`: bestehendes Verhalten unveraendert.
- `PHOTO_PAGE`: jedes lesbare Bild erzeugt genau eine Seite.
- `PHOTO_PAGE` nutzt `pageSetup.sizePreset` als maximale physische Groesse.
- `PHOTO_PAGE` ignoriert `pageSetup.orientation` und `pageSetup.marginPreset`.
- Im UI werden bei `PHOTO_PAGE` Layout, Orientierung und Rand ausgeblendet oder deaktiviert.
- Papierformat wird im Fotoformat-Modus als "Maximale Groesse" angezeigt.
- Der Modus wird in Version 1 nicht als globale Einstellung gespeichert.
- Append-Flow: Der Modus wird auch beim Anhaengen von Bildern angeboten, weil `AppendSource.Images` bereits `ImagePdfOptions` transportiert und dieselbe Options-UI nutzt.

Nicht-Ziele fuer Version 1:

- Keine randlose Druckgarantie.
- Keine automatische A4/Letter-Druckkopie.
- Kein globaler Druckdialog fuer alle PDFs.
- Keine Unterstuetzung fuer 2/4 Fotos pro Seite im Fotoformat-Modus.
- Keine Ableitung der PDF-Seitengroesse aus Bildpixeln.

## Seitengeometrie

Pixelmasse duerfen nicht direkt in PDF-Points uebernommen werden. Ein Foto mit 4000 x 3000 Pixeln wuerde sonst eine physische Seite von 4000 x 3000 Points erzeugen, also ca. 141 x 106 cm.

Berechnungsregel fuer `PHOTO_PAGE`:

- Basisformat aus `pageSetup.sizePreset` laden: A5, A4, A3 oder Letter.
- Lange Seite des Basisformats ist die maximale lange Seite.
- Kurze Seite des Basisformats ist die maximale kurze Seite.
- Fotoausrichtung aus `image.width` und `image.height` ableiten.
- Skalenfaktor: `min(maxLong / imgLong, maxShort / imgShort)`.
- PDF-Seitengroesse:
  - Querformatfoto: `imgLong * scale` x `imgShort * scale`
  - Hochformatfoto: `imgShort * scale` x `imgLong * scale`
- Quadratische Fotos werden explizit als quadratische Seiten behandelt. Implementierung: erst `imgW == imgH` pruefen, dann `imgW > imgH` fuer Querformat.
- Bild wird auf `0, 0, pageWidth, pageHeight` gezeichnet.

Beispiel:

- Maximalformat A4: 595.28 x 841.89 Points.
- Foto 4000 x 3000 Pixel, Querformat 4:3.
- Lange Seite wird auf A4 lang begrenzt.
- Ergebnis: ca. 841.89 x 631.42 Points.
- Das entspricht einem Querformat-Foto innerhalb der A4-Maximalgroesse, aber nicht einer A4-Seite.

## Konkrete Umsetzung

### Phase 1: Domain-Modell erweitern

Dateien:

- `app/src/main/java/info/meuse24/pdf_scanner/domain/usecase/ImagePdfOptions.kt`

Aenderungen:

- Neues Enum `ImagePdfPageMode` in Domain einfuehren:

```kotlin
enum class ImagePdfPageMode {
    FIXED_PAGE,
    PHOTO_PAGE
}
```

- `ImagePdfOptions` erweitern:

```kotlin
data class ImagePdfOptions(
    val layout: ImagePageLayout,
    val pageSetup: PdfPageSetup = PdfPageSetup(),
    val pageMode: ImagePdfPageMode = ImagePdfPageMode.FIXED_PAGE
)
```

Begruendung:

- Domain-Modell bleibt frameworkfrei.
- Default ist rueckwaertskompatibel.
- `PdfRenderingOps`-Signatur muss nicht geaendert werden.
- Testfakes koennen die bestehende Methode weiter ueberschreiben.

### Phase 2: PDF-Geometrie-Helfer ergaenzen

Dateien:

- `app/src/main/java/info/meuse24/pdf_scanner/util/PdfEditorImageOps.kt`

Aenderungen:

- `photoPageRectangle(imgW, imgH, maxSetup)` neben `pageRectangle()`, `layoutCells()` und `fitInsideCell()` ergaenzen.
- Hilfsfunktion muss defensiv mit ungueltigen Bildmassen umgehen: `require(imgW > 0f && imgH > 0f)`.
- Bestehende `pageRectangle()` unveraendert lassen, damit FIXED_PAGE stabil bleibt.

Pseudo-Code:

```kotlin
internal fun photoPageRectangle(imgW: Float, imgH: Float, maxSetup: PdfPageSetup): PDRectangle {
    require(imgW > 0f && imgH > 0f)
    val base = pageRectangle(maxSetup.copy(orientation = PdfPageOrientation.PORTRAIT))
    val maxLong = maxOf(base.width, base.height)
    val maxShort = minOf(base.width, base.height)
    val imgLong = maxOf(imgW, imgH)
    val imgShort = minOf(imgW, imgH)
    val scale = minOf(maxLong / imgLong, maxShort / imgShort)
    val width = when {
        imgW == imgH -> imgLong * scale
        imgW > imgH -> imgLong * scale
        else -> imgShort * scale
    }
    val height = when {
        imgW == imgH -> imgLong * scale
        imgW > imgH -> imgShort * scale
        else -> imgLong * scale
    }
    return PDRectangle(width, height)
}
```

Architekturhinweis:

- Diese Funktion liegt bewusst in `util`, weil sie PdfBox-Typen (`PDRectangle`) verwendet.
- Domain bekommt keine PdfBox-Abhaengigkeit.

### Phase 3: PdfBox-Erzeugung verzweigen

Dateien:

- `app/src/main/java/info/meuse24/pdf_scanner/util/PdfEditor.kt`

Aenderungen:

- `createPdfFromImages(...)` in zwei private Pfade aufteilen:
  - `createFixedPagePdfFromImages(...)`
  - `createPhotoPagePdfFromImages(...)`
- Oeffentliche Override-Signatur bleibt gleich.

FIXED_PAGE:

- Bestehendes Verhalten moeglichst 1:1 beibehalten.
- `layoutCells(options)`, `pageRectangle(options.pageSetup)` und `imageBytes.chunked(options.layout.imagesPerPage)` bleiben im FIXED_PAGE-Pfad.

PHOTO_PAGE:

- Ueber alle `imageBytes` iterieren.
- `null`-Eintraege ueberspringen, nicht als leere Seite schreiben.
- Fuer jedes lesbare Bild:
  - `PDImageXObject.createFromByteArray(...)`
  - `photoPageRectangle(image.width.toFloat(), image.height.toFloat(), options.pageSetup)`
  - `PDPage(pageRectangle)`
  - Bild vollflaechig zeichnen.
- Wenn keine Seite erzeugt wurde, wie bisher eine `IOException` werfen.

Begruendung zum Ueberspringen von `null`:

- `ImagePdfBuilder` garantiert bereits, dass nicht alle Bilder unlesbar sind.
- Im Standardmodus erzeugen `null`-Eintraege leere Layout-Zellen, weil ein Seitenlayout mit festen Slots existiert.
- Im Fotoformat-Modus gibt es keinen sinnvollen leeren Foto-Slot; eine leere individuelle Foto-Seite waere fachlich irritierend.
- `skippedCount` bleibt weiter die Nutzerinformation.

### Phase 4: ViewModel-Schnittstelle praezisieren

Dateien:

- `app/src/main/java/info/meuse24/pdf_scanner/ui/imagestopdf/ImagesToPdfViewModel.kt`
- `app/src/test/java/info/meuse24/pdf_scanner/ui/imagestopdf/ImagesToPdfViewModelTest.kt`

Aenderungen:

- `createPdf(imageUris, filename, layout)` ersetzen durch:

```kotlin
fun createPdf(imageUris: List<Uri>, filename: String, options: ImagePdfOptions)
```

- ViewModel fuegt keine eigenen Options mehr zusammen, sondern verwendet das vom Screen gelieferte `ImagePdfOptions`.
- `pageSetup` bleibt im ViewModel, weil es aus `AppSettingsRepository` kommt und weiterhin persistiert wird.
- `pageMode` wird nicht im ViewModel persistiert.

Begruendung:

- UI ist fuer den temporaeren Modus verantwortlich.
- ViewModel bleibt schlank und orchestriert nur Ladezustand, Fehler, UseCase-Aufruf und Persistenz der bestehenden Seiteneinstellung.
- Domain-UseCase-Schnittstelle ist bereits optionsbasiert; der ViewModel-Call sollte das widerspiegeln.

Testanpassungen:

- Bestehende Tests von `createPdf(..., layout)` auf `createPdf(..., options)` umstellen.
- Test `createPdf reicht Layout und PageSetup als Optionen weiter` erweitern um `pageMode`.
- Neuer Test: `createPdf reicht PHOTO_PAGE weiter`.
- Test fuer `updatePageSetup` bleibt unveraendert.

### Phase 5: UI-State und Compose-UI anpassen

Dateien:

- `app/src/main/java/info/meuse24/pdf_scanner/ui/imagestopdf/ImagesToPdfScreen.kt`
- `app/src/main/java/info/meuse24/pdf_scanner/ui/imagestopdf/ImagesPdfOptionsContent.kt`
- `app/src/main/java/info/meuse24/pdf_scanner/ui/components/PdfPageSetupSection.kt`

State:

- `ImagesToPdfScreen` haelt `pageMode` lokal mit `rememberSaveable`.
- `AppendScreen` haelt `pageMode` fuer pending images ebenfalls lokal mit `rememberSaveable`.
- `selectedLayout` bleibt lokal.
- Beim Klick auf Erzeugen baut der Screen:

```kotlin
val options = ImagePdfOptions(
    layout = selectedLayout,
    pageSetup = pageSetup,
    pageMode = pageMode
)
viewModel.createPdf(imageUris, effectiveName, options)
```

Wichtig: Der Screen berechnet kein `effectiveLayout`. Im `PHOTO_PAGE`-Modus ignoriert die PdfBox-Implementierung `options.layout`; diese Semantik gehoert genau an diese Implementierungsstelle und wird nicht im UI dupliziert.

UI-Komponenten:

- `ImagesPdfOptionsContent` bekommt neue Parameter:

```kotlin
pageMode: ImagePdfPageMode,
onPageModeSelected: (ImagePdfPageMode) -> Unit
```

- Modusauswahl als `SingleChoiceSegmentedButtonRow`:
  - "Standardseite"
  - "Fotoformat"

Verhalten bei `FIXED_PAGE`:

- Layoutauswahl sichtbar.
- `PdfPageSetupSection` zeigt Papierformat, Orientierung, Rand.
- Bestehende Layout-Vorschau bleibt.

Verhalten bei `PHOTO_PAGE`:

- Layoutauswahl mit `AnimatedVisibility` ausgeblendet.
- Papierformat-Zeile sichtbar, aber Label lautet "Maximale Groesse".
- Orientierung und Rand ausgeblendet.
- Vorschau zeigt ein einzelnes Foto-Seitenrechteck, kein 2/4er-Layout. Konkrete Spec: Canvas zeichnet ein nicht-standardisiertes Rechteck (z. B. 3:4) mit einem Photo-/Crop-Icon-Overlay, damit "Seite passt sich Foto an" sofort visuell erkennbar ist.
- Unter der Modusauswahl wird ein `bodySmall`-Supporting-Text angezeigt. Bei `PHOTO_PAGE`: "Jedes Foto erhaelt eine eigene Seite im Originalformat." Bei `FIXED_PAGE`: knapper Hinweis auf Standardseiten.
- Die Modus-Segmented-Buttons nutzen Icons:
  - `FIXED_PAGE`: Dokument-/Article-Icon
  - `PHOTO_PAGE`: Photo-/CropOriginal-Icon

`PdfPageSetupSection`:

- Nicht anfassen.
- Bedingungslogik bleibt im Aufrufer `ImagesPdfOptionsContent`.
- Fuer `FIXED_PAGE` wird `PdfPageSetupSection(...)` unveraendert verwendet.
- Fuer `PHOTO_PAGE` rendert `ImagesPdfOptionsContent` eine lokale `MaxPageSizeRow` mit eigenem Label; die Segmentauswahl selbst nutzt die gemeinsame `PdfPageSizeSegmentedRow`.

Compose-/Material-Regeln:

- State bleibt gehoistet.
- Keine Side Effects direkt in der Composition.
- Segmented Controls sind fuer diese 2-Modus-Auswahl passend.
- Moduswechsel nutzt `AnimatedVisibility` mit `expandVertically() + fadeIn()` und `shrinkVertically() + fadeOut()`.
- Touch Targets bleiben Material-konform.
- Texte verwenden `MaterialTheme.typography`.
- Farben bleiben ueber `MaterialTheme.colorScheme`.
- Der Druckhinweis spaeter als `AlertDialog`, weil er vor einer potenziell missverstaendlichen Druckaktion steht.

### Phase 6: Strings und Lokalisierung

Dateien:

- `app/src/main/res/values*/strings_images_to_pdf.xml`

Neue Strings:

- `images_to_pdf_mode_label`
- `images_to_pdf_mode_fixed`
- `images_to_pdf_mode_photo`
- `images_to_pdf_page_size_max_label`
- `images_to_pdf_mode_fixed_supporting_text`
- `images_to_pdf_mode_photo_supporting_text`
- `print_custom_page_size_title`
- `print_custom_page_size_message`
- `print_custom_page_size_confirm`

Locales:

- `values/`
- `values-de/`
- `values-es/`
- `values-fr/`
- `values-pt/`
- `values-zh-rCN/`
- `values-ar/`
- `values-ja/`
- `values-ru/`
- `values-hi/`

Vorgehen:

- Erst englische und deutsche Texte fachlich sauber formulieren.
- Fuer die weiteren Locales knappe, konsistente Uebersetzungen eintragen.
- Keine sichtbaren langen Erklaertexte im Haupt-UI, damit der Optionsbereich nicht ueberladen wird.

### Phase 7: Druck-Hinweis vorbereiten

Aktueller Druckpfad:

- `PdfPrintHelper.print(...)`
- `PdfPrintAdapter`

Der Adapter reicht die bestehende PDF direkt an Androids Drucksystem weiter. Das soll so bleiben.

Empfehlung:

- Kein allgemeiner vorgeschalteter Druckdialog.
- Ein Hinweisdialog nur dann, wenn das PDF gemischte oder nicht standardisierte Seitengroessen enthaelt.
- Keine automatische Skalierung in Version 1.

Neue fachliche Klassifizierung:

```kotlin
enum class PdfPageSizeCategory {
    UNIFORM_STANDARD,
    UNIFORM_CUSTOM,
    MIXED
}
```

Architekturentscheidung:

- Die Klassifizierung ist fachlich fuer Druck-/Anzeigeentscheidungen relevant, aber ihre Implementierung braucht PdfBox.
- Sie gehoert auf den bestehenden Metadaten-Port `PdfMetadataOps`, nicht auf `PdfRenderingOps`.
- `PdfPageSizeCategory` gehoert als reines Domain-Modell nach `domain/model/`, nicht nach `domain/pdf/`.

Empfohlene Variante:

- `PdfMetadataOps` um eine Methode erweitern:

```kotlin
fun classifyPageSizes(pdfFile: File): PdfPageSizeCategory
```

- `PdfPageSizeCategory` in `domain/model/` ablegen.
- Implementierung in `PdfEditor`.

Begruendung:

- UI kennt nur den Domain-Port bzw. das Ergebnis.
- PdfBox bleibt in `util`.
- Die Methode ist Metadatenanalyse, nicht Rendering.

Klassifizierungslogik:

- Alle `mediaBox`-Groessen lesen.
- Mit Toleranz von ca. 2 Points vergleichen.
- Standardformate erkennen:
  - A5
  - A4
  - A3
  - Letter
  - jeweils Hoch- und Querformat.
- Wenn alle Seiten gleich und Standard: `UNIFORM_STANDARD`.
- Wenn alle Seiten gleich, aber nicht Standard: `UNIFORM_CUSTOM`.
- Wenn mehrere Groessen: `MIXED`.

Hinweisdialog:

- Nur bei `UNIFORM_CUSTOM` oder `MIXED`.
- Text kurz halten:

"Dieses PDF enthaelt Foto- oder Sonderformate. Beim Drucken kann der Druckdialog die Seiten auf das gewaehlte Papier skalieren oder bei tatsaechlicher Groesse abschneiden. Fuer normale Ausdrucke waehle im Druckdialog 'Anpassen' bzw. 'Fit'."

Buttons:

- Confirm: "Drucken"
- Dismiss: "Abbrechen"

Optional fuer spaeter:

- "Nicht mehr anzeigen" als gespeicherte App-Einstellung.
- Nicht in Version 1 aufnehmen, wenn der Scope klein bleiben soll.

Wichtig:

- Die App soll Androids Druckdialog nicht ersetzen.
- Die App soll keine Druckerskalierung erzwingen, weil Print Services herstellerabhaengig sind.
- Eine temporaere A4/Letter-Druckversion waere ein eigenes Feature fuer eine spaetere Version.

### Phase 8: Tests

Unit-Tests fuer Geometrie:

- Datei: `app/src/test/java/info/meuse24/pdf_scanner/util/PdfEditorImageOpsTest.kt`
- Tests:
  - `photoPageRectangle begrenzt Querformat auf maximale lange Seite`
  - `photoPageRectangle begrenzt Hochformat auf maximale lange Seite`
  - `photoPageRectangle erhaelt Foto-Seitenverhaeltnis`
  - `photoPageRectangle bleibt innerhalb A5 A4 A3 Letter`
  - `photoPageRectangle wirft bei ungueltigen Bildmassen`

Unit-/Integration-Tests fuer PDF-Erzeugung:

- Datei: `app/src/test/java/info/meuse24/pdf_scanner/util/PdfEditorImageOpsTest.kt` oder bestehende PdfEditor-Tests.
- Tests:
  - `FIXED_PAGE erzeugt weiterhin bisherige A4 Single Zelle`
  - `PHOTO_PAGE erzeugt eine Seite pro lesbarem Bild`
  - `PHOTO_PAGE erzeugt verschiedene MediaBoxen fuer Hoch- und Querformat`
  - `PHOTO_PAGE ueberspringt unlesbare Bilder ohne leere Seite`
  - `PHOTO_PAGE pageCount entspricht lesbare Bilder und damit imageUris minus skippedCount`

ViewModel-Tests:

- Datei: `ImagesToPdfViewModelTest.kt`
- Tests:
  - `createPdf reicht ImagePdfOptions unveraendert weiter`
  - `createPdf reicht PHOTO_PAGE weiter`
  - `updatePageSetup speichert weiter im Repository`
  - `zweiter Aufruf waehrend editLoading wird ignoriert`

UI-Tests, falls im Projekt passend:

- `PHOTO_PAGE` blendet Layoutauswahl aus.
- `PHOTO_PAGE` blendet Orientierung und Rand aus.
- `FIXED_PAGE` zeigt alle bisherigen Optionen.
- Moduswechsel nutzt animierte Sichtbarkeit.
- Supporting Text und Icons sind sichtbar.

Druckklassifizierung:

- Tests fuer `classifyPageSizes`:
  - einheitliches A4 -> `UNIFORM_STANDARD`
  - einheitliches Custom -> `UNIFORM_CUSTOM`
  - Hoch-/Querformat oder verschiedene Fotogroessen -> `MIXED`
  - Toleranzvergleich gegen Rundungsunterschiede.

Regression:

- Bestehende Tests fuer `CreatePdfFromImagesUseCase`, `ImagesToPdfViewModel`, `PdfEditorImageOps` aktualisieren.
- Keine Migration fuer Settings noetig, solange `pageMode` nicht persistiert wird.

### Phase 9: Manuelle Verifikation

Prueffaelle:

- 1 Hochformatfoto -> 1 PDF-Seite im Hochformat-Fotoverhaeltnis.
- 1 Querformatfoto -> 1 PDF-Seite im Querformat-Fotoverhaeltnis.
- Mehrere gemischte Fotos -> mehrere Seiten mit passenden MediaBoxen.
- Standardmodus mit 2/4 Bildern -> unveraendertes Layout.
- Import/Archiv/Thumbnail funktioniert nach Erstellung.
- Viewer zeigt Seiten mit verschiedenen Groessen ohne Absturz.
- Drucken eines Standard-A4-PDFs startet ohne Hinweis.
- Drucken eines Fotoformat-PDFs zeigt Hinweis und startet danach den Android-Druckdialog.

## Risiken und Gegenmassnahmen

Risiko: Nutzer verstehen "vollflaechig" als randlosen Ausdruck.

- Gegenmassnahme: Option "Fotoformat" nennen, nicht "Randlos drucken".
- Optionaler kurzer Hinweis in Druckdialog: Drucker kann trotzdem Rand erzeugen.

Risiko: Gemischte Seitengroessen wirken im Viewer ungewohnt.

- Gegenmassnahme: Spezialmodus bewusst separat anbieten und nicht als Default speichern.

Risiko: Android-Druckdienste behandeln Sonderformate unterschiedlich.

- Gegenmassnahme: Nur Hinweis anzeigen, Original-PDF unveraendert an Print Spooler geben.

Risiko: Domain wird durch PdfBox-Details verschmutzt.

- Gegenmassnahme: `PDRectangle` und PdfBox-Logik bleiben in `util`; Domain transportiert nur Optionen und Kategorien.

Risiko: UI wird zu voll.

- Gegenmassnahme: Modusauswahl oben, danach kontextabhaengig nur relevante Optionen anzeigen.

## Reihenfolge der Umsetzung

1. Domain-Modell `ImagePdfPageMode` und `ImagePdfOptions.pageMode`.
2. Geometrie-Helfer `photoPageRectangle` mit Unit-Tests.
3. `PdfEditor.createPdfFromImages()` in FIXED_PAGE/PHOTO_PAGE verzweigen.
4. ViewModel-Signatur auf `ImagePdfOptions` umstellen und Tests aktualisieren.
5. Compose-UI um Modusauswahl und kontextabhaengige Optionen erweitern.
6. Strings in allen Locales ergaenzen.
7. Druck-Seitengroessenklassifizierung am PDF-Port ergaenzen.
8. Hinweisdialog nur bei `UNIFORM_CUSTOM`/`MIXED` in den Druck-Entry-Points anschliessen.
9. Gesamttests und manuelle Verifikation.

## Fortschritt

- [x] Findings eingearbeitet: `PdfPageSetupSection` bleibt semantisch unveraendert; `MaxPageSizeRow` bleibt im Aufrufer und nutzt die gemeinsame `PdfPageSizeSegmentedRow`.
- [x] Findings eingearbeitet: Seitengroessenklassifizierung gehoert auf `PdfMetadataOps`.
- [x] Findings eingearbeitet: `PdfPageSizeCategory` gehoert nach `domain/model/`.
- [x] Findings eingearbeitet: kein `effectiveLayout` im Screen.
- [x] Findings eingearbeitet: quadratische Fotos explizit behandeln.
- [x] Findings eingearbeitet: pageCount/skippedCount-Konsistenz testen.
- [x] Findings eingearbeitet: `AnimatedVisibility`, Icons und Supporting Text sind Teil der UI-Spec.
- [x] Append-Flow analysiert: durch `AppendSource.Images(..., ImagePdfOptions)` ohne Domain-Mehraufwand moeglich; UI wird mitgezogen.
- [x] Domain-Modell umgesetzt: `ImagePdfPageMode`, `ImagePdfOptions.pageMode`, `PdfPageSizeCategory`.
- [x] PDF-Geometrie umgesetzt: `photoPageRectangle`, FIXED_PAGE/PHOTO_PAGE-Verzweigung in `PdfEditor`.
- [x] Images-to-PDF UI/ViewModel umgesetzt: lokaler `pageMode`, Optionsobjekt wird unveraendert weitergereicht.
- [x] Append-UI/ViewModel umgesetzt: gleicher Optionsfluss wie Bilder-zu-PDF, `PHOTO_PAGE` wird mitgezogen.
- [x] Druckklassifizierung und Hinweisdialog umgesetzt: `PdfMetadataOps.classifyPageSizes`, Hinweis in Home und Viewer.
- [x] Tests aktualisiert und ausgefuehrt: `./gradlew.bat testDebugUnitTest` erfolgreich.
- [x] Review-Fix F1/F2: Print-Request-Guard und Pending-Dialog-State liegen im ViewModel; schnelle Doppelklicks starten keinen zweiten Check/Print-Job.
- [x] Review-Fix F3/F4: Tests fuer Print-Warning-Flow und `classifyPageSizes` ergaenzt.
- [x] Review-Fix F5/F9/F10: 0-Seiten-PDF loest keinen Sonderformat-Hinweis aus, Default in `PdfMetadataOps` dokumentiert, redundantes `open` entfernt.
- [x] Review-Cleanup F6: Print-Warning-Entscheidung in `CheckPrintPageSizeWarningUseCase` extrahiert; Home und Viewer delegieren den Print-State an `PrintRequestCoordinator` mit atomarem Race-Guard.
- [x] Review-Cleanup F7: gemeinsamer `PrintPageSizeWarningDialog` extrahiert.
- [x] Review-Cleanup F8: Gemeinsame `PdfPageSizeSegmentedRow` extrahiert; `PdfPageSetupSection` bleibt semantisch unveraendert, `MaxPageSizeRow` nutzt nur noch eigenes Label plus gemeinsame Segmentauswahl.
- [x] Review-Verifikation: `./gradlew.bat testDebugUnitTest` nach Review-Fixes erfolgreich.
- [x] Review-Verifikation nach F6/F8: `./gradlew.bat testDebugUnitTest` erfolgreich.

## Offene Entscheidungen vor Implementierung

- Soll der Modus-Name final "Fotoformat" bleiben?
- Entscheidung fuer Umsetzung: "Fotoformat" bleibt finaler Arbeitsname.
- Entscheidung fuer Umsetzung: Append-Flow wird mitgezogen.
- Entscheidung fuer Umsetzung: Druckhinweis bleibt bewusst einfach, ohne "Nicht mehr anzeigen".
- Entscheidung fuer Umsetzung: Druckklassifizierung wird in Version 1 umgesetzt, weil sie die Druck-Erwartung sauber absichert.
