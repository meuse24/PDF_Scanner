# Implementierungsplan: AcroForm-Formulare ausfüllen

## Umsetzungsstatus

**Status: umgesetzt.** Der Plan wurde vor der Implementierung wie folgt konkretisiert:

- Flattening ist eine optionale Speicheroption; standardmäßig bleibt die erzeugte Kopie editierbar.
- Automatisch erzeugte AcroForm-Fixtures decken Erkennung und Wert-Round-Trips ab. Flattening wird wegen der von PdfBox-Android verwendeten Android-Grafikklassen als Instrumentation-Test geprüft.
- Das Feature ist frei verfügbar. Billing/Premium-Gating bleibt ein separates Vorhaben.
- v1 zeigt jeweils eine Seite ohne Pinch-Zoom. Dadurch bleiben Feldpositionierung, IME-Verhalten und Speicherverbrauch deterministisch.
- Mehrfachauswahl-Listboxen verwenden Listenwerte statt eines verlustbehafteten Einzelstrings.
- Unicode-Fallbacks werden anhand der im Feldwert vorkommenden Schrift gewählt. Getrennte PDF-Ressourcennamen verhindern Kollisionen, wenn ein Formular mehrere Schriften verwendet. Gebündelt sind Noto Sans (allgemein), Noto Sans Devanagari, Noto Sans Arabic und Noto Sans CJK JP (deckt Han, Kana und Hangul ab); die Glyph-Abdeckung wird vor dem Schreiben geprüft. Die Fonts werden gemäß dem AcroForm-Ladepfad von PDFBox vollständig statt als verzögertes Subset eingebettet, damit die Appearance-Erzeugung ihre Unicode-CMap behält.

Umgesetzt sind Erkennung, XFA-/Signatur-Schutz, Feldbaum- und Widget-Mapping, Text-/Checkbox-/Radio-/Combo-/Listbox-Unterstützung, mehrseitige Overlays, Pflichtfeldprüfung, Zurücksetzen, optionales Flattening, Persistierung als neue Kopie, Viewer-/Navigations-/Hilt-Integration, skriptabhängiger Unicode-Font-Fallback mit Glyph-Prüfung, Tests, Dokumentation und zehn Locales.

## Kurzfazit

Machbar, aber **kein Wochenend-Feature** — realistisch **8–12 Personentage** für eine solide Erstversion (Textfelder, Checkboxen, Radiobuttons, Dropdowns; ohne Signaturfelder/XFA). Der Grund: PdfBox-Android bringt bereits vollständige AcroForm-Unterstützung mit (`com.tom_roush.pdfbox.pdmodel.interactive.form.*` — verifiziert im aktuell eingebundenen `pdfbox-android-2.0.27.0`-Artefakt: `PDAcroForm`, `PDTextField`, `PDCheckBox`, `PDRadioButton`, `PDComboBox`, `PDListBox`, `AppearanceGeneratorHelper`). Es muss also **keine neue Bibliothek** integriert werden — der Aufwand steckt in UI (Overlay-Eingabefelder über der gerenderten Seite), Koordinaten-Mapping (existiert bereits analog in `AnnotateCanvasHelpers`/`PdfEditorAnnotationOps`) und den bekannten Font/Unicode-Fallstricken.

## Technische Machbarkeit

- `PDAcroForm` liefert die Feldbaumstruktur (`PDFieldTree`), jedes `PDField` kennt Typ, Wert, Widget-Annotation (Rect + Seite), Flags (readonly/required).
- Wertsetzen: `PDTextField.setValue()`, `PDCheckBox.check()`/`unCheck()`, `PDRadioButton.setValue()`, `PDChoice.setValue()` — Appearance-Regenerierung übernimmt `AppearanceGeneratorHelper` automatisch (Alternative: `acroForm.setNeedAppearances(true)`, riskanter, da nicht jeder Fremd-Viewer NeedAppearances korrekt neu rendert).
- `PDAcroForm.flatten()` erlaubt optionales "Festschreiben" der Werte (Formular wird nicht mehr editierbar, Werte werden Teil des Seiteninhalts) — sinnvoll als Export-Option, damit auch Viewer ohne Formular-Support die Werte korrekt anzeigen.

## Bekannte Risiken (aus Projekterfahrung ableitbar)

