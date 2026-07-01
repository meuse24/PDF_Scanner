package info.meuse24.pdf_scanner.domain.common

import java.time.LocalDate

private const val MAX_ENTITIES_PER_TYPE = 3
private const val DATE_CONTEXT_RADIUS = 64

data class DetectedEntities(
    val ibans: List<String> = emptyList(),
    val amounts: List<String> = emptyList(),
    val dates: List<LocalDate> = emptyList()
) {
    val isEmpty: Boolean
        get() = ibans.isEmpty() && amounts.isEmpty() && dates.isEmpty()
}

fun detectDocumentEntities(text: String): DetectedEntities {
    if (text.isBlank()) return DetectedEntities()
    return detectNormalizedDocumentEntities(normalizeOcrText(text))
}

internal fun detectNormalizedDocumentEntities(normalized: String): DetectedEntities {
    val ibans = IBAN_CANDIDATE_REGEX.findAll(normalized)
        .mapNotNull { extractIban(it.value) }
        .distinctByNormalized { uppercase() }

    val amounts = AMOUNT_REGEX.findAll(normalized)
        .map { it.value.trim() }
        .distinctByNormalized { uppercase() }

    val dates = DATE_REGEX.findAll(normalized)
        .mapNotNull { match ->
            parseDate(match.value)?.let { date ->
                DateCandidate(
                    date = date,
                    offset = match.range.first,
                    hasDeadlineContext = hasDeadlineContext(normalized, match.range)
                )
            }
        }
        .sortedWith(compareByDescending<DateCandidate> { it.hasDeadlineContext }.thenBy { it.offset })
        .distinctBy { it.date }
        .take(MAX_ENTITIES_PER_TYPE)
        .map { it.date }
        .toList()

    return DetectedEntities(
        ibans = ibans,
        amounts = amounts,
        dates = dates
    )
}

fun normalizeOcrText(raw: String): String =
    raw.replace(HYPHENATED_LINE_BREAK_REGEX, "$1$2")
        .replace(WHITESPACE_REGEX, " ")
        .trim()

private fun parseDate(value: String): LocalDate? = runCatching {
    if ('-' in value) {
        val parts = value.split('-')
        LocalDate.of(parts[0].toInt(), parts[1].toInt(), parts[2].toInt())
    } else {
        val parts = value.split('.')
        val parsedYear = parts[2].toInt()
        val year = if (parts[2].length == 2) {
            if (parsedYear >= TWO_DIGIT_YEAR_PIVOT) 1900 + parsedYear else 2000 + parsedYear
        } else {
            parsedYear
        }
        LocalDate.of(year, parts[1].toInt(), parts[0].toInt())
    }
}.getOrNull()

private fun extractIban(candidate: String): String? {
    val expectedLength = IBAN_LENGTHS[candidate.take(2)] ?: return null
    var alphanumericCount = 0
    candidate.forEachIndexed { index, character ->
        if (character.isLetterOrDigit()) alphanumericCount++
        if (alphanumericCount == expectedLength) {
            val nextCharacter = candidate.getOrNull(index + 1)
            if (nextCharacter != null && !nextCharacter.isWhitespace()) return null
            return candidate.substring(0, index + 1).trim()
        }
    }
    return null
}

private fun hasDeadlineContext(text: String, range: IntRange): Boolean {
    val start = (range.first - DATE_CONTEXT_RADIUS).coerceAtLeast(0)
    return DEADLINE_CONTEXT_REGEX.containsMatchIn(text.substring(start, range.first))
}

private inline fun Sequence<String>.distinctByNormalized(
    transform: String.() -> String
): List<String> {
    val seen = mutableSetOf<String>()
    val result = mutableListOf<String>()
    for (value in this) {
        if (seen.add(value.transform())) {
            result += value
            if (result.size == MAX_ENTITIES_PER_TYPE) break
        }
    }
    return result
}

private data class DateCandidate(
    val date: LocalDate,
    val offset: Int,
    val hasDeadlineContext: Boolean
)

private val AMOUNT_REGEX =
    Regex("""(?<!\d)\d{1,3}(?:[.\s]\d{3})*[,.]\d{2}\s*(?:€|EUR)(?!\p{L})""")

private val IBAN_CANDIDATE_REGEX =
    Regex("""(?<![A-Z0-9])[A-Z]{2}\d{2}(?:\s?[A-Z0-9]){11,40}""")

private val IBAN_LENGTHS = mapOf(
    "AD" to 24, "AE" to 23, "AL" to 28, "AT" to 20, "AZ" to 28,
    "BA" to 20, "BE" to 16, "BG" to 22, "BH" to 22, "BR" to 29,
    "BY" to 28, "CH" to 21, "CR" to 22, "CY" to 28, "CZ" to 24,
    "DE" to 22, "DK" to 18, "DO" to 28, "EE" to 20, "ES" to 24,
    "FI" to 18, "FO" to 18, "FR" to 27, "GB" to 22, "GE" to 22,
    "GI" to 23, "GL" to 18, "GR" to 27, "GT" to 28, "HR" to 21,
    "HU" to 28, "IE" to 22, "IL" to 23, "IQ" to 23, "IS" to 26,
    "IT" to 27, "JO" to 30, "KW" to 30, "KZ" to 20, "LB" to 28,
    "LC" to 32, "LI" to 21, "LT" to 20, "LU" to 20, "LV" to 21,
    "MC" to 27, "MD" to 24, "ME" to 22, "MK" to 19, "MR" to 27,
    "MT" to 31, "MU" to 30, "NL" to 18, "NO" to 15, "PK" to 24,
    "PL" to 28, "PS" to 29, "PT" to 25, "QA" to 29, "RO" to 24,
    "RS" to 22, "SA" to 24, "SC" to 31, "SE" to 24, "SI" to 19,
    "SK" to 24, "SM" to 27, "ST" to 25, "TL" to 23, "TN" to 24,
    "TR" to 26, "UA" to 29, "VA" to 22, "VG" to 24, "XK" to 20
)

private val DATE_REGEX = Regex(
    """(?<!\d)(?:\d{1,2}\.\d{1,2}\.(?:\d{2}|\d{4})|\d{4}-\d{1,2}-\d{1,2})(?!\d)"""
)

private val DEADLINE_CONTEXT_REGEX = Regex(
    """(?:fällig(?:keit|keitsdatum)?|zahlbar\s+bis|zahlungsziel|frist|gültig\s+bis|due\s+date|payable\s+by|deadline|valid\s+until|fecha\s+de\s+vencimiento|pagadero\s+hasta|date\s+d['’]échéance|à\s+payer\s+avant|data\s+de\s+vencimento|pagar\s+até|срок\s+оплаты|оплатить\s+до|تاريخ\s+الاستحقاق|الدفع\s+قبل|नियत\s+तारीख|भुगतान\s+की\s+अंतिम\s+तिथि|截止日期|付款期限|支払期限)""",
    RegexOption.IGNORE_CASE
)

private const val TWO_DIGIT_YEAR_PIVOT = 50
private val HYPHENATED_LINE_BREAK_REGEX = Regex("""(\w)-\s*\n\s*(\w)""")
private val WHITESPACE_REGEX = Regex("""\s+""")
