package info.meuse24.pdf_scanner.ui.ocr

import java.util.Locale

private val supportedOcrLanguages = listOf("de", "en", "es", "fr", "pt", "ru", "ar", "hi")

fun buildOcrLanguageOptions(displayLocale: Locale): List<Pair<String, String>> {
    return supportedOcrLanguages.map { code ->
        val name = Locale.forLanguageTag(code).getDisplayLanguage(displayLocale)
            .replaceFirstChar { char ->
                if (char.isLowerCase()) char.titlecase(displayLocale) else char.toString()
            }
        code to name
    }
}

fun defaultOcrLanguage(systemLocale: Locale = Locale.getDefault()): String {
    val language = systemLocale.language
    return language.takeIf(supportedOcrLanguages::contains) ?: "en"
}