1. **Unicode/Font-Problem** — analog zum bereits dokumentierten ZH/JA/KO-Problem bei Searchable-PDF (siehe CLAUDE.md, TTC/OTC-Fonts). AcroForm-Textfelder referenzieren im `/DA`-String (Default Appearance) meist eine Base-14-Schriftart (Helvetica). Nicht-lateinische Eingaben (auch Umlaute in Einzelfällen, je nach eingebettetem Font) können zu Tofu-Zeichen führen. Muss geprüft und ggf. mit Font-Ersetzung (Noto Sans o.ä., analog `PdfEditorAnnotationOps.sanitizeCommentText`) abgefangen werden.
2. **Signierte Formulare** — wenn das PDF bereits digitale Signaturfelder mit vorhandener Signatur enthält, invalidiert jede Wertänderung die Signatur. Muss erkannt und der Nutzer gewarnt werden (Formular ggf. nur read-only anzeigen).
3. **XFA-Formulare** — manche Behörden-PDFs nutzen XFA (dynamische Formulare, `PDXFAResource`) statt klassischer AcroForm-Felder. PdfBox unterstützt XFA nicht zum Ausfüllen. Muss erkannt und klar kommuniziert werden ("Dieses Formular wird nicht unterstützt"), sonst Frustration bei genau der Zielgruppe (Behörden-PDFs), die das Feature adressiert.
4. **Radiobuttons/Checkbox-Gruppen** — Mapping von Kind-Widgets zu Parent-Feld (`/Parent`, `/Kids`) muss korrekt sein, sonst falsche Werte.
5. **Mehrseitige Formulare** — Felder können über mehrere Seiten verteilt sein; UI muss Feldliste pro Seite aufbauen (kein reines "aktuelle Seite"-Modell).
6. **Performance** — große Formulare (Steuererklärungen etc.) mit hunderten Feldern; Overlay-Rendering darf nicht die Bitmap-Rendering-Pipeline (`PdfPageBitmapRenderer`, Mutex) blockieren.
7. **Kein Premium-Gating vorhanden** — im Code existiert aktuell keine Billing/IAP-Infrastruktur. Falls das Feature als Premium-Feature verkauft werden soll, ist das ein separates, zusätzliches Vorhaben (Play Billing Library, Entitlement-Check, eigener Plan).

## Architektur-Einordnung (folgt bestehenden Schichten-Regeln)

| Schicht | Neue/geänderte Artefakte |
|---|---|
| `domain/model/` | `FormField` (id, fqn, type, pageIndex, rectNormalized, value, options, required, readOnly), `FormFieldType` (TEXT, MULTILINE_TEXT, CHECKBOX, RADIO_GROUP, COMBO_BOX, LIST_BOX, UNSUPPORTED), `AcroFormCapability` (NONE, FILLABLE, XFA_UNSUPPORTED, SIGNED_LOCKED) |
| `domain/pdf/` | neuer Port `PdfFormOps`: `detectFormCapability(file): AcroFormCapability`, `readFormFields(file): List<FormField>`, `fillFormFields(input, outputDir, values, flatten: Boolean): File` |
| `util/` | `PdfEditorFormOps.kt` (Implementierung via PdfBox-Android, analog `PdfEditorAnnotationOps`) — Feldbaum traversieren, Werte setzen, Font-Fallback für Unicode, `flatten()` optional |
| `domain/usecase/` | `ReadFormFieldsUseCase`, `FillFormUseCase` (persistiert via `ScanArtifactPersister`, analog `ApplySignatureStampUseCase`) |
| `domain/workflow/` | `FormFillWorkflow` (Guards: Datei existiert, nicht verschlüsselt, `AcroFormCapability == FILLABLE`, Validierung Pflichtfelder), neue `ScanWorkflowError`-Fälle (`FormNotFillable`, `FormFillFailed`, `RequiredFieldMissing`) |
| `ui/formfill/` | neuer Screen `FormFillScreen.kt`, `FormFillViewModel.kt`, `FormFillModels.kt` — Seiten-Bitmap (Wiederverwendung `PdfPageBitmapRenderer`) + Compose-Overlay (TextField/Checkbox/RadioButton/Dropdown), Koordinaten-Mapping analog `mapDisplayToPdfCoord`/`mapPdfToDisplayCoord` aus `PdfEditorAnnotationOps` |
| `ui/viewer/` | neuer Aktions-Button "Formular ausfüllen", nur sichtbar wenn `AcroFormCapability == FILLABLE` (Detection beim Öffnen, analog Print-Warning-Check-Pattern) |
| `ui/navigation/` | neue Route `Screen.FormFill` |
| `di/` | `PdfFormOps`-Binding in `PdfOperationsModule` |
| Strings | `strings_formfill.xml` in allen 10 Locales (`values/`, `-de`, `-es`, `-fr`, `-pt`, `-zh-rCN`, `-ar`, `-ja`, `-ru`, `-hi`) — Feldbeschriftungen, Fehlermeldungen ("Formular nicht unterstützt", "Pflichtfeld", "Formular gesperrt (signiert)"), Buttons (Speichern/Abbrechen/Als PDF festschreiben) |

