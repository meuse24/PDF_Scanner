package info.meuse24.pdf_scanner.ui.ocr

import info.meuse24.pdf_scanner.domain.model.supportsSearchablePdfTextLayer
import java.util.Locale

/** Sprachcode für den automatischen Erkennungsmodus (Latin-Recognizer, geeignet für die meisten Schriften). */
const val OCR_LANGUAGE_AUTO = "auto"

private val supportedOcrLanguages = listOf("de", "en", "es", "fr", "pt", "ru", "ar", "hi", "zh", "ja", "ko")

/**
 * Erstellt die Sprachliste für OCR-Dropdowns.
 * @param autoLabel lokalisierter Anzeigetext für den automatischen Modus (z. B. "Automatisch")
 */
fun buildOcrLanguageOptions(autoLabel: String, displayLocale: Locale): List<Pair<String, String>> {
    val manual = supportedOcrLanguages.map { code ->
        val name = Locale.forLanguageTag(code).getDisplayLanguage(displayLocale)
            .replaceFirstChar { char ->
                if (char.isLowerCase()) char.titlecase(displayLocale) else char.toString()
            }
        code to name
    }
    return listOf(OCR_LANGUAGE_AUTO to autoLabel) + manual
}

fun buildSearchablePdfLanguageOptions(
    autoLabel: String,
    displayLocale: Locale
): List<Pair<String, String>> =
    buildOcrLanguageOptions(autoLabel, displayLocale)
        .filter { (code, _) -> code.supportsSearchablePdfTextLayer() }

fun searchablePdfLanguageOrAuto(languageCode: String): String =
    languageCode.takeIf(String::supportsSearchablePdfTextLayer) ?: OCR_LANGUAGE_AUTO

/**
 * Gibt den Standard-OCR-Sprachcode zurück.
 *
 * Für Schriften, die einen dedizierten Nicht-Latin-Recognizer benötigen (hi, zh, ja, ko),
 * wird der Locale-Code zurückgegeben — OCR-Qualität wäre mit dem Latin-Recognizer
 * deutlich schlechter. Alle anderen Systemlocales landen auf "auto" (Latin, offline).
 */
fun defaultOcrLanguage(systemLocale: Locale = Locale.getDefault()): String {
    val language = systemLocale.language
    return if (language in setOf("hi", "zh", "ja", "ko")) language else OCR_LANGUAGE_AUTO
}
