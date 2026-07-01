package info.meuse24.pdf_scanner.util

import org.json.JSONArray

fun List<String>.toOcrPageTextJson(): String? {
    val pages = map { it.trim() }
    if (pages.none { it.isNotEmpty() }) return null
    return JSONArray(pages).toString()
}

fun String?.fromOcrPageTextJson(): List<String> {
    if (this.isNullOrBlank()) return emptyList()
    return runCatching {
        val array = JSONArray(this)
        List(array.length()) { index -> array.optString(index) }
            .map { it.trim() }
    }.getOrDefault(emptyList())
}
