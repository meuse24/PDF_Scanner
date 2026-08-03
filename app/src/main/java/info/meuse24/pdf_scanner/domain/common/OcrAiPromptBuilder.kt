package info.meuse24.pdf_scanner.domain.common

object OcrAiPromptBuilder {
    const val MAX_PROMPT_CHARS = 20_000

    private const val startMarker = "--- BEGIN OCR TEXT ---"
    private const val endMarker = "--- END OCR TEXT ---"
    private const val pageSeparator = "\n\n"

    data class Result(
        val prompt: String? = null,
        val tooLong: Boolean = false,
        val empty: Boolean = false
    )

    fun build(
        instruction: String,
        pageHint: String?,
        ocrText: String,
        languageRule: String? = null
    ): Result {
        if (ocrText.isBlank()) return Result(empty = true)
        val escapedText = escapeMarkerLines(ocrText)
        val prompt = buildString {
            append(instruction.trim())
            append("\n\n")
            languageRule?.trim()?.takeIf { it.isNotEmpty() }?.let {
                append(it)
                append("\n\n")
            }
            pageHint?.trim()?.takeIf { it.isNotEmpty() }?.let {
                append(it)
                append("\n\n")
            }
            append(startMarker)
            append("\n")
            append(escapedText)
            append("\n")
            append(endMarker)
        }
        return if (prompt.length > MAX_PROMPT_CHARS) Result(tooLong = true) else Result(prompt = prompt)
    }

    fun resolveOcrText(extractedText: String?, pageTexts: List<String>): String =
        extractedText?.takeIf { it.isNotBlank() } ?: pageTexts.joinToString(pageSeparator)

    private fun escapeMarkerLines(text: String): String = text
        .split("\n")
        .joinToString("\n") { line ->
            if (line.trim() == startMarker || line.trim() == endMarker) {
                line.replace("---", "- - -")
            } else {
                line
            }
        }
}
