package info.meuse24.pdf_scanner.domain.common

private val sentenceEndRegex = Regex("""[.!?。！？؟]$""")
private val latinHyphenationRegex = Regex("""\p{L}-$""")
private val latinContinuationRegex = Regex("""^\p{Ll}""")

fun linesToParagraphs(lines: List<String>): List<String> {
    val normalized = lines.map { it.trim() }
    val contentLengths = normalized
        .filter { it.isNotBlank() }
        .map { it.length }
        .sorted()
    val medianLength = contentLengths
        .takeIf { it.isNotEmpty() }
        ?.let { it[it.size / 2] }
        ?: 0

    val paragraphs = mutableListOf<String>()
    val current = StringBuilder()

    fun flush() {
        val text = current.toString().trim()
        if (text.isNotEmpty()) paragraphs.add(text)
        current.clear()
    }

    normalized.forEachIndexed { index, line ->
        if (line.isBlank()) {
            flush()
            return@forEachIndexed
        }

        val nextLine = normalized.drop(index + 1).firstOrNull { it.isNotBlank() }
        appendLineToParagraph(current, line)

        if (shouldEndParagraph(line, nextLine, medianLength)) {
            flush()
        }
    }

    flush()
    return paragraphs
}

private fun appendLineToParagraph(
    current: StringBuilder,
    line: String
) {
    if (current.isEmpty()) {
        current.append(line)
        return
    }

    if (current.endsWithLatinHyphenation() && latinContinuationRegex.containsMatchIn(line)) {
        current.deleteCharAt(current.lastIndex)
        current.append(line)
    } else {
        current.append(' ')
        current.append(line)
    }
}

private fun shouldEndParagraph(
    line: String,
    nextLine: String?,
    medianLength: Int
): Boolean {
    if (nextLine == null) return true
    if (line.endsWithLatinHyphenation()) return false
    if (sentenceEndRegex.containsMatchIn(line)) {
        return !latinContinuationRegex.containsMatchIn(nextLine)
    }
    if (line.length <= (medianLength * 0.55f).toInt().coerceAtLeast(1)) return true
    return false
}

private fun StringBuilder.endsWithLatinHyphenation(): Boolean =
    latinHyphenationRegex.containsMatchIn(toString())

private fun String.endsWithLatinHyphenation(): Boolean =
    latinHyphenationRegex.containsMatchIn(this)
