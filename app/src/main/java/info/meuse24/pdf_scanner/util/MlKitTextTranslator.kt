package info.meuse24.pdf_scanner.util

import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import info.meuse24.pdf_scanner.domain.gateway.TextTranslator
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MlKitTextTranslator @Inject constructor() : TextTranslator {

    override suspend fun translate(
        pageTexts: List<String>,
        sourceLanguage: String,
        targetLanguage: String,
        onProgress: (current: Int, total: Int) -> Unit
    ): List<String> {
        val options = TranslatorOptions.Builder()
            .setSourceLanguage(sourceLanguage)
            .setTargetLanguage(targetLanguage)
            .build()
        val translator = Translation.getClient(options)
        return try {
            translator.downloadModelIfNeeded(DownloadConditions.Builder().build()).awaitTask()
            pageTexts.mapIndexed { index, text ->
                onProgress(index + 1, pageTexts.size)
                translator.translate(text).awaitTask()
            }
        } finally {
            translator.close()
        }
    }
}
