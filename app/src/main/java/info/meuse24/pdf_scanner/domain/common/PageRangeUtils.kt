package info.meuse24.pdf_scanner.domain.common

fun buildRanges(pageCount: Int, splitPoints: List<Int>): List<IntRange> {
    val boundaries = listOf(0) + splitPoints.map { it + 1 } + listOf(pageCount)
    return boundaries.zipWithNext { from, to -> from until to }.filter { !it.isEmpty() }
}

fun normalizeSplitPoints(pageCount: Int, splitPoints: List<Int>): List<Int> {
    if (pageCount < 2) return emptyList()
    return splitPoints
        .filter { it in 0 until (pageCount - 1) }
        .sorted()
        .distinct()
}

fun normalizePageIndexes(pageCount: Int, pageIndexes: List<Int>): List<Int> {
    return pageIndexes
        .filter { it in 0 until pageCount }
        .sorted()
        .distinct()
}