## Phasenplan

**Phase 1 — Erkennung & Datenmodell (1–1,5 Tage)**
- `PdfFormOps.detectFormCapability()` + `readFormFields()` implementieren.
- Unit-Tests mit zur Laufzeit generierten Test-AcroForms (PdfBox kann im JVM-Unit-Test selbst ein Formular-PDF bauen — kein Android-Gerät nötig, analog `PdfEditorTest`).
- Radiobutton-/Kids-Mapping, Seiten-Zuordnung, XFA-Erkennung (`PDXFAResource` vorhanden?).

**Phase 2 — Werte schreiben (1,5–2 Tage)**
- `fillFormFields()`: Werte pro Feldtyp setzen, Appearance-Regenerierung testen (echte Behörden-PDF-Fixtures besorgen, z. B. öffentlich verfügbare Formulare).
- Font/Unicode-Fallback für Textfelder (Wiederverwendung der Erkenntnisse aus dem Searchable-PDF-Font-Problem).
- Optionales `flatten()` für Export.
- Instrumentation-Test: Ausfüllen → Speichern → mit PdfBox wieder einlesen → Werte verifizieren (Round-Trip, analog `SearchableAndRoundTripInstrumentedTest`).

**Phase 3 — UI: Formular-Ausfüll-Screen (3–4 Tage)**
- Seiten als Bitmap rendern (bestehende Pipeline), Overlay-Composables pro Feldtyp positioniert nach normalisiertem Rect.
- Scroll-/Zoom-Verhalten analog Viewer (ggf. vereinfachte Variante: eine Seite auf einmal, kein Pinch-Zoom in v1, um Aufwand zu begrenzen).
- Tastatur-Handling (IME), Pflichtfeld-Validierung vor Speichern, Fehler-/Erfolgs-State nach bestehendem Pattern (`_error`/`_success` StateFlow).
- Sperr-Zustand anzeigen bei signierten/XFA-Formularen statt Editor zu öffnen.

**Phase 4 — Integration & Verkabelung (1 Tag)**
- Viewer-Aktion + Navigation-Route + Hilt-Bindings.
- `DocumentEditSheet`/`ScanAction` ggf. um `FillForm`-Eintrag ergänzen, falls Formular erkannt.

**Phase 5 — Lokalisierung & Doku (0,5–1 Tag)**
- Neue Strings in 10 Sprachen.
- Privacy-/Help-Texte prüfen (keine neuen Datenflüsse, rein lokal — voraussichtlich unkritisch).

**Phase 6 — Feinschliff/Edge-Cases (1–1,5 Tage)**
- Große Formulare (Performance), leere Formulare ohne Kids, gemischte Feldtypen, Formulare mit bereits vorhandenen Werten (Vorbelegung anzeigen), Undo/Reset-Button.

## Bewusst außerhalb des Scopes (v1)

- **XFA-Formulare** (dynamische Behörden-Formulare) — nur Erkennung + verständliche Fehlermeldung, kein Ausfüllen.
- **Digitale Signaturfelder** (`PDSignatureField`) — eigenes, größeres Feature (Kryptografie, Zertifikate), nicht Teil dieses Plans.
- **JavaScript-Formularlogik** (Auto-Berechnungen, Validierungsskripte im PDF) — wird ignoriert, nur statische Werte.
- **Formulare neu erstellen/designen** — nur Ausfüllen bestehender Felder, kein Formular-Editor.

## Entschiedene Priorisierungsfragen

1. Flattening ist optional und standardmäßig deaktiviert.
2. Selbst erzeugte Fixtures sind Teil der automatisierten Tests; reale Behördenformulare können später als rechtlich unbedenkliche Regressionsfixtures ergänzt werden.
3. Das Feature bleibt vorerst frei verfügbar; es wird keine Billing-Infrastruktur eingeführt.
