package info.meuse24.pdf_scanner.util

import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Liefert den passenden ML Kit TextRecognizer für einen ISO-639-1-Sprachcode.
 *
 * ZH/JA/HI: GMS-unbundled (Modell beim ersten Aufruf automatisch heruntergeladen).
 * RU: Latin-Recognizer — ML Kit v2 (16.x) unterstützt Kyrillisch im Latin-Bundle.
 * AR: Latin-Fallback — ein dediziertes `text-recognition-arabic`-Artifact ist in
 *     Google Maven (16.x) nicht veröffentlicht; OCR-Qualität für Arabisch ist
 *     dadurch eingeschränkt (bekannte v1-Limitierung).
 * EN/DE/ES/FR/PT: Latin-Recognizer (gebündelt, immer offline verfügbar).
 */
@Singleton
class OcrManager @Inject constructor() {

    fun getRecognizer(languageCode: String): TextRecognizer = when (languageCode) {
        "zh" -> TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
        "ja" -> TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build())
        "hi" -> TextRecognition.getClient(DevanagariTextRecognizerOptions.Builder().build())
        else -> TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }
}
