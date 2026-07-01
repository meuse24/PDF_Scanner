package info.meuse24.pdf_scanner.domain.common

fun findMatchingPages(pageTexts: List<String>, query: String): List<Int> {
    val normalizedQuery = query.trim()
    if (normalizedQuery.isEmpty()) return emptyList()
    return pageTexts.mapIndexedNotNull { pageIndex, pageText ->
        pageIndex.takeIf { pageText.contains(normalizedQuery, ignoreCase = true) }
    }
}
