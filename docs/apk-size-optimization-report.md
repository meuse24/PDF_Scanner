# APK-Größenoptimierung: Abschlussbericht

Stand: 2026-07-04.

Status: **abgeschlossen**. Die beschlossenen risikoarmen Optimierungen sind umgesetzt;
bewusst beibehaltene Komponenten sind als Produkt- und Kompatibilitätsentscheidungen
dokumentiert.

## Umgesetzte Optimierungen

| Maßnahme | Ersparnis | Verifikation |
|---|---:|---|
| Latin-OCR → GMS-unbundled (`play-services-mlkit-text-recognition`) | ~13 MB/Gerät | OCR-Binaries/TFLite aus APK weg, App-Start ok |
| Barcode → GMS-unbundled (`play-services-mlkit-barcode-scanning` 18.3.1) | ~5,6 MB/Gerät | `libbarhopper` + Barcode-TFLite aus APK weg, App-Start ok |
| BouncyCastle-PQC-Ressourcen (Picnic/SIKE) via `packaging.excludes` | ~8,2 MB | im APK nicht mehr vorhanden |
| `NotoSansCJKjp-VF.ttf` komprimiert ausgeliefert | ~14,7 MB (APK) | Font jetzt `Defl:N`, 36,2 → 21,4 MB |
| `localeFilters` auf die 10 unterstützten Sprachen | ~1–2 MB (Universal-/Debug-APK) | Fremd-Locales aus `res/values-*` entfernt |

OCR- und Barcode-Modelle werden per `com.google.mlkit.vision.DEPENDENCIES = "ocr,barcode"`
bereits bei der App-Installation im Hintergrund von Play Services vorgeladen; fehlen sie
doch, lädt `ModuleInstallClient` sie beim ersten Gebrauch mit Status-UI nach.

Effekt am Debug-APK (universal, alle ABIs): **183 MB → 143 MB**. Pro ausgeliefertem
Release-Split (eine ABI, eine Sprache) fällt der Barhopper-/OCR-Anteil geringer aus,
BC-Exclude und CJK-Kompression schlagen aber voll durch. Tests und `lintDebug` grün,
App startet im Emulator fehlerfrei.

## Bewusste Abschlussentscheidungen

- **CJK-Fallback-Font bleibt vollständig erhalten.** `NotoSansCJKjp-VF.ttf` wird
  komprimiert ausgeliefert (21,4 statt 36,2 MB im APK). Ein kleineres Subset oder
  On-demand-Asset-Pack würde Abdeckung, Glyphenform oder Architektur verändern und ist
  deshalb nicht Bestandteil der abgeschlossenen Optimierung.
- **ML Kit Translate bleibt unverändert.** Es gibt keine GMS-unbundled-Variante für die
  rund 16,4 MB große native Runtime. Das Feature bleibt erhalten; ausschließlich die
  Sprachmodelle werden bei Bedarf nachgeladen.
- **PdfBox-CMaps bleiben enthalten.** Ihre Entfernung könnte importierte CJK-PDFs mit
  CID-Fonts beschädigen und widerspricht der Kompatibilitätsanforderung.

Damit bestehen keine offenen Implementierungsmaßnahmen aus diesem Review. Als
Release-Validierung sind OCR- und QR-Durchläufe nach einer frischen Installation auf
realer Hardware weiterhin sinnvoll, um den GMS-Preload unter Praxisbedingungen zu
bestätigen.
