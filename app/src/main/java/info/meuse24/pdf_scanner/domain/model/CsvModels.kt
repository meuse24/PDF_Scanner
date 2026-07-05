package info.meuse24.pdf_scanner.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class CsvDelimiter(val char: Char) {
    COMMA(','),
    SEMICOLON(';'),
    TAB('\t')
}

/**
 * Explizite Export-Konfiguration für [CsvEncoder]. RFC 4180 definiert Komma als Trennzeichen;
 * Semikolon und Tab sind RFC-4180-nahe Dialekte mit denselben Quoting-Regeln, nicht strikt
 * RFC 4180.
 */
data class DelimitedTextDialect(
    val delimiter: CsvDelimiter = CsvDelimiter.COMMA,
    val protectFormulas: Boolean = true
) {
    val fileExtension: String get() = if (delimiter == CsvDelimiter.TAB) "tsv" else "csv"
    val mimeType: String get() = if (delimiter == CsvDelimiter.TAB) "text/tab-separated-values" else "text/csv"
}
