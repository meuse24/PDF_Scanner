package info.meuse24.pdf_scanner.domain.common

fun findMatchingPages(pageTexts: List<String>, query: String): List<Int> {
    val normalizedQuery = query.trim()
    if (normalizedQuery.isEmpty()) return emptyList()
    return pageTexts.mapIndexedNotNull { pageIndex, pageText ->
        pageIndex.takeIf { pageText.contains(normalizedQuery, ignoreCase = true) }
    }
}

data class PdfSearchMatch(
    val pageIndex: Int,
    val rawRange: IntRange
)

data class NormalizedPageText(
    val text: String,
    val sourceIndex: IntArray
)

fun findMatches(pageTexts: List<String>, query: String): List<PdfSearchMatch> {
    val normalizedQuery = normalizeForSearch(query).text.trim()
    if (normalizedQuery.isEmpty()) return emptyList()
    return pageTexts.flatMapIndexed { pageIndex, pageText ->
        val normalizedPage = normalizeForSearch(pageText)
        buildList {
            var searchStart = 0
            while (searchStart < normalizedPage.text.length) {
                val matchStart = normalizedPage.text.indexOf(normalizedQuery, searchStart, ignoreCase = true)
                if (matchStart < 0) break
                val matchEnd = matchStart + normalizedQuery.length - 1
                val rawStart = normalizedPage.sourceIndex[matchStart]
                val rawEnd = normalizedPage.sourceIndex[matchEnd]
                add(PdfSearchMatch(pageIndex, rawStart..rawEnd))
                searchStart = matchStart + normalizedQuery.length
            }
        }
    }
}

fun normalizeForSearch(raw: String): NormalizedPageText {
    val output = StringBuilder(raw.length)
    val indexes = mutableListOf<Int>()
    var index = 0
    while (index < raw.length) {
        val char = raw[index]
        if (char == '\u00AD') {
            index++
            continue
        }
        if (char == '-' && index + 1 < raw.length && raw[index + 1].isWhitespace()) {
            var next = index + 1
            while (next < raw.length && raw[next].isWhitespace()) next++
            index = next
            continue
        }
        if (char.isWhitespace() || char == '\u00A0') {
            val source = index
            while (index < raw.length && (raw[index].isWhitespace() || raw[index] == '\u00A0')) index++
            if (output.isNotEmpty() && output.last() != ' ') {
                output.append(' ')
                indexes += source
            }
            continue
        }
        output.append(char)
        indexes += index
        index++
    }
    return NormalizedPageText(output.toString(), indexes.toIntArray())
}
