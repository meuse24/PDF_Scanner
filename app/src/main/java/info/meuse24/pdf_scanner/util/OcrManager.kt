package info.meuse24.pdf_scanner.util

import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import info.meuse24.pdf_scanner.domain.model.OcrScript
import info.meuse24.pdf_scanner.domain.model.OcrUsage
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Liefert den passenden ML Kit TextRecognizer für einen ISO-639-1-Sprachcode.
 *
 * ZH/JA/KO/HI: GMS-unbundled (Modell beim ersten Aufruf automatisch heruntergeladen).
 * RU: Latin-Recognizer — ML Kit v2 (16.x) unterstützt Kyrillisch im Latin-Bundle.
 * AR: Latin-Fallback — ein dediziertes `text-recognition-arabic`-Artifact ist in
 *     Google Maven (16.x) nicht veröffentlicht; OCR-Qualität für Arabisch ist
 *     dadurch eingeschränkt (bekannte v1-Limitierung).
 * EN/DE/ES/FR/PT: Latin-Recognizer (gebündelt, immer offline verfügbar).
 * "auto" (OCR_LANGUAGE_AUTO): Latin-Recognizer — Standard-Automatik-Modus, offline.
 */
@Singleton
class OcrManager @Inject constructor() {

    fun getRecognizer(script: OcrScript): TextRecognizer = when (script) {
        OcrScript.CHINESE -> TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
        OcrScript.JAPANESE -> TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build())
        OcrScript.DEVANAGARI -> TextRecognition.getClient(DevanagariTextRecognizerOptions.Builder().build())
        OcrScript.KOREAN -> TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())
        OcrScript.LATIN -> TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    fun getRecognizer(languageCode: String): TextRecognizer = when (languageCode) {
        "zh" -> getRecognizer(OcrScript.CHINESE)
        "ja" -> getRecognizer(OcrScript.JAPANESE)
        "hi" -> getRecognizer(OcrScript.DEVANAGARI)
        "ko" -> getRecognizer(OcrScript.KOREAN)
        else -> getRecognizer(OcrScript.LATIN)
    }

    fun recognitionPlan(
        languageCode: String,
        usage: OcrUsage
    ): List<OcrScript> {
        val manualScript = scriptForLanguageCode(languageCode)
        if (manualScript != null) return listOf(manualScript)

        return when (usage) {
            // Automatik bleibt offline und verwendet ausschließlich das gebündelte Latin-Modell.
            // Nicht-lateinische Modelle werden nur durch eine konkrete Sprachauswahl aktiviert.
            OcrUsage.EXTRACT_TEXT,
            OcrUsage.SEARCHABLE_PDF -> listOf(OcrScript.LATIN)
            // Barcode-Scanning nutzt keinen TextRecognizer — kein Fallback erforderlich.
            OcrUsage.SCAN_BARCODES -> emptyList()
        }
    }

    private fun scriptForLanguageCode(languageCode: String): OcrScript? = when (languageCode) {
        "de", "en", "es", "fr", "pt", "ru", "ar" -> OcrScript.LATIN
        "hi" -> OcrScript.DEVANAGARI
        "ja" -> OcrScript.JAPANESE
        "zh" -> OcrScript.CHINESE
        "ko" -> OcrScript.KOREAN
        else -> null
    }
}

fun OcrScript.requiresModuleDownload(): Boolean = this != OcrScript.LATIN
